package com.gdimitria.notificationreminders.ui

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibratorManager
import android.os.Vibrator
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gdimitria.notificationreminders.R
import com.gdimitria.notificationreminders.engine.AlertPlayer
import com.gdimitria.notificationreminders.model.FlashPattern
import com.gdimitria.notificationreminders.model.ReminderConfig
import com.gdimitria.notificationreminders.model.SoundChannel
import com.gdimitria.notificationreminders.model.TimeWindow
import com.gdimitria.notificationreminders.model.VibrationType

/** Editor for every reminder option. Each change is reported immediately through [onChange]. */
@Composable
fun ConfigEditor(config: ReminderConfig, onChange: (ReminderConfig) -> Unit) {
    RepetitionSection(config, onChange)
    VibrationSection(config, onChange)
    SoundSection(config, onChange)
    LightSection(config, onChange)
    BehaviourSection(config, onChange)
    QuietWifiSection(config.quietWifi) { quiet -> onChange(config.copy(quietWifi = quiet)) }
}

@Composable
private fun RepetitionSection(config: ReminderConfig, onChange: (ReminderConfig) -> Unit) {
    val context = LocalContext.current
    SectionCard(stringResource(R.string.section_repetition)) {
        StepSlider(
            title = stringResource(R.string.repeat_every),
            values = Options.intervalsSeconds,
            value = config.intervalSeconds,
            label = { context.formatSeconds(it) },
            onValueChange = { onChange(config.copy(intervalSeconds = it)) },
        )
        StepSlider(
            title = stringResource(R.string.alert_duration),
            values = Options.alertDurationsSeconds,
            value = config.alertDurationSeconds,
            label = { context.formatSeconds(it) },
            onValueChange = { onChange(config.copy(alertDurationSeconds = it)) },
        )
        StepSlider(
            title = stringResource(R.string.keep_reminding_for),
            values = Options.maxDurationsMinutes,
            value = config.maxDurationMinutes,
            label = { if (it == 0) context.getString(R.string.until_dismissed) else context.formatMinutes(it) },
            onValueChange = { onChange(config.copy(maxDurationMinutes = it)) },
        )
        StepSlider(
            title = stringResource(R.string.max_repeats),
            values = Options.maxRepeats,
            value = config.maxRepeats,
            label = { if (it == 0) context.getString(R.string.unlimited) else it.toString() },
            onValueChange = { onChange(config.copy(maxRepeats = it)) },
        )
        SwitchRow(
            title = stringResource(R.string.restart_on_new),
            subtitle = stringResource(R.string.restart_on_new_hint),
            checked = config.restartOnNewNotification,
            onCheckedChange = { onChange(config.copy(restartOnNewNotification = it)) },
        )
    }
}

/** Editor for a list of time windows with their days of the week. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimeWindowsEditor(windows: List<TimeWindow>, emptyText: String, onChange: (List<TimeWindow>) -> Unit) {
    val context = LocalContext.current
    val days = remember { dayLabels() }

    fun replace(index: Int, window: TimeWindow) = onChange(windows.toMutableList().also { it[index] = window })

    fun pickTime(initial: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            context,
            { _, hour, minute -> onPicked(hour * 60 + minute) },
            initial / 60, initial % 60,
            android.text.format.DateFormat.is24HourFormat(context),
        ).show()
    }

    if (windows.isEmpty()) {
        Text(emptyText, style = MaterialTheme.typography.bodyMedium)
    }
    windows.forEachIndexed { index, window ->
        if (index > 0) HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { pickTime(window.startMinute) { replace(index, window.copy(startMinute = it)) } }) {
                Text(context.formatClock(window.startMinute), style = MaterialTheme.typography.titleMedium)
            }
            Text("–")
            TextButton(onClick = { pickTime(window.endMinute) { replace(index, window.copy(endMinute = it)) } }) {
                Text(context.formatClock(window.endMinute), style = MaterialTheme.typography.titleMedium)
            }
            if (window.endMinute <= window.startMinute) {
                Text(
                    stringResource(if (window.endMinute == window.startMinute) R.string.all_day else R.string.next_day),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { onChange(windows.filterIndexed { i, _ -> i != index }) }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove))
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            days.forEach { (day, name) ->
                FilterChip(
                    selected = day in window.days,
                    onClick = {
                        val updated = if (day in window.days) window.days - day else window.days + day
                        replace(index, window.copy(days = updated))
                    },
                    label = { Text(name) },
                )
            }
        }
    }
    OutlinedButton(onClick = { onChange(windows + TimeWindow()) }) {
        Icon(Icons.Default.Add, contentDescription = null)
        Text(stringResource(R.string.add_time_window))
    }
}

@Composable
private fun VibrationSection(config: ReminderConfig, onChange: (ReminderConfig) -> Unit) {
    val context = LocalContext.current
    val hasAmplitude = remember {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        vibrator.hasAmplitudeControl()
    }
    SectionCard(stringResource(R.string.section_vibration)) {
        DropdownRow(
            title = stringResource(R.string.vibration_type),
            options = VibrationType.entries,
            selected = config.vibrationType,
            label = { stringResource(it.labelRes()) },
            onSelect = { onChange(config.copy(vibrationType = it)) },
        )
        if (config.vibrationType != VibrationType.NONE) {
            PercentSlider(
                title = stringResource(R.string.vibration_intensity),
                value = (config.vibrationIntensity * 100 / 255).coerceIn(1, 100),
                range = 1..100,
                onValueChange = { onChange(config.copy(vibrationIntensity = (it * 255 / 100).coerceIn(1, 255))) },
                enabled = hasAmplitude,
            )
            if (!hasAmplitude) {
                Text(stringResource(R.string.no_amplitude_control), style = MaterialTheme.typography.bodySmall)
            }
            TestButton(stringResource(R.string.test_vibration)) {
                AlertPlayer.play(context, config.copy(soundEnabled = false), ignoreSilentMode = true)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SoundSection(config: ReminderConfig, onChange: (ReminderConfig) -> Unit) {
    val context = LocalContext.current

    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
        }
        val name = uri?.let { runCatching { RingtoneManager.getRingtone(context, it)?.getTitle(context) }.getOrNull() }
        onChange(config.copy(soundUri = uri?.toString(), soundName = name))
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
        onChange(config.copy(soundUri = uri.toString(), soundName = name))
    }

    SectionCard(stringResource(R.string.section_sound)) {
        SwitchRow(
            title = stringResource(R.string.play_sound),
            checked = config.soundEnabled,
            onCheckedChange = { onChange(config.copy(soundEnabled = it)) },
        )
        if (!config.soundEnabled) return@SectionCard

        Text(stringResource(R.string.melody), style = MaterialTheme.typography.bodyLarge)
        Text(
            config.soundName ?: stringResource(R.string.default_sound),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val type = when (config.soundChannel) {
                    SoundChannel.ALARM -> RingtoneManager.TYPE_ALARM
                    SoundChannel.RINGTONE -> RingtoneManager.TYPE_RINGTONE
                    else -> RingtoneManager.TYPE_NOTIFICATION
                }
                ringtonePicker.launch(
                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, type)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, config.soundUri?.let(Uri::parse))
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(R.string.melody)),
                )
            }) { Text(stringResource(R.string.system_sounds)) }
            OutlinedButton(onClick = { filePicker.launch(arrayOf("audio/*")) }) {
                Text(stringResource(R.string.audio_file))
            }
            if (config.soundUri != null) {
                TextButton(onClick = { onChange(config.copy(soundUri = null, soundName = null)) }) {
                    Text(stringResource(R.string.reset_default))
                }
            }
        }
        PercentSlider(
            title = stringResource(R.string.volume),
            value = config.volumePercent,
            range = 0..100,
            onValueChange = { onChange(config.copy(volumePercent = it)) },
        )
        DropdownRow(
            title = stringResource(R.string.audio_channel),
            options = SoundChannel.entries,
            selected = config.soundChannel,
            label = { stringResource(it.labelRes()) },
            onSelect = { onChange(config.copy(soundChannel = it)) },
        )
        SwitchRow(
            title = stringResource(R.string.force_volume),
            subtitle = stringResource(R.string.force_volume_hint),
            checked = config.forceVolume,
            onCheckedChange = { onChange(config.copy(forceVolume = it)) },
        )
        TestButton(stringResource(R.string.test_sound)) {
            AlertPlayer.play(context, config.copy(vibrationType = VibrationType.NONE), ignoreSilentMode = true)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LightSection(config: ReminderConfig, onChange: (ReminderConfig) -> Unit) {
    val context = LocalContext.current
    val hasFlash = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH) }
    SectionCard(stringResource(R.string.section_light)) {
        SwitchRow(
            title = stringResource(R.string.screen_on),
            subtitle = stringResource(R.string.screen_on_hint),
            checked = config.screenOn,
            onCheckedChange = { onChange(config.copy(screenOn = it)) },
        )
        HorizontalDivider()
        SwitchRow(
            title = stringResource(R.string.flash),
            subtitle = stringResource(if (hasFlash) R.string.flash_hint else R.string.no_flash),
            checked = config.flashEnabled,
            onCheckedChange = { onChange(config.copy(flashEnabled = it)) },
            enabled = hasFlash,
        )
        if (config.flashEnabled && hasFlash) {
            DropdownRow(
                title = stringResource(R.string.flash_pattern),
                options = FlashPattern.entries,
                selected = config.flashPattern,
                label = { stringResource(it.labelRes()) },
                onSelect = { onChange(config.copy(flashPattern = it)) },
            )
            TestButton(stringResource(R.string.test_flash)) {
                AlertPlayer.play(
                    context,
                    config.copy(soundEnabled = false, vibrationType = VibrationType.NONE, screenOn = false),
                    ignoreSilentMode = true,
                )
            }
        }
        HorizontalDivider()
        SwitchRow(
            title = stringResource(R.string.led),
            subtitle = stringResource(R.string.led_hint),
            checked = config.ledEnabled,
            onCheckedChange = { onChange(config.copy(ledEnabled = it)) },
        )
        if (config.ledEnabled) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Options.ledColors.forEach { (color, name) ->
                    val selected = config.ledColor == color
                    val description = stringResource(name)
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = CircleShape,
                            )
                            .semantics { contentDescription = description; this.selected = selected }
                            .clickable { onChange(config.copy(ledColor = color)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BehaviourSection(config: ReminderConfig, onChange: (ReminderConfig) -> Unit) {
    SectionCard(stringResource(R.string.section_behaviour)) {
        SwitchRow(
            title = stringResource(R.string.respect_silent),
            subtitle = stringResource(R.string.respect_silent_hint),
            checked = config.respectSilentMode,
            onCheckedChange = { onChange(config.copy(respectSilentMode = it)) },
        )
        SwitchRow(
            title = stringResource(R.string.ignore_silent_notifications),
            subtitle = stringResource(R.string.ignore_silent_notifications_hint),
            checked = config.ignoreSilentNotifications,
            onCheckedChange = { onChange(config.copy(ignoreSilentNotifications = it)) },
        )
    }
}

@Composable
fun TestButton(text: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onClick) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Text(text)
        }
        TextButton(onClick = { AlertPlayer.stop() }) { Text(stringResource(R.string.stop)) }
    }
}
