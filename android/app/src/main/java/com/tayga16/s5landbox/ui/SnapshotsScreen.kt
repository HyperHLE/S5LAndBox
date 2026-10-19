package com.tayga16.s5landbox.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tayga16.s5landbox.data.BindResult
import com.tayga16.s5landbox.data.EngineRepository
import com.tayga16.s5landbox.data.EngineStatus
import com.tayga16.s5landbox.data.SnapshotInfo
import com.tayga16.s5landbox.data.StopMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SnapshotsScreen(
    repository: EngineRepository,
    machineId: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var machineName by remember { mutableStateOf("Machine") }
    var snapshots by remember { mutableStateOf<List<SnapshotInfo>>(emptyList()) }
    var status by remember { mutableStateOf<EngineStatus?>(null) }
    var snapshotError by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf<SnapshotInfo?>(null) }
    var confirmDelete by remember { mutableStateOf<SnapshotInfo?>(null) }

    suspend fun load() {
        val data = withContext(Dispatchers.IO) {
            Triple(
                repository.machines().firstOrNull { it.id == machineId }?.name ?: "Machine",
                repository.snapshots(machineId),
                repository.status(),
            )
        }
        machineName = data.first
        snapshots = data.second
        status = data.third
        snapshotError = withContext(Dispatchers.IO) { repository.snapshotsError(machineId) }
        loaded = true
    }

    LaunchedEffect(machineId) { load() }

    val running = status?.let { !it.idle } ?: false

    fun stopAndWait(mode: Int): Boolean {
        repository.stop(mode)
        var waited = 0
        while (!repository.status().idle && waited < 600) {
            Thread.sleep(100)
            waited++
        }
        return repository.status().idle
    }

    fun takeSnapshot() {
        if (busy != null) return
        scope.launch {
            busy = "Stopping with a checkpoint…"
            val result = withContext(Dispatchers.IO) {
                if (!stopAndWait(StopMode.CHECKPOINT)) {
                    BindResult(false, "", "The machine did not stop in time.")
                } else {
                    repository.takeSnapshot(machineId)
                }
            }
            busy = null
            snackbar.showSnackbar(
                if (result.ok) {
                    "Snapshot saved."
                } else {
                    result.detail.ifEmpty { "The snapshot could not be taken." }
                },
            )
            load()
        }
    }

    fun restore(snapshot: SnapshotInfo) {
        if (busy != null) return
        scope.launch {
            busy = "Restoring the snapshot…"
            val result = withContext(Dispatchers.IO) {
                if (!stopAndWait(StopMode.CHECKPOINT)) {
                    BindResult(false, "", "The machine did not stop in time.")
                } else {
                    repository.restoreSnapshot(machineId, snapshot.id)
                }
            }
            busy = null
            snackbar.showSnackbar(
                if (result.ok) {
                    "Snapshot restored."
                } else {
                    result.detail.ifEmpty { "The snapshot could not be restored." }
                },
            )
            load()
        }
    }

    fun delete(snapshot: SnapshotInfo) {
        if (busy != null) return
        scope.launch {
            busy = "Deleting the snapshot…"
            val result = withContext(Dispatchers.IO) {
                repository.deleteSnapshot(machineId, snapshot.id)
            }
            busy = null
            snackbar.showSnackbar(
                if (result.ok) {
                    "Snapshot deleted."
                } else {
                    result.detail.ifEmpty { "The snapshot could not be deleted." }
                },
            )
            load()
        }
    }

    Scaffold(
        topBar = {
            AppBar(
                title = "Snapshots",
                subtitle = machineName,
                onBack = onBack,
                actions = {
                    IconButton(onClick = { takeSnapshot() }, enabled = busy == null) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = "Take a snapshot")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!loaded) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 32.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "intro") {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    "A snapshot is the machine's memory and disk at one " +
                                        "moment. Taking one stops the machine with a " +
                                        "checkpoint first.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "Restoring an older snapshot discards every newer one, " +
                                        "because their disk differences no longer apply.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (running) {
                                    Text(
                                        "The machine is running; taking or restoring a " +
                                            "snapshot stops it first.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                                Button(
                                    onClick = { takeSnapshot() },
                                    enabled = busy == null,
                                ) {
                                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Take a snapshot")
                                }
                            }
                        }
                    }

                    if (snapshotError.isNotBlank()) {
                        item(key = "error") {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = snapshotError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                    }

                    if (snapshots.isEmpty()) {
                        item(key = "empty") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Filled.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "No snapshots yet",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    "Boot the machine, use it, then come back here.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        item(key = "header") {
                            Text(
                                text = "NEWEST FIRST",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                            )
                        }
                        items(snapshots, key = { it.id }) { snapshot ->
                            SnapshotCard(
                                snapshot = snapshot,
                                restoreEnabled = busy == null,
                                deleteEnabled = busy == null && !running,
                                onRestore = { confirmRestore = snapshot },
                                onDelete = { confirmDelete = snapshot },
                            )
                        }
                    }
                }
            }

            if (busy != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color(0xAA000000))
                        .clickable(enabled = true, onClick = {}),
                    contentAlignment = Alignment.Center,
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(22.dp))
                            Spacer(Modifier.width(14.dp))
                            Text(busy.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    confirmRestore?.let { snapshot ->
        val newer = snapshots.indexOfFirst { it.id == snapshot.id }
        val removesNewer = newer > 0
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Restore this snapshot?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("The machine returns to ${formatDate(snapshot.createdUnix)}.")
                    if (removesNewer) {
                        Text(
                            text = "The $newer newer snapshot" +
                                (if (newer == 1) "" else "s") +
                                " will be deleted, and every change made after this " +
                                "moment is discarded.",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (running) {
                        Text("The machine is stopped with a checkpoint first.")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = null
                        restore(snapshot)
                    },
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = null }) { Text("Cancel") }
            },
        )
    }

    confirmDelete?.let { snapshot ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this snapshot?") },
            text = { Text("Only this saved point is removed; the machine itself stays.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = null
                        delete(snapshot)
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SnapshotCard(
    snapshot: SnapshotInfo,
    restoreEnabled: Boolean,
    deleteEnabled: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = formatDate(snapshot.createdUnix),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "${formatRetired(snapshot.retired)} instructions · " +
                            "${formatBytes(snapshot.directoryBytes)} on disk",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = snapshot.id,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!snapshot.complete) {
                    Text(
                        "incomplete",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onRestore, enabled = restoreEnabled && snapshot.complete) {
                    Icon(Icons.Filled.Restore, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Restore")
                }
                IconButton(
                    onClick = onDelete,
                    enabled = deleteEnabled && snapshot.complete,
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete snapshot")
                }
            }
        }
    }
}
