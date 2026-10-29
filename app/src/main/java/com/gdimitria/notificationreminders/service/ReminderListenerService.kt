package com.gdimitria.notificationreminders.service

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.notification.NotificationListenerService
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.time.ZoneId
import com.gdimitria.notificationreminders.data.Store
import com.gdimitria.notificationreminders.engine.ContactExceptions
import com.gdimitria.notificationreminders.engine.ProfileStatusNotifier
import com.gdimitria.notificationreminders.engine.ReminderEngine
import com.gdimitria.notificationreminders.engine.SoundLock
import com.gdimitria.notificationreminders.engine.WifiWatcher
import com.gdimitria.notificationreminders.model.AppSettings
import com.gdimitria.notificationreminders.model.Schedule
import com.gdimitria.notificationreminders.model.Sender
import com.gdimitria.notificationreminders.model.Sources

/** Watches the notifications of the monitored apps, missed calls and SMS. */
class ReminderListenerService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())

    /** Keys of the notifications currently shown, used to tell new notifications from updates. */
    private val knownKeys = HashSet<String>()

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (Store.get(context).settings.value.stopOnUnlock) ReminderEngine.stopAll(context)
        }
    }

    override fun onListenerConnected() {
        instance = this
        SoundLock.start(this)
        WifiWatcher.start(this)
        ContextCompat.registerReceiver(
            this, unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        knownKeys.clear()
        safeActive().forEach { knownKeys += it.key }
        ReminderEngine.reconcile(this, collectPending())
    }

    override fun onListenerDisconnected() {
        detach()
        requestRebind(ComponentName(this, ReminderListenerService::class.java))
    }

    override fun onDestroy() {
        detach()
        super.onDestroy()
    }

    private fun detach() {
        if (instance === this) {
            instance = null
            SoundLock.stop()
            WifiWatcher.stop()
        }
        runCatching { unregisterReceiver(unlockReceiver) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val isUpdate = !knownKeys.add(sbn.key)
        val settings = Store.get(this).settings.value
        val phoneApps = PhoneApps()
        val keys = sourcesOf(sbn, settings, phoneApps)
        val onlyAlertOnce = sbn.notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0
        val isNewAlert = !(isUpdate && onlyAlertOnce)

        // Messages and notifications of the selected apps from an exception contact alert at once with its profile.
        val exceptions = Store.get(this).soundProfiles.value.contactExceptions.isNotEmpty()
        val messageLike = sbn.packageName != packageName && !sbn.isOngoing &&
            (sbn.packageName == phoneApps.smsApp || settings.apps.any { it.packageName == sbn.packageName && it.enabled })
        if (keys.isEmpty() && !(exceptions && messageLike && isNewAlert)) return
        val sender = if (exceptions) senderOf(sbn) else null
        val forMessages = sender?.let { ContactExceptions.find(this, it, forCalls = false) }
        val forCalls = sender?.takeIf { Sources.MISSED_CALLS in keys }?.let { ContactExceptions.find(this, it, forCalls = true) }
        if (isNewAlert && messageLike && forMessages != null) ContactExceptions.alertForMessage(this, forMessages.second)

        val text = textOf(sbn)
        keys.forEach { key ->
            val match = if (key == Sources.MISSED_CALLS) forCalls else forMessages
            ReminderEngine.onNotificationPosted(this, key, text, isNewAlert, match?.second?.id)
        }
    }

    /** Names, phone numbers and contact keys of whoever the notification comes from. */
    private fun senderOf(sbn: StatusBarNotification): Sender {
        val notification = sbn.notification
        val extras = notification.extras
        val names = mutableListOf<String>()
        val numbers = mutableListOf<String>()
        val keys = mutableListOf<String>()

        fun addUri(uri: String?) {
            if (uri == null) return
            when {
                uri.startsWith("tel:") -> numbers += Uri.decode(uri.removePrefix("tel:"))
                uri.contains("/lookup/") -> uri.substringAfter("/lookup/").substringBefore('/').takeIf { it.isNotEmpty() }
                    ?.let { keys += Uri.decode(it) }
            }
        }

        listOf(Notification.EXTRA_TITLE, Notification.EXTRA_CONVERSATION_TITLE, Notification.EXTRA_TITLE_BIG)
            .mapNotNull { extras.getCharSequence(it)?.toString() }
            .forEach { names += it }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val people = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelableArrayList(Notification.EXTRA_PEOPLE_LIST, android.app.Person::class.java)
            } else {
                @Suppress("DEPRECATION")
                extras.getParcelableArrayList(Notification.EXTRA_PEOPLE_LIST)
            }
            people?.forEach { person ->
                person.name?.let { names += it.toString() }
                addUri(person.uri)
            }
        }
        @Suppress("DEPRECATION")
        extras.getStringArray(Notification.EXTRA_PEOPLE)?.forEach(::addUri)
        NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)?.let { style ->
            style.conversationTitle?.let { names += it.toString() }
            style.messages.lastOrNull()?.person?.let { person ->
                person.name?.let { names += it.toString() }
                addUri(person.uri)
            }
        }
        // Missed calls from numbers without a name show the number itself.
        val phoneLike = Regex("[+(]?\\d[\\d ()\\-]{5,}\\d")
        listOfNotNull(extras.getCharSequence(Notification.EXTRA_TITLE), extras.getCharSequence(Notification.EXTRA_TEXT))
            .forEach { text -> phoneLike.findAll(text).forEach { numbers += it.value } }
        return Sender(numbers.distinct(), names.distinct(), keys.distinct())
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        knownKeys -= sbn.key
        if (sbn.packageName == packageName && sbn.id == ProfileStatusNotifier.NOTIFICATION_ID) {
            // Cleared by the user: post the status bar notification again (it cancels itself when switched off).
            handler.postDelayed({ ProfileStatusNotifier.update(this) }, 500)
            return
        }
        val keys = sourcesOf(sbn, Store.get(this).settings.value, PhoneApps(), checkRemoved = true)
        if (keys.isEmpty()) return
        val pending = collectPending()
        keys.filter { it !in pending }.forEach { ReminderEngine.onAllNotificationsCleared(this, it) }
    }

    /** Default phone and SMS apps, resolved once per batch of notifications. */
    private inner class PhoneApps {
        val dialers: Set<String> = buildSet {
            add("com.android.server.telecom")
            add("com.android.phone")
            runCatching { getSystemService(TelecomManager::class.java).defaultDialerPackage }.getOrNull()?.let(::add)
        }
        val smsApp: String? = runCatching { Telephony.Sms.getDefaultSmsPackage(this@ReminderListenerService) }.getOrNull()
    }

    /**
     * Source keys a notification counts for: the app itself when it is monitored, Missed calls for
     * notifications of the phone app, SMS and MMS for notifications of the default messaging app.
     * @param checkRemoved skip the importance check, as a removed notification has no ranking.
     */
    private fun sourcesOf(
        sbn: StatusBarNotification,
        settings: AppSettings,
        phoneApps: PhoneApps,
        checkRemoved: Boolean = false,
    ): List<String> {
        if (sbn.packageName == packageName) return emptyList()
        val notification = sbn.notification
        if (sbn.isOngoing || notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return emptyList()
        // Ongoing call notifications were excluded above, so what the phone app leaves behind is a missed call
        // (or voicemail) notification.
        val missedCall = sbn.packageName in phoneApps.dialers || notification.category == CATEGORY_MISSED_CALL
        val keys = buildList {
            if (missedCall) add(Sources.MISSED_CALLS)
            if (sbn.packageName == phoneApps.smsApp) add(Sources.MESSAGES)
            add(sbn.packageName)
        }
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        return keys.filter { key ->
            if (!Schedule.isMonitored(settings, key)) return@filter false
            val config = Schedule.anyConfig(settings, key, now, zone) ?: return@filter false
            checkRemoved || !config.ignoreSilentNotifications || !isSilent(sbn)
        }
    }

    private fun isSilent(sbn: StatusBarNotification): Boolean {
        val ranking = Ranking()
        return currentRanking?.getRanking(sbn.key, ranking) == true &&
            ranking.importance < NotificationManager.IMPORTANCE_DEFAULT
    }

    /** Monitored source keys with a pending notification, mapped to the text of the latest one. */
    private fun collectPending(): Map<String, String?> {
        val settings = Store.get(this).settings.value
        val phoneApps = PhoneApps()
        val pending = LinkedHashMap<String, String?>()
        safeActive().sortedBy { it.postTime }.forEach { sbn ->
            sourcesOf(sbn, settings, phoneApps).forEach { pending[it] = textOf(sbn) }
        }
        return pending
    }

    private fun safeActive(): Array<StatusBarNotification> = try {
        activeNotifications ?: emptyArray()
    } catch (e: Exception) {
        Log.w(TAG, "Cannot read active notifications", e)
        emptyArray()
    }

    private fun textOf(sbn: StatusBarNotification): String? {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        return listOfNotNull(title, text).joinToString(": ").ifBlank { null }
    }

    companion object {
        private const val TAG = "ReminderListener"

        /** Notification.CATEGORY_MISSED_CALL, added in API 30. */
        private const val CATEGORY_MISSED_CALL = "missed_call"

        @Volatile
        private var instance: ReminderListenerService? = null

        val isConnected: Boolean get() = instance != null

        /** Monitored sources with a pending notification, or null when the listener is not connected. */
        fun pendingNotifications(): Map<String, String?>? = instance?.collectPending()

        fun pendingPackages(): Set<String>? = pendingNotifications()?.keys
    }
}
