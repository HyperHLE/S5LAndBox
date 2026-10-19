package com.tayga16.s5landbox.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import android.os.Environment
import java.io.File
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.tayga16.s5landbox.audio.AudioOutput
import com.tayga16.s5landbox.data.BindResult
import com.tayga16.s5landbox.data.Buttons
import com.tayga16.s5landbox.data.EngineRepository
import com.tayga16.s5landbox.data.EngineStatus
import com.tayga16.s5landbox.data.StopMode
import com.tayga16.s5landbox.data.TouchPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EmulatorScreen(
    repository: EngineRepository,
    machineId: String,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onSnapshots: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val audio = remember { AudioOutput(repository) }

    var machineName by remember { mutableStateOf("Machine") }
    var status by remember { mutableStateOf<EngineStatus?>(null) }
    var paused by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    var startError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var showConsole by remember { mutableStateOf(false) }
    var consoleText by remember { mutableStateOf("") }

    suspend fun startMachine(): BindResult = withContext(Dispatchers.IO) {
        val current = repository.status()
        if (!current.idle && current.instanceId == machineId && !current.stopping) {
            // Coming back to the screen of a machine that is still alive: it was
            // paused when this screen went away, so resuming is enough.
            repository.setPaused(false)
            BindResult(true, machineId, "")
        } else {
            if (!current.idle) {
                repository.stop(StopMode.PLAIN)
                var waited = 0
                while (!repository.status().idle && waited < 300) {
                    Thread.sleep(100)
                    waited++
                }
            }
            repository.start(machineId)
        }
    }

    LaunchedEffect(machineId) {
        machineName = withContext(Dispatchers.IO) {
            repository.machines().firstOrNull { it.id == machineId }?.name ?: "Machine"
        }
        val result = startMachine()
        if (result.ok) {
            started = true
            audio.start()
        } else {
            startError = result.detail.ifEmpty { "The machine could not start." }
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            val next = withContext(Dispatchers.IO) { repository.status() }
            status = next
            paused = next.paused
            delay(250)
        }
    }

    LaunchedEffect(showConsole) {
        if (!showConsole) return@LaunchedEffect
        while (isActive) {
            val chunk = withContext(Dispatchers.IO) { repository.consoleDrain() }
            if (chunk.isNotEmpty()) {
                consoleText = (consoleText + chunk).takeLast(24_000)
            }
            delay(300)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        repository.setPaused(true)
        paused = true
        audio.stop()
    }

    DisposableEffect(Unit) {
        onDispose {
            repository.setSurface(null)
            repository.setPaused(true)
            audio.stop()
        }
    }

    fun applyPaused(next: Boolean) {
        paused = next
        repository.setPaused(next)
        if (next) audio.stop() else audio.start()
    }

    fun stopMachine(mode: Int) {
        if (busy != null) return
        menuOpen = false
        scope.launch {
            busy = when (mode) {
                StopMode.CHECKPOINT -> "Saving state…"
                StopMode.POWER_OFF -> "Powering off…"
                else -> "Stopping…"
            }
            val idle = withContext(Dispatchers.IO) {
                repository.stop(mode)
                var waited = 0
                while (!repository.status().idle && waited < 600) {
                    Thread.sleep(100)
                    waited++
                }
                repository.status().idle
            }
            busy = null
            audio.stop()
            if (!idle) {
                snackbar.showSnackbar("The machine is still stopping. Try again in a moment.")
            } else {
                onBack()
            }
        }
    }

    fun takeSnapshot() {
        if (busy != null) return
        menuOpen = false
        scope.launch {
            busy = "Saving a checkpoint…"
            val result = withContext(Dispatchers.IO) {
                repository.stop(StopMode.CHECKPOINT)
                var waited = 0
                while (!repository.status().idle && waited < 600) {
                    Thread.sleep(100)
                    waited++
                }
                if (!repository.status().idle) {
                    BindResult(false, "", "The machine did not stop in time.")
                } else {
                    repository.takeSnapshot(machineId)
                }
            }
            if (result.ok) {
                busy = "Starting again…"
                val restarted = startMachine()
                if (restarted.ok) {
                    started = true
                    audio.start()
                } else {
                    started = false
                    startError = restarted.detail.ifEmpty { "The machine could not restart." }
                }
            }
            busy = null
            snackbar.showSnackbar(
                if (result.ok) {
                    "Snapshot saved."
                } else {
                    result.detail.ifEmpty { "The snapshot failed." }
                },
            )
        }
    }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            AppBar(
                title = machineName,
                subtitle = "iPhone OS 3.1.3 · n82ap",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { applyPaused(!paused) }) {
                        Icon(
                            if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (paused) "Resume" else "Pause",
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Console") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Terminal, contentDescription = null)
                                },
                                onClick = { menuOpen = false; showConsole = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Snapshots") },
                                leadingIcon = {
                                    Icon(Icons.Filled.History, contentDescription = null)
                                },
                                onClick = { menuOpen = false; onSnapshots() },
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Settings, contentDescription = null)
                                },
                                onClick = { menuOpen = false; onSettings() },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Take a snapshot") },
                                leadingIcon = {
                                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                                },
                                onClick = ::takeSnapshot,
                            )
                            DropdownMenuItem(
                                text = { Text("Save & stop") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Save, contentDescription = null)
                                },
                                onClick = { stopMachine(StopMode.CHECKPOINT) },
                            )
                            DropdownMenuItem(
                                text = { Text("Power off") },
                                leadingIcon = {
                                    Icon(Icons.Filled.PowerSettingsNew, contentDescription = null)
                                },
                                onClick = { stopMachine(StopMode.POWER_OFF) },
                            )
                            DropdownMenuItem(
                                text = { Text("Stop now") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Stop, contentDescription = null)
                                },
                                onClick = { stopMachine(StopMode.PLAIN) },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                BoxWithConstraints(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    val targetWidth = minOf(maxWidth, maxHeight * (2f / 3f))
                    Box(
                        Modifier
                            .width(targetWidth)
                            .height(targetWidth * 1.5f),
                    ) {
                        AndroidView(
                            factory = { context ->
                                SurfaceView(context).apply {
                                    holder.addCallback(object : SurfaceHolder.Callback {
                                        override fun surfaceCreated(holder: SurfaceHolder) {
                                            repository.setSurface(holder.surface)
                                        }

                                        override fun surfaceChanged(
                                            holder: SurfaceHolder,
                                            format: Int,
                                            width: Int,
                                            height: Int,
                                        ) {
                                        }

                                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                                            repository.setSurface(null)
                                        }
                                    })
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        var x = down.position.x
                                        var y = down.position.y
                                        val width = size.width.toDouble()
                                        val height = size.height.toDouble()
                                        var sent = false
                                        try {
                                            repository.touch(
                                                x.toDouble(),
                                                y.toDouble(),
                                                width,
                                                height,
                                                TouchPhase.BEGAN,
                                            )
                                            sent = true
                                            down.consume()
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes
                                                    .firstOrNull { it.id == down.id } ?: break
                                                x = change.position.x
                                                y = change.position.y
                                                if (!change.pressed) break
                                                repository.touch(
                                                    x.toDouble(),
                                                    y.toDouble(),
                                                    width,
                                                    height,
                                                    TouchPhase.MOVED,
                                                )
                                                change.consume()
                                            }
                                        } finally {
                                            if (sent) {
                                                repository.touch(
                                                    x.toDouble(),
                                                    y.toDouble(),
                                                    width,
                                                    height,
                                                    TouchPhase.ENDED,
                                                )
                                            }
                                        }
                                    }
                                },
                        )
                    }
                }

                val current = status
                if (current != null && current.preparing) {
                    PreparingCard(current, Modifier.align(Alignment.Center))
                } else if (current != null && !current.idle && current.fps <= 0.0 && current.retired in 100_000L..2_500_000_000L) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                            .widthIn(max = 300.dp),
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                            Text(
                                "Booting iOS…",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            val pct = (current.retired.toDouble() / 2_500_000_000.0 * 100.0).coerceIn(1.0, 99.0).toInt()
                            LinearProgressIndicator(
                                progress = { pct / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "First boot in progress ($pct%). Please wait.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (current != null && current.idle && started && busy == null) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "The machine is stopped.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        busy = "Starting…"
                                        val result = startMachine()
                                        busy = null
                                        if (result.ok) audio.start() else {
                                            started = false
                                            startError = result.detail
                                        }
                                    }
                                },
                            ) { Text("Start") }
                        }
                    }
                }

                val note = current?.note
                if (!note.isNullOrBlank()) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xCC060A10))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }

                if (busy != null) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0xAA000000))
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

            val current = status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when {
                        current == null -> "starting"
                        current.paused -> "${current.status} · paused"
                        else -> current.status.ifEmpty { "idle" }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (current != null && !current.idle) {
                    Text(
                        text = "${formatRate(current.rate)} · " +
                            "${"%.0f".format(java.util.Locale.US, current.fps)} fps · " +
                            formatRetired(current.retired),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            ButtonBar(repository = repository)
        }
    }

    if (showConsole) {
        AlertDialog(
            onDismissRequest = { showConsole = false },
            title = { Text("Guest console") },
            text = {
                val scroll = rememberScrollState()
                LaunchedEffect(consoleText) { scroll.scrollTo(scroll.maxValue) }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF04070B))
                        .padding(10.dp),
                ) {
                    SelectionContainer {
                        Text(
                            text = consoleText.ifEmpty { "(no output yet)" },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = Color(0xFF9BE6B8),
                            modifier = Modifier.verticalScroll(scroll),
                        )
                    }
                }
            },
            confirmButton = {
                val clipboard = LocalClipboardManager.current
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(consoleText))
                        scope.launch {
                            snackbar.showSnackbar("Console log copied to clipboard.")
                        }
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy")
                    }
                    TextButton(onClick = {
                        try {
                            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                            downloadDir.mkdirs()
                            val safeName = machineName.replace(" ", "_")
                            val logFile = File(downloadDir, "s5landbox-$safeName.log")
                            logFile.writeText(consoleText)
                            scope.launch {
                                snackbar.showSnackbar("Saved to Downloads/${logFile.name}")
                            }
                        } catch (e: Exception) {
                            scope.launch {
                                snackbar.showSnackbar("Save failed: ${e.message}")
                            }
                        }
                    }) {
                        Icon(Icons.Filled.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }
                    TextButton(onClick = { showConsole = false }) { Text("Close") }
                }
            },
        )
    }

    startError?.let { message ->
        AlertDialog(
            onDismissRequest = { startError = null },
            title = { Text("Could not start") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = onBack) { Text("Back to machines") }
            },
            dismissButton = {
                TextButton(onClick = { startError = null }) { Text("Stay") }
            },
        )
    }
}

@Composable
private fun PreparingCard(status: EngineStatus, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.widthIn(max = 320.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Preparing the writable root filesystem",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "This machine gets its own copy of the guest disk. It happens once.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (status.prepareTotal > 0L) {
                LinearProgressIndicator(
                    progress = {
                        (status.prepareDone.toDouble() / status.prepareTotal.toDouble())
                            .coerceIn(0.0, 1.0)
                            .toFloat()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "${formatBytes(status.prepareDone)} of " +
                        formatBytes(status.prepareTotal),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ButtonBar(repository: EngineRepository) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HoldButton(
                    icon = Icons.Filled.VolumeMute,
                    label = "Silent switch",
                    onPress = { repository.button(Buttons.RINGER_SILENT, true) },
                    onRelease = { repository.button(Buttons.RINGER_SILENT, false) },
                )
                HoldButton(
                    icon = Icons.Filled.VolumeDown,
                    label = "Volume down",
                    onPress = { repository.button(Buttons.VOLUME_DOWN, true) },
                    onRelease = { repository.button(Buttons.VOLUME_DOWN, false) },
                )
                HoldButton(
                    icon = Icons.Filled.VolumeUp,
                    label = "Volume up",
                    onPress = { repository.button(Buttons.VOLUME_UP, true) },
                    onRelease = { repository.button(Buttons.VOLUME_UP, false) },
                )
                HoldButton(
                    icon = Icons.Filled.Home,
                    label = "Home",
                    onPress = { repository.button(Buttons.HOME, true) },
                    onRelease = { repository.button(Buttons.HOME, false) },
                )
                HoldButton(
                    icon = Icons.Filled.PowerSettingsNew,
                    label = "Power",
                    onPress = { repository.button(Buttons.POWER, true) },
                    onRelease = { repository.button(Buttons.POWER, false) },
                )
            }
        }
    }
}

/**
 * A physical-looking button with hold semantics: the guest sees a press when
 * the finger goes down and a release when it comes up, which is what the
 * power-menu and volume-hold policies expect.
 */
@Composable
private fun HoldButton(
    icon: ImageVector,
    label: String,
    onPress: () -> Unit,
    onRelease: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    onPress()
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        onRelease()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}
