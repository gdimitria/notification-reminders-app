package com.gdimitria.notificationreminders.ui

import android.content.Context
import com.gdimitria.notificationreminders.model.SoundIcon
import com.gdimitria.notificationreminders.engine.ProfileStatusNotifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.StringRes
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gdimitria.notificationreminders.R
import com.gdimitria.notificationreminders.data.Store
import com.gdimitria.notificationreminders.engine.SoundLock
import com.gdimitria.notificationreminders.engine.SoundProfileApplier
import com.gdimitria.notificationreminders.model.DndSetting
import com.gdimitria.notificationreminders.model.RingerSetting
import com.gdimitria.notificationreminders.model.SoundProfile
import com.gdimitria.notificationreminders.model.SoundProfiles
import com.gdimitria.notificationreminders.model.SoundStream
import com.gdimitria.notificationreminders.model.Toggle
import com.gdimitria.notificationreminders.service.SoundLockAccessibilityService
import java.util.UUID

/** Sound profile name, localized for Loud, Normal and Silent while they keep their original names. */
fun Context.soundProfileName(profile: SoundProfile): String = when {
    profile.id == SoundProfiles.LOUD_ID && profile.name == SoundProfiles.LOUD_NAME -> getString(R.string.sound_loud)
    profile.id == SoundProfiles.NORMAL_ID && profile.name == SoundProfiles.NORMAL_NAME -> getString(R.string.sound_normal)
    profile.id == SoundProfiles.SILENT_ID && profile.name == SoundProfiles.SILENT_NAME -> getString(R.string.sound_silent)
    else -> profile.name
}

/** The profile's icon, as shown in the status bar: a symbol, or the first letter of its name. */
@Composable
fun SoundProfileIcon(profile: SoundProfile, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (profile.effectiveIcon == SoundIcon.LETTER) {
        Box(modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Text(
                context.soundProfileName(profile).trim().take(1).uppercase().ifEmpty { "?" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    } else {
        Icon(
            painterResource(ProfileStatusNotifier.iconRes(profile.effectiveIcon)),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = modifier.size(24.dp),
        )
    }
}

@StringRes
fun SoundIcon.labelRes(): Int = when (this) {
    SoundIcon.VOLUME_UP -> R.string.icon_volume_up
    SoundIcon.VOLUME_DOWN -> R.string.icon_volume_down
    SoundIcon.VIBRATE -> R.string.icon_vibrate
    SoundIcon.MUTE -> R.string.icon_mute
    SoundIcon.BELL -> R.string.icon_bell
    SoundIcon.MOON -> R.string.icon_moon
    SoundIcon.LETTER -> R.string.icon_letter
}

/** Summary such as "Normal ringer, ring 80%, notifications 60%". */
fun Context.soundProfileSummary(profile: SoundProfile): String = buildList {
    if (profile.ringerMode != RingerSetting.KEEP) add(getString(profile.ringerMode.labelRes()))
    SoundStream.entries.forEach { stream ->
        profile.volumes[stream]?.let { add(getString(R.string.volume_summary, getString(stream.labelRes()), it)) }
    }
    if (profile.doNotDisturb != DndSetting.KEEP) add(getString(profile.doNotDisturb.labelRes()))
}.joinToString(", ").ifEmpty { getString(R.string.sound_profile_no_changes) }

@StringRes
fun RingerSetting.labelRes(): Int = when (this) {
    RingerSetting.KEEP -> R.string.keep_unchanged
    RingerSetting.NORMAL -> R.string.ringer_normal
    RingerSetting.VIBRATE -> R.string.ringer_vibrate
    RingerSetting.SILENT -> R.string.ringer_silent
}

@StringRes
fun DndSetting.labelRes(): Int = when (this) {
    DndSetting.KEEP -> R.string.keep_unchanged
    DndSetting.OFF -> R.string.dnd_off
    DndSetting.PRIORITY -> R.string.dnd_priority
    DndSetting.ALARMS -> R.string.dnd_alarms
    DndSetting.TOTAL_SILENCE -> R.string.dnd_total_silence
}

@StringRes
fun Toggle.labelRes(): Int = when (this) {
    Toggle.KEEP -> R.string.keep_unchanged
    Toggle.ON -> R.string.toggle_on
    Toggle.OFF -> R.string.toggle_off
}

@StringRes
fun SoundStream.labelRes(): Int = when (this) {
    SoundStream.RING -> R.string.stream_ring
    SoundStream.NOTIFICATION -> R.string.stream_notification
    SoundStream.MEDIA -> R.string.stream_media
    SoundStream.ALARM -> R.string.stream_alarm
    SoundStream.SYSTEM -> R.string.stream_system
    SoundStream.CALL -> R.string.stream_call
}

private fun applyWithFeedback(context: Context, profile: SoundProfile) {
    val problems = SoundProfileApplier.apply(context, profile)
    val message = when {
        problems.isEmpty() -> context.getString(R.string.sound_profile_applied, context.soundProfileName(profile))
        SoundProfileApplier.Problem.LOCKED in problems -> context.getString(R.string.sound_locked_for_app_toast)
        else -> context.getString(R.string.sound_profile_partly_applied)
    }
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

@Composable
private fun DeleteSoundProfileDialog(profile: SoundProfile, onDismiss: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_sound_profile)) },
        text = { Text(stringResource(R.string.delete_profile_confirm, context.soundProfileName(profile))) },
        confirmButton = {
            TextButton(onClick = {
                Store.get(context).updateSoundProfiles { s ->
                    s.copy(
                        profiles = s.profiles.filter { it.id != profile.id },
                        activeId = s.activeId.takeIf { it != profile.id },
                    )
                }
                onDeleted()
            }) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Home screen card: tap a profile to apply it to the phone. */
@Composable
fun SoundProfilesCard(
    onEdit: (String) -> Unit,
    onEditWifiRule: (String) -> Unit,
    onEditContactException: (String) -> Unit,
) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    val sound by store.soundProfiles.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<SoundProfile?>(null) }
    var buttonsBlocked by remember { mutableStateOf(SoundLockAccessibilityService.isEnabled(context)) }
    var listenerRunning by remember { mutableStateOf(Permissions.state(context).notificationAccess) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        buttonsBlocked = SoundLockAccessibilityService.isEnabled(context)
        listenerRunning = Permissions.state(context).notificationAccess
    }

    SectionCard(stringResource(R.string.sound_profiles)) {
        Text(
            stringResource(R.string.sound_profiles_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        sound.profiles.forEach { profile ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { applyWithFeedback(context, profile) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = profile.id == sound.activeId, onClick = { applyWithFeedback(context, profile) })
                SoundProfileIcon(profile)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(context.soundProfileName(profile), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        context.soundProfileSummary(profile),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                IconButton(onClick = { onEdit(profile.id) }) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                }
                IconButton(onClick = { deleting = profile }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_sound_profile))
                }
            }
        }
        Button(onClick = {
            val base = sound.profile(SoundProfiles.NORMAL_ID) ?: SoundProfile(id = "", name = "")
            val profile = base.copy(
                id = UUID.randomUUID().toString(),
                name = context.getString(R.string.new_sound_profile_name, sound.profiles.size + 1),
            )
            store.updateSoundProfiles { it.copy(profiles = it.profiles + profile) }
            onEdit(profile.id)
        }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(stringResource(R.string.add_sound_profile))
        }

        HorizontalDivider()
        WifiRulesSection(onEdit = onEditWifiRule)

        HorizontalDivider()
        ContactExceptionsSection(onEdit = onEditContactException)

        HorizontalDivider()
        SwitchRow(
            title = stringResource(R.string.show_in_status_bar),
            subtitle = stringResource(R.string.show_in_status_bar_hint),
            checked = sound.showInStatusBar,
            onCheckedChange = { show -> store.updateSoundProfiles { it.copy(showInStatusBar = show) } },
        )

        HorizontalDivider()
        val locked = sound.lock != null
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = if (locked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            )
            Column(Modifier.weight(1f)) {
                SwitchRow(
                    title = stringResource(R.string.sound_lock),
                    subtitle = stringResource(if (locked) R.string.sound_lock_on_hint else R.string.sound_lock_off_hint),
                    checked = locked,
                    onCheckedChange = { lock -> if (lock) SoundLock.lock(context) else SoundLock.unlock(context) },
                )
            }
        }
        SwitchRow(
            title = stringResource(R.string.lock_includes_app),
            subtitle = stringResource(
                if (sound.lockIncludesApp) R.string.lock_includes_app_on_hint else R.string.lock_includes_app_off_hint,
            ),
            checked = sound.lockIncludesApp,
            onCheckedChange = { value -> store.updateSoundProfiles { it.copy(lockIncludesApp = value) } },
        )
        SwitchRow(
            title = stringResource(R.string.block_volume_buttons),
            subtitle = stringResource(R.string.block_volume_buttons_hint),
            checked = buttonsBlocked,
            onCheckedChange = { Permissions.open(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
        )
        if (locked && !buttonsBlocked && !listenerRunning) {
            Text(
                stringResource(R.string.sound_lock_needs_service),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    deleting?.let { profile ->
        DeleteSoundProfileDialog(profile, onDismiss = { deleting = null }, onDeleted = { deleting = null })
    }
}

/** Editor for one sound profile. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SoundProfileScreen(profileId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    val sound by store.soundProfiles.collectAsStateWithLifecycle()
    val profile = sound.profile(profileId)
    var confirmDelete by remember { mutableStateOf(false) }

    fun update(transform: (SoundProfile) -> SoundProfile) = store.updateSoundProfiles { it.update(profileId, transform) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(profile?.let { context.soundProfileName(it) } ?: stringResource(R.string.sound_profiles)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
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
        ) {
            if (profile == null) {
                Text(stringResource(R.string.profile_removed))
                return@Column
            }

            SectionCard(stringResource(R.string.section_profile)) {
                OutlinedTextField(
                    value = context.soundProfileName(profile),
                    onValueChange = { name -> update { it.copy(name = name) } },
                    label = { Text(stringResource(R.string.profile_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.profile_icon), style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoundIcon.entries.forEach { icon ->
                        val choice = profile.copy(icon = icon)
                        val selected = profile.effectiveIcon == icon
                        val description = stringResource(icon.labelRes())
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .border(
                                    width = if (selected) 2.dp else 0.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .semantics { contentDescription = description; this.selected = selected }
                                .clickable { update { it.copy(icon = icon) } },
                            contentAlignment = Alignment.Center,
                        ) { SoundProfileIcon(choice) }
                    }
                }
                DropdownRow(
                    title = stringResource(R.string.ringer_mode),
                    options = RingerSetting.entries,
                    selected = profile.ringerMode,
                    label = { stringResource(it.labelRes()) },
                    onSelect = { mode -> update { it.copy(ringerMode = mode) } },
                )
            }

            SectionCard(stringResource(R.string.section_volumes)) {
                Text(stringResource(R.string.volumes_hint), style = MaterialTheme.typography.bodySmall)
                SoundStream.entries.forEachIndexed { index, stream ->
                    if (index > 0) HorizontalDivider()
                    val value = profile.volumes[stream]
                    SwitchRow(
                        title = stringResource(stream.labelRes()),
                        subtitle = if (value == null) stringResource(R.string.keep_unchanged) else null,
                        checked = value != null,
                        onCheckedChange = { change ->
                            update {
                                it.copy(volumes = if (change) it.volumes + (stream to 70) else it.volumes - stream)
                            }
                        },
                    )
                    if (value != null) {
                        PercentSlider(
                            title = stringResource(R.string.volume),
                            value = value,
                            range = 0..100,
                            onValueChange = { v -> update { it.copy(volumes = it.volumes + (stream to v)) } },
                        )
                    }
                }
            }

            SectionCard(stringResource(R.string.section_vibration)) {
                DropdownRow(
                    title = stringResource(R.string.vibrate_when_ringing),
                    options = Toggle.entries,
                    selected = profile.vibrateWhenRinging,
                    label = { stringResource(it.labelRes()) },
                    onSelect = { value -> update { it.copy(vibrateWhenRinging = value) } },
                )
                DropdownRow(
                    title = stringResource(R.string.touch_vibration),
                    options = Toggle.entries,
                    selected = profile.touchVibration,
                    label = { stringResource(it.labelRes()) },
                    onSelect = { value -> update { it.copy(touchVibration = value) } },
                )
                Text(stringResource(R.string.system_settings_hint), style = MaterialTheme.typography.bodySmall)
            }

            SectionCard(stringResource(R.string.do_not_disturb)) {
                DropdownRow(
                    title = stringResource(R.string.do_not_disturb),
                    options = DndSetting.entries,
                    selected = profile.doNotDisturb,
                    label = { stringResource(it.labelRes()) },
                    onSelect = { value -> update { it.copy(doNotDisturb = value) } },
                )
            }

            Button(onClick = { applyWithFeedback(context, profile) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Check, contentDescription = null)
                Text(stringResource(R.string.apply_now))
            }
            OutlinedButton(
                onClick = { confirmDelete = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Text(stringResource(R.string.delete_sound_profile))
            }
        }
    }

    if (confirmDelete && profile != null) {
        DeleteSoundProfileDialog(profile, onDismiss = { confirmDelete = false }, onDeleted = {
            confirmDelete = false
            onBack()
        })
    }
}
