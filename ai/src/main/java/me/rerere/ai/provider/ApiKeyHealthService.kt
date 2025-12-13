package me.rerere.ai.provider

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.ai.ui.UIMessage

/**
 * API Key 健康检测结果
 */
sealed class HealthCheckResult {
    data class Success(
        val keyId: String,
        val responseTime: Long // 响应时间（毫秒）
    ) : HealthCheckResult()
    
    data class Error(
        val keyId: String,
        val error: Throwable,
        val errorMessage: String
    ) : HealthCheckResult()
    
    data class RateLimited(
        val keyId: String,
        val retryAfter: Long? = null // 重试时间（秒）
    ) : HealthCheckResult()
}

/**
 * 批量检测进度
 */
data class BatchCheckProgress(
    val total: Int,
    val completed: Int,
    val currentKeyId: String?,
    val results: Map<String, HealthCheckResult>
)

/**
 * API Key 健康检测服务
 * 
 * 参考 Kelivo 项目的实现，提供以下功能：
 * 1. 单个 Key 健康检测
 * 2. 批量 Key 健康检测（带延迟）
 * 3. 流式检测模式验证
 * 4. 自动更新 Key 状态和使用统计
 */
class ApiKeyHealthService(
    private val providerManager: ProviderManager
) {
    companion object {
        private const val TAG = "ApiKeyHealthService"
        
        // 批量检测时每个 Key 之间的延迟（毫秒）
        const val DEFAULT_BATCH_DELAY_MS = 120L
        
        // 模型检测时每个模型之间的延迟（毫秒）
        const val DEFAULT_MODEL_DELAY_MS = 500L
        
        // 测试消息
        private val TEST_MESSAGE = UIMessage.user("hello")
    }
    
    /**
     * 测试单个 API Key 的连接
     * 
     * @param provider Provider 设置
     * @param apiKey 要测试的 API Key
     * @param model 用于测试的模型
     * @param useStream 是否使用流式请求进行测试
     * @return 健康检测结果
     */
    suspend fun testSingleKey(
        provider: ProviderSetting,
        apiKey: ApiKeyConfig,
        model: Model,
        useStream: Boolean = false
    ): HealthCheckResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            // 创建临时的 provider 配置，使用指定的 API Key
            val testProvider = createTestProvider(provider, apiKey.key)
            val providerImpl = providerManager.getProviderByType(testProvider)
            
            if (useStream) {
                // 流式检测：验证是否能收到 SSE 数据
                var receivedData = false
                providerImpl.streamText(
                    providerSetting = testProvider,
                    messages = listOf(TEST_MESSAGE),
                    params = TextGenerationParams(model = model)
                ).collect { chunk ->
                    receivedData = true
                    // 收到第一个 chunk 就认为成功，取消收集
                    throw StreamTestSuccessException()
                }
                
                if (!receivedData) {
                    throw IllegalStateException("No SSE data received")
                }
            } else {
                // 非流式检测
                providerImpl.generateText(
                    providerSetting = testProvider,
                    messages = listOf(TEST_MESSAGE),
                    params = TextGenerationParams(model = model)
                )
            }
            
            val responseTime = System.currentTimeMillis() - startTime
            Log.i(TAG, "Key ${apiKey.getDisplayName()} test success, response time: ${responseTime}ms")
            
            HealthCheckResult.Success(
                keyId = apiKey.id,
                responseTime = responseTime
            )
        } catch (e: StreamTestSuccessException) {
            // 流式测试成功
            val responseTime = System.currentTimeMillis() - startTime
            Log.i(TAG, "Key ${apiKey.getDisplayName()} stream test success, response time: ${responseTime}ms")
            
            HealthCheckResult.Success(
                keyId = apiKey.id,
                responseTime = responseTime
            )
        } catch (e: Exception) {
            Log.e(TAG, "Key ${apiKey.getDisplayName()} test failed", e)
            
            // 检查是否是限流错误
            val errorMessage = e.message ?: "Unknown error"
            if (isRateLimitError(e)) {
                HealthCheckResult.RateLimited(
                    keyId = apiKey.id,
                    retryAfter = parseRetryAfter(e)
                )
            } else {
                HealthCheckResult.Error(
                    keyId = apiKey.id,
                    error = e,
                    errorMessage = errorMessage
                )
            }
        }
    }
    
    /**
     * 批量测试 API Keys
     * 
     * @param provider Provider 设置
     * @param apiKeys 要测试的 API Key 列表
     * @param model 用于测试的模型
     * @param useStream 是否使用流式请求进行测试
     * @param delayMs 每个 Key 之间的延迟（毫秒）
     * @return 检测进度流
     */
    fun testBatchKeys(
        provider: ProviderSetting,
        apiKeys: List<ApiKeyConfig>,
        model: Model,
        useStream: Boolean = false,
        delayMs: Long = DEFAULT_BATCH_DELAY_MS
    ): Flow<BatchCheckProgress> = flow {
        val results = mutableMapOf<String, HealthCheckResult>()
        
        apiKeys.forEachIndexed { index, apiKey ->
            // 发送当前进度
            emit(BatchCheckProgress(
                total = apiKeys.size,
                completed = index,
                currentKeyId = apiKey.id,
                results = results.toMap()
            ))
            
            // 测试当前 Key
            val result = testSingleKey(provider, apiKey, model, useStream)
            results[apiKey.id] = result
            
            // 添加延迟，避免请求过快
            if (index < apiKeys.size - 1) {
                delay(delayMs)
            }
        }
        
        // 发送最终结果
        emit(BatchCheckProgress(
            total = apiKeys.size,
            completed = apiKeys.size,
            currentKeyId = null,
            results = results.toMap()
        ))
    }
    
    /**
     * 根据检测结果更新 API Key 配置
     * 
     * @param apiKey 原始 API Key 配置
     * @param result 检测结果
     * @param keyManagement Key 管理配置
     * @return 更新后的 API Key 配置
     */
    fun updateKeyWithResult(
        apiKey: ApiKeyConfig,
        result: HealthCheckResult,
        keyManagement: KeyManagementConfig
    ): ApiKeyConfig {
        val now = System.currentTimeMillis()
        
        return when (result) {
            is HealthCheckResult.Success -> {
                apiKey.copy(
                    status = ApiKeyStatus.ACTIVE,
                    lastError = null,
                    usage = apiKey.usage.copyWith(
                        totalRequests = apiKey.usage.totalRequests + 1,
                        successfulRequests = apiKey.usage.successfulRequests + 1,
                        consecutiveFailures = 0,
                        lastUsed = now
                    ),
                    updatedAt = now
                )
            }
            
            is HealthCheckResult.Error -> {
                val newConsecutiveFailures = apiKey.usage.consecutiveFailures + 1
                val shouldDisable = newConsecutiveFailures >= keyManagement.maxFailuresBeforeDisable
                
                apiKey.copy(
                    status = if (shouldDisable) ApiKeyStatus.ERROR else apiKey.status,
                    lastError = result.errorMessage,
                    usage = apiKey.usage.copyWith(
                        totalRequests = apiKey.usage.totalRequests + 1,
                        failedRequests = apiKey.usage.failedRequests + 1,
                        consecutiveFailures = newConsecutiveFailures,
                        lastUsed = now
                    ),
                    updatedAt = now
                )
            }
            
            is HealthCheckResult.RateLimited -> {
                apiKey.copy(
                    status = ApiKeyStatus.RATE_LIMITED,
                    lastError = "Rate limited" + (result.retryAfter?.let { ", retry after ${it}s" } ?: ""),
                    usage = apiKey.usage.copyWith(
                        totalRequests = apiKey.usage.totalRequests + 1,
                        failedRequests = apiKey.usage.failedRequests + 1,
                        lastUsed = now
                    ),
                    updatedAt = now
                )
            }
        }
    }
    
    /**
     * 检查并恢复可以自动恢复的 Key
     * 
     * @param apiKeys API Key 列表
     * @param keyManagement Key 管理配置
     * @return 更新后的 API Key 列表
     */
    fun checkAndRecoverKeys(
        apiKeys: List<ApiKeyConfig>,
        keyManagement: KeyManagementConfig
    ): List<ApiKeyConfig> {
        if (!keyManagement.enableAutoRecovery) {
            return apiKeys
        }
        
        val now = System.currentTimeMillis()
        val recoveryTimeMs = keyManagement.failureRecoveryTimeMinutes * 60 * 1000L
        
        return apiKeys.map { apiKey ->
            // 检查是否可以恢复
            val canRecover = (apiKey.status == ApiKeyStatus.ERROR || apiKey.status == ApiKeyStatus.RATE_LIMITED) &&
                    apiKey.usage.lastUsed != null &&
                    (now - apiKey.usage.lastUsed) >= recoveryTimeMs
            
            if (canRecover) {
                Log.i(TAG, "Auto recovering key: ${apiKey.getDisplayName()}")
                apiKey.copy(
                    status = ApiKeyStatus.ACTIVE,
                    lastError = null,
                    usage = apiKey.usage.copyWith(
                        consecutiveFailures = 0
                    ),
                    updatedAt = now
                )
            } else {
                apiKey
            }
        }
    }
    
    /**
     * 创建用于测试的临时 Provider 配置
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : ProviderSetting> createTestProvider(provider: T, apiKey: String): T {
        return when (provider) {
            is ProviderSetting.OpenAI -> provider.copy(apiKey = apiKey) as T
            is ProviderSetting.Google -> provider.copy(apiKey = apiKey) as T
            is ProviderSetting.Claude -> provider.copy(apiKey = apiKey) as T
        }
    }
    
    /**
     * 检查是否是限流错误
     */
    private fun isRateLimitError(e: Exception): Boolean {
        val message = e.message?.lowercase() ?: return false
        return message.contains("rate limit") ||
                message.contains("too many requests") ||
                message.contains("429") ||
                message.contains("quota exceeded")
    }
    
    /**
     * 解析重试时间
     */
    private fun parseRetryAfter(e: Exception): Long? {
        val message = e.message ?: return null
        // 尝试从错误消息中解析 retry-after 值
        val regex = Regex("retry.?after[:\\s]*(\\d+)", RegexOption.IGNORE_CASE)
        return regex.find(message)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }
    
    /**
     * 用于标记流式测试成功的异常
     */
    private class StreamTestSuccessException : Exception()
}

/**
 * 扩展函数：批量更新 API Keys 的检测结果
 */
fun List<ApiKeyConfig>.updateWithResults(
    results: Map<String, HealthCheckResult>,
    keyManagement: KeyManagementConfig,
    healthService: ApiKeyHealthService
): List<ApiKeyConfig> {
    return this.map { apiKey ->
        val result = results[apiKey.id]
        if (result != null) {
            healthService.updateKeyWithResult(apiKey, result, keyManagement)
        } else {
            apiKey
        }
    }
}