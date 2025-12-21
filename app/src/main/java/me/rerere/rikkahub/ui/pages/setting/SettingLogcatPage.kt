package me.rerere.rikkahub.ui.pages.setting

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowDown
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Eraser
import com.composables.icons.lucide.Filter
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Save
import com.composables.icons.lucide.Trash2
import com.dokar.sonner.ToastType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalToaster
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter

data class LogEntry(
    val level: String,
    val tag: String,
    val message: String,
    val raw: String
)

enum class LogLevel(val label: String, val color: Color) {
    VERBOSE("V", Color.Gray),
    DEBUG("D", Color.Blue),
    INFO("I", Color.Green),
    WARN("W", Color(0xFFFFA500)), // Orange
    ERROR("E", Color.Red),
    ASSERT("A", Color.Magenta)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingLogcatPage() {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var isLoading by remember { mutableStateOf(true) }
    val logEntries = remember { mutableStateListOf<LogEntry>() }
    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    var showFilterMenu by remember { mutableStateOf(false) }

    // 实时保存相关状态
    var isStreamSaving by remember { mutableStateOf(false) }
    var saveUri by remember { mutableStateOf<Uri?>(null) }
    var streamJob by remember { mutableStateOf<Job?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var customSavePath by remember { mutableStateOf("/storage/emulated/0/1RikkaHubX/Logcat.txt") }
    var logFileExists by remember { mutableStateOf(false) }

    // 检查日志文件是否存在
    fun checkLogFileExists() {
        logFileExists = File(customSavePath).exists()
    }

    // 删除日志文件
    fun deleteLogFile() {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val file = File(customSavePath)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            checkLogFileExists()
            toaster.show(context.getString(R.string.setting_logcat_file_deleted), type = ToastType.Success)
        }
    }

    // 开始实时保存到自定义路径
    fun startStreamSave() {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val file = File(customSavePath)
                    val dir = file.parentFile
                    if (dir != null && !dir.exists()) {
                        dir.mkdirs()
                    }
                    if (!file.exists()) {
                        file.createNewFile()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            isStreamSaving = true
            checkLogFileExists()
            toaster.show(context.getString(R.string.setting_logcat_stream_started), type = ToastType.Success)
        }
    }

    // 复制单行日志
    fun copySingleLog(entry: LogEntry) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Logcat Line", entry.raw)
        clipboard.setPrimaryClip(clip)
        toaster.show(context.getString(R.string.setting_logcat_line_copied), type = ToastType.Success)
    }

    // 停止实时保存
    fun stopStreamSave() {
        streamJob?.cancel()
        streamJob = null
        isStreamSaving = false
        saveUri = null
        checkLogFileExists()
        toaster.show(context.getString(R.string.setting_logcat_stream_stopped), type = ToastType.Success)
    }

    // 实时保存日志流
    LaunchedEffect(isStreamSaving) {
        if (isStreamSaving) {
            streamJob = launch(Dispatchers.IO) {
                try {
                    val process = Runtime.getRuntime().exec("logcat")
                    val reader = BufferedReader(InputStreamReader(process.inputStream))

                    val writer = OutputStreamWriter(FileOutputStream(customSavePath, true))

                    writer.use { w ->
                        while (isActive) {
                            val line = reader.readLine() ?: break
                            w.write(line + "\n")
                            w.flush()

                            // 同时更新UI
                            val entry = parseLogLine(line)
                            if (entry != null) {
                                withContext(Dispatchers.Main) {
                                    logEntries.add(entry)
                                    // 保持最近500条
                                    if (logEntries.size > 500) {
                                        logEntries.removeAt(0)
                                    }
                                }
                            }
                        }
                    }

                    process.destroy()
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        toaster.show(context.getString(R.string.setting_logcat_stream_error, e.message ?: "Unknown error"), type = ToastType.Error)
                        isStreamSaving = false
                    }
                }
            }
        }
    }

    // 清理
    DisposableEffect(Unit) {
        onDispose {
            streamJob?.cancel()
        }
    }

    // 加载日志
    fun loadLogs() {
        scope.launch {
            isLoading = true
            logEntries.clear()
            val logs = withContext(Dispatchers.IO) {
                readLogcat()
            }
            logEntries.addAll(logs)
            isLoading = false
            // 滚动到底部
            if (logEntries.isNotEmpty()) {
                listState.animateScrollToItem(logEntries.size - 1)
            }
        }
    }

    // 清除日志
    fun clearLogs() {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    Runtime.getRuntime().exec("logcat -c")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            logEntries.clear()
            toaster.show(context.getString(R.string.setting_logcat_cleared), type = ToastType.Success)
        }
    }

    // 复制日志
    fun copyLogs() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val filteredLogs = if (selectedLevel != null) {
            logEntries.filter { it.level == selectedLevel?.label }
        } else {
            logEntries
        }
        val text = filteredLogs.joinToString("\n") { it.raw }
        val clip = ClipData.newPlainText("Logcat", text)
        clipboard.setPrimaryClip(clip)
        toaster.show(context.getString(R.string.setting_logcat_copied), type = ToastType.Success)
    }

    LaunchedEffect(Unit) {
        loadLogs()
        checkLogFileExists()
    }

    // 当路径改变时检查文件是否存在
    LaunchedEffect(customSavePath) {
        checkLogFileExists()
    }

    // 保存对话框
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(stringResource(R.string.setting_logcat_save_title)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 保存开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.setting_logcat_enable_save))
                        Switch(
                            checked = isStreamSaving,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    startStreamSave()
                                } else {
                                    stopStreamSave()
                                }
                            }
                        )
                    }

                    HorizontalDivider()

                    // 保存路径
                    OutlinedTextField(
                        value = customSavePath,
                        onValueChange = { customSavePath = it },
                        label = { Text(stringResource(R.string.setting_logcat_save_path)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isStreamSaving
                    )

                    // 文件状态提示
                    if (logFileExists) {
                        Text(
                            text = stringResource(R.string.setting_logcat_file_exists),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // 删除日志文件按钮
                    if (logFileExists) {
                        HorizontalDivider()

                        FilledTonalButton(
                            onClick = { deleteLogFile() },
                            enabled = !isStreamSaving,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Lucide.Trash2,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text(stringResource(R.string.setting_logcat_delete_file))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(stringResource(R.string.setting_logcat_close))
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.setting_page_logcat))
                },
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    // 过滤按钮
                    Box {
                        IconButton(
                            onClick = { showFilterMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Lucide.Filter, contentDescription = stringResource(R.string.setting_logcat_filter), modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.setting_logcat_all)) },
                                onClick = {
                                    selectedLevel = null
                                    showFilterMenu = false
                                }
                            )
                            LogLevel.entries.forEach { level ->
                                DropdownMenuItem(
                                    text = { 
                                        Text(
                                            text = level.name,
                                            color = level.color
                                        )
                                    },
                                    onClick = {
                                        selectedLevel = level
                                        showFilterMenu = false
                                    }
                                )
                            }
                        }
                    }
                    
                    // 实时保存按钮
                    IconButton(
                        onClick = {
                            if (isStreamSaving) {
                                stopStreamSave()
                            } else {
                                showSaveDialog = true
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Lucide.Save,
                            contentDescription = stringResource(
                                if (isStreamSaving) R.string.setting_logcat_stop_save
                                else R.string.setting_logcat_start_save
                            ),
                            tint = if (isStreamSaving) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 刷新按钮
                    IconButton(
                        onClick = { loadLogs() },
                        enabled = !isStreamSaving,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Lucide.RefreshCw, contentDescription = stringResource(R.string.setting_logcat_refresh), modifier = Modifier.size(20.dp))
                    }

                    // 复制按钮
                    IconButton(
                        onClick = { copyLogs() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Lucide.Copy, contentDescription = stringResource(R.string.setting_logcat_copy), modifier = Modifier.size(20.dp))
                    }

                    // 清除按钮
                    IconButton(
                        onClick = { clearLogs() },
                        enabled = !isStreamSaving,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Lucide.Eraser, contentDescription = stringResource(R.string.setting_logcat_clear), modifier = Modifier.size(20.dp))
                    }

                    // 滚动到底部按钮
                    IconButton(
                        onClick = {
                            scope.launch {
                                if (logEntries.isNotEmpty()) {
                                    listState.animateScrollToItem(logEntries.size - 1)
                                }
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Lucide.ArrowDown, contentDescription = stringResource(R.string.setting_logcat_scroll_bottom), modifier = Modifier.size(20.dp))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 过滤器标签
            if (selectedLevel != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = true,
                        onClick = { selectedLevel = null },
                        label = { 
                            Text(
                                text = "${stringResource(R.string.setting_logcat_filter)}: ${selectedLevel?.name}",
                                color = selectedLevel?.color ?: Color.Unspecified
                            )
                        }
                    )
                }
            }
            
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val filteredLogs = if (selectedLevel != null) {
                    logEntries.filter { it.level == selectedLevel?.label }
                } else {
                    logEntries
                }
                
                if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.setting_logcat_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(filteredLogs) { entry ->
                            LogEntryItem(
                                entry = entry,
                                onLongClick = { copySingleLog(entry) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogEntryItem(
    entry: LogEntry,
    onLongClick: () -> Unit
) {
    val levelColor = when (entry.level) {
        "V" -> LogLevel.VERBOSE.color
        "D" -> LogLevel.DEBUG.color
        "I" -> LogLevel.INFO.color
        "W" -> LogLevel.WARN.color
        "E" -> LogLevel.ERROR.color
        "A" -> LogLevel.ASSERT.color
        else -> Color.Gray
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onLongClick
            )
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "[${entry.level}]",
            color = levelColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
        Text(
            text = entry.tag,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
        Text(
            text = entry.message,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

private fun readLogcat(): List<LogEntry> {
    val logs = mutableListOf<LogEntry>()
    try {
        // 读取最近的日志，限制行数避免内存问题
        val process = Runtime.getRuntime().exec("logcat -d -t 500")
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        
        reader.useLines { lines ->
            lines.forEach { line ->
                val entry = parseLogLine(line)
                if (entry != null) {
                    logs.add(entry)
                }
            }
        }
        
        process.waitFor()
    } catch (e: Exception) {
        e.printStackTrace()
        logs.add(LogEntry("E", "Logcat", "Failed to read logcat: ${e.message}", e.toString()))
    }
    return logs
}

private fun parseLogLine(line: String): LogEntry? {
    // 日志格式通常是: 日期 时间 PID TID 级别 TAG: 消息
    // 例如: 12-18 15:30:45.123  1234  5678 D MyTag: This is a message
    val regex = Regex("""^\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d+\s+\d+\s+\d+\s+([VDIWEA])\s+([^:]+):\s*(.*)$""")
    val match = regex.find(line)
    
    return if (match != null) {
        val (level, tag, message) = match.destructured
        LogEntry(level, tag.trim(), message, line)
    } else if (line.isNotBlank()) {
        // 如果无法解析，作为普通消息处理
        LogEntry("I", "Unknown", line, line)
    } else {
        null
    }
}
