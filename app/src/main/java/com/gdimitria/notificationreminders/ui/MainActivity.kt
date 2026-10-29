package com.gdimitria.notificationreminders.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.gdimitria.notificationreminders.data.Store
import com.gdimitria.notificationreminders.engine.ProfileStatusNotifier
import com.gdimitria.notificationreminders.engine.ReminderEngine
import com.gdimitria.notificationreminders.engine.SoundLock
import com.gdimitria.notificationreminders.engine.StatusNotifier
import com.gdimitria.notificationreminders.engine.WifiWatcher
import com.gdimitria.notificationreminders.model.AppSettings
import com.gdimitria.notificationreminders.model.MonitoredApp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Replaces the selected apps with [picked], keeping the on/off state of apps already selected. */
private fun AppSettings.withApps(picked: List<InstalledApp>): AppSettings {
    val existing = apps.associateBy { it.packageName }
    return copy(apps = picked.map { existing[it.packageName] ?: MonitoredApp(it.packageName, it.label) })
}

class MainActivity : ComponentActivity() {

    // While the app is on screen the current Wi-Fi network is followed, also without the background services.
    override fun onStart() {
        super.onStart()
        WifiWatcher.start(this)
    }

    override fun onStop() {
        WifiWatcher.stop()
        super.onStop()
    }

    @OptIn(FlowPreview::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        StatusNotifier.ensureChannel(this)
        SoundLock.restore(this)
        val store = Store.get(this)
        ProfileStatusNotifier.update(this)

        // Every settings change re-aligns reminders with the pending notifications.
        lifecycleScope.launch {
            store.settings.drop(1).debounce(400).collect { ReminderEngine.onSettingsChanged(applicationContext) }
        }
        // Renaming a profile or switching the status bar display updates the status notification.
        lifecycleScope.launch {
            store.soundProfiles.drop(1).debounce(400).collect { ReminderEngine.refresh(applicationContext) }
        }

        setContent {
            AppTheme {
                // "home", "picker", "picker:<id>", "profile:<id>", "config:<id>:<common or type>", "sound:<id>", "wifi:<id>" or "contact:<id>"
                var route by rememberSaveable { mutableStateOf("home") }
                val profileId = route.substringAfter(':').substringBefore(':')
                val back = when {
                    route.startsWith("config:") || route.startsWith("picker:") -> "profile:$profileId"
                    else -> "home"
                }
                BackHandler(enabled = route != "home") { route = back }
                LaunchedEffect(Unit) { ReminderEngine.onSettingsChanged(applicationContext) }

                when {
                    route.startsWith("picker") -> AppPickerScreen(
                        initiallySelected = remember { store.settings.value.apps.map { it.packageName }.toSet() },
                        onDone = { picked ->
                            store.updateSettings { it.withApps(picked) }
                            route = back
                        },
                        onBack = { route = back },
                    )
                    route.startsWith("profile:") -> ProfileScreen(
                        profileId = profileId,
                        onBack = { route = back },
                        onPickApps = { route = "picker:$profileId" },
                        onEditConfig = { target -> route = "config:$profileId:$target" },
                    )
                    route.startsWith("config:") -> ConfigScreen(
                        profileId = profileId,
                        target = route.substringAfterLast(':'),
                        onBack = { route = back },
                    )
                    route.startsWith("sound:") -> SoundProfileScreen(profileId = profileId, onBack = { route = back })
                    route.startsWith("wifi:") -> WifiRuleScreen(ruleId = profileId, onBack = { route = back })
                    route.startsWith("contact:") -> ContactExceptionScreen(exceptionId = profileId, onBack = { route = back })
                    else -> HomeScreen(
                        onOpenProfile = { route = "profile:$it" },
                        onPickApps = { route = "picker" },
                        onEditSoundProfile = { route = "sound:$it" },
                        onEditWifiRule = { route = "wifi:$it" },
                        onEditContactException = { route = "contact:$it" },
                    )
                }
            }
        }
    }
}
