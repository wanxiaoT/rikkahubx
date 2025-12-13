
package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.CircleHelp
import com.composables.icons.lucide.HeartPulse
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Trash2
import com.dokar.sonner.ToastType
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ApiKeyConfig
import me.rerere.ai.provider.ApiKeyHealthService
import me.rerere.ai.provider.ApiKeyStatus
import me.rerere.ai.provider.BatchCheckProgress
import me.rerere.ai.provider.HealthCheckResult
import me.rerere.ai.provider.KeyManagementConfig
import me.rerere.ai.provider.LoadBalanceStrategy
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.updateWithResults
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingMultiKeyPage(
    providerId: Uuid,
    vm: SettingVM = koinViewModel()
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val providerManager = koinInject<ProviderManager>()
    
    val provider = settings.providers.find { it.id == providerId } ?: return
    val apiKeys = provider.apiKeys ?: emptyList()
    val keyManagement = provider.keyManagement ?: KeyManagementConfig()
    
    // 健康检测服务
    val healthService = remember { ApiKeyHealthService(providerManager) }
    
    // 统计信息
    val totalKeys = apiKeys.size
    val activeKeys = apiKeys.count { it.status == ApiKeyStatus.ACTIVE && it.isEnabled }
    val errorKeys = apiKeys.count { it.status == ApiKeyStatus.ERROR }
    
    // 编辑状态
    var showAddKeysSheet by remember { mutableStateOf(false) }
    var editingKey by remember { mutableStateOf<ApiKeyConfig?>(null) }
    var showStrategySheet by remember { mutableStateOf(false) }
    var showBatchTestSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    
    // 脱敏开关状态（默认开启脱敏）
    var maskApiKey by remember { mutableStateOf(true) }
    
    // 单个 Key 测试状态
    var testingKeyId by remember { mutableStateOf<String?>(null) }
    
    // 批量测试状态
    var batchTestProgress by remember { mutableStateOf<BatchCheckProgress?>(null) }
    var batchTestJob by remember { mutableStateOf<Job?>(null) }
    
    val onUpdateProvider = { newProvider: ProviderSetting ->
        val newSettings = settings.copy(
            providers = settings.providers.map {
                if (newProvider.id == it.id) newProvider else it
            }
        )
        vm.updateSettings(newSettings)
    }
    
    // 自动恢复检查
    LaunchedEffect(apiKeys, keyManagement) {
        if (keyManagement.enableAutoRecovery && apiKeys.isNotEmpty()) {
            val recoveredKeys = healthService.checkAndRecoverKeys(apiKeys, keyManagement)
            if (recoveredKeys != apiKeys) {
                onUpdateProvider(provider.copyProvider(apiKeys = recoveredKeys))
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.multi_key_page_title)) },
                navigationIcon = { BackButton() },
                actions = {
                    // 批量测试按钮
                    if (apiKeys.isNotEmpty()) {
                        IconButton(
                            onClick = { showBatchTestSheet = true }
                        ) {
                            Icon(Lucide.Activity, contentDescription = stringResource(R.string.multi_key_page_batch_test))
                        }
                    }
                    // 删除所有错误 Key
                    if (errorKeys > 0) {
                        IconButton(
                            onClick = {
                                val newKeys = apiKeys.filter { it.status != ApiKeyStatus.ERROR }
                                onUpdateProvider(provider.copyProvider(apiKeys = newKeys))
                                toaster.show(
                                    "Deleted $errorKeys error keys",
                                    type = ToastType.Success
                                )
                            }
                        ) {
                            Icon(Lucide.Trash2, contentDescription = stringResource(R.string.multi_key_page_delete_errors))
                        }
                    }
                    // 添加 Key
                    IconButton(onClick = { showAddKeysSheet = true }) {
                        Icon(Lucide.Plus, contentDescription = stringResource(R.string.multi_key_page_add))
                    }
                    // 设置按钮
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(Lucide.Settings, contentDescription = stringResource(R.string.multi_key_page_settings))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 统计卡片
            item {
                StatsCard(
                    totalKeys = totalKeys,
                    activeKeys = activeKeys,
                    errorKeys = errorKeys,
                    strategy = keyManagement.strategy,
                    onStrategyClick = { showStrategySheet = true }
                )
            }
            
            // 批量测试进度
            if (batchTestProgress != null) {
                item {
                    BatchTestProgressCard(
                        progress = batchTestProgress!!,
                        onCancel = {
                            batchTestJob?.cancel()
                            batchTestJob = null
                            batchTestProgress = null
                        }
                    )
                }
            }
            
            // 多 Key 开关
            item {
                MultiKeyEnableCard(
                    enabled = provider.multiKeyEnabled,
                    onEnabledChange = { enabled ->
                        onUpdateProvider(provider.setMultiKeyEnabled(enabled))
                    }
                )
            }
            
            // Key 列表
            if (apiKeys.isEmpty()) {
                item {
                    EmptyKeysCard(onAddClick = { showAddKeysSheet = true })
                }
            } else {
                items(apiKeys, key = { it.id }) { apiKey ->
                    ApiKeyCard(
                        apiKey = apiKey,
                        isTesting = testingKeyId == apiKey.id,
                        testResult = batchTestProgress?.results?.get(apiKey.id),
                        maskApiKey = maskApiKey,
                        onToggleEnabled = { enabled ->
                            val updatedKey = apiKey.copy(isEnabled = enabled)
                            onUpdateProvider(provider.updateApiKey(updatedKey))
                        },
                        onEdit = { editingKey = apiKey },
                        onDelete = {
                            onUpdateProvider(provider.removeApiKey(apiKey.id))
                            toaster.show(
                                "Key deleted",
                                type = ToastType.Info
                            )
                        },
                        onTest = {
                            // 单个 Key 测试
                            val testModel = provider.models.firstOrNull { it.type == ModelType.CHAT }
                            if (testModel == null) {
                                toaster.show("No chat model available for testing", type = ToastType.Warning)
                                return@ApiKeyCard
                            }
                            
                            testingKeyId = apiKey.id
                            scope.launch {
                                val result = healthService.testSingleKey(
                                    provider = provider,
                                    apiKey = apiKey,
                                    model = testModel,
                                    useStream = false
                                )
                                
                                // 更新 Key 状态
                                val updatedKey = healthService.updateKeyWithResult(apiKey, result, keyManagement)
                                onUpdateProvider(provider.updateApiKey(updatedKey))
                                
                                testingKeyId = null
                                
                                when (result) {
                                    is HealthCheckResult.Success -> {
                                        toaster.show("Test success (${result.responseTime}ms)", type = ToastType.Success)
                                    }
                                    is HealthCheckResult.Error -> {
                                        toaster.show("Test failed: ${result.errorMessage}", type = ToastType.Error)
                                    }
                                    is HealthCheckResult.RateLimited -> {
                                        toaster.show("Rate limited", type = ToastType.Warning)
                                    }
                                }
                            }
                        }
                    )
                }
            }
            
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
    
    // 添加 Keys 底部弹窗
    if (showAddKeysSheet) {
        AddKeysBottomSheet(
            onDismiss = { showAddKeysSheet = false },
            onAdd = { newKeys ->
                val existingKeySet = apiKeys.map { it.key.trim() }.toSet()
                val uniqueKeys = newKeys
                    .map { it.trim() }
                    .filter { it.isNotBlank() && it !in existingKeySet }
                    .map { ApiKeyConfig.create(it) }
                
                if (uniqueKeys.isNotEmpty()) {
                    var updatedProvider = provider
                    uniqueKeys.forEach { key ->
                        updatedProvider = updatedProvider.addApiKey(key) as ProviderSetting
                    }
                    // 自动启用多 Key
                    if (!provider.multiKeyEnabled) {
                        updatedProvider = updatedProvider.setMultiKeyEnabled(true)
                    }
                    onUpdateProvider(updatedProvider)
                    toaster.show(
                        "Added ${uniqueKeys.size} keys",
                        type = ToastType.Success
                    )
                } else {
                    toaster.show(
                        "No new keys to add",
                        type = ToastType.Warning
                    )
                }
                showAddKeysSheet = false
            }
        )
    }
    
    // 编辑 Key 底部弹窗
    editingKey?.let { key ->
        EditKeyBottomSheet(
            apiKey = key,
            onDismiss = { editingKey = null },
            onSave = { updatedKey ->
                // 检查是否有重复的 key
                val isDuplicate = apiKeys.any { it.id != key.id && it.key.trim() == updatedKey.key.trim() }
                if (isDuplicate) {
                    toaster.show("Duplicate key", type = ToastType.Warning)
                } else {
                    onUpdateProvider(provider.updateApiKey(updatedKey))
                    editingKey = null
                }
            }
        )
    }
    
    // 策略选择底部弹窗
    if (showStrategySheet) {
        StrategyBottomSheet(
            currentStrategy = keyManagement.strategy,
            keyManagement = keyManagement,
            onDismiss = { showStrategySheet = false },
            onSave = { newConfig ->
                onUpdateProvider(provider.updateKeyManagement(newConfig))
                showStrategySheet = false
            }
        )
    }
    
    // 批量测试底部弹窗
    if (showBatchTestSheet) {
        BatchTestBottomSheet(
            provider = provider,
            apiKeys = apiKeys,
            onDismiss = { showBatchTestSheet = false },
            onStartTest = { selectedModel, useStream ->
                showBatchTestSheet = false
                
                // 开始批量测试
                batchTestJob = scope.launch {
                    healthService.testBatchKeys(
                        provider = provider,
                        apiKeys = apiKeys,
                        model = selectedModel,
                        useStream = useStream
                    ).collect { progress ->
                        batchTestProgress = progress
                        
                        // 测试完成时更新所有 Key 状态
                        if (progress.completed == progress.total) {
                            val updatedKeys = apiKeys.updateWithResults(
                                results = progress.results,
                                keyManagement = keyManagement,
                                healthService = healthService
                            )
                            onUpdateProvider(provider.copyProvider(apiKeys = updatedKeys))
                            
                            val successCount = progress.results.values.count { it is HealthCheckResult.Success }
                            val failedCount = progress.results.values.count { it !is HealthCheckResult.Success }
                            
                            toaster.show(
                                "Test complete: $successCount success, $failedCount failed",
                                type = if (failedCount == 0) ToastType.Success else ToastType.Warning
                            )
                            
                            // 延迟清除进度
                            kotlinx.coroutines.delay(3000)
                            batchTestProgress = null
                            batchTestJob = null
                        }
                    }
                }
            }
        )
    }
    
    // 设置底部弹窗
    if (showSettingsSheet) {
        MultiKeySettingsBottomSheet(
            maskApiKey = maskApiKey,
            onMaskApiKeyChange = { maskApiKey = it },
            onDismiss = { showSettingsSheet = false }
        )
    }
}

@Composable
private fun StatsCard(
    totalKeys: Int,
    activeKeys: Int,
    errorKeys: Int,
    strategy: LoadBalanceStrategy,
    onStrategyClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    label = stringResource(R.string.multi_key_page_total),
                    value = totalKeys.toString()
                )
                StatItem(
                    label = stringResource(R.string.multi_key_page_active),
                    value = activeKeys.toString(),
                    valueColor = MaterialTheme.colorScheme.primary
                )
                StatItem(
                    label = stringResource(R.string.multi_key_page_error),
                    value = errorKeys.toString(),
                    valueColor = if (errorKeys > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
            
            // 策略选择
            Surface(
                onClick = onStrategyClick,
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.multi_key_page_strategy),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = getStrategyDisplayName(strategy),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            Lucide.ChevronDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchTestProgressCard(
    progress: BatchCheckProgress,
    onCancel: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (progress.completed < progress.total) {
                        stringResource(R.string.multi_key_page_batch_test_progress, progress.completed, progress.total)
                    } else {
                        val successCount = progress.results.values.count { it is HealthCheckResult.Success }
                        val failedCount = progress.results.values.count { it !is HealthCheckResult.Success }
                        stringResource(R.string.multi_key_page_batch_test_complete, successCount, failedCount)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                
                if (progress.completed < progress.total) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.multi_key_page_batch_test_cancel))
                    }
                }
            }
            
            LinearProgressIndicator(
                progress = { progress.completed.toFloat() / progress.total.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = valueColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MultiKeyEnableCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.multi_key_page_enable),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.multi_key_page_enable_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange
            )
        }
    }
}

@Composable
private fun EmptyKeysCard(onAddClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.multi_key_page_no_keys),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(onClick = onAddClick) {
                Icon(Lucide.Plus, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.multi_key_page_add))
            }
        }
    }
}

@Composable
private fun ApiKeyCard(
    apiKey: ApiKeyConfig,
    isTesting: Boolean,
    testResult: HealthCheckResult?,
    maskApiKey: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit
) {
    val statusColor = when (apiKey.status) {
        ApiKeyStatus.ACTIVE -> Color(0xFF4CAF50)
        ApiKeyStatus.DISABLED -> MaterialTheme.colorScheme.onSurfaceVariant
        ApiKeyStatus.ERROR -> MaterialTheme.colorScheme.error
        ApiKeyStatus.RATE_LIMITED -> MaterialTheme.colorScheme.tertiary
    }
    
    val statusText = when (apiKey.status) {
        ApiKeyStatus.ACTIVE -> stringResource(R.string.multi_key_page_status_active)
        ApiKeyStatus.DISABLED -> stringResource(R.string.multi_key_page_status_disabled)
        ApiKeyStatus.ERROR -> stringResource(R.string.multi_key_page_status_error)
        ApiKeyStatus.RATE_LIMITED -> stringResource(R.string.multi_key_page_status_rate_limited)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (apiKey.isEnabled) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 状态标签和名称
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Tag(
                            type = when (apiKey.status) {
                                ApiKeyStatus.ACTIVE -> TagType.SUCCESS
                                ApiKeyStatus.ERROR -> TagType.ERROR
                                ApiKeyStatus.RATE_LIMITED -> TagType.WARNING
                                ApiKeyStatus.DISABLED -> TagType.DEFAULT
                            }
                        ) {
                            Text(statusText, style = MaterialTheme.typography.labelSmall)
                        }
                        // 显示名称（如果有）
                        apiKey.name?.takeIf { it.isNotBlank() }?.let { name ->
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        // 根据脱敏开关显示 Key
                        Text(
                            text = if (maskApiKey) maskKey(apiKey.key) else apiKey.key,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    // 使用统计
                    if (apiKey.usage.totalRequests > 0) {
                        Text(
                            text = stringResource(
                                R.string.multi_key_page_usage_stats,
                                apiKey.usage.totalRequests,
                                apiKey.usage.successfulRequests
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // 开关
                Switch(
                    checked = apiKey.isEnabled,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                
                // 测试按钮
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = onTest, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Lucide.HeartPulse,
                            contentDescription = stringResource(R.string.multi_key_page_test),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                // 编辑按钮
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Lucide.Pencil,
                        contentDescription = stringResource(R.string.multi_key_page_edit),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                // 删除按钮
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Lucide.Trash2,
                        contentDescription = stringResource(R.string.multi_key_page_delete),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            // 显示额外信息
            AnimatedVisibility(visible = apiKey.usage.consecutiveFailures > 0 || apiKey.lastError != null || testResult is HealthCheckResult.Success) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 连续失败次数
                    if (apiKey.usage.consecutiveFailures > 0) {
                        Text(
                            text = stringResource(R.string.multi_key_page_consecutive_failures, apiKey.usage.consecutiveFailures),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    
                    // 最后错误
                    apiKey.lastError?.let { error ->
                        Text(
                            text = stringResource(R.string.multi_key_page_last_error, error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    // 响应时间（来自测试结果）
                    if (testResult is HealthCheckResult.Success) {
                        Text(
                            text = stringResource(R.string.multi_key_page_response_time, testResult.responseTime),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatchTestBottomSheet(
    provider: ProviderSetting,
    apiKeys: List<ApiKeyConfig>,
    onDismiss: () -> Unit,
    onStartTest: (Model, Boolean) -> Unit
) {
    var selectedModel by remember {
        mutableStateOf(provider.models.firstOrNull { it.type == ModelType.CHAT })
    }
    var useStream by remember { mutableStateOf(false) }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.multi_key_page_batch_test_all),
                style = MaterialTheme.typography.headlineSmall
            )
            
            Text(
                text = "Will test ${apiKeys.size} keys",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // 模型选择
            Text(
                text = stringResource(R.string.multi_key_page_batch_test_select_model),
                style = MaterialTheme.typography.titleMedium
            )
            
            ModelSelector(
                modelId = selectedModel?.id,
                providers = listOf(provider),
                type = ModelType.CHAT,
                modifier = Modifier.fillMaxWidth()
            ) {
                selectedModel = it
            }
            
            // 流式模式开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.multi_key_page_batch_test_use_stream),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.multi_key_page_batch_test_use_stream_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = useStream,
                    onCheckedChange = { useStream = it }
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        selectedModel?.let { model ->
                            onStartTest(model, useStream)
                        }
                    },
                    enabled = selectedModel != null
                ) {
                    Icon(Lucide.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.multi_key_page_batch_test_start))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddKeysBottomSheet(
    onDismiss: () -> Unit,
    onAdd: (List<String>) -> Unit
) {
    var keysText by remember { mutableStateOf("") }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.multi_key_page_add_keys_title),
                style = MaterialTheme.typography.headlineSmall
            )
            
            Text(
                text = stringResource(R.string.multi_key_page_add_keys_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            OutlinedTextField(
                value = keysText,
                onValueChange = { keysText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                label = { Text(stringResource(R.string.multi_key_page_api_keys)) },
                placeholder = { Text("sk-xxx\nsk-yyy\nsk-zzz") }
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val keys = keysText.split(Regex("[\\s,]+"))
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                        onAdd(keys)
                    },
                    enabled = keysText.isNotBlank()
                ) {
                    Text(stringResource(R.string.multi_key_page_add))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditKeyBottomSheet(
    apiKey: ApiKeyConfig,
    onDismiss: () -> Unit,
    onSave: (ApiKeyConfig) -> Unit
) {
    var name by remember { mutableStateOf(apiKey.name ?: "") }
    var key by remember { mutableStateOf(apiKey.key) }
    var priority by remember { mutableStateOf(apiKey.priority.toFloat()) }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.multi_key_page_edit_key),
                style = MaterialTheme.typography.headlineSmall
            )
            
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.multi_key_page_key_name)) },
                placeholder = { Text(stringResource(R.string.multi_key_page_key_name_placeholder)) }
            )
            
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.multi_key_page_api_key)) }
            )
            
            // 优先级滑块
            Column {
                Text(
                    text = stringResource(R.string.multi_key_page_priority, priority.toInt()),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.multi_key_page_priority_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = priority,
                    onValueChange = { priority = it },
                    valueRange = 1f..10f,
                    steps = 8
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSave(
                            apiKey.copy(
                                name = name.takeIf { it.isNotBlank() },
                                key = key,
                                priority = priority.toInt(),
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    },
                    enabled = key.isNotBlank()
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StrategyBottomSheet(
    currentStrategy: LoadBalanceStrategy,
    keyManagement: KeyManagementConfig,
    onDismiss: () -> Unit,
    onSave: (KeyManagementConfig) -> Unit
) {
    var strategy by remember { mutableStateOf(currentStrategy) }
    var maxFailures by remember { mutableStateOf(keyManagement.maxFailuresBeforeDisable) }
    var recoveryTime by remember { mutableStateOf(keyManagement.failureRecoveryTimeMinutes) }
    var autoRecovery by remember { mutableStateOf(keyManagement.enableAutoRecovery) }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.multi_key_page_strategy_settings),
                style = MaterialTheme.typography.headlineSmall
            )
            
            // 策略选择
            Text(
                text = stringResource(R.string.multi_key_page_strategy),
                style = MaterialTheme.typography.titleMedium
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LoadBalanceStrategy.entries.forEach { s ->
                    Surface(
                        onClick = { strategy = s },
                        shape = MaterialTheme.shapes.medium,
                        color = if (strategy == s) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = getStrategyDisplayName(s),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = getStrategyDescription(s),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            // 高级设置
            Text(
                text = stringResource(R.string.multi_key_page_advanced_settings),
                style = MaterialTheme.typography.titleMedium
            )
            
            // 最大失败次数
            OutlinedTextField(
                value = maxFailures.toString(),
                onValueChange = { maxFailures = it.toIntOrNull() ?: 3 },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.multi_key_page_max_failures)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            
            // 恢复时间
            OutlinedTextField(
                value = recoveryTime.toString(),
                onValueChange = { recoveryTime = it.toIntOrNull() ?: 5 },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.multi_key_page_recovery_time)) },
                suffix = { Text(stringResource(R.string.multi_key_page_minutes)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            
            // 自动恢复开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.multi_key_page_auto_recovery),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = autoRecovery,
                    onCheckedChange = { autoRecovery = it }
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSave(
                            keyManagement.copyWith(
                                strategy = strategy,
                                maxFailuresBeforeDisable = maxFailures,
                                failureRecoveryTimeMinutes = recoveryTime,
                                enableAutoRecovery = autoRecovery
                            )
                        )
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun getStrategyDisplayName(strategy: LoadBalanceStrategy): String {
    return when (strategy) {
        LoadBalanceStrategy.ROUND_ROBIN -> stringResource(R.string.multi_key_page_strategy_round_robin)
        LoadBalanceStrategy.PRIORITY -> stringResource(R.string.multi_key_page_strategy_priority)
        LoadBalanceStrategy.LEAST_USED -> stringResource(R.string.multi_key_page_strategy_least_used)
        LoadBalanceStrategy.RANDOM -> stringResource(R.string.multi_key_page_strategy_random)
    }
}

@Composable
private fun getStrategyDescription(strategy: LoadBalanceStrategy): String {
    return when (strategy) {
        LoadBalanceStrategy.ROUND_ROBIN -> stringResource(R.string.multi_key_page_strategy_round_robin_desc)
        LoadBalanceStrategy.PRIORITY -> stringResource(R.string.multi_key_page_strategy_priority_desc)
        LoadBalanceStrategy.LEAST_USED -> stringResource(R.string.multi_key_page_strategy_least_used_desc)
        LoadBalanceStrategy.RANDOM -> stringResource(R.string.multi_key_page_strategy_random_desc)
    }
}

/**
 * 脱敏 Key 显示
 */
private fun maskKey(key: String): String {
    return if (key.length <= 8) {
        key
    } else {
        "${key.take(4)}••••${key.takeLast(4)}"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MultiKeySettingsBottomSheet(
    maskApiKey: Boolean,
    onMaskApiKeyChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var showHelpDialog by remember { mutableStateOf(false) }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.multi_key_page_settings),
                style = MaterialTheme.typography.headlineSmall
            )
            
            // API Key 脱敏开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.multi_key_page_mask_api_key),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.multi_key_page_mask_api_key_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = maskApiKey,
                    onCheckedChange = onMaskApiKeyChange
                )
            }
            
            // "这是什么？"帮助按钮
            Surface(
                onClick = { showHelpDialog = true },
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Lucide.CircleHelp,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.multi_key_page_what_is_this),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    
    // 帮助对话框
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text(stringResource(R.string.multi_key_page_mask_api_key)) },
            text = {
                Text(
                    text = stringResource(R.string.multi_key_page_mask_explanation),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text(stringResource(R.string.confirm))
                }
            }
        )
    }
}