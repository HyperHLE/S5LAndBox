package com.tayga16.s5landbox.ui

import android.net.Uri
import android.os.StatFs
import android.system.Os
import android.system.OsConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tayga16.s5landbox.data.EngineRepository
import com.tayga16.s5landbox.data.FirmwareState
import com.tayga16.s5landbox.data.ImportOutcome
import com.tayga16.s5landbox.data.ImportProgress
import com.tayga16.s5landbox.data.ImportStage
import java.io.FileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ImportScreen(repository: EngineRepository, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var firmware by remember { mutableStateOf<FirmwareState?>(null) }
    var importing by remember { mutableStateOf(false) }
    var cancelling by remember { mutableStateOf(false) }
    var cancelled by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<ImportProgress?>(null) }
    var outcome by remember { mutableStateOf<ImportOutcome?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf("") }
    var skipChecksums by remember { mutableStateOf(false) }
    var freeBytes by remember { mutableStateOf(0L) }

    suspend fun refreshFirmware() {
        val state = withContext(Dispatchers.IO) { repository.firmware() }
        firmware = state
        report = state.lastReport
    }

    LaunchedEffect(Unit) {
        refreshFirmware()
        freeBytes = withContext(Dispatchers.IO) {
            try {
                val root = context.getExternalFilesDir(null) ?: context.filesDir
                StatFs(root.absolutePath).availableBytes
            } catch (_: Throwable) {
                0L
            }
        }
    }

    LaunchedEffect(importing) {
        while (importing) {
            progress = withContext(Dispatchers.IO) { repository.importProgress() }
            delay(250)
        }
    }

    if (importing) KeepScreenOn()

    fun startImport(uri: Uri) {
        if (importing) return
        scope.launch {
            failure = null
            outcome = null
            importing = true
            cancelling = false
            cancelled = false
            progress = null

            val descriptor = withContext(Dispatchers.IO) {
                try {
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                        ?: return@withContext null
                    var size = pfd.statSize
                    if (size <= 0L) {
                        size = Os.lseek(pfd.fileDescriptor, 0L, OsConstants.SEEK_END)
                        Os.lseek(pfd.fileDescriptor, 0L, OsConstants.SEEK_SET)
                    }
                    if (size <= 0L) {
                        pfd.close()
                        null
                    } else {
                        pfd.detachFd() to size
                    }
                } catch (_: Throwable) {
                    null
                }
            }
            if (descriptor == null) {
                importing = false
                failure = "The selected file could not be opened."
                return@launch
            }

            val fd = descriptor.first
            val size = descriptor.second
            val result = withContext(Dispatchers.IO) {
                try {
                    repository.importIpsw(fd, size, skipChecksums)
                } catch (error: Throwable) {
                    ImportOutcome(false, error.message ?: "The import stopped unexpectedly.")
                } finally {
                    try {
                        // detachFd() handed us a raw descriptor; wrap it so Os
                        // can close it after the native import is done.
                        Os.close(FileDescriptor(fd))
                    } catch (_: Throwable) {
                    }
                }
            }

            val wasCancelling = cancelling
            importing = false
            cancelling = false
            cancelled = wasCancelling && !result.ok
            outcome = result
            refreshFirmware()
            if (!result.ok) failure = result.detail.ifEmpty { "The import did not finish." }
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) startImport(uri)
    }

    Scaffold(
        topBar = {
            AppBar(title = "Firmware", subtitle = "IPSW import", onBack = onDone)
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("iPhone1,2 · 3.1.3 (7E18)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "The IPSW is read where it is. Only the kernel, device tree and " +
                            "root filesystem are unpacked, once, into the app's storage. " +
                            "Nothing is uploaded anywhere.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "About 700 MB of free space is needed while importing.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (freeBytes in 1L until 900L * 1024L * 1024L) {
                        Text(
                            text = "Only ${formatBytes(freeBytes)} is free on this volume.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            firmware?.let { state -> ArtefactCard(state) }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { picker.launch(arrayOf("*/*")) },
                        enabled = !importing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Choose IPSW…")
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Skip stock checksums",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "Developer option. Accept an IPSW whose payload hashes " +
                                    "differ from the stock 7E18 firmware.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = skipChecksums,
                            onCheckedChange = { skipChecksums = it },
                            enabled = !importing,
                        )
                    }
                }
            }

            if (importing) {
                ImportProgressCard(
                    progress = progress,
                    cancelling = cancelling,
                    onCancel = {
                        cancelling = true
                        repository.cancelImport()
                    },
                )
            }

            val result = outcome
            if (result != null && result.ok) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Firmware ready",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        Text(
                            text = result.detail.ifEmpty {
                                "Kernel, device tree and root filesystem are unpacked."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Button(onClick = onDone) { Text("Back to machines") }
                    }
                }
            }

            if (failure != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(20.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Column {
                            Text(
                                text = if (cancelled) "Import cancelled" else "Import did not finish",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                text = failure.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            val shownReport = outcome?.detail?.takeIf { it.isNotBlank() } ?: report
            if (shownReport.isNotBlank() && !importing) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Import report", style = MaterialTheme.typography.titleMedium)
                        HorizontalDivider()
                        SelectionContainer {
                            Text(
                                text = shownReport,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(
                                    rememberScrollState(),
                                ),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ArtefactCard(state: FirmwareState) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("On disk", style = MaterialTheme.typography.titleMedium)
            ArtefactRow("Kernel", state.kernelPresent, state.kernelSize)
            ArtefactRow("Device tree", state.devicetreePresent, state.devicetreeSize)
            ArtefactRow("Root filesystem", state.rootfsPresent, state.rootfsSize)
        }
    }
}

@Composable
private fun ArtefactRow(title: String, present: Boolean, size: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (present) formatBytes(size) else "missing",
            style = MaterialTheme.typography.labelSmall,
            color = if (present) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun ImportProgressCard(
    progress: ImportProgress?,
    cancelling: Boolean,
    onCancel: () -> Unit,
) {
    val artefact = when (progress?.artefact) {
        0 -> "kernel"
        1 -> "device tree"
        2 -> "root filesystem"
        else -> ""
    }
    val stage = progress?.let { ImportStage.name(it.stage) } ?: "starting"
    val fraction = progress?.fraction ?: 0f

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Importing", style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (artefact.isEmpty()) stage else "$stage · $artefact",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (progress != null && progress.total > 0L) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "${formatBytes(progress.done)} of ${formatBytes(progress.total)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Keep the app in the foreground.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCancel, enabled = !cancelling) {
                    Text(if (cancelling) "Cancelling…" else "Cancel")
                }
            }
        }
    }
}
