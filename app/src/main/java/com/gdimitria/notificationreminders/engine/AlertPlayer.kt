package com.gdimitria.notificationreminders.engine

import android.app.NotificationManager
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import com.gdimitria.notificationreminders.data.Store
import com.gdimitria.notificationreminders.model.FlashPatterns
import com.gdimitria.notificationreminders.model.ReminderConfig
import com.gdimitria.notificationreminders.model.SoundChannel
import com.gdimitria.notificationreminders.model.VibrationPatterns
import com.gdimitria.notificationreminders.model.VibrationType
import kotlin.math.roundToInt

/** Plays one reminder: melody, vibration, screen wake-up and flash blinking for the configured duration. */
object AlertPlayer {

    private const val TAG = "AlertPlayer"
    private val handler = Handler(Looper.getMainLooper())

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var focusRequest: AudioFocusRequest? = null
    private var restoreVolume: Pair<Int, Int>? = null

    /** Whether the sound lock was told about a temporary volume change that is still in progress. */
    private var appChange = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenLock: PowerManager.WakeLock? = null
    private var torch: Pair<CameraManager, String>? = null
    private var flashSteps: List<Pair<Long, Boolean>> = emptyList()
    private var flashIndex = 0
    private var appContext: Context? = null

    private val stopRunnable = Runnable { stopNow() }
    private val flashRunnable = object : Runnable {
        override fun run() {
            val (camera, id) = torch ?: return
            val (length, on) = flashSteps.getOrNull(flashIndex++) ?: return setTorch(camera, id, false)
            setTorch(camera, id, on)
            handler.postDelayed(this, length)
        }
    }

    /** @param ignoreSilentMode true for test alerts started from the UI. */
    fun play(context: Context, config: ReminderConfig, ignoreSilentMode: Boolean = false) {
        val app = context.applicationContext
        handler.post { start(app, config, ignoreSilentMode) }
    }

    fun stop() {
        handler.post { stopNow() }
    }

    private fun start(context: Context, config: ReminderConfig, ignoreSilentMode: Boolean) {
        stopNow()
        appContext = context
        val audio = context.getSystemService(AudioManager::class.java)

        var sound = config.soundEnabled && config.volumePercent > 0
        var vibrate = config.vibrationType != VibrationType.NONE
        // Screen and flash are silent signals, so only Do Not Disturb suppresses them.
        var screen = config.screenOn
        var flash = config.flashEnabled
        if (config.respectSilentMode && !ignoreSilentMode) {
            when (audio.ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> { sound = false; vibrate = false }
                AudioManager.RINGER_MODE_VIBRATE -> sound = false
            }
            val filter = context.getSystemService(NotificationManager::class.java).currentInterruptionFilter
            if (filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            ) {
                sound = false
                vibrate = false
                screen = false
                flash = false
            }
        }
        if (!sound && !vibrate && !screen && !flash) return

        val durationMs = config.alertDurationSeconds.coerceIn(1, 120) * 1000L
        wakeLock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NotificationReminders:alert")
            .apply { acquire(durationMs + 3000) }

        if (sound) startSound(context, audio, config)
        if (vibrate) startVibration(context, config, durationMs)
        if (screen) wakeScreen(context, durationMs)
        if (flash) startFlash(context, config, durationMs)
        handler.postDelayed(stopRunnable, durationMs)
    }

    private fun startSound(context: Context, audio: AudioManager, config: ReminderConfig) {
        val (usage, stream) = when (config.soundChannel) {
            SoundChannel.NOTIFICATION -> AudioAttributes.USAGE_NOTIFICATION to AudioManager.STREAM_NOTIFICATION
            SoundChannel.ALARM -> AudioAttributes.USAGE_ALARM to AudioManager.STREAM_ALARM
            SoundChannel.RINGTONE -> AudioAttributes.USAGE_NOTIFICATION_RINGTONE to AudioManager.STREAM_RING
            SoundChannel.MEDIA -> AudioAttributes.USAGE_MEDIA to AudioManager.STREAM_MUSIC
        }
        val attributes = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        var playerVolume = config.volumePercent / 100f
        // A sound lock that also restricts this app forbids raising the volume.
        val allowed = !Store.get(context).soundProfiles.value.lockedForApp
        if (config.forceVolume && allowed) {
            try {
                val max = audio.getStreamMaxVolume(stream)
                val previous = audio.getStreamVolume(stream)
                val target = (max * config.volumePercent / 100f).roundToInt().coerceIn(1, max)
                SoundLock.beginAppChange()
                appChange = true
                audio.setStreamVolume(stream, target, 0)
                restoreVolume = stream to previous
                playerVolume = 1f
            } catch (e: SecurityException) {
                // Changing ring or notification volume is refused while Do Not Disturb is on.
                Log.w(TAG, "Cannot change stream volume", e)
            }
        }

        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
            .also { audio.requestAudioFocus(it) }

        val defaultType = if (config.soundChannel == SoundChannel.ALARM) {
            RingtoneManager.TYPE_ALARM
        } else {
            RingtoneManager.TYPE_NOTIFICATION
        }
        val candidates = listOfNotNull(
            config.soundUri?.let(Uri::parse),
            RingtoneManager.getActualDefaultRingtoneUri(context, defaultType),
            Settings.System.DEFAULT_NOTIFICATION_URI,
        )
        for (uri in candidates) {
            val mediaPlayer = MediaPlayer()
            try {
                mediaPlayer.setAudioAttributes(attributes)
                mediaPlayer.setDataSource(context, uri)
                mediaPlayer.isLooping = config.loopSound
                mediaPlayer.setVolume(playerVolume, playerVolume)
                mediaPlayer.prepare()
                mediaPlayer.start()
                player = mediaPlayer
                return
            } catch (e: Exception) {
                Log.w(TAG, "Cannot play $uri", e)
                mediaPlayer.release()
            }
        }
    }

    private fun startVibration(context: Context, config: ReminderConfig, durationMs: Long) {
        val (timings, amplitudes) =
            VibrationPatterns.build(config.vibrationType, config.vibrationIntensity, durationMs)
        if (timings.isEmpty()) return
        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        if (!vib.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
        // Alarm usage keeps vibrating when notification haptics are muted by the system.
        val alarmLike = config.soundChannel == SoundChannel.ALARM || !config.respectSilentMode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val usage = if (alarmLike) VibrationAttributes.USAGE_ALARM else VibrationAttributes.USAGE_NOTIFICATION
            vib.vibrate(effect, VibrationAttributes.createForUsage(usage))
        } else {
            val usage = if (alarmLike) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION
            @Suppress("DEPRECATION")
            vib.vibrate(effect, AudioAttributes.Builder().setUsage(usage).build())
        }
        vibrator = vib
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen(context: Context, durationMs: Long) {
        val power = context.getSystemService(PowerManager::class.java)
        if (power.isInteractive) return
        // Deprecated but still the only way for a background app to light the screen without an activity.
        screenLock = power.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "NotificationReminders:screen",
        ).apply { acquire(durationMs) }
    }

    private fun startFlash(context: Context, config: ReminderConfig, durationMs: Long) {
        val camera = context.getSystemService(CameraManager::class.java)
        val id = runCatching {
            camera.cameraIdList.firstOrNull {
                camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull() ?: return
        torch = camera to id
        flashSteps = FlashPatterns.build(config.flashPattern, durationMs)
        flashIndex = 0
        handler.post(flashRunnable)
    }

    private fun setTorch(camera: CameraManager, id: String, on: Boolean) {
        try {
            camera.setTorchMode(id, on)
        } catch (e: Exception) {
            // The camera is in use by another app.
            Log.w(TAG, "Cannot switch the flash", e)
        }
    }

    private fun stopNow() {
        handler.removeCallbacks(stopRunnable)
        handler.removeCallbacks(flashRunnable)
        torch?.let { (camera, id) -> setTorch(camera, id, false) }
        torch = null
        screenLock?.let { if (it.isHeld) it.release() }
        screenLock = null
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
        val audio = appContext?.getSystemService(AudioManager::class.java)
        focusRequest?.let { audio?.abandonAudioFocusRequest(it) }
        focusRequest = null
        restoreVolume?.let { (stream, volume) ->
            runCatching { audio?.setStreamVolume(stream, volume, 0) }
        }
        restoreVolume = null
        if (appChange) {
            appChange = false
            SoundLock.endAppChange()
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
}
