@file:OptIn(ExperimentalMaterial3Api::class)

package com.dante.veil

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppScreen(vm: MainViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(vm.message) {
        val m = vm.message
        if (m != null) {
            snack.showSnackbar(m)
            vm.message = null
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Veil", fontWeight = FontWeight.Bold) }) },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Text("▦") },
                    label = { Text("Apps") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Text("◌") },
                    label = { Text(if (vm.hiddenCount > 0) "Hidden (${vm.hiddenCount})" else "Hidden") }
                )
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            ShizukuBanner(vm)
            Box(Modifier.weight(1f)) {
                if (tab == 0) AppsTab(vm) else HiddenTab(vm)
            }
        }
    }

    vm.pendingHide?.let { app ->
        AlertDialog(
            onDismissRequest = { vm.pendingHide = null },
            title = { Text("Hide ${app.label} entirely?") },
            text = {
                Text(
                    "The app disappears from the drawer, Settings and recents, and can't run " +
                        "or send notifications until you restore it from the Hidden tab. " +
                        "Its data is kept."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setAppHidden(app.pkg, app.label, true)
                    vm.pendingHide = null
                }) { Text("Hide") }
            },
            dismissButton = {
                TextButton(onClick = { vm.pendingHide = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ShizukuBanner(vm: MainViewModel) {
    val ctx = LocalContext.current
    if (vm.shizuku == ShizukuState.Ready || vm.shizuku == ShizukuState.Checking) return

    val running = vm.shizuku == ShizukuState.NeedPermission
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (running) "Veil needs Shizuku permission" else "Shizuku isn't running",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.size(4.dp))
            Text(
                if (running) {
                    "Allow Veil in the Shizuku prompt so it can toggle launcher icons."
                } else {
                    "Start Shizuku (wireless debugging or root), then come back. You can browse apps, but toggles stay locked."
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (running) {
                    Button(onClick = { vm.requestPermission() }) { Text("Grant permission") }
                } else {
                    Button(onClick = {
                        val i = ctx.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                        if (i != null) ctx.startActivity(i) else vm.message = "Shizuku isn't installed"
                    }) { Text("Open Shizuku") }
                }
                OutlinedButton(onClick = { vm.updateState() }) { Text("Re-check") }
            }
        }
    }
}

@Composable
fun AppsTab(vm: MainViewModel) {
    val shown = vm.visibleApps()
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            singleLine = true,
            placeholder = { Text("Search apps") },
            trailingIcon = if (vm.query.isNotEmpty()) {
                { TextButton(onClick = { vm.query = "" }) { Text("✕") } }
            } else null,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        )
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = vm.multiOnly,
                onClick = { vm.multiOnly = !vm.multiOnly },
                label = { Text("Multi-icon apps") }
            )
            FilterChip(
                selected = vm.showSystem,
                onClick = { vm.showSystem = !vm.showSystem },
                label = { Text("System apps") }
            )
        }
        if (vm.loading && vm.apps.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(shown, key = { it.pkg }) { app -> AppRow(vm, app) }
            }
        }
    }
}

@Composable
fun AppRow(vm: MainViewModel, app: AppEntry) {
    var expanded by rememberSaveable(app.pkg) { mutableStateOf(false) }
    val ready = vm.ready
    val multi = app.icons.size > 1

    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(app.pkg, 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    app.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (multi) "${app.icons.size} icons · ${app.icons.count { !it.enabled }} hidden" else app.pkg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!multi) {
                val ic = app.icons[0]
                Switch(
                    checked = ic.enabled,
                    enabled = ready,
                    onCheckedChange = { vm.setIconVisible(ic, it) }
                )
            } else {
                Text(if (expanded) "▲" else "▼", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (expanded) {
            Column(Modifier.padding(start = 74.dp, end = 16.dp, bottom = 10.dp)) {
                if (multi) {
                    app.icons.forEach { ic ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(ic.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    ic.cls.substringAfterLast('.'),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Switch(
                                checked = ic.enabled,
                                enabled = ready,
                                onCheckedChange = { vm.setIconVisible(ic, it) }
                            )
                        }
                    }
                } else {
                    Text(
                        app.icons[0].cls.substringAfterLast('.'),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { vm.pendingHide = app }, enabled = ready) {
                    Text("Hide entire app…")
                }
            }
        }
    }
}

@Composable
fun HiddenTab(vm: MainViewModel) {
    val rows = vm.hiddenItems()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        if (vm.drift > 0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "${vm.drift} icon(s) were re-enabled by their apps",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.size(8.dp))
                        Button(onClick = { vm.reapply() }, enabled = vm.ready) { Text("Re-hide them") }
                    }
                }
            }
        }
        if (rows.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Nothing hidden yet.\nToggle an icon off in the Apps tab.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        items(rows, key = { it.kind.name + it.key }) { row -> HiddenRow(vm, row) }
        if (rows.isNotEmpty()) {
            item {
                OutlinedButton(
                    onClick = { vm.restoreAll() },
                    enabled = vm.ready,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) { Text("Restore everything") }
            }
        }
    }
}

@Composable
fun HiddenRow(vm: MainViewModel, row: HiddenItem) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(row.pkg, 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                row.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        FilledTonalButton(
            enabled = vm.ready,
            onClick = {
                if (row.kind == Kind.ICON) {
                    vm.showIconByKey(row.key, row.title)
                } else {
                    vm.setAppHidden(row.pkg, row.title, false)
                }
            }
        ) { Text("Show") }
    }
}

@Composable
fun AppIcon(pkg: String, size: Dp) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) {
            try {
                ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
            } catch (_: Throwable) {
                null
            }
        }
    }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        bmp?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}
