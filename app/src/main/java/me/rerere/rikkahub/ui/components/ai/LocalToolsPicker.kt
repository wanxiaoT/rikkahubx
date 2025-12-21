package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Code
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Wrench
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ui.ToggleSurface

@Composable
fun LocalToolsPickerButton(
    assistant: Assistant,
    modifier: Modifier = Modifier,
    onUpdateAssistant: (Assistant) -> Unit
) {
    var showLocalToolsPicker by remember { mutableStateOf(false) }
    val hasLocalTools = assistant.localTools.isNotEmpty()

    ToggleSurface(
        modifier = modifier,
        checked = hasLocalTools,
        onClick = {
            showLocalToolsPicker = true
        }
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 8.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                BadgedBox(
                    badge = {
                        if (hasLocalTools) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(text = assistant.localTools.size.toString())
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Lucide.Wrench,
                        contentDescription = stringResource(R.string.local_tools),
                    )
                }
            }
        }
    }

    if (showLocalToolsPicker) {
        ModalBottomSheet(
            onDismissRequest = { showLocalToolsPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.7f)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.local_tools_picker_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )

                LocalToolsPicker(
                    assistant = assistant,
                    onUpdateAssistant = onUpdateAssistant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LocalToolsPicker(
    assistant: Assistant,
    onUpdateAssistant: (Assistant) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.local_tools_picker_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        // JavaScript Engine
        item {
            LocalToolItem(
                icon = Lucide.Code,
                title = stringResource(R.string.assistant_page_local_tools_javascript_engine_title),
                description = stringResource(R.string.assistant_page_local_tools_javascript_engine_desc),
                enabled = assistant.localTools.contains(LocalToolOption.JavascriptEngine),
                onToggle = { enabled ->
                    val newTools = if (enabled) {
                        assistant.localTools + LocalToolOption.JavascriptEngine
                    } else {
                        assistant.localTools - LocalToolOption.JavascriptEngine
                    }
                    onUpdateAssistant(assistant.copy(localTools = newTools))
                }
            )
        }

        // File System
        item {
            LocalToolItem(
                icon = Lucide.FolderOpen,
                title = stringResource(R.string.assistant_page_local_tools_file_system_title),
                description = stringResource(R.string.assistant_page_local_tools_file_system_desc),
                enabled = assistant.localTools.contains(LocalToolOption.FileSystem),
                onToggle = { enabled ->
                    val newTools = if (enabled) {
                        assistant.localTools + LocalToolOption.FileSystem
                    } else {
                        assistant.localTools - LocalToolOption.FileSystem
                    }
                    onUpdateAssistant(assistant.copy(localTools = newTools))
                }
            )
        }

        // Web Fetch
        item {
            LocalToolItem(
                icon = Lucide.Globe,
                title = stringResource(R.string.assistant_page_local_tools_web_fetch_title),
                description = stringResource(R.string.assistant_page_local_tools_web_fetch_desc),
                enabled = assistant.localTools.contains(LocalToolOption.WebFetch),
                onToggle = { enabled ->
                    val newTools = if (enabled) {
                        assistant.localTools + LocalToolOption.WebFetch
                    } else {
                        assistant.localTools - LocalToolOption.WebFetch
                    }
                    onUpdateAssistant(assistant.copy(localTools = newTools))
                }
            )
        }
    }
}

@Composable
private fun LocalToolItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    ListItem(
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = title,
            )
        },
        headlineContent = {
            Text(title)
        },
        supportingContent = {
            Text(description)
        },
        trailingContent = {
            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    )
}
