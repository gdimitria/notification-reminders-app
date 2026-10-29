package com.gdimitria.notificationreminders.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.gdimitria.notificationreminders.data.Store
import com.gdimitria.notificationreminders.model.ContactException
import com.gdimitria.notificationreminders.model.ContactMatching
import com.gdimitria.notificationreminders.model.ExceptionContact
import com.gdimitria.notificationreminders.model.ExceptionGroup
import com.gdimitria.notificationreminders.model.ExceptionPeople
import com.gdimitria.notificationreminders.model.ReminderConfig
import com.gdimitria.notificationreminders.model.RingerSetting
import com.gdimitria.notificationreminders.model.Sender
import com.gdimitria.notificationreminders.model.SoundLockState
import com.gdimitria.notificationreminders.model.SoundProfile

/** Reads contacts and contact groups; everything returns empty without the contacts permission. */
object ContactDirectory {

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun groups(context: Context): List<ExceptionGroup> {
        if (!hasPermission(context)) return emptyList()
        val projection = arrayOf(ContactsContract.Groups._ID, ContactsContract.Groups.TITLE)
        val selection = "${ContactsContract.Groups.DELETED} = 0"
        return runCatching {
            context.contentResolver.query(ContactsContract.Groups.CONTENT_URI, projection, selection, null, null)
                ?.use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            val title = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                            add(ExceptionGroup(cursor.getLong(0), title))
                        }
                    }
                }.orEmpty().distinctBy { it.title }.sortedBy { it.title.lowercase() }
        }.getOrDefault(emptyList())
    }

    /** Name, lookup key and phone numbers of a contact picked with the contact picker. */
    fun contact(context: Context, contactUri: Uri): ExceptionContact? {
        if (!hasPermission(context)) return null
        return runCatching {
            val (id, lookupKey, name) = context.contentResolver.query(
                contactUri,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME),
                null, null, null,
            )?.use { if (it.moveToFirst()) Triple(it.getLong(0), it.getString(1), it.getString(2)) else null } ?: return null
            ExceptionContact(lookupKey = lookupKey, name = name.orEmpty(), numbers = numbersOf(context, id))
        }.getOrNull()
    }

    private fun numbersOf(context: Context, contactId: Long): List<String> =
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?", arrayOf(contactId.toString()), null,
        )?.use { cursor -> buildList { while (cursor.moveToNext()) cursor.getString(0)?.let(::add) } }.orEmpty()

    /** Numbers, names and lookup keys of the members of a contact group. */
    fun members(context: Context, groupId: Long): ExceptionPeople {
        if (!hasPermission(context)) return ExceptionPeople()
        return runCatching {
            val contactIds = context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data.CONTACT_ID),
                "${ContactsContract.Data.MIMETYPE} = ? AND " +
                    "${ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID} = ?",
                arrayOf(ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE, groupId.toString()),
                null,
            )?.use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getLong(0)) } }.orEmpty()
            if (contactIds.isEmpty()) return ExceptionPeople()
            val ids = contactIds.joinToString(",")
            val numbers = mutableSetOf<String>()
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} IN ($ids)", null, null,
            )?.use { cursor ->
                while (cursor.moveToNext()) cursor.getString(0)?.let { numbers += ContactMatching.normalize(it) }
            }
            val names = mutableSetOf<String>()
            val keys = mutableSetOf<String>()
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts.LOOKUP_KEY),
                "${ContactsContract.Contacts._ID} IN ($ids)", null, null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.let { names += ContactMatching.normalizeName(it) }
                    cursor.getString(1)?.let { keys += it }
                }
            }
            ExceptionPeople(numbers.filter { it.length >= 6 }.toSet(), names, keys)
        }.getOrDefault(ExceptionPeople())
    }
}

/** Finds the contact exception for a sender and overrides the sound for its calls. */
object ContactExceptions {

    private const val TAG = "ContactExceptions"
    private const val CACHE_MS = 5 * 60_000L

    private val groupCache = HashMap<Long, Pair<Long, ExceptionPeople>>()

    private fun people(context: Context, exception: ContactException): ExceptionPeople {
        val direct = ContactMatching.peopleOf(exception)
        val now = System.currentTimeMillis()
        val groups = exception.groups.map { group ->
            groupCache[group.id]?.takeIf { now - it.first < CACHE_MS }?.second
                ?: ContactDirectory.members(context, group.id).also { groupCache[group.id] = now to it }
        }
        return groups.fold(direct) { acc, p ->
            ExceptionPeople(acc.numbers + p.numbers, acc.names + p.names, acc.lookupKeys + p.lookupKeys)
        }
    }

    /** First enabled exception for [sender] that covers calls ([forCalls]) or messages, with its sound profile. */
    fun find(context: Context, sender: Sender, forCalls: Boolean): Pair<ContactException, SoundProfile>? {
        if (sender.numbers.isEmpty() && sender.names.isEmpty() && sender.lookupKeys.isEmpty()) return null
        val sound = Store.get(context).soundProfiles.value
        for (exception in sound.contactExceptions) {
            if (!exception.enabled || !(if (forCalls) exception.calls else exception.messages)) continue
            val profile = sound.profile(exception.profileId) ?: continue
            if (ContactMatching.matches(people(context, exception), sender)) return exception to profile
        }
        return null
    }

    /** A message or app notification from an exception contact: alert at once with the contact's profile. */
    fun alertForMessage(context: Context, profile: SoundProfile) {
        if (profile.ringerMode == RingerSetting.SILENT) return
        AlertPlayer.play(context, ReminderConfig(alertDurationSeconds = 4).phoneStyle(profile))
    }

    // ---- Calls -------------------------------------------------------------------------------------------

    private val handler = Handler(Looper.getMainLooper())
    private var saved: SoundLockState? = null
    private var appChange = false
    private var sawCall = false
    private var startedAt = 0L

    private val watchCall = object : Runnable {
        override fun run() {
            val context = appContextForCall ?: return
            val mode = context.getSystemService(AudioManager::class.java).mode
            val inCall = mode == AudioManager.MODE_RINGTONE || mode == AudioManager.MODE_IN_CALL ||
                mode == AudioManager.MODE_IN_COMMUNICATION
            if (inCall) sawCall = true
            val elapsed = System.currentTimeMillis() - startedAt
            // Restore once the call is over, after 20 seconds if it never really started, after 4 hours at most.
            if ((sawCall && !inCall) || (!sawCall && elapsed > 20_000) || elapsed > 4 * 3600_000L) {
                endCallOverride(context)
            } else {
                handler.postDelayed(this, 1500)
            }
        }
    }
    private var appContextForCall: Context? = null

    /** Called before the phone rings: switches to the caller's exception profile until the call ends. */
    fun onIncomingCall(context: Context, number: String?) {
        if (number.isNullOrBlank()) return
        val app = context.applicationContext
        val (exception, profile) = find(app, Sender(numbers = listOf(number)), forCalls = true) ?: return
        val sound = Store.get(app).soundProfiles.value
        if (sound.lockedForApp) return
        Log.i(TAG, "Incoming call matches ${exception.name}")
        if (saved == null) saved = SoundLock.capture(app)
        if (sound.lock != null && !appChange) {
            SoundLock.beginAppChange()
            appChange = true
        }
        SoundProfileApplier.applySettings(app, profile)
        appContextForCall = app
        sawCall = false
        startedAt = System.currentTimeMillis()
        handler.removeCallbacks(watchCall)
        handler.postDelayed(watchCall, 1500)
    }

    private fun endCallOverride(context: Context) {
        handler.removeCallbacks(watchCall)
        saved?.let { SoundLock.applyState(context, it) }
        saved = null
        appContextForCall = null
        if (appChange) {
            appChange = false
            SoundLock.endAppChange()
        }
    }
}
