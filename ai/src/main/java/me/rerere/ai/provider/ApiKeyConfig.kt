package me.rerere.ai.provider

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.random.Random
import kotlin.uuid.Uuid

/**
 * API Key 状态枚举
 */
@Serializable
enum class ApiKeyStatus {
    @SerialName("active")
    ACTIVE,      // 正常可用
    
    @SerialName("disabled")
    DISABLED,    // 已禁用
    
    @SerialName("error")
    ERROR,       // 错误状态（连续失败后）
    
    @SerialName("rate_limited")
    RATE_LIMITED // 限速中
}

/**
 * 负载均衡策略枚举
 */
@Serializable
enum class LoadBalanceStrategy {
    @SerialName("round_robin")
    ROUND_ROBIN,  // 轮询
    
    @SerialName("priority")
    PRIORITY,     // 优先级（数字越小优先级越高）
    
    @SerialName("least_used")
    LEAST_USED,   // 最少使用
    
    @SerialName("random")
    RANDOM        // 随机
}

/**
 * API Key 使用统计
 */
@Serializable
data class ApiKeyUsage(
    val totalRequests: Int = 0,           // 总请求数
    val successfulRequests: Int = 0,      // 成功请求数
    val failedRequests: Int = 0,          // 失败请求数
    val consecutiveFailures: Int = 0,     // 连续失败次数
    val lastUsed: Long? = null            // 最后使用时间戳
) {
    fun copyWith(
        totalRequests: Int = this.totalRequests,
        successfulRequests: Int = this.successfulRequests,
        failedRequests: Int = this.failedRequests,
        consecutiveFailures: Int = this.consecutiveFailures,
        lastUsed: Long? = this.lastUsed
    ) = ApiKeyUsage(
        totalRequests = totalRequests,
        successfulRequests = successfulRequests,
        failedRequests = failedRequests,
        consecutiveFailures = consecutiveFailures,
        lastUsed = lastUsed
    )
}

/**
 * 单个 API Key 配置
 */
@Serializable
data class ApiKeyConfig(
    val id: String,                              // 唯一标识
    val key: String,                             // API Key 值
    val name: String? = null,                    // 显示名称（可选）
    val isEnabled: Boolean = true,               // 是否启用
    val priority: Int = 5,                       // 优先级 1-10，数字越小优先级越高
    val maxRequestsPerMinute: Int? = null,       // 每分钟最大请求数（可选）
    val usage: ApiKeyUsage = ApiKeyUsage(),      // 使用统计
    val status: ApiKeyStatus = ApiKeyStatus.ACTIVE, // 当前状态
    val lastError: String? = null,               // 最后错误信息
    val createdAt: Long = System.currentTimeMillis(), // 创建时间
    val updatedAt: Long = System.currentTimeMillis()  // 更新时间
) {
    companion object {
        private var counter = 0
        
        /**
         * 生成唯一的 Key ID
         */
        private fun generateKeyId(): String {
            val timestamp = System.currentTimeMillis().toString(36)
            val random = Random.nextInt(0x7fffffff).toString(36)
            counter = (counter + 1) and 0x7fffffff
            val count = counter.toString(36)
            return "key_${timestamp}_${random}_$count"
        }
        
        /**
         * 创建新的 API Key 配置
         */
        fun create(key: String, name: String? = null, priority: Int = 5): ApiKeyConfig {
            val now = System.currentTimeMillis()
            return ApiKeyConfig(
                id = generateKeyId(),
                key = key,
                name = name,
                isEnabled = true,
                priority = priority,
                usage = ApiKeyUsage(),
                status = ApiKeyStatus.ACTIVE,
                createdAt = now,
                updatedAt = now
            )
        }
    }
    
    /**
     * 获取显示名称（如果没有设置名称，则显示脱敏后的 Key）
     */
    fun getDisplayName(): String {
        return name?.takeIf { it.isNotBlank() } ?: maskKey(key)
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
}

/**
 * Key 管理配置
 */
@Serializable
data class KeyManagementConfig(
    val strategy: LoadBalanceStrategy = LoadBalanceStrategy.ROUND_ROBIN, // 负载均衡策略
    val maxFailuresBeforeDisable: Int = 3,      // 连续失败多少次后禁用
    val failureRecoveryTimeMinutes: Int = 5,    // 失败后恢复时间（分钟）
    val enableAutoRecovery: Boolean = true,     // 是否启用自动恢复
    val roundRobinIndex: Int = 0                // 轮询当前索引（持久化）
) {
    fun copyWith(
        strategy: LoadBalanceStrategy = this.strategy,
        maxFailuresBeforeDisable: Int = this.maxFailuresBeforeDisable,
        failureRecoveryTimeMinutes: Int = this.failureRecoveryTimeMinutes,
        enableAutoRecovery: Boolean = this.enableAutoRecovery,
        roundRobinIndex: Int = this.roundRobinIndex
    ) = KeyManagementConfig(
        strategy = strategy,
        maxFailuresBeforeDisable = maxFailuresBeforeDisable,
        failureRecoveryTimeMinutes = failureRecoveryTimeMinutes,
        enableAutoRecovery = enableAutoRecovery,
        roundRobinIndex = roundRobinIndex
    )
}

/**
 * Key 选择结果
 */
data class KeySelectionResult(
    val key: ApiKeyConfig?,
    val reason: String,
    val nextRoundRobinIndex: Int = 0  // 下一次轮询的索引
)