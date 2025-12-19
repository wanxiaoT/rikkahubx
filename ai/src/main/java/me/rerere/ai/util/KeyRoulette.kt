package me.rerere.ai.util

import me.rerere.ai.provider.ApiKeyConfig
import me.rerere.ai.provider.ApiKeyStatus
import me.rerere.ai.provider.KeyManagementConfig
import me.rerere.ai.provider.KeySelectionResult
import me.rerere.ai.provider.LoadBalanceStrategy
import kotlin.random.Random

interface KeyRoulette {
    /**
     * 从逗号/空格分隔的 keys 字符串中随机选择一个 key（兼容旧版本）
     */
    fun next(keys: String): String
    
    /**
     * 根据负载均衡策略从 API Key 列表中选择一个 key
     */
    fun selectKey(
        apiKeys: List<ApiKeyConfig>,
        keyManagement: KeyManagementConfig?
    ): KeySelectionResult
    
    /**
     * 更新 Key 状态（成功/失败后调用）
     */
    fun updateKeyStatus(
        key: ApiKeyConfig,
        success: Boolean,
        keyManagement: KeyManagementConfig?,
        error: String? = null
    ): ApiKeyConfig

    companion object {
        fun default(): KeyRoulette = DefaultKeyRoulette()
    }
}

private val SPLIT_KEY_REGEX = "[\\s,]+".toRegex() // 空格换行和逗号

private fun splitKey(key: String): List<String> {
    return key
        .split(SPLIT_KEY_REGEX)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

private class DefaultKeyRoulette : KeyRoulette {
    
    // 每个 Provider 的轮询索引（内存中维护）
    private val roundRobinIndexMap = mutableMapOf<String, Int>()
    
    override fun next(keys: String): String {
        val keyList = splitKey(keys)
        return if (keyList.isNotEmpty()) {
            keyList.random()
        } else {
            keys
        }
    }
    
    override fun selectKey(
        apiKeys: List<ApiKeyConfig>,
        keyManagement: KeyManagementConfig?
    ): KeySelectionResult {
        // 过滤出启用的 keys
        val enabledKeys = apiKeys.filter { it.isEnabled }
        if (enabledKeys.isEmpty()) {
            return KeySelectionResult(null, "no_keys")
        }
        
        val now = System.currentTimeMillis()
        val cooldownMs = (keyManagement?.failureRecoveryTimeMinutes ?: 5) * 60 * 1000L
        
        // 过滤出可用的 keys（状态正常或已过冷却期）
        // 同时记录哪些 key 是从冷却期恢复的
        val availableKeysWithRecovery = enabledKeys.mapNotNull { key ->
            when (key.status) {
                ApiKeyStatus.DISABLED -> null
                ApiKeyStatus.ERROR -> {
                    // 检查是否已过冷却期
                    val timeSinceUpdate = now - key.updatedAt
                    if (timeSinceUpdate >= cooldownMs) {
                        // 已过冷却期，标记为需要恢复状态
                        key to true
                    } else {
                        null
                    }
                }
                ApiKeyStatus.RATE_LIMITED -> {
                    // 限速状态也检查冷却期
                    val timeSinceUpdate = now - key.updatedAt
                    if (timeSinceUpdate >= cooldownMs) {
                        // 已过冷却期，标记为需要恢复状态
                        key to true
                    } else {
                        null
                    }
                }
                ApiKeyStatus.ACTIVE -> key to false
            }
        }
        
        if (availableKeysWithRecovery.isEmpty()) {
            return KeySelectionResult(null, "no_available_keys")
        }
        
        val strategy = keyManagement?.strategy ?: LoadBalanceStrategy.ROUND_ROBIN
        val chosenPair = when (strategy) {
            LoadBalanceStrategy.PRIORITY -> {
                // 按优先级排序，选择优先级最高的（数字最小）
                availableKeysWithRecovery.sortedBy { it.first.priority }.first()
            }
            
            LoadBalanceStrategy.LEAST_USED -> {
                // 按使用次数排序，选择使用最少的
                availableKeysWithRecovery.sortedBy { it.first.usage.totalRequests }.first()
            }
            
            LoadBalanceStrategy.RANDOM -> {
                // 随机选择
                availableKeysWithRecovery[Random.nextInt(availableKeysWithRecovery.size)]
            }
            
            LoadBalanceStrategy.ROUND_ROBIN -> {
                // 轮询选择 - 使用内存中的索引来实现真正的轮询
                val sortedKeys = availableKeysWithRecovery.sortedBy { it.first.id }
                // 使用第一个key的id作为provider标识来维护轮询索引
                val providerKey = apiKeys.firstOrNull()?.id ?: "default"
                val currentIndex = roundRobinIndexMap.getOrDefault(providerKey, 0)
                val idx = currentIndex % sortedKeys.size
                // 更新索引到下一个位置
                roundRobinIndexMap[providerKey] = (currentIndex + 1) % sortedKeys.size
                sortedKeys[idx]
            }
        }
        
        // 如果选中的 key 是从冷却期恢复的，重置其状态和连续失败次数
        val chosenKey = if (chosenPair.second) {
            chosenPair.first.copy(
                status = ApiKeyStatus.ACTIVE,
                usage = chosenPair.first.usage.copy(consecutiveFailures = 0),
                updatedAt = now
            )
        } else {
            chosenPair.first
        }
        
        // 计算下一个轮询索引
        val nextIndex = if (strategy == LoadBalanceStrategy.ROUND_ROBIN) {
            val currentIndex = keyManagement?.roundRobinIndex ?: 0
            (currentIndex + 1) % availableKeysWithRecovery.size
        } else {
            keyManagement?.roundRobinIndex ?: 0
        }
        
        return KeySelectionResult(chosenKey, "strategy_${strategy.name.lowercase()}", nextIndex)
    }
    
    override fun updateKeyStatus(
        key: ApiKeyConfig,
        success: Boolean,
        keyManagement: KeyManagementConfig?,
        error: String?
    ): ApiKeyConfig {
        val now = System.currentTimeMillis()
        val maxFailures = keyManagement?.maxFailuresBeforeDisable ?: 3
        
        val newConsecutiveFailures = if (success) 0 else key.usage.consecutiveFailures + 1
        
        val newStatus = when {
            success -> ApiKeyStatus.ACTIVE
            newConsecutiveFailures >= maxFailures -> ApiKeyStatus.ERROR
            else -> key.status
        }
        
        return key.copy(
            usage = key.usage.copy(
                totalRequests = key.usage.totalRequests + 1,
                successfulRequests = key.usage.successfulRequests + if (success) 1 else 0,
                failedRequests = key.usage.failedRequests + if (success) 0 else 1,
                consecutiveFailures = newConsecutiveFailures,
                lastUsed = now
            ),
            status = newStatus,
            lastError = if (success) null else (error ?: key.lastError),
            updatedAt = now
        )
    }
}

/**
 * 扩展函数：从 ProviderSetting 获取有效的 API Key
 * 如果启用了多 Key 管理，则使用负载均衡策略选择
 * 否则使用传统的随机选择方式
 */
fun KeyRoulette.getEffectiveApiKey(
    apiKey: String,
    apiKeys: List<ApiKeyConfig>?,
    keyManagement: KeyManagementConfig?,
    multiKeyEnabled: Boolean
): String {
    return if (multiKeyEnabled && !apiKeys.isNullOrEmpty()) {
        // 使用多 Key 管理
        val result = selectKey(apiKeys, keyManagement)
        result.key?.key ?: next(apiKey)
    } else {
        // 使用传统方式
        next(apiKey)
    }
}
