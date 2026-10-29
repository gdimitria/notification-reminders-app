package com.gdimitria.notificationreminders.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gdimitria.notificationreminders.R
import com.gdimitria.notificationreminders.data.Store
import com.gdimitria.notificationreminders.engine.ContactDirectory
import com.gdimitria.notificationreminders.model.ContactException
import com.gdimitria.notificationreminders.model.SoundProfiles
import java.util.UUID

private fun isCallScreeningApp(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)

/** Contact exceptions inside the Sound profiles card. */
@Composable
fun ContactExceptionsSection(onEdit: (String) -> Unit) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    val sound by store.soundProfiles.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<ContactException?>(null) }

    Text(stringResource(R.string.contact_exceptions), style = MaterialTheme.typography.titleSmall)
    Text(
        stringResource(R.string.contact_exceptions_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    sound.contactExceptions.forEach { exception ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    exception.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (exception.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val profile = sound.profile(exception.profileId)?.let { context.soundProfileName(it) }
                    ?: stringResource(R.string.wifi_profile_missing)
                val people = exception.contacts.size + exception.groups.size
                Text(
                    "$profile, " + pluralStringResource(R.plurals.exception_people, people, people),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onEdit(exception.id) }) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
            }
            IconButton(onClick = { deleting = exception }) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_contact_exception))
            }
        }
    }
    OutlinedButton(onClick = {
        val exception = ContactException(
            id = UUID.randomUUID().toString(),
            name = context.getString(R.string.new_contact_exception_name, sound.contactExceptions.size + 1),
            profileId = SoundProfiles.LOUD_ID.takeIf { sound.profile(it) != null } ?: sound.profiles.firstOrNull()?.id.orEmpty(),
        )
        store.updateSoundProfiles { it.copy(contactExceptions = it.contactExceptions + exception) }
        onEdit(exception.id)
    }) {
        Icon(Icons.Default.Add, contentDescription = null)
        Text(stringResource(R.string.add_contact_exception))
    }

    deleting?.let { exception ->
        DeleteContactExceptionDialog(exception, onDismiss = { deleting = null }, onDeleted = { deleting = null })
    }
}

@Composable
private fun DeleteContactExceptionDialog(exception: ContactException, onDismiss: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_contact_exception)) },
        text = { Text(stringResource(R.string.delete_profile_confirm, exception.name)) },
        confirmButton = {
            TextButton(onClick = {
                Store.get(context).updateSoundProfiles { s ->
                    s.copy(contactExceptions = s.contactExceptions.filter { it.id != exception.id })
                }
                onDeleted()
            }) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun StatusRow(title: String, hint: String, granted: Boolean, onGrant: () -> Unit) {
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

/** Editor for one contact exception. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactExceptionScreen(exceptionId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    val sound by store.soundProfiles.collectAsStateWithLifecycle()
    val exception = sound.contactException(exceptionId)
    var confirmDelete by remember { mutableStateOf(false) }
    var contactsAllowed by remember { mutableStateOf(ContactDirectory.hasPermission(context)) }
    var screening by remember { mutableStateOf(isCallScreeningApp(context)) }
    var groups by remember { mutableStateOf(ContactDirectory.groups(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        contactsAllowed = ContactDirectory.hasPermission(context)
        screening = isCallScreeningApp(context)
        groups = ContactDirectory.groups(context)
    }

    fun update(transform: (ContactException) -> ContactException) =
        store.updateSoundProfiles { it.updateContactException(exceptionId, transform) }

    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        contactsAllowed = ContactDirectory.hasPermission(context)
        groups = ContactDirectory.groups(context)
    }
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        val contact = uri?.let { ContactDirectory.contact(context, it) } ?: return@rememberLauncherForActivityResult
        update { e -> e.copy(contacts = e.contacts.filter { it.lookupKey != contact.lookupKey } + contact) }
    }
    val requestRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        screening = isCallScreeningApp(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(exception?.name ?: stringResource(R.string.contact_exceptions)) },
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
            if (exception == null) {
                Text(stringResource(R.string.profile_removed))
                return@Column
            }

            SectionCard(stringResource(R.string.section_profile)) {
                OutlinedTextField(
                    value = exception.name,
                    onValueChange = { name -> update { it.copy(name = name) } },
                    label = { Text(stringResource(R.string.profile_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownRow(
                    title = stringResource(R.string.wifi_apply_profile),
                    options = sound.profiles.map { it.id },
                    selected = exception.profileId,
                    label = { id -> sound.profile(id)?.let { context.soundProfileName(it) } ?: stringResource(R.string.wifi_profile_missing) },
                    onSelect = { id -> update { it.copy(profileId = id) } },
                )
                SwitchRow(
                    title = stringResource(R.string.wifi_rule_enabled),
                    checked = exception.enabled,
                    onCheckedChange = { enabled -> update { it.copy(enabled = enabled) } },
                )
            }

            SectionCard(stringResource(R.string.exception_people_title)) {
                StatusRow(
                    stringResource(R.string.perm_contacts),
                    stringResource(R.string.perm_contacts_hint),
                    contactsAllowed,
                ) { contactsPermission.launch(Manifest.permission.READ_CONTACTS) }

                Text(stringResource(R.string.exception_contacts), style = MaterialTheme.typography.titleSmall)
                if (exception.contacts.isEmpty()) {
                    Text(stringResource(R.string.exception_no_contacts), style = MaterialTheme.typography.bodySmall)
                }
                exception.contacts.forEach { contact ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(contact.name, style = MaterialTheme.typography.bodyLarge)
                            if (contact.numbers.isNotEmpty()) {
                                Text(contact.numbers.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        IconButton(onClick = {
                            update { e -> e.copy(contacts = e.contacts.filter { it.lookupKey != contact.lookupKey }) }
                        }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove))
                        }
                    }
                }
                OutlinedButton(
                    onClick = { if (contactsAllowed) pickContact.launch(null) else contactsPermission.launch(Manifest.permission.READ_CONTACTS) },
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(stringResource(R.string.exception_add_contact))
                }

                Text(stringResource(R.string.exception_groups), style = MaterialTheme.typography.titleSmall)
                if (groups.isEmpty()) {
                    Text(
                        stringResource(if (contactsAllowed) R.string.exception_no_groups else R.string.perm_contacts_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                groups.forEach { group ->
                    val selected = exception.groups.any { it.id == group.id }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                update { e ->
                                    e.copy(groups = if (selected) e.groups.filter { it.id != group.id } else e.groups + group)
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = selected, onCheckedChange = null)
                        Text(group.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                // Groups no longer on the phone stay listed so that they can be removed.
                exception.groups.filter { chosen -> groups.none { it.id == chosen.id } }.forEach { group ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(group.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { update { e -> e.copy(groups = e.groups.filter { it.id != group.id }) } }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove))
                        }
                    }
                }
            }

            SectionCard(stringResource(R.string.exception_applies_to)) {
                SwitchRow(
                    title = stringResource(R.string.exception_calls),
                    subtitle = stringResource(R.string.exception_calls_hint),
                    checked = exception.calls,
                    onCheckedChange = { calls -> update { it.copy(calls = calls) } },
                )
                if (exception.calls) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        StatusRow(
                            stringResource(R.string.perm_call_screening),
                            stringResource(R.string.perm_call_screening_hint),
                            screening,
                        ) {
                            requestRole.launch(
                                context.getSystemService(RoleManager::class.java)
                                    .createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING),
                            )
                        }
                    } else {
                        Text(stringResource(R.string.exception_calls_unsupported), style = MaterialTheme.typography.bodySmall)
                    }
                }
                SwitchRow(
                    title = stringResource(R.string.exception_messages),
                    subtitle = stringResource(R.string.exception_messages_hint),
                    checked = exception.messages,
                    onCheckedChange = { messages -> update { it.copy(messages = messages) } },
                )
                Text(stringResource(R.string.exception_reminders_hint), style = MaterialTheme.typography.bodySmall)
            }

            OutlinedButton(
                onClick = { confirmDelete = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Text(stringResource(R.string.delete_contact_exception))
            }
        }
    }

    if (confirmDelete && exception != null) {
        DeleteContactExceptionDialog(exception, onDismiss = { confirmDelete = false }, onDeleted = {
            confirmDelete = false
            onBack()
        })
    }
}
