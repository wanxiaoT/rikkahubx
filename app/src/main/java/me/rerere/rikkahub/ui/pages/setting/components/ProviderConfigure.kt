package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Lucide
import com.dokar.sonner.ToastType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

/**
 * Mask API Key to show only first 3 and last 3 characters
 * Example: "sk-abcdefghijklmnopqrstuvwxyz" -> "sk-*******xyz"
 */
fun maskApiKey(apiKey: String): String {
    if (apiKey.length <= 6) return apiKey
    val prefix = apiKey.take(3)
    val suffix = apiKey.takeLast(3)
    return "$prefix*******$suffix"
}

@Composable
fun ProviderConfigure(
    provider: ProviderSetting,
    modifier: Modifier = Modifier,
    onEdit: (provider: ProviderSetting) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        // Type
        if (!provider.builtIn) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                ProviderSetting.Types.forEachIndexed { index, type ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ProviderSetting.Types.size
                        ),
                        label = {
                            Text(type.simpleName ?: "")
                        },
                        selected = provider::class == type,
                        onClick = {
                            onEdit(provider.convertTo(type))
                        }
                    )
                }
            }
        }

        // [!] just for debugging
        // Text(JsonInstant.encodeToString(provider), fontSize = 10.sp)

        // Provider Configure
        when (provider) {
            is ProviderSetting.OpenAI -> {
                ProviderConfigureOpenAI(provider, onEdit)
            }

            is ProviderSetting.Google -> {
                ProviderConfigureGoogle(provider, onEdit)
            }

            is ProviderSetting.Claude -> {
                ProviderConfigureClaude(provider, onEdit)
            }
        }
    }
}

fun ProviderSetting.convertTo(type: KClass<out ProviderSetting>): ProviderSetting {
    val apiKey = when (this) {
        is ProviderSetting.OpenAI -> this.apiKey
        is ProviderSetting.Google -> this.apiKey
        is ProviderSetting.Claude -> this.apiKey
    }
    val newProvider = type.primaryConstructor!!.callBy(emptyMap())
    return when (newProvider) {
        is ProviderSetting.OpenAI -> newProvider.copy(apiKey = apiKey)
        is ProviderSetting.Google -> newProvider.copy(apiKey = apiKey)
        is ProviderSetting.Claude -> newProvider.copy(apiKey = apiKey)
    }
}

@Composable
private fun ColumnScope.ProviderConfigureOpenAI(
    provider: ProviderSetting.OpenAI,
    onEdit: (provider: ProviderSetting.OpenAI) -> Unit
) {
    val toaster = LocalToaster.current
    var isApiKeyMasked by remember { mutableStateOf(false) }

    provider.description()

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(id = R.string.setting_provider_page_enable), modifier = Modifier.weight(1f))
        Checkbox(
            checked = provider.enabled,
            onCheckedChange = {
                onEdit(provider.copy(enabled = it))
            }
        )
    }

    OutlinedTextField(
        value = provider.name,
        onValueChange = {
            onEdit(provider.copy(name = it.trim()))
        },
        label = {
            Text(stringResource(id = R.string.setting_provider_page_name))
        },
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = if (provider.multiKeyEnabled) {
            ""
        } else if (isApiKeyMasked && provider.apiKey.isNotBlank()) {
            maskApiKey(provider.apiKey)
        } else {
            provider.apiKey
        },
        onValueChange = {
            onEdit(provider.copy(apiKey = it.trim()))
            // Reset mask state when user edits
            if (isApiKeyMasked) {
                isApiKeyMasked = false
            }
        },
        label = {
            Text(stringResource(id = R.string.setting_provider_page_api_key))
        },
        modifier = Modifier.fillMaxWidth(),
        maxLines = 3,
        enabled = !provider.multiKeyEnabled,
        placeholder = if (provider.multiKeyEnabled) {
            { Text(stringResource(id = R.string.setting_provider_page_api_key_disabled_by_multi_key)) }
        } else null,
        trailingIcon = if (!provider.multiKeyEnabled && provider.apiKey.isNotBlank()) {
            {
                IconButton(
                    onClick = { isApiKeyMasked = !isApiKeyMasked }
                ) {
                    Icon(
                        imageVector = if (isApiKeyMasked) Lucide.EyeOff else Lucide.Eye,
                        contentDescription = if (isApiKeyMasked) "Show API Key" else "Hide API Key"
                    )
                }
            }
        } else null
    )

    OutlinedTextField(
        value = provider.baseUrl,
        onValueChange = {
            onEdit(provider.copy(baseUrl = it.trim()))
        },
        label = {
            Text(stringResource(id = R.string.setting_provider_page_api_base_url))
        },
        modifier = Modifier.fillMaxWidth()
    )

    if (!provider.useResponseApi) {
        OutlinedTextField(
            value = provider.chatCompletionsPath,
            onValueChange = {
                onEdit(provider.copy(chatCompletionsPath = it.trim()))
            },
            label = {
                Text(stringResource(id = R.string.setting_provider_page_api_path))
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !provider.builtIn
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(id = R.string.setting_provider_page_response_api), modifier = Modifier.weight(1f))
        val responseAPIWarning = stringResource(id = R.string.setting_provider_page_response_api_warning)
        Checkbox(
            checked = provider.useResponseApi,
            onCheckedChange = {
                onEdit(provider.copy(useResponseApi = it))

                if(it && provider.baseUrl.toHttpUrlOrNull()?.host != "api.openai.com") {
                    toaster.show(
                        message = responseAPIWarning,
                        type = ToastType.Warning
                    )
                }
            }
        )
    }
}

@Composable
private fun ColumnScope.ProviderConfigureClaude(
    provider: ProviderSetting.Claude,
    onEdit: (provider: ProviderSetting.Claude) -> Unit
) {
    var isApiKeyMasked by remember { mutableStateOf(false) }

    provider.description()

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(id = R.string.setting_provider_page_enable), modifier = Modifier.weight(1f))
        Checkbox(
            checked = provider.enabled,
            onCheckedChange = {
                onEdit(provider.copy(enabled = it))
            }
        )
    }

    OutlinedTextField(
        value = provider.name,
        onValueChange = {
            onEdit(provider.copy(name = it.trim()))
        },
        label = {
            Text(stringResource(id = R.string.setting_provider_page_name))
        },
        modifier = Modifier.fillMaxWidth(),
        maxLines = 3,
    )

    OutlinedTextField(
        value = if (provider.multiKeyEnabled) {
            ""
        } else if (isApiKeyMasked && provider.apiKey.isNotBlank()) {
            maskApiKey(provider.apiKey)
        } else {
            provider.apiKey
        },
        onValueChange = {
            onEdit(provider.copy(apiKey = it.trim()))
            // Reset mask state when user edits
            if (isApiKeyMasked) {
                isApiKeyMasked = false
            }
        },
        label = {
            Text(stringResource(id = R.string.setting_provider_page_api_key))
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = !provider.multiKeyEnabled,
        placeholder = if (provider.multiKeyEnabled) {
            { Text(stringResource(id = R.string.setting_provider_page_api_key_disabled_by_multi_key)) }
        } else null,
        trailingIcon = if (!provider.multiKeyEnabled && provider.apiKey.isNotBlank()) {
            {
                IconButton(
                    onClick = { isApiKeyMasked = !isApiKeyMasked }
                ) {
                    Icon(
                        imageVector = if (isApiKeyMasked) Lucide.EyeOff else Lucide.Eye,
                        contentDescription = if (isApiKeyMasked) "Show API Key" else "Hide API Key"
                    )
                }
            }
        } else null
    )

    OutlinedTextField(
        value = provider.baseUrl,
        onValueChange = {
            onEdit(provider.copy(baseUrl = it.trim()))
        },
        label = {
            Text(stringResource(id = R.string.setting_provider_page_api_base_url))
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ColumnScope.ProviderConfigureGoogle(
    provider: ProviderSetting.Google,
    onEdit: (provider: ProviderSetting.Google) -> Unit
) {
    var isApiKeyMasked by remember { mutableStateOf(false) }

    provider.description()

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(id = R.string.setting_provider_page_enable), modifier = Modifier.weight(1f))
        Checkbox(
            checked = provider.enabled,
            onCheckedChange = {
                onEdit(provider.copy(enabled = it))
            }
        )
    }

    OutlinedTextField(
        value = provider.name,
        onValueChange = {
            onEdit(provider.copy(name = it.trim()))
        },
        label = {
            Text(stringResource(id = R.string.setting_provider_page_name))
        },
        modifier = Modifier.fillMaxWidth()
    )

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(id = R.string.setting_provider_page_vertex_ai), modifier = Modifier.weight(1f))
        Checkbox(
            checked = provider.vertexAI,
            onCheckedChange = {
                onEdit(provider.copy(vertexAI = it))
            }
        )
    }

    if (!provider.vertexAI) {
        OutlinedTextField(
            value = if (provider.multiKeyEnabled) {
                ""
            } else if (isApiKeyMasked && provider.apiKey.isNotBlank()) {
                maskApiKey(provider.apiKey)
            } else {
                provider.apiKey
            },
            onValueChange = {
                onEdit(provider.copy(apiKey = it.trim()))
                // Reset mask state when user edits
                if (isApiKeyMasked) {
                    isApiKeyMasked = false
                }
            },
            label = {
                Text(stringResource(id = R.string.setting_provider_page_api_key))
            },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            enabled = !provider.multiKeyEnabled,
            placeholder = if (provider.multiKeyEnabled) {
                { Text(stringResource(id = R.string.setting_provider_page_api_key_disabled_by_multi_key)) }
            } else null,
            trailingIcon = if (!provider.multiKeyEnabled && provider.apiKey.isNotBlank()) {
                {
                    IconButton(
                        onClick = { isApiKeyMasked = !isApiKeyMasked }
                    ) {
                        Icon(
                            imageVector = if (isApiKeyMasked) Lucide.EyeOff else Lucide.Eye,
                            contentDescription = if (isApiKeyMasked) "Show API Key" else "Hide API Key"
                        )
                    }
                }
            } else null
        )

        OutlinedTextField(
            value = provider.baseUrl,
            onValueChange = {
                onEdit(provider.copy(baseUrl = it.trim()))
            },
            label = {
                Text(stringResource(id = R.string.setting_provider_page_api_base_url))
            },
            modifier = Modifier.fillMaxWidth(),
            isError = !provider.baseUrl.endsWith("/v1beta"),
            supportingText = if (!provider.baseUrl.endsWith("/v1beta")) {
                {
                    Text("The base URL usually ends with `/v1beta`")
                }
            } else null
        )
    } else {
        OutlinedTextField(
            value = provider.serviceAccountEmail,
            onValueChange = {
                onEdit(provider.copy(serviceAccountEmail = it.trim()))
            },
            label = {
                Text(stringResource(id = R.string.setting_provider_page_service_account_email))
            },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = provider.privateKey,
            onValueChange = {
                onEdit(provider.copy(privateKey = it.trim()))
            },
            label = {
                Text(stringResource(id = R.string.setting_provider_page_private_key))
            },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 6,
            minLines = 3,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetbrainsMono),
        )
        OutlinedTextField(
            value = provider.location,
            onValueChange = {
                onEdit(provider.copy(location = it.trim()))
            },
            label = {
                // https://cloud.google.com/vertex-ai/generative-ai/docs/learn/locations#available-regions
                Text(stringResource(id = R.string.setting_provider_page_location))
            },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = provider.projectId,
            onValueChange = {
                onEdit(provider.copy(projectId = it.trim()))
            },
            label = {
                Text(stringResource(id = R.string.setting_provider_page_project_id))
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
