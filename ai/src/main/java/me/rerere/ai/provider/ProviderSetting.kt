package me.rerere.ai.provider

import androidx.compose.runtime.Composable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.uuid.Uuid

@Serializable
sealed class ProviderProxy {
    @Serializable
    @SerialName("none")
    object None : ProviderProxy()

    @Serializable
    @SerialName("http")
    data class Http(
        val address: String,
        val port: Int,
        val username: String? = null,
        val password: String? = null,
    ) : ProviderProxy()
}

@Serializable
data class BalanceOption(
    val enabled: Boolean = false, // 是否开启余额获取功能
    val apiPath: String = "/credits", // 余额获取API路径
    val resultPath: String = "data.total_usage", // 余额获取JSON路径
)

@Serializable
sealed class ProviderSetting {
    abstract val id: Uuid
    abstract val enabled: Boolean
    abstract val name: String
    abstract val models: List<Model>
    abstract val proxy: ProviderProxy
    abstract val balanceOption: BalanceOption
    
    // 多 Key 管理相关字段
    abstract val apiKeys: List<ApiKeyConfig>?
    abstract val keyManagement: KeyManagementConfig?
    abstract val multiKeyEnabled: Boolean

    abstract val builtIn: Boolean
    abstract val description: @Composable() () -> Unit
    abstract val shortDescription: @Composable() () -> Unit

    abstract fun addModel(model: Model): ProviderSetting
    abstract fun editModel(model: Model): ProviderSetting
    abstract fun delModel(model: Model): ProviderSetting
    abstract fun moveMove(from: Int, to: Int): ProviderSetting
    abstract fun copyProvider(
        id: Uuid = this.id,
        enabled: Boolean = this.enabled,
        name: String = this.name,
        models: List<Model> = this.models,
        proxy: ProviderProxy = this.proxy,
        balanceOption: BalanceOption = this.balanceOption,
        builtIn: Boolean = this.builtIn,
        description: @Composable (() -> Unit) = this.description,
        shortDescription: @Composable (() -> Unit) = this.shortDescription,
        apiKeys: List<ApiKeyConfig>? = this.apiKeys,
        keyManagement: KeyManagementConfig? = this.keyManagement,
        multiKeyEnabled: Boolean = this.multiKeyEnabled,
    ): ProviderSetting
    
    // 多 Key 管理辅助方法
    abstract fun addApiKey(apiKey: ApiKeyConfig): ProviderSetting
    abstract fun updateApiKey(apiKey: ApiKeyConfig): ProviderSetting
    abstract fun removeApiKey(apiKeyId: String): ProviderSetting
    abstract fun updateKeyManagement(config: KeyManagementConfig): ProviderSetting
    abstract fun setMultiKeyEnabled(enabled: Boolean): ProviderSetting

    @Serializable
    @SerialName("openai")
    data class OpenAI(
        override var id: Uuid = Uuid.random(),
        override var enabled: Boolean = true,
        override var name: String = "OpenAI",
        override var models: List<Model> = emptyList(),
        override var proxy: ProviderProxy = ProviderProxy.None,
        override val balanceOption: BalanceOption = BalanceOption(),
        @Transient override val builtIn: Boolean = false,
        @Transient override val description: @Composable (() -> Unit) = {},
        @Transient override val shortDescription: @Composable (() -> Unit) = {},
        var apiKey: String = "",
        var baseUrl: String = "https://api.openai.com/v1",
        var chatCompletionsPath: String = "/chat/completions",
        var useResponseApi: Boolean = false,
        // 多 Key 管理
        override val apiKeys: List<ApiKeyConfig>? = null,
        override val keyManagement: KeyManagementConfig? = null,
        override val multiKeyEnabled: Boolean = false,
    ) : ProviderSetting() {
        override fun addModel(model: Model): ProviderSetting {
            return copy(models = models + model)
        }

        override fun editModel(model: Model): ProviderSetting {
            return copy(models = models.map { if (it.id == model.id) model.copy() else it })
        }

        override fun delModel(model: Model): ProviderSetting {
            return copy(models = models.filter { it.id != model.id })
        }

        override fun moveMove(
            from: Int,
            to: Int
        ): ProviderSetting {
            return copy(models = models.toMutableList().apply {
                val model = removeAt(from)
                add(to, model)
            })
        }

        override fun copyProvider(
            id: Uuid,
            enabled: Boolean,
            name: String,
            models: List<Model>,
            proxy: ProviderProxy,
            balanceOption: BalanceOption,
            builtIn: Boolean,
            description: @Composable (() -> Unit),
            shortDescription: @Composable (() -> Unit),
            apiKeys: List<ApiKeyConfig>?,
            keyManagement: KeyManagementConfig?,
            multiKeyEnabled: Boolean,
        ): ProviderSetting {
            return this.copy(
                id = id,
                enabled = enabled,
                name = name,
                models = models,
                builtIn = builtIn,
                description = description,
                proxy = proxy,
                balanceOption = balanceOption,
                shortDescription = shortDescription,
                apiKeys = apiKeys,
                keyManagement = keyManagement,
                multiKeyEnabled = multiKeyEnabled
            )
        }
        
        override fun addApiKey(apiKey: ApiKeyConfig): ProviderSetting {
            val currentKeys = apiKeys ?: emptyList()
            return copy(apiKeys = currentKeys + apiKey)
        }
        
        override fun updateApiKey(apiKey: ApiKeyConfig): ProviderSetting {
            val currentKeys = apiKeys ?: return this
            return copy(apiKeys = currentKeys.map { if (it.id == apiKey.id) apiKey else it })
        }
        
        override fun removeApiKey(apiKeyId: String): ProviderSetting {
            val currentKeys = apiKeys ?: return this
            return copy(apiKeys = currentKeys.filter { it.id != apiKeyId })
        }
        
        override fun updateKeyManagement(config: KeyManagementConfig): ProviderSetting {
            return copy(keyManagement = config)
        }
        
        override fun setMultiKeyEnabled(enabled: Boolean): ProviderSetting {
            return copy(multiKeyEnabled = enabled)
        }
    }

    @Serializable
    @SerialName("google")
    data class Google(
        override var id: Uuid = Uuid.random(),
        override var enabled: Boolean = true,
        override var name: String = "Google",
        override var models: List<Model> = emptyList(),
        override var proxy: ProviderProxy = ProviderProxy.None,
        override val balanceOption: BalanceOption = BalanceOption(),
        @Transient override val builtIn: Boolean = false,
        @Transient override val description: @Composable (() -> Unit) = {},
        @Transient override val shortDescription: @Composable (() -> Unit) = {},
        var apiKey: String = "",
        var baseUrl: String = "https://generativelanguage.googleapis.com/v1beta", // only for google AI
        var vertexAI: Boolean = false,
        var privateKey: String = "", // only for vertex AI
        var serviceAccountEmail: String = "", // only for vertex AI
        var location: String = "us-central1", // only for vertex AI
        var projectId: String = "", // only for vertex AI
        // 多 Key 管理
        override val apiKeys: List<ApiKeyConfig>? = null,
        override val keyManagement: KeyManagementConfig? = null,
        override val multiKeyEnabled: Boolean = false,
    ) : ProviderSetting() {
        override fun addModel(model: Model): ProviderSetting {
            return copy(models = models + model)
        }

        override fun editModel(model: Model): ProviderSetting {
            return copy(models = models.map { if (it.id == model.id) model.copy() else it })
        }

        override fun delModel(model: Model): ProviderSetting {
            return copy(models = models.filter { it.id != model.id })
        }

        override fun moveMove(
            from: Int,
            to: Int
        ): ProviderSetting {
            return copy(models = models.toMutableList().apply {
                val model = removeAt(from)
                add(to, model)
            })
        }

        override fun copyProvider(
            id: Uuid,
            enabled: Boolean,
            name: String,
            models: List<Model>,
            proxy: ProviderProxy,
            balanceOption: BalanceOption,
            builtIn: Boolean,
            description: @Composable (() -> Unit),
            shortDescription: @Composable (() -> Unit),
            apiKeys: List<ApiKeyConfig>?,
            keyManagement: KeyManagementConfig?,
            multiKeyEnabled: Boolean,
        ): ProviderSetting {
            return this.copy(
                id = id,
                enabled = enabled,
                name = name,
                models = models,
                builtIn = builtIn,
                description = description,
                shortDescription = shortDescription,
                proxy = proxy,
                balanceOption = balanceOption,
                apiKeys = apiKeys,
                keyManagement = keyManagement,
                multiKeyEnabled = multiKeyEnabled
            )
        }
        
        override fun addApiKey(apiKey: ApiKeyConfig): ProviderSetting {
            val currentKeys = apiKeys ?: emptyList()
            return copy(apiKeys = currentKeys + apiKey)
        }
        
        override fun updateApiKey(apiKey: ApiKeyConfig): ProviderSetting {
            val currentKeys = apiKeys ?: return this
            return copy(apiKeys = currentKeys.map { if (it.id == apiKey.id) apiKey else it })
        }
        
        override fun removeApiKey(apiKeyId: String): ProviderSetting {
            val currentKeys = apiKeys ?: return this
            return copy(apiKeys = currentKeys.filter { it.id != apiKeyId })
        }
        
        override fun updateKeyManagement(config: KeyManagementConfig): ProviderSetting {
            return copy(keyManagement = config)
        }
        
        override fun setMultiKeyEnabled(enabled: Boolean): ProviderSetting {
            return copy(multiKeyEnabled = enabled)
        }
    }

    @Serializable
    @SerialName("claude")
    data class Claude(
        override var id: Uuid = Uuid.random(),
        override var enabled: Boolean = true,
        override var name: String = "Claude",
        override var models: List<Model> = emptyList(),
        override var proxy: ProviderProxy = ProviderProxy.None,
        override val balanceOption: BalanceOption = BalanceOption(),
        @Transient override val builtIn: Boolean = false,
        @Transient override val description: @Composable (() -> Unit) = {},
        @Transient override val shortDescription: @Composable (() -> Unit) = {},
        var apiKey: String = "",
        var baseUrl: String = "https://api.anthropic.com/v1",
        // 多 Key 管理
        override val apiKeys: List<ApiKeyConfig>? = null,
        override val keyManagement: KeyManagementConfig? = null,
        override val multiKeyEnabled: Boolean = false,
    ) : ProviderSetting() {
        override fun addModel(model: Model): ProviderSetting {
            return copy(models = models + model)
        }

        override fun editModel(model: Model): ProviderSetting {
            return copy(models = models.map { if (it.id == model.id) model.copy() else it })
        }

        override fun delModel(model: Model): ProviderSetting {
            return copy(models = models.filter { it.id != model.id })
        }

        override fun moveMove(
            from: Int,
            to: Int
        ): ProviderSetting {
            return copy(models = models.toMutableList().apply {
                val model = removeAt(from)
                add(to, model)
            })
        }

        override fun copyProvider(
            id: Uuid,
            enabled: Boolean,
            name: String,
            models: List<Model>,
            proxy: ProviderProxy,
            balanceOption: BalanceOption,
            builtIn: Boolean,
            description: @Composable (() -> Unit),
            shortDescription: @Composable (() -> Unit),
            apiKeys: List<ApiKeyConfig>?,
            keyManagement: KeyManagementConfig?,
            multiKeyEnabled: Boolean,
        ): ProviderSetting {
            return this.copy(
                id = id,
                enabled = enabled,
                name = name,
                models = models,
                proxy = proxy,
                balanceOption = balanceOption,
                builtIn = builtIn,
                description = description,
                shortDescription = shortDescription,
                apiKeys = apiKeys,
                keyManagement = keyManagement,
                multiKeyEnabled = multiKeyEnabled
            )
        }
        
        override fun addApiKey(apiKey: ApiKeyConfig): ProviderSetting {
            val currentKeys = apiKeys ?: emptyList()
            return copy(apiKeys = currentKeys + apiKey)
        }
        
        override fun updateApiKey(apiKey: ApiKeyConfig): ProviderSetting {
            val currentKeys = apiKeys ?: return this
            return copy(apiKeys = currentKeys.map { if (it.id == apiKey.id) apiKey else it })
        }
        
        override fun removeApiKey(apiKeyId: String): ProviderSetting {
            val currentKeys = apiKeys ?: return this
            return copy(apiKeys = currentKeys.filter { it.id != apiKeyId })
        }
        
        override fun updateKeyManagement(config: KeyManagementConfig): ProviderSetting {
            return copy(keyManagement = config)
        }
        
        override fun setMultiKeyEnabled(enabled: Boolean): ProviderSetting {
            return copy(multiKeyEnabled = enabled)
        }
    }

    companion object {
        val Types by lazy {
            listOf(
                OpenAI::class,
                Google::class,
                Claude::class,
            )
        }
    }
}

/**
 * 扩展函数：获取有效的 API Key
 * 如果启用了多 Key 管理，则从 apiKeys 列表中选择
 * 否则返回单个 apiKey
 */
fun ProviderSetting.getEffectiveApiKey(): String {
    return when (this) {
        is ProviderSetting.OpenAI -> this.apiKey
        is ProviderSetting.Google -> this.apiKey
        is ProviderSetting.Claude -> this.apiKey
    }
}
