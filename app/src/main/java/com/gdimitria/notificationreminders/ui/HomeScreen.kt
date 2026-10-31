package com.gdimitria.notificationreminders.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gdimitria.notificationreminders.R
import com.gdimitria.notificationreminders.data.Store
import com.gdimitria.notificationreminders.engine.AlertPlayer
import com.gdimitria.notificationreminders.engine.ReminderEngine
import com.gdimitria.notificationreminders.engine.StatusNotifier
import com.gdimitria.notificationreminders.model.Profile
import com.gdimitria.notificationreminders.model.Profiles
import com.gdimitria.notificationreminders.model.ReminderConfig
import com.gdimitria.notificationreminders.model.Schedule
import com.gdimitria.notificationreminders.model.TimeWindow
import java.time.ZoneId
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenProfile: (String) -> Unit,
    onPickApps: () -> Unit,
    onEditSoundProfile: (String) -> Unit,
    onEditWifiRule: (String) -> Unit,
    onEditContactException: (String) -> Unit,
) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    val settings by store.settings.collectAsStateWithLifecycle()
    val active by store.active.collectAsStateWithLifecycle()
    var permissions by remember { mutableStateOf(Permissions.state(context)) }
    var deleting by remember { mutableStateOf<Profile?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissions = Permissions.state(context) }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // The reminders master switch first, then the sound profiles, then the reminder sections.
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (settings.masterEnabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    SwitchRow(
                        title = stringResource(R.string.reminders_enabled),
                        checked = settings.masterEnabled,
                        onCheckedChange = { enabled -> store.updateSettings { it.copy(masterEnabled = enabled) } },
                    )
                    val now = System.currentTimeMillis()
                    if (settings.snoozeUntil > now) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.snoozed_until, context.formatTime(settings.snoozeUntil)),
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { ReminderEngine.resume(context) }) { Text(stringResource(R.string.resume)) }
                        }
                    }
                }
            }

            SoundProfilesCard(
                onEdit = onEditSoundProfile,
                onEditWifiRule = onEditWifiRule,
                onEditContactException = onEditContactException,
            )

            PermissionsCard(permissions)

            val running = active.values.filter { !it.stopped }
            if (running.isNotEmpty()) {
                SectionCard(stringResource(R.string.section_active)) {
                    running.forEach { reminder ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AppIcon(reminder.packageName)
                            Column(Modifier.weight(1f)) {
                                Text(context.sourceLabel(settings, reminder.packageName), style = MaterialTheme.typography.bodyLarge)
                                val next = if (reminder.nextFireAt == Schedule.NEVER) "—" else context.formatTime(reminder.nextFireAt)
                                Text(
                                    pluralStringResource(R.plurals.active_status, reminder.count, reminder.count, next),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                reminder.lastText?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            TextButton(onClick = { ReminderEngine.stop(context, reminder.packageName) }) {
                                Text(stringResource(R.string.stop))
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { ReminderEngine.stopAll(context) }) { Text(stringResource(R.string.action_stop_all)) }
                        OutlinedButton(onClick = { ReminderEngine.snooze(context, StatusNotifier.SNOOZE_MINUTES) }) {
                            Text(stringResource(R.string.action_snooze_minutes, StatusNotifier.SNOOZE_MINUTES))
                        }
                    }
                }
            }

            val activeProfile = Schedule.activeProfile(settings, System.currentTimeMillis(), ZoneId.systemDefault())
            SectionCard(stringResource(R.string.profiles)) {
                DropdownRow(
                    title = stringResource(R.string.profile_selection),
                    options = listOf<String?>(null) + settings.profiles.map { it.id },
                    selected = settings.manualProfileId?.takeIf { settings.profile(it) != null },
                    label = { id ->
                        settings.profile(id)?.let { context.profileName(it) } ?: stringResource(R.string.profile_automatic)
                    },
                    onSelect = { id -> store.updateSettings { it.copy(manualProfileId = id) } },
                )
                settings.profiles.forEach { profile ->
                    HorizontalDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenProfile(profile.id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(context.profileName(profile), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                context.scheduleSummary(profile),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (profile.id == activeProfile?.id) {
                                Text(
                                    stringResource(R.string.active_now),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Switch(checked = profile.enabled, onCheckedChange = { enabled ->
                            store.updateSettings { s -> s.updateProfile(profile.id) { it.copy(enabled = enabled) } }
                        })
                        IconButton(onClick = { deleting = profile }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_profile))
                        }
                    }
                }
                if (settings.profiles.isEmpty()) {
                    Text(stringResource(R.string.no_profiles), style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = {
                    // Starts as a copy of the active profile (or of Day) with a schedule to adjust.
                    val base = activeProfile ?: Profiles.day()
                    val profile = base.copy(
                        id = UUID.randomUUID().toString(),
                        name = context.getString(R.string.new_profile_name, settings.profiles.size + 1),
                        enabled = true,
                        schedule = listOf(TimeWindow()),
                    )
                    store.updateSettings { it.copy(profiles = it.profiles + profile) }
                    onOpenProfile(profile.id)
                }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(stringResource(R.string.add_profile))
                }
            }

            SectionCard(stringResource(R.string.section_apps)) {
                Text(stringResource(R.string.apps_hint), style = MaterialTheme.typography.bodySmall)
                if (settings.apps.isEmpty()) {
                    Text(stringResource(R.string.no_apps), style = MaterialTheme.typography.bodyMedium)
                }
                settings.apps.forEach { app ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppIcon(app.packageName)
                        Text(app.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(checked = app.enabled, onCheckedChange = { enabled ->
                            store.updateSettings { s ->
                                s.copy(apps = s.apps.map { if (it.packageName == app.packageName) it.copy(enabled = enabled) else it })
                            }
                        })
                        IconButton(onClick = {
                            store.updateSettings { s -> s.copy(apps = s.apps.filter { it.packageName != app.packageName }) }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove))
                        }
                    }
                }
                Button(onClick = onPickApps) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(stringResource(R.string.add_apps))
                }
            }

            SectionCard(stringResource(R.string.section_general)) {
                SwitchRow(
                    title = stringResource(R.string.stop_on_unlock),
                    subtitle = stringResource(R.string.stop_on_unlock_hint),
                    checked = settings.stopOnUnlock,
                    onCheckedChange = { value -> store.updateSettings { it.copy(stopOnUnlock = value) } },
                )
                SwitchRow(
                    title = stringResource(R.string.alarm_clock_mode),
                    subtitle = stringResource(R.string.alarm_clock_mode_hint),
                    checked = settings.alarmClockMode,
                    onCheckedChange = { value -> store.updateSettings { it.copy(alarmClockMode = value) } },
                )
                OutlinedButton(
                    onClick = { AlertPlayer.play(context, activeProfile?.defaults ?: ReminderConfig(), ignoreSilentMode = true) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text(stringResource(R.string.test_alert))
                }
            }
        }
    }

    deleting?.let { profile ->
        DeleteProfileDialog(profile, onDismiss = { deleting = null }, onDeleted = { deleting = null })
    }
}

@Composable
private fun PermissionsCard(state: PermissionState) {
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    SectionCard(stringResource(R.string.section_permissions)) {
        PermissionRow(
            title = stringResource(R.string.perm_notification_access),
            hint = stringResource(R.string.perm_notification_access_hint),
            granted = state.notificationAccess,
            onGrant = { Permissions.open(context, Permissions.notificationAccessIntent(context)) },
        )
        if (!state.notificationAccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Text(stringResource(R.string.restricted_settings_hint), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { Permissions.open(context, Permissions.appDetailsIntent(context)) }) {
                Text(stringResource(R.string.open_app_info))
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionRow(
                title = stringResource(R.string.perm_post_notifications),
                hint = stringResource(R.string.perm_post_notifications_hint),
                granted = state.postNotifications,
                onGrant = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PermissionRow(
                title = stringResource(R.string.perm_exact_alarms),
                hint = stringResource(R.string.perm_exact_alarms_hint),
                granted = state.exactAlarms,
                onGrant = { Permissions.open(context, Permissions.exactAlarmIntent(context)) },
            )
        }
        PermissionRow(
            title = stringResource(R.string.perm_dnd_access),
            hint = stringResource(R.string.perm_dnd_access_hint),
            granted = state.dndAccess,
            onGrant = { Permissions.open(context, Permissions.dndAccessIntent()) },
        )
        PermissionRow(
            title = stringResource(R.string.perm_write_settings),
            hint = stringResource(R.string.perm_write_settings_hint),
            granted = state.writeSettings,
            onGrant = { Permissions.open(context, Permissions.writeSettingsIntent(context)) },
        )
        PermissionRow(
            title = stringResource(R.string.perm_battery),
            hint = stringResource(R.string.perm_battery_hint),
            granted = state.batteryUnrestricted,
            onGrant = { Permissions.open(context, Permissions.batteryIntent(context)) },
        )
    }
}

@Composable
private fun PermissionRow(title: String, hint: String, granted: Boolean, onGrant: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(
            if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!granted) Text(hint, style = MaterialTheme.typography.bodySmall)
        }
        if (!granted) TextButton(onClick = onGrant) { Text(stringResource(R.string.grant)) }
    }
}
