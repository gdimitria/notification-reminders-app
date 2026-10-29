package com.gdimitria.notificationreminders.data

import android.content.Context
import androidx.core.content.edit
import com.gdimitria.notificationreminders.model.ActiveReminder
import com.gdimitria.notificationreminders.model.AppSettings
import com.gdimitria.notificationreminders.model.SoundProfiles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/** Process-wide store for settings and active reminders, persisted as JSON in SharedPreferences. */
class Store private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("store", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val lock = Any()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _active = MutableStateFlow(
        prefs.getString(KEY_ACTIVE, null)
            ?.let { runCatching { json.decodeFromString<List<ActiveReminder>>(it) }.getOrNull() }
            ?.associateBy { it.packageName }
            ?: emptyMap()
    )
    val active: StateFlow<Map<String, ActiveReminder>> = _active.asStateFlow()

    private val _soundProfiles = MutableStateFlow(SoundProfiles())

    /** Phone sound profiles (Loud, Normal, Silent, …), independent of the reminders. */
    val soundProfiles: StateFlow<SoundProfiles> = _soundProfiles.asStateFlow()

    init {
        val stored = prefs.getString(KEY_SETTINGS, null)
            ?.let { runCatching { json.decodeFromString<AppSettings>(it) }.getOrNull() }
            ?: AppSettings()
        // Upgrades settings saved by older versions and creates the default profiles on first start.
        updateSettings { stored.migrated() }

        val storedSound = prefs.getString(KEY_SOUND_PROFILES, null)
            ?.let { runCatching { json.decodeFromString<SoundProfiles>(it) }.getOrNull() }
        updateSoundProfiles {
            storedSound?.withCurrentDefaults() ?: SoundProfiles.initial()
        }
    }

    fun updateSoundProfiles(transform: (SoundProfiles) -> SoundProfiles) = synchronized(lock) {
        val updated = transform(_soundProfiles.value)
        if (updated == _soundProfiles.value) return@synchronized
        _soundProfiles.value = updated
        prefs.edit { putString(KEY_SOUND_PROFILES, json.encodeToString(updated)) }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = synchronized(lock) {
        val updated = transform(_settings.value)
        if (updated == _settings.value) return@synchronized
        _settings.value = updated
        prefs.edit { putString(KEY_SETTINGS, json.encodeToString(updated)) }
    }

    fun updateActive(
        transform: (Map<String, ActiveReminder>) -> Map<String, ActiveReminder>,
    ): Map<String, ActiveReminder> = synchronized(lock) {
        val updated = transform(_active.value)
        if (updated != _active.value) {
            _active.value = updated
            prefs.edit { putString(KEY_ACTIVE, json.encodeToString(updated.values.toList())) }
        }
        updated
    }

    companion object {
        private const val KEY_SETTINGS = "settings"
        private const val KEY_ACTIVE = "active"
        private const val KEY_SOUND_PROFILES = "sound_profiles"

        @Volatile
        private var instance: Store? = null

        fun get(context: Context): Store =
            instance ?: synchronized(this) {
                instance ?: Store(context.applicationContext).also { instance = it }
            }
    }
}
