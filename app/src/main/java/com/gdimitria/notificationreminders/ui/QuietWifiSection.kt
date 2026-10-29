package com.gdimitria.notificationreminders.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gdimitria.notificationreminders.R
import com.gdimitria.notificationreminders.data.Store
import com.gdimitria.notificationreminders.engine.WifiWatcher
import com.gdimitria.notificationreminders.model.AppSettings
import com.gdimitria.notificationreminders.model.Sources
import com.gdimitria.notificationreminders.model.WifiQuiet

/** Sources that can be kept quiet: Missed calls, SMS and MMS, then the selected apps. */
private fun AppSettings.quietKeys(): List<String> =
    listOf(Sources.MISSED_CALLS, Sources.MESSAGES) + apps.map { it.packageName }

/** Wi-Fi networks on which a set of reminder settings does not remind, such as home. */
@Composable
fun QuietWifiSection(quietWifi: List<WifiQuiet>, onChange: (List<WifiQuiet>) -> Unit) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    val settings by store.settings.collectAsStateWithLifecycle()
    val sound by store.soundProfiles.collectAsStateWithLifecycle()
    val ssid by WifiWatcher.ssid.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WifiQuiet?>(null) }

    fun save(old: WifiQuiet?, new: WifiQuiet) = onChange(quietWifi.filter { it != old && it.ssid != new.ssid } + new)

    SectionCard(stringResource(R.string.quiet_wifi)) {
        Text(
            stringResource(R.string.quiet_wifi_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        quietWifi.forEachIndexed { index, quiet ->
            if (index > 0) HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        quiet.ssid + if (quiet.ssid == ssid) " " + stringResource(R.string.wifi_rule_active) else "",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        if (quiet.keys.isEmpty()) {
                            stringResource(R.string.quiet_all)
                        } else {
                            quiet.keys.joinToString(", ") { context.sourceLabel(settings, it) }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { editing = quiet }) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                }
                IconButton(onClick = { onChange(quietWifi - quiet) }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove))
                }
            }
        }
        OutlinedButton(onClick = { picking = true }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(stringResource(R.string.quiet_add_network))
        }
        if (ssid == null) {
            Text(stringResource(R.string.quiet_needs_location), style = MaterialTheme.typography.bodySmall)
        }
    }

    if (picking) {
        WifiPickerDialog(
            current = ssid,
            known = sound.knownSsids,
            onPick = { name ->
                picking = false
                editing = quietWifi.firstOrNull { it.ssid == name } ?: WifiQuiet(name)
            },
            onDismiss = { picking = false },
        )
    }

    editing?.let { quiet ->
        QuietEditDialog(
            quiet = quiet,
            keys = settings.quietKeys(),
            label = { context.sourceLabel(settings, it) },
            onSave = { new ->
                save(quietWifi.firstOrNull { it.ssid == quiet.ssid }, new)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun QuietEditDialog(
    quiet: WifiQuiet,
    keys: List<String>,
    label: (String) -> String,
    onSave: (WifiQuiet) -> Unit,
    onDismiss: () -> Unit,
) {
    var all by remember { mutableStateOf(quiet.keys.isEmpty()) }
    var chosen by remember { mutableStateOf(quiet.keys) }

    @Composable
    fun option(text: String, checked: Boolean, onToggle: () -> Unit) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = null)
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quiet_on_network, quiet.ssid)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                option(stringResource(R.string.quiet_all), all) { all = !all }
                if (!all) {
                    HorizontalDivider()
                    keys.forEach { key ->
                        option(label(key), key in chosen) {
                            chosen = if (key in chosen) chosen - key else chosen + key
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(WifiQuiet(quiet.ssid, if (all) emptySet() else chosen)) },
                enabled = all || chosen.isNotEmpty(),
            ) { Text(stringResource(R.string.done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
