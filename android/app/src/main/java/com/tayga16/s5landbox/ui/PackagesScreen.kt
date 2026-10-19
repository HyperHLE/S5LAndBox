package com.tayga16.s5landbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tayga16.s5landbox.data.EngineRepository
import com.tayga16.s5landbox.data.GuestManifest
import com.tayga16.s5landbox.data.GuestPackage
import com.tayga16.s5landbox.data.GuestPackageDownloader
import com.tayga16.s5landbox.data.InstallPhase
import com.tayga16.s5landbox.data.InstallResult
import com.tayga16.s5landbox.data.StopMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Cydia provisioning for one machine.
 *
 * The screen downloads nothing on its own: the pinned manifest comes from the
 * native core, [GuestPackageDownloader] verifies every byte against it, and
 * the portable builder publishes the guest disk. The install runs on an IO
 * thread while this screen polls the engine's atomic progress counters.
 */
@Composable
fun PackagesScreen(
    repository: EngineRepository,
    machineId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val downloader = remember { GuestPackageDownloader(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var manifest by remember { mutableStateOf<GuestManifest?>(null) }
    var machinePresent by remember { mutableStateOf(false) }
    var machineReady by remember { mutableStateOf(false) }
    var engineIdle by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var includeAppsync by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }
    var error by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    suspend fun refresh() {
        val data = withContext(Dispatchers.IO) {
            Triple(
                repository.packages(machineId),
                repository.machines(),
                repository.status(),
            )
        }
        manifest = data.first
        val machine = data.second.firstOrNull { it.id == machineId }
        machinePresent = machine != null
        machineReady = machine?.work == true
        // `instanceId` outlives a stopped machine, so only the phase tells
        // whether the engine is free to replace a disk.
        engineIdle = data.third.idle
        loaded = true
    }

    LaunchedEffect(machineId) {
        refresh()
        while (isActive) {
            if (!busy) refresh()
            delay(2_000)
        }
    }

    if (busy) KeepScreenOn()

    fun stopMachine() {
        if (busy) return
        busy = true
        stage = "Powering off the machine…"
        scope.launch {
            withContext(Dispatchers.IO) {
                repository.stop(StopMode.POWER_OFF)
                var waited = 0
                while (!repository.status().idle && waited < 600) {
                    delay(100)
                    waited++
                }
            }
            busy = false
            refresh()
        }
    }

    fun blockerFor(state: GuestManifest?): String? = when {
        state == null -> null
        !state.machineOk || !machinePresent -> "This machine no longer exists."
        !machineReady ->
            "Boot this machine once first. The installer replaces the writable " +
                "guest disk, so it must exist before Cydia can be added."
        state.snapshots > 0L ->
            "This machine has ${state.snapshots} snapshot(s). Delete them in " +
                "Snapshots first: the installer refuses to replace a disk that " +
                "snapshots still depend on."
        state.busy -> "Another installation is already running."
        else -> null
    }

    fun startInstall() {
        val state = manifest ?: return
        if (blockerFor(state) != null) return
        val appsync = includeAppsync && !state.installed
        busy = true
        error = ""
        message = ""
        progress = 0f
        stage = "Preparing"
        scope.launch {
            var poller: Job? = null
            try {
                if (!repository.status().idle) {
                    stage = "Powering off the machine…"
                    val stopped = withContext(Dispatchers.IO) {
                        repository.stop(StopMode.POWER_OFF)
                        var waited = 0
                        while (!repository.status().idle && waited < 600) {
                            delay(100)
                            waited++
                        }
                        repository.status().idle
                    }
                    if (!stopped) {
                        error = "The machine could not be stopped. Try again in a moment."
                        busy = false
                        return@launch
                    }
                    refresh()
                }

                poller = launch {
                    while (isActive) {
                        val p = withContext(Dispatchers.IO) { repository.installProgress() }
                        if (p.active) {
                            stage = InstallPhase.name(p.phase)
                            progress = p.fraction
                        }
                        delay(250)
                    }
                }

                suspend fun download(packages: List<GuestPackage>): String {
                    stage = "Downloading packages"
                    val directory = downloader.downloadAll(
                        packages,
                        state.identity,
                    ) { p ->
                        stage = "Downloading ${p.packageName} " +
                            "(${p.packageIndex + 1}/${p.packageCount})"
                        progress = p.fraction
                    }
                    return directory.absolutePath
                }

                var result: InstallResult
                if (state.installed) {
                    stage = "Checking the existing installation"
                    result = withContext(Dispatchers.IO) {
                        repository.installPackages(machineId, "", false)
                    }
                    if (result.needsPackages) {
                        val packages = state.installSet
                        val directory = download(packages)
                        stage = "Building the guest disk"
                        progress = 0f
                        result = withContext(Dispatchers.IO) {
                            repository.installPackages(machineId, directory, false)
                        }
                    }
                } else {
                    val packages = state.installSet +
                        listOfNotNull(state.appsync?.takeIf { appsync })
                    val directory = download(packages)
                    stage = "Building the guest disk"
                    progress = 0f
                    result = withContext(Dispatchers.IO) {
                        repository.installPackages(machineId, directory, appsync)
                    }
                }

                if (result.ok) {
                    message = when {
                        state.installed -> "The installation is up to date."
                        appsync -> "Cydia and AppSync are installed. Start the machine " +
                            "and open Cydia."
                        else -> "Cydia is installed. Start the machine and open Cydia."
                    }
                } else {
                    error = result.detail.ifEmpty { result.statusText }
                }
            } catch (failure: Throwable) {
                error = failure.message ?: "The installation could not be completed."
            } finally {
                poller?.cancel()
                busy = false
                refresh()
            }
        }
    }

    val state = manifest
    val blocker = blockerFor(state)

    Scaffold(
        topBar = {
            AppBar(
                title = "Cydia & Packages",
                subtitle = "Cydia · iPhone OS 3.1.3",
                onBack = onBack,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (!loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state == null || !state.ok) {
                item(key = "manifest") {
                    NoticeCard(
                        tone = NoticeTone.ERROR,
                        title = "The package manifest is unavailable",
                        body = "This build cannot verify its pinned packages.",
                    )
                }
            }

            item(key = "intro") {
                IntroCard(state = state)
            }

            if (busy) {
                item(key = "progress") {
                    ProgressCard(stage = stage, fraction = progress)
                }
            }

            if (error.isNotEmpty()) {
                item(key = "error") {
                    NoticeCard(
                        tone = NoticeTone.ERROR,
                        title = "Installation failed",
                        body = error,
                    )
                }
            }
            if (message.isNotEmpty()) {
                item(key = "message") {
                    NoticeCard(
                        tone = NoticeTone.SUCCESS,
                        title = "Done",
                        body = message,
                    )
                }
            }

            if (!engineIdle && !busy) {
                item(key = "running-notice") {
                    NoticeCard(
                        tone = NoticeTone.WARNING,
                        title = "Machine is active",
                        body = "The machine is currently running or paused. Starting installation will power it off automatically, discarding unsaved guest RAM.",
                        action = "Power off now",
                        onAction = { stopMachine() },
                    )
                }
            }

            blocker?.let { reason ->
                item(key = "blocker") {
                    NoticeCard(
                        tone = NoticeTone.WARNING,
                        title = "Not ready",
                        body = reason,
                    )
                }
            }

            if (state != null && state.ok && state.installed && !busy) {
                item(key = "installed") {
                    InstalledCard(
                        enabled = blocker == null,
                        onVerify = { startInstall() },
                    )
                }
            }

            if (state != null && state.ok && !state.installed && !busy) {
                item(key = "appsync") {
                    AppsyncCard(
                        state = state,
                        checked = includeAppsync,
                        enabled = blocker == null,
                        onCheckedChange = { includeAppsync = it },
                    )
                }
                item(key = "install") {
                    Button(
                        onClick = { startInstall() },
                        enabled = blocker == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Download and install Cydia",
                        )
                    }
                }
                item(key = "warning") {
                    NoticeCard(
                        tone = NoticeTone.WARNING,
                        title = "This replaces the guest disk",
                        body = "The machine must be stopped. The installer powers it off, " +
                            "discards unsaved guest RAM, and upgrades the disk to the 2 GiB " +
                            "minimum. Keep about 2.2 GB free.",
                    )
                }
            }

            if (state != null && state.packages.isNotEmpty()) {
                item(key = "packages-header") {
                    Text(
                        text = "PINNED PACKAGES",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                    )
                }
                items(state.packages, key = { it.filename }) { packageInfo ->
                    PackageCard(packageInfo)
                }
            }
        }
    }
}

@Composable
private fun IntroCard(state: GuestManifest?) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = if (state?.installed == true) "Cydia is installed" else "Add Cydia",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Cydia 3.7 and its ${state?.installSet?.size ?: 0} pinned " +
                    "dependencies are fetched from their original archives, checked " +
                    "byte-for-byte and SHA-256 against the manifest, then built into " +
                    "this machine's guest disk.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state != null) {
                Text(
                    text = "Download: ${formatBytes(state.totalBytes)} · disk: " +
                        "${formatBytes(state.workImageBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InstalledCard(enabled: Boolean, onVerify: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "  Jailbreak installed",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                text = "Start the machine and open Cydia. Verifying repeats the " +
                    "integrity checks and applies any maintenance the builder still owes " +
                    "this disk; it downloads only what is missing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            OutlinedButton(onClick = onVerify, enabled = enabled) { Text("Verify or repair") }
        }
    }
}

@Composable
private fun AppsyncCard(
    state: GuestManifest,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val appsync = state.appsync ?: return
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Install AppSync",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "Optional. ${appsync.name} ${appsync.version} patches " +
                        "installd so unsigned apps can run. It is added to new " +
                        "jailbreaks only and can be installed now for " +
                        "${formatBytes(appsync.size)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

@Composable
private fun ProgressCard(stage: String, fraction: Float) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = stage, style = MaterialTheme.typography.titleSmall)
            if (fraction > 0f) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "${(fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private enum class NoticeTone { ERROR, WARNING, SUCCESS }

@Composable
private fun NoticeCard(
    tone: NoticeTone,
    title: String,
    body: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val color = when (tone) {
        NoticeTone.ERROR -> MaterialTheme.colorScheme.errorContainer
        NoticeTone.WARNING -> MaterialTheme.colorScheme.secondaryContainer
        NoticeTone.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
    }
    val content = when (tone) {
        NoticeTone.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        NoticeTone.WARNING -> MaterialTheme.colorScheme.onSecondaryContainer
        NoticeTone.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (tone != NoticeTone.SUCCESS) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = content,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = content,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(
                        start = if (tone == NoticeTone.SUCCESS) 0.dp else 6.dp,
                    ),
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = content,
            )
            if (action != null && onAction != null) {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = content,
                        contentColor = color,
                    ),
                ) {
                    Text(action)
                }
            }
        }
    }
}

@Composable
private fun PackageCard(packageInfo: GuestPackage) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = packageInfo.name,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = packageInfo.version,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = formatBytes(packageInfo.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
