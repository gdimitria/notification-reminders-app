package com.gdimitria.notificationreminders.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.gdimitria.notificationreminders.R
import com.gdimitria.notificationreminders.model.Sources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(val packageName: String, val label: String, val launchable: Boolean)

private fun loadInstalledApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    val launchable = pm.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
    ).map { it.activityInfo.packageName }.toSet()
    return pm.getInstalledApplications(0)
        .filter { it.packageName != context.packageName }
        .map {
            InstalledApp(
                packageName = it.packageName,
                label = it.loadLabel(pm).toString(),
                launchable = it.packageName in launchable || it.flags and ApplicationInfo.FLAG_SYSTEM == 0,
            )
        }
        .sortedBy { it.label.lowercase() }
}

@Composable
fun rememberAppIcon(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }
    }
    return icon
}

@Composable
fun AppIcon(packageName: String) {
    if (Sources.isBuiltIn(packageName)) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(
                if (packageName == Sources.MISSED_CALLS) Icons.Default.Call else Icons.Default.Email,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        return
    }
    val icon = rememberAppIcon(packageName)
    Box(Modifier.size(40.dp)) {
        if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(40.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(
    initiallySelected: Set<String>,
    onDone: (List<InstalledApp>) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val apps by produceState<List<InstalledApp>?>(null) {
        value = withContext(Dispatchers.IO) { loadInstalledApps(context) }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var showSystem by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(initiallySelected) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.choose_apps)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = {
                        onDone(apps.orEmpty().filter { it.packageName in selected })
                    }) { Text(stringResource(R.string.done)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.search_apps)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = showSystem, onCheckedChange = { showSystem = it })
                Text(stringResource(R.string.show_system_apps))
                Spacer(Modifier.weight(1f))
                Text(
                    pluralStringResource(R.plurals.selected_count, selected.size, selected.size),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            val list = apps
            if (list == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            val filtered = list.filter {
                (showSystem || it.launchable || it.packageName in selected) &&
                    (query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true))
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.packageName }) { app ->
                    val checked = app.packageName in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selected = if (checked) selected - app.packageName else selected + app.packageName }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppIcon(app.packageName)
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Checkbox(checked = checked, onCheckedChange = null)
                    }
                }
            }
        }
    }
}
