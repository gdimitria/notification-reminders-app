package com.gdimitria.notificationreminders.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gdimitria.notificationreminders.R
import com.gdimitria.notificationreminders.data.Store
import com.gdimitria.notificationreminders.model.Profile
import com.gdimitria.notificationreminders.model.ReminderMode
import com.gdimitria.notificationreminders.model.Schedule
import com.gdimitria.notificationreminders.model.SourceType
import java.time.ZoneId

/** What a configuration screen edits: the common settings or the custom settings of one type. */
const val COMMON_TARGET = "common"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = { actions() },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
    }
}

/** Asks before deleting [profile]; [onDeleted] runs after the deletion. */
@Composable
fun DeleteProfileDialog(profile: Profile, onDismiss: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_profile)) },
        text = { Text(stringResource(R.string.delete_profile_confirm, context.profileName(profile))) },
        confirmButton = {
            TextButton(onClick = {
                Store.get(context).updateSettings { s ->
                    s.copy(
                        profiles = s.profiles.filter { it.id != profile.id },
                        manualProfileId = s.manualProfileId.takeIf { it != profile.id },
                    )
                }
                onDeleted()
            }) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Name, schedule, reminder choice per type and common settings of one profile. */
@Composable
fun ProfileScreen(
    profileId: String,
    onBack: () -> Unit,
    onPickApps: () -> Unit,
    onEditConfig: (target: String) -> Unit,
) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    val settings by store.settings.collectAsStateWithLifecycle()
    val profile = settings.profile(profileId)
    var confirmDelete by remember { mutableStateOf(false) }

    fun update(transform: (Profile) -> Profile) = store.updateSettings { it.updateProfile(profileId, transform) }

    ScreenScaffold(
        title = profile?.let { context.profileName(it) } ?: stringResource(R.string.profiles),
        onBack = onBack,
        actions = {
            if (profile != null) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_profile))
                }
            }
        },
    ) {
        if (profile == null) {
            Text(stringResource(R.string.profile_removed))
            return@ScreenScaffold
        }
        val active = Schedule.activeProfile(settings, System.currentTimeMillis(), ZoneId.systemDefault())?.id == profileId

        SectionCard(stringResource(R.string.section_profile)) {
            OutlinedTextField(
                value = context.profileName(profile),
                onValueChange = { name -> update { it.copy(name = name) } },
                label = { Text(stringResource(R.string.profile_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SwitchRow(
                title = stringResource(R.string.profile_enabled),
                subtitle = stringResource(if (active) R.string.active_now else R.string.not_active_now),
                checked = profile.enabled,
                onCheckedChange = { enabled -> update { it.copy(enabled = enabled) } },
            )
        }

        SectionCard(stringResource(R.string.section_schedule)) {
            Text(
                stringResource(R.string.schedule_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TimeWindowsEditor(profile.schedule, stringResource(R.string.schedule_fallback_hint)) { windows ->
                update { it.copy(schedule = windows) }
            }
        }

        SectionCard(stringResource(R.string.section_remind_about)) {
            SourceType.entries.forEachIndexed { index, type ->
                if (index > 0) HorizontalDivider()
                TypeRow(
                    type = type,
                    mode = profile.type(type).mode,
                    onModeChange = { mode ->
                        update { p ->
                            p.updateType(type) { it.copy(mode = mode, custom = it.custom ?: p.defaults) }
                        }
                    },
                    onEditCustom = { onEditConfig(type.name) },
                )
                if (type == SourceType.APPS) {
                    val count = settings.apps.count { it.enabled }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (count == 0) stringResource(R.string.no_apps_short)
                            else context.resources.getQuantityString(R.plurals.apps_selected, count, count),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onPickApps) { Text(stringResource(R.string.choose_apps)) }
                    }
                }
            }
        }

        SectionCard(stringResource(R.string.common_settings)) {
            Text(stringResource(R.string.common_settings_hint), style = MaterialTheme.typography.bodySmall)
            Text(context.configSummary(profile.defaults), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { onEditConfig(COMMON_TARGET) }) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Text(stringResource(R.string.edit_common_settings))
            }
        }

        OutlinedButton(
            onClick = { confirmDelete = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Delete, contentDescription = null)
            Text(stringResource(R.string.delete_profile))
        }
    }

    if (confirmDelete && profile != null) {
        DeleteProfileDialog(profile, onDismiss = { confirmDelete = false }, onDeleted = {
            confirmDelete = false
            onBack()
        })
    }
}

@StringRes
fun ReminderMode.labelRes(): Int = when (this) {
    ReminderMode.OFF -> R.string.mode_off
    ReminderMode.PHONE -> R.string.mode_phone
    ReminderMode.COMMON -> R.string.mode_common
    ReminderMode.CUSTOM -> R.string.mode_custom
}

@StringRes
fun SourceType.labelRes(): Int = when (this) {
    SourceType.CALLS -> R.string.source_missed_calls
    SourceType.MESSAGES -> R.string.source_messages
    SourceType.APPS -> R.string.source_apps
}

private fun SourceType.icon(): ImageVector = when (this) {
    SourceType.CALLS -> Icons.Default.Call
    SourceType.MESSAGES -> Icons.Default.Email
    SourceType.APPS -> Icons.Default.Notifications
}

/** One type with its reminder choice; tapping opens the four choices. */
@Composable
private fun TypeRow(
    type: SourceType,
    mode: ReminderMode,
    onModeChange: (ReminderMode) -> Unit,
    onEditCustom: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(type.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(type.labelRes()), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(mode.labelRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (mode == ReminderMode.OFF) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ReminderMode.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes())) },
                    onClick = { expanded = false; onModeChange(option) },
                )
            }
        }
    }
    when (mode) {
        ReminderMode.PHONE -> Text(stringResource(R.string.mode_phone_hint), style = MaterialTheme.typography.bodySmall)
        ReminderMode.CUSTOM -> TextButton(onClick = onEditCustom) {
            Icon(Icons.Default.Edit, contentDescription = null)
            Text(stringResource(R.string.edit_custom_settings))
        }
        else -> Unit
    }
}

/** Edits the common settings of a profile, or the custom settings of one type ([target] = type name). */
@Composable
fun ConfigScreen(profileId: String, target: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    val settings by store.settings.collectAsStateWithLifecycle()
    val profile = settings.profile(profileId)
    val type = SourceType.entries.firstOrNull { it.name == target }

    val title = if (type == null) {
        stringResource(R.string.common_settings)
    } else {
        stringResource(R.string.custom_settings_title, stringResource(type.labelRes()))
    }
    ScreenScaffold(title = title, onBack = onBack) {
        if (profile == null) {
            Text(stringResource(R.string.profile_removed))
            return@ScreenScaffold
        }
        Text(stringResource(R.string.source_in_profile, context.profileName(profile)), style = MaterialTheme.typography.titleMedium)
        if (type == null) {
            Text(stringResource(R.string.common_settings_hint))
            ConfigEditor(profile.defaults) { config ->
                store.updateSettings { s -> s.updateProfile(profileId) { it.copy(defaults = config) } }
            }
        } else {
            ConfigEditor(profile.type(type).custom ?: profile.defaults) { config ->
                store.updateSettings { s -> s.updateProfile(profileId) { p -> p.updateType(type) { it.copy(custom = config) } } }
            }
        }
    }
}
