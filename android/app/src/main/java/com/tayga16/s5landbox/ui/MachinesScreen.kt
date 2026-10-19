package com.tayga16.s5landbox.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.os.Environment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tayga16.s5landbox.data.BindResult
import com.tayga16.s5landbox.data.EngineRepository
import com.tayga16.s5landbox.data.FirmwareState
import com.tayga16.s5landbox.data.IpaInfo
import com.tayga16.s5landbox.data.Machine
import com.tayga16.s5landbox.data.StopMode
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MachinesScreen(
    repository: EngineRepository,
    onOpen: (String) -> Unit,
    onImport: () -> Unit,
    onSettings: (String) -> Unit,
    onSnapshots: (String) -> Unit,
    onPackages: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var machines by remember { mutableStateOf<List<Machine>>(emptyList()) }
    var firmware by remember { mutableStateOf<FirmwareState?>(null) }
    var runningId by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Machine?>(null) }
    var deleteTarget by remember { mutableStateOf<Machine?>(null) }
    var kernelLogTarget by remember { mutableStateOf<Machine?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    var ipaMachineTarget by remember { mutableStateOf<Machine?>(null) }
    var ipaInfoTarget by remember { mutableStateOf<Pair<File, IpaInfo>?>(null) }
    var ipaInspecting by remember { mutableStateOf(false) }
    var ipaInstalling by remember { mutableStateOf(false) }

    val ipaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val machine = ipaMachineTarget ?: return@rememberLauncherForActivityResult
        scope.launch {
            ipaInspecting = true
            val (cachedFile, info) = withContext(Dispatchers.IO) {
                try {
                    val temp = File(context.cacheDir, "ipa_install_${System.currentTimeMillis()}.ipa")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(temp).use { output ->
                            input.copyTo(output)
                        }
                    }
                    val res = repository.inspectIpa(temp.absolutePath)
                    temp to res
                } catch (e: Exception) {
                    null to IpaInfo(ok = false, detail = e.message ?: "Failed reading IPA")
                }
            }
            ipaInspecting = false
            if (cachedFile != null && info.ok) {
                ipaInfoTarget = cachedFile to info
            } else {
                cachedFile?.delete()
                snackbar.showSnackbar(info.detail.ifEmpty { "Invalid or unsupported IPA file." })
            }
        }
    }

    suspend fun load() {
        val data = withContext(Dispatchers.IO) {
            Triple(repository.machines(), repository.firmware(), repository.runningMachineId())
        }
        machines = data.first
        firmware = data.second
        runningId = data.third
        loaded = true
    }

    fun announce(result: BindResult, success: String) {
        scope.launch {
            val message = if (result.ok) success else result.detail.ifEmpty { "That did not work." }
            snackbar.showSnackbar(message)
            load()
        }
    }

    fun stopMachine(id: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                repository.stop(StopMode.POWER_OFF)
                var waited = 0
                while (!repository.status().idle && waited < 600) {
                    delay(100)
                    waited++
                }
            }
            snackbar.showSnackbar("Machine powered off.")
            load()
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            load()
            delay(2_000)
        }
    }

    Scaffold(
        topBar = {
            AppBar(
                title = "S5LAndBox",
                subtitle = "Samsung S5L8900 · iPhone OS 3.1.3",
                actions = {
                    IconButton(onClick = onImport) {
                        Icon(Icons.Filled.Download, contentDescription = "Firmware")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New machine") },
            )
        },
    ) { padding ->
        if (!loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val state = firmware
                if (state != null && !state.ok) {
                    item(key = "firmware") {
                        FirmwareBanner(state = state, onImport = onImport)
                    }
                }

                if (machines.isEmpty()) {
                    item(key = "empty") {
                        EmptyMachines(onCreate = { creating = true })
                    }
                } else {
                    item(key = "header") {
                        Text(
                            text = "MACHINES",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        )
                    }
                    items(machines, key = { it.id }) { machine ->
                        MachineCard(
                            machine = machine,
                            running = machine.id == runningId,
                            menuOpen = menuFor == machine.id,
                            onOpen = { onOpen(machine.id) },
                            onMenu = {
                                menuFor = if (menuFor == machine.id) null else machine.id
                            },
                            onDismissMenu = { menuFor = null },
                            onRename = { menuFor = null; renameTarget = machine },
                            onSettings = { menuFor = null; onSettings(machine.id) },
                            onSnapshots = { menuFor = null; onSnapshots(machine.id) },
                            onPackages = { menuFor = null; onPackages(machine.id) },
                            onInstallIpa = {
                                menuFor = null
                                ipaMachineTarget = machine
                                ipaPicker.launch(arrayOf("*/*"))
                            },
                            onDelete = { menuFor = null; deleteTarget = machine },
                            onStop = { menuFor = null; stopMachine(machine.id) },
                            onOpenKernelLog = { menuFor = null; kernelLogTarget = machine },
                            onExportLog = {
                                menuFor = null
                                val machineDir = File(context.getExternalFilesDir(null), "Machines/${machine.id}")
                                val logFile = File(machineDir, "console.log")
                                if (logFile.exists() && logFile.length() > 0) {
                                    try {
                                        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                        downloadDir.mkdirs()
                                        val safeName = machine.name.replace(" ", "_")
                                        val target = File(downloadDir, "s5landbox-$safeName.log")
                                        logFile.copyTo(target, overwrite = true)
                                        scope.launch {
                                            snackbar.showSnackbar("Log exported to Downloads/${target.name}")
                                        }
                                    } catch (e: Exception) {
                                        scope.launch {
                                            snackbar.showSnackbar("Export failed: ${e.message}")
                                        }
                                    }
                                } else {
                                    scope.launch {
                                        snackbar.showSnackbar("No log file found for ${machine.name} yet.")
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        CreateMachineDialog(
            suggestedName = "iPhone ${machines.size + 1}",
            onDismiss = { creating = false },
            onCreate = { name, diskGiB ->
                creating = false
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        repository.createMachine(name, BooleanArray(0), diskGiB)
                    }
                    if (result.ok) {
                        snackbar.showSnackbar("Machine created.")
                        load()
                    } else {
                        snackbar.showSnackbar(
                            result.detail.ifEmpty { "The machine could not be created." },
                        )
                    }
                }
            },
        )
    }

    renameTarget?.let { machine ->
        RenameMachineDialog(
            machine = machine,
            onDismiss = { renameTarget = null },
            onRename = { name ->
                renameTarget = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        repository.renameMachine(machine.id, name)
                    }
                    announce(result, "Machine renamed.")
                }
            },
        )
    }

    deleteTarget?.let { machine ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${machine.name}?") },
            text = {
                Text(
                    "Its writable root filesystem and every snapshot are deleted. " +
                        "Imported firmware is kept.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                repository.deleteMachine(machine.id)
                            }
                            announce(result, "Machine deleted.")
                        }
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }

    if (ipaInspecting) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Inspecting IPA") },
            text = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text("Reading Info.plist and Mach-O binary…")
                }
            },
            confirmButton = {},
        )
    }

    if (ipaInstalling) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Installing Application") },
            text = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text("Provisioning app into root filesystem…")
                }
            },
            confirmButton = {},
        )
    }

    ipaInfoTarget?.let { (cachedFile, info) ->
        val targetMachine = ipaMachineTarget
        if (targetMachine != null) {
            val isRunning = targetMachine.id == runningId
            InstallIpaDialog(
                machine = targetMachine,
                info = info,
                running = isRunning,
                onDismiss = {
                    cachedFile.delete()
                    ipaInfoTarget = null
                    ipaMachineTarget = null
                },
                onInstall = {
                    val m = targetMachine
                    ipaInfoTarget = null
                    ipaInstalling = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            repository.installIpa(m.id, context.cacheDir.absolutePath, cachedFile.absolutePath)
                        }
                        cachedFile.delete()
                        ipaInstalling = false
                        ipaMachineTarget = null
                        if (result.ok) {
                            snackbar.showSnackbar("Installed ${info.name}! Start the machine to see it on the Home screen.")
                            load()
                        } else {
                            snackbar.showSnackbar(result.detail.ifEmpty { "Installation failed." })
                        }
                    }
                },
            )
        }
    }

    kernelLogTarget?.let { machine ->
        KernelLogDialog(
            machine = machine,
            isRunning = machine.id == runningId,
            onDismiss = { kernelLogTarget = null },
            onSaveToDownloads = { text ->
                try {
                    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    downloadDir.mkdirs()
                    val safeName = machine.name.replace(" ", "_")
                    val target = File(downloadDir, "s5landbox-$safeName-kernel.log")
                    target.writeText(text)
                    scope.launch {
                        snackbar.showSnackbar("Saved to Downloads/${target.name}")
                    }
                } catch (e: Exception) {
                    scope.launch {
                        snackbar.showSnackbar("Save failed: ${e.message}")
                    }
                }
            },
        )
    }
}

@Composable
private fun FirmwareBanner(state: FirmwareState, onImport: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Firmware needed",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "Choose an iPhone1,2 3.1.3 (7E18) IPSW. Its kernel, device tree " +
                    "and root filesystem are unpacked once into app storage.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            val present = buildList {
                if (state.kernelPresent) add("kernel")
                if (state.devicetreePresent) add("device tree")
                if (state.rootfsPresent) add("root filesystem")
            }
            if (present.isNotEmpty()) {
                Text(
                    text = "On disk: ${present.joinToString()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Button(onClick = onImport) { Text("Import firmware") }
        }
    }
}

@Composable
private fun EmptyMachines(onCreate: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Filled.Smartphone,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("No machines yet", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "A machine owns its own writable copy of the root filesystem, " +
                    "so changes in the guest never touch the imported firmware.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Button(onClick = onCreate) { Text("Create the first machine") }
        }
    }
}

@Composable
private fun MachineCard(
    machine: Machine,
    running: Boolean,
    menuOpen: Boolean,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onRename: () -> Unit,
    onSettings: () -> Unit,
    onSnapshots: () -> Unit,
    onPackages: () -> Unit,
    onInstallIpa: () -> Unit,
    onDelete: () -> Unit,
    onStop: () -> Unit,
    onOpenKernelLog: () -> Unit,
    onExportLog: () -> Unit,
) {
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Smartphone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = machine.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (running) {
                        Text(
                            text = "  RUNNING",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                Text(
                    text = "iPhone OS 3.1.3 · n82ap · ${machine.diskGiB} GB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val (label, tint) = when {
                        running -> "running" to MaterialTheme.colorScheme.secondary
                        machine.ready -> "ready to start" to MaterialTheme.colorScheme.onSurfaceVariant
                        machine.needsProvision -> "first boot prepares disk" to
                            MaterialTheme.colorScheme.tertiary
                        else -> "firmware incomplete" to MaterialTheme.colorScheme.error
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                    )
                    Text(
                        text = "· ${formatRetired(machine.retiredTotal)} instr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "opened ${formatDate(machine.lastOpenedUnix)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = onMenu) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Machine actions")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
                    if (running) {
                        DropdownMenuItem(
                            text = { Text("Power off") },
                            leadingIcon = { Icon(Icons.Filled.PowerSettingsNew, contentDescription = null) },
                            onClick = onStop,
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = onRename,
                    )
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        onClick = onSettings,
                    )
                    DropdownMenuItem(
                        text = { Text("Packages") },
                        leadingIcon = { Icon(Icons.Filled.Extension, contentDescription = null) },
                        onClick = onPackages,
                    )
                    DropdownMenuItem(
                        text = { Text("Install IPA") },
                        leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                        onClick = onInstallIpa,
                    )
                    DropdownMenuItem(
                        text = { Text("Snapshots") },
                        leadingIcon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                        onClick = onSnapshots,
                    )
                    DropdownMenuItem(
                        text = { Text("Kernel log") },
                        leadingIcon = { Icon(Icons.Filled.Terminal, contentDescription = null) },
                        onClick = onOpenKernelLog,
                    )
                    DropdownMenuItem(
                        text = { Text("Export log") },
                        leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null) },
                        onClick = onExportLog,
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = onDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateMachineDialog(
    suggestedName: String,
    onDismiss: () -> Unit,
    onCreate: (String, Int) -> Unit,
) {
    var name by remember { mutableStateOf(suggestedName) }
    var diskGiB by remember { mutableStateOf(4) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New machine") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Name") },
                )
                Text(
                    text = "Storage Capacity",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 4, 8).forEach { size ->
                        FilterChip(
                            selected = diskGiB == size,
                            onClick = { diskGiB = size },
                            label = { Text("$size GB") },
                        )
                    }
                }
                Text(
                    text = "The first boot creates a $diskGiB GB rootfs disk image for this machine. 4 GB or 8 GB is recommended for installing Cydia tweaks and IPA apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.trim(), diskGiB) },
                enabled = name.trim().isNotEmpty(),
            ) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RenameMachineDialog(
    machine: Machine,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by remember { mutableStateOf(machine.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename machine") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(name.trim()) }, enabled = name.trim().isNotEmpty()) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun InstallIpaDialog(
    machine: Machine,
    info: IpaInfo,
    running: Boolean,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Install Application") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = info.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "ID: ${info.bundleId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (info.version.isNotBlank()) {
                    Text(
                        text = "Version: ${info.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "Files: ${info.fileCount} (${info.uncompressedBytes / (1024 * 1024)} MB unpacked)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                if (running) {
                    Text(
                        text = "The machine is currently running. Stop the machine before installing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(
                        text = "The application will be added to ${machine.name}. Start the machine to see it on the Home screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onInstall,
                enabled = !running,
            ) {
                Text("Install")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun KernelLogDialog(
    machine: Machine,
    isRunning: Boolean,
    onDismiss: () -> Unit,
    onSaveToDownloads: (String) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val machineDir = remember(machine.id) {
        File(context.getExternalFilesDir(null), "Machines/${machine.id}")
    }
    val logFile = remember(machineDir) { File(machineDir, "console.log") }

    var logText by remember { mutableStateOf("") }
    var filterQuery by remember { mutableStateOf("") }

    fun readLog() {
        if (logFile.exists() && logFile.length() > 0) {
            try {
                val len = logFile.length()
                val text = if (len > 300_000) {
                    logFile.inputStream().use { stream ->
                        stream.skip(len - 300_000)
                        val bytes = stream.readBytes()
                        "[… earlier kernel log truncated, showing last 300 KB …]\n" + String(bytes)
                    }
                } else {
                    logFile.readText()
                }
                logText = text
            } catch (e: Exception) {
                logText = "Error reading kernel log: ${e.message}"
            }
        } else {
            logText = "(no kernel log output yet. Start the machine to record kernel console output.)"
        }
    }

    LaunchedEffect(machine.id) {
        readLog()
        if (isRunning) {
            while (isActive) {
                delay(1000)
                readLog()
            }
        }
    }

    val displayedText = remember(logText, filterQuery) {
        if (filterQuery.isBlank()) {
            logText
        } else {
            val q = filterQuery.trim()
            val filtered = logText.lineSequence().filter { it.contains(q, ignoreCase = true) }.joinToString("\n")
            if (filtered.isBlank()) "(no lines matching \"$q\")" else filtered
        }
    }

    val scroll = rememberScrollState()
    LaunchedEffect(displayedText) {
        if (filterQuery.isBlank()) {
            scroll.scrollTo(scroll.maxValue)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Filled.Terminal,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Kernel Log: ${machine.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isRunning) {
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = filterQuery,
                    onValueChange = { filterQuery = it },
                    placeholder = { Text("Filter log (e.g. panic, launchd, error)…", style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (filterQuery.isNotEmpty()) {
                            IconButton(onClick = { filterQuery = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0D1117))
                        .padding(10.dp),
                ) {
                    SelectionContainer {
                        Text(
                            text = displayedText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                            ),
                            color = Color(0xFF7EE787),
                            modifier = Modifier.verticalScroll(scroll),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val sizeKb = if (logFile.exists()) logFile.length() / 1024 else 0
                    val lineCount = logText.count { it == '\n' }
                    Text(
                        text = "$sizeKb KB · $lineCount lines",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (filterQuery.isNotEmpty()) {
                        Text(
                            text = "${displayedText.count { it == '\n' }} matches",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(displayedText))
                }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy")
                }
                TextButton(onClick = {
                    onSaveToDownloads(logText)
                }) {
                    Icon(Icons.Filled.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
    )
}
