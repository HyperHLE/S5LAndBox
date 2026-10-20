package com.tayga16.s5landbox.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tayga16.s5landbox.data.EngineRepository
import com.tayga16.s5landbox.data.LogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun LogViewerDialog(
    repository: EngineRepository,
    machineName: String,
    onDismiss: () -> Unit,
    onShowSnackbar: (String) -> Unit = {},
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val webPort = remember { repository.getWebLogPort() }
    val wifiIp = remember {
        try {
            var found = "127.0.0.1"
            val ifaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (ifaces.hasMoreElements()) {
                val iface = ifaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val a = addrs.nextElement()
                    if (!a.isLoopbackAddress && a is java.net.Inet4Address) {
                        found = a.hostAddress ?: found
                        break
                    }
                }
            }
            found
        } catch (_: Exception) { "127.0.0.1" }
    }
    val webUrl = "http://$wifiIp:$webPort"

    val logs = remember { mutableStateListOf<LogEntry>() }
    var lastId by remember { mutableLongStateOf(0L) }
    var activeFilter by remember { mutableStateOf("all") }
    var searchQuery by remember { mutableStateOf("") }
    var autoScroll by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    /* Live poll for new log entries */
    LaunchedEffect(Unit) {
        while (isActive) {
            val newEntries = withContext(Dispatchers.IO) { repository.getLogs(lastId) }
            if (newEntries.isNotEmpty()) {
                lastId = newEntries.last().id
                logs.addAll(newEntries)
                if (logs.size > 2000) {
                    val dropCount = logs.size - 2000
                    repeat(dropCount) { logs.removeAt(0) }
                }
                if (autoScroll && logs.isNotEmpty()) {
                    listState.animateScrollToItem(logs.size - 1)
                }
            }
            delay(300)
        }
    }

    val filteredLogs = remember(logs.size, activeFilter, searchQuery) {
        logs.filter { entry ->
            val matchCategory = activeFilter == "all" || entry.category.equals(activeFilter, ignoreCase = true)
            val matchSearch = searchQuery.isEmpty() || entry.message.contains(searchQuery, ignoreCase = true)
            matchCategory && matchSearch
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Logs & Inspector", fontWeight = FontWeight.Bold)
                    Text(
                        text = "${filteredLogs.size} lines",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }

                if (webPort > 0) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                clipboard.setText(AnnotatedString(webUrl))
                                onShowSnackbar("Web URL copied: $webUrl")
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Wi-Fi: $webUrl",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "Copy",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                /* Search bar */
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter text, symbol, address...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                )

                Spacer(Modifier.height(8.dp))

                /* Category filter chips */
                val categories = listOf("all", "kernel", "gles", "dyld", "crash", "info")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (cat in categories) {
                        val count = if (cat == "all") logs.size else logs.count { it.category.equals(cat, ignoreCase = true) }
                        FilterChip(
                            selected = activeFilter == cat,
                            onClick = { activeFilter = cat },
                            label = { Text("${cat.uppercase()} ($count)", fontSize = 11.sp) },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                /* Log container */
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF090D13))
                        .padding(8.dp),
                ) {
                    if (filteredLogs.isEmpty()) {
                        Text(
                            text = if (logs.isEmpty()) "(waiting for logs...)" else "(no matching logs)",
                            color = Color(0xFF6B7280),
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else {
                        SelectionContainer {
                            LazyColumn(state = listState) {
                                items(filteredLogs, key = { it.id }) { item ->
                                    val catColor = when (item.category.lowercase()) {
                                        "kernel" -> Color(0xFF38BDF8)
                                        "gles" -> Color(0xFF4ADE80)
                                        "dyld" -> Color(0xFFC084FC)
                                        "crash" -> Color(0xFFEF4444)
                                        else -> Color(0xFFF59E0B)
                                    }
                                    val isCrash = item.category.equals("crash", ignoreCase = true)

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (isCrash) Color(0x33EF4444) else Color.Transparent)
                                            .padding(vertical = 1.dp),
                                    ) {
                                        Text(
                                            text = item.time,
                                            color = Color(0xFF4B5563),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            modifier = Modifier.width(68.dp),
                                        )
                                        Text(
                                            text = "[${item.category.uppercase()}]",
                                            color = catColor,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            modifier = Modifier.width(58.dp),
                                        )
                                        Text(
                                            text = item.message,
                                            color = if (isCrash) Color(0xFFFCA5A5) else Color(0xFFD1D5DB),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = autoScroll,
                        onCheckedChange = { autoScroll = it },
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Auto", style = MaterialTheme.typography.labelSmall)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        val text = filteredLogs.joinToString("\n") { "[${it.time}] [${it.category}] ${it.message}" }
                        clipboard.setText(AnnotatedString(text))
                        onShowSnackbar("Copied ${filteredLogs.size} lines")
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
                            val logFile = File(downloadDir, "s5landbox-$safeName-log.txt")
                            val text = logs.joinToString("\n") { "[${it.time}] [${it.category}] ${it.message}" }
                            logFile.writeText(text)
                            onShowSnackbar("Saved to Downloads/${logFile.name}")
                        } catch (e: Exception) {
                            onShowSnackbar("Save failed: ${e.message}")
                        }
                    }) {
                        Icon(Icons.Filled.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }

                    if (webPort > 0) {
                        TextButton(onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
                            } catch (_: Exception) {}
                        }) {
                            Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Web")
                        }
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        },
    )
}
