package com.gdimitria.notificationreminders.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gdimitria.notificationreminders.R
import com.gdimitria.notificationreminders.data.Store
import com.gdimitria.notificationreminders.engine.WifiRules
import com.gdimitria.notificationreminders.engine.WifiWatcher
import com.gdimitria.notificationreminders.model.SoundProfiles
import com.gdimitria.notificationreminders.model.WifiRule
import java.util.UUID

private data class LocationState(val foreground: Boolean, val background: Boolean, val servicesOn: Boolean)

private fun locationState(context: Context): LocationState {
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    val foreground = granted(Manifest.permission.ACCESS_FINE_LOCATION)
    val background = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    val manager = context.getSystemService(LocationManager::class.java)
    val servicesOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) manager.isLocationEnabled else true
    return LocationState(foreground, background, servicesOn)
}

/** Location permission and location services, which Android requires to tell apps the Wi-Fi network name. */
@Composable
private fun LocationRows() {
    val context = LocalContext.current
    var state by remember { mutableStateOf(locationState(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state = locationState(context) }
    val foreground = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        state = locationState(context)
    }
    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        state = locationState(context)
    }

    @Composable
    fun row(title: Int, hint: Int, granted: Boolean, onGrant: () -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
                if (!granted) Text(stringResource(hint), style = MaterialTheme.typography.bodySmall)
            }
            if (!granted) TextButton(onClick = onGrant) { Text(stringResource(R.string.grant)) }
        }
    }

    row(R.string.perm_location, R.string.perm_location_hint, state.foreground) {
        foreground.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        row(R.string.perm_background_location, R.string.perm_background_location_hint, state.background) {
            // Android 11 and later open the app's location settings, where "Allow all the time" is chosen.
            if (state.foreground) background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            else foreground.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    row(R.string.location_services, R.string.location_services_hint, state.servicesOn) {
        Permissions.open(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }
}

/** Short description such as "Normal, locked while connected". */
private fun Context.wifiRuleSummary(sound: SoundProfiles, rule: WifiRule): String {
    val profile = sound.profile(rule.profileId)?.let { soundProfileName(it) } ?: getString(R.string.wifi_profile_missing)
    val mode = getString(if (rule.lock) R.string.wifi_summary_locked else R.string.wifi_summary_manual)
    return "$profile, $mode"
}

/** Wi-Fi rules inside the Sound profiles card. */
@Composable
fun WifiRulesSection(onEdit: (String) -> Unit) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    val sound by store.soundProfiles.collectAsStateWithLifecycle()
    val ssid by WifiWatcher.ssid.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<WifiRule?>(null) }

    Text(stringResource(R.string.wifi_rules), style = MaterialTheme.typography.titleSmall)
    Text(
        ssid?.let { stringResource(R.string.wifi_connected_to, it) } ?: stringResource(R.string.wifi_not_connected),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    sound.wifiRules.forEach { rule ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (rule.lock) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    rule.ssid + if (rule.id == sound.wifiActiveRuleId) " " + stringResource(R.string.wifi_rule_active) else "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (rule.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    context.wifiRuleSummary(sound, rule),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onEdit(rule.id) }) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
            }
            IconButton(onClick = { deleting = rule }) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_wifi_rule))
            }
        }
    }
    OutlinedButton(onClick = {
        val rule = WifiRule(
            id = UUID.randomUUID().toString(),
            ssid = ssid.orEmpty(),
            profileId = SoundProfiles.NORMAL_ID.takeIf { sound.profile(it) != null } ?: sound.profiles.firstOrNull()?.id.orEmpty(),
        )
        store.updateSoundProfiles { it.copy(wifiRules = it.wifiRules + rule) }
        onEdit(rule.id)
    }) {
        Icon(Icons.Default.Add, contentDescription = null)
        Text(stringResource(R.string.add_wifi_rule))
    }

    deleting?.let { rule ->
        DeleteWifiRuleDialog(rule, onDismiss = { deleting = null }, onDeleted = { deleting = null })
    }
}

@Composable
private fun DeleteWifiRuleDialog(rule: WifiRule, onDismiss: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_wifi_rule)) },
        text = { Text(stringResource(R.string.delete_wifi_rule_confirm, rule.ssid)) },
        confirmButton = {
            TextButton(onClick = {
                val store = Store.get(context)
                // Leaving the rule first releases its lock and, if chosen, restores the previous profile.
                if (store.soundProfiles.value.wifiActiveRuleId == rule.id) {
                    store.updateSoundProfiles { s -> s.copy(wifiRules = s.wifiRules.map { if (it.id == rule.id) it.copy(enabled = false) else it }) }
                    WifiRules.onNetworkChanged(context, WifiWatcher.ssid.value)
                }
                store.updateSoundProfiles { s -> s.copy(wifiRules = s.wifiRules.filter { it.id != rule.id }) }
                onDeleted()
            }) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Picks a network from the current one, the ones the phone connected to before, and the ones in range. */
@Composable
fun WifiPickerDialog(current: String?, known: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var nearby by remember { mutableStateOf(WifiWatcher.nearby(context)) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                nearby = WifiWatcher.nearby(context)
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        WifiWatcher.requestScan(context)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    val previous = known.filter { it != current }
    val inRange = nearby.filter { it != current && it !in previous }

    @Composable
    fun header(text: Int) {
        Text(
            stringResource(text),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
    }

    @Composable
    fun network(name: String) {
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPick(name) }
                .padding(vertical = 10.dp),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wifi_choose_from_list)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (current != null) {
                    header(R.string.wifi_list_current)
                    network(current)
                }
                header(R.string.wifi_list_known)
                if (previous.isEmpty()) {
                    Text(stringResource(R.string.wifi_list_known_empty), style = MaterialTheme.typography.bodySmall)
                }
                previous.forEach { network(it) }
                header(R.string.wifi_list_nearby)
                if (inRange.isEmpty()) {
                    Text(stringResource(R.string.wifi_list_nearby_empty), style = MaterialTheme.typography.bodySmall)
                }
                inRange.forEach { network(it) }
                Text(
                    stringResource(R.string.wifi_list_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Editor for one Wi-Fi rule. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiRuleScreen(ruleId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    val sound by store.soundProfiles.collectAsStateWithLifecycle()
    val ssid by WifiWatcher.ssid.collectAsStateWithLifecycle()
    val rule = sound.wifiRule(ruleId)
    var confirmDelete by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }

    fun update(transform: (WifiRule) -> WifiRule) {
        val old = store.soundProfiles.value.wifiRule(ruleId) ?: return
        val new = transform(old)
        store.updateSoundProfiles { s -> s.copy(wifiRules = s.wifiRules.map { if (it.id == ruleId) new else it }) }
        WifiRules.onRuleChanged(context, old, new)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(rule?.ssid?.ifBlank { null } ?: stringResource(R.string.wifi_rule)) },
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
            if (rule == null) {
                Text(stringResource(R.string.profile_removed))
                return@Column
            }

            SectionCard(stringResource(R.string.wifi_network)) {
                OutlinedTextField(
                    value = rule.ssid,
                    onValueChange = { name -> update { it.copy(ssid = name.trim()) } },
                    label = { Text(stringResource(R.string.wifi_network_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val current = ssid
                if (current != null && current != rule.ssid) {
                    OutlinedButton(onClick = { update { it.copy(ssid = current) } }) {
                        Text(stringResource(R.string.wifi_use_current, current))
                    }
                }
                OutlinedButton(onClick = { choosing = true }) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                    Text(stringResource(R.string.wifi_choose_from_list))
                }
                if (current == null) {
                    Text(stringResource(R.string.wifi_name_hidden_hint), style = MaterialTheme.typography.bodySmall)
                }
                LocationRows()
            }

            SectionCard(stringResource(R.string.wifi_when_connected)) {
                DropdownRow(
                    title = stringResource(R.string.wifi_apply_profile),
                    options = sound.profiles.map { it.id },
                    selected = rule.profileId,
                    label = { id -> sound.profile(id)?.let { context.soundProfileName(it) } ?: stringResource(R.string.wifi_profile_missing) },
                    onSelect = { id -> update { it.copy(profileId = id) } },
                )
                DropdownRow(
                    title = stringResource(R.string.wifi_while_connected),
                    options = listOf(true, false),
                    selected = rule.lock,
                    label = { lock -> stringResource(if (lock) R.string.wifi_lock else R.string.wifi_manual) },
                    onSelect = { lock -> update { it.copy(lock = lock) } },
                )
                Text(
                    stringResource(if (rule.lock) R.string.wifi_lock_hint else R.string.wifi_manual_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(stringResource(R.string.wifi_restore_previous_hint), style = MaterialTheme.typography.bodySmall)
                SwitchRow(
                    title = stringResource(R.string.wifi_rule_enabled),
                    checked = rule.enabled,
                    onCheckedChange = { enabled -> update { it.copy(enabled = enabled) } },
                )
            }

            OutlinedButton(
                onClick = { confirmDelete = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Text(stringResource(R.string.delete_wifi_rule))
            }
        }
    }

    if (choosing) {
        WifiPickerDialog(
            current = ssid,
            known = sound.knownSsids,
            onPick = { name ->
                choosing = false
                update { it.copy(ssid = name) }
            },
            onDismiss = { choosing = false },
        )
    }

    if (confirmDelete && rule != null) {
        DeleteWifiRuleDialog(rule, onDismiss = { confirmDelete = false }, onDeleted = {
            confirmDelete = false
            onBack()
        })
    }
}
