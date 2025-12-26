package me.rerere.rikkahub.ui.pages.setting

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Languages
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.SearchCheck
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Wrench
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.InputButtonItem
import me.rerere.rikkahub.data.datastore.InputButtonType
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.PermissionNotification
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode
import me.rerere.rikkahub.ui.hooks.rememberSharedPreferenceBoolean
import me.rerere.rikkahub.ui.pages.setting.components.PresetThemeButtonGroup
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingDisplayPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }
    var amoledDarkMode by rememberAmoledDarkMode()

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings(
            settings.copy(
                displaySetting = setting
            )
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val permissionState = rememberPermissionState(
        permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setOf(
            PermissionNotification
        ) else emptySet(),
    )
    PermissionManager(permissionState = permissionState)

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(stringResource(R.string.setting_display_page_title))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .consumeWindowInsets(contentPadding),
            contentPadding = contentPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            stickyHeader {
                Text(
                    text = stringResource(R.string.setting_page_theme_setting),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            item {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text(stringResource(R.string.setting_page_dynamic_color))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.setting_page_dynamic_color_desc))
                    },
                    trailingContent = {
                        Switch(
                            checked = settings.dynamicColor,
                            onCheckedChange = {
                                vm.updateSettings(settings.copy(dynamicColor = it))
                            },
                        )
                    },
                )
            }

            if (!settings.dynamicColor) {
                item {
                    PresetThemeButtonGroup(
                        themeId = settings.themeId,
                        modifier = Modifier.fillMaxWidth(),
                        onChangeTheme = {
                            vm.updateSettings(settings.copy(themeId = it))
                        }
                    )
                }
            }

            item {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text(stringResource(R.string.setting_display_page_amoled_dark_mode_title))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.setting_display_page_amoled_dark_mode_desc))
                    },
                    trailingContent = {
                        Switch(
                            checked = amoledDarkMode,
                            onCheckedChange = {
                                amoledDarkMode = it
                            }
                        )
                    },
                )
            }

            stickyHeader {
                Text(
                    text = stringResource(R.string.setting_page_basic_settings),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            item {
                var createNewConversationOnStart by rememberSharedPreferenceBoolean(
                    "create_new_conversation_on_start",
                    true
                )
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text(stringResource(R.string.setting_display_page_create_new_conversation_on_start_title))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.setting_display_page_create_new_conversation_on_start_desc))
                    },
                    trailingContent = {
                        Switch(
                            checked = createNewConversationOnStart,
                            onCheckedChange = {
                                createNewConversationOnStart = it
                            }
                        )
                    },
                )
            }

            item {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text(stringResource(R.string.setting_display_page_show_updates_title))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.setting_display_page_show_updates_desc))
                    },
                    trailingContent = {
                        Switch(
                            checked = displaySetting.showUpdates,
                            onCheckedChange = {
                                updateDisplaySetting(displaySetting.copy(showUpdates = it))
                            }
                        )
                    },
                )
            }

            item {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text(stringResource(R.string.setting_display_page_notification_message_generated))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.setting_display_page_notification_message_generated_desc))
                    },
                    trailingContent = {
                        Switch(
                            checked = displaySetting.enableNotificationOnMessageGeneration,
                            onCheckedChange = {
                                if (it && !permissionState.allPermissionsGranted) {
                                    // 请求权限
                                    permissionState.requestPermissions()
                                }
                                updateDisplaySetting(displaySetting.copy(enableNotificationOnMessageGeneration = it))
                            }
                        )
                    },
                )
            }

//            item {
//                ListItem(
//                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
//                    headlineContent = {
//                        Text(stringResource(R.string.setting_display_page_developer_mode))
//                    },
//                    supportingContent = {
//                        Text(stringResource(R.string.setting_display_page_developer_mode_desc))
//                    },
//                    trailingContent = {
//                        Switch(
//                            checked = settings.developerMode,
//                            onCheckedChange = {
//                                vm.updateSettings(settings.copy(developerMode = it))
//                            }
//                        )
//                    },
//                )
//            }

            stickyHeader {
                Text(
                    text = stringResource(R.string.setting_page_chat_settings),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            item {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text(stringResource(R.string.setting_display_page_show_user_avatar_title))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.setting_display_page_show_user_avatar_desc))
                    },
                    trailingContent = {
                        Switch(
                            checked = displaySetting.showUserAvatar,
                            onCheckedChange = {
                                updateDisplaySetting(displaySetting.copy(showUserAvatar = it))
                            }
                        )
                    },
                )
            }

            item {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text(stringResource(R.string.setting_display_page_chat_list_model_icon_title))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.setting_display_page_chat_list_model_icon_desc))
                    },
                    trailingContent = {
                        Switch(
                            checked = displaySetting.showModelIcon,
                            onCheckedChange = {
                                updateDisplaySetting(displaySetting.copy(showModelIcon = it))
                            }
                        )
                    },
                )
            }

            item {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text(stringResource(R.string.setting_display_page_show_model_name_title))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.setting_display_page_show_model_name_desc))
                    },
                    trailingContent = {
                        Switch(
                            checked = displaySetting.showModelName,
                            onCheckedChange = {
                                updateDisplaySetting(displaySetting.copy(showModelName = it))
                            }
                        )
                    },
                )
            }

            item {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text(stringResource(R.string.setting_display_page_show_token_usage_title))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.setting_display_page_show_token_usage_desc))
                    },
                    trailingContent = {
                        Switch(
                            checked = displaySetting.showTokenUsage,
                            onCheckedChange = {
                                updateDisplaySetting(displaySetting.copy(showTokenUsage = it))
                            }
                        )
                    },
                )
            }

            item {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text(stringResource(R.string.setting_display_page_auto_collapse_thinking_title))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.setting_display_page_auto_collapse_thinking_desc))
                    },
                    trailingContent = {
                        Switch(
                            checked = displaySetting.autoCloseThinking,
                            onCheckedChange = {
                                updateDisplaySetting(displaySetting.copy(autoCloseThinking = it))
                            }
                        )
                    },
                )
            }

            item {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text(stringResource(R.string.setting_display_page_show_message_jumper_title))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.setting_display_page_show_message_jumper_desc))
                    },
                    trailingContent = {
                        Switch(
                            checked = displaySetting.showMessageJumper,
                            onCheckedChange = {
                                updateDisplaySetting(displaySetting.copy(showMessageJumper = it))
                            }
                        )
                    },
                )
            }

            if (displaySetting.showMessageJumper) {
                item {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = {
                            Text(stringResource(R.string.setting_display_page_message_jumper_position_title))
                        },
                        supportingContent = {
                            Text(stringResource(R.string.setting_display_page_message_jumper_position_desc))
                        },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.messageJumperOnLeft,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(messageJumperOnLeft = it))
                                }
                            )
                        },
                    )
                }
            }

            item {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text(stringResource(R.string.setting_display_page_enable_message_generation_haptic_effect_title))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.setting_display_page_enable_message_generation_haptic_effect_desc))
                    },
                    trailingContent = {
                        Switch(
                            checked = displaySetting.enableMessageGenerationHapticEffect,
                            onCheckedChange = {
                                updateDisplaySetting(displaySetting.copy(enableMessageGenerationHapticEffect = it))
                            }
                        )
                    },
                )
            }

            item {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text(stringResource(R.string.setting_display_page_skip_crop_image_title))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.setting_display_page_skip_crop_image_desc))
                    },
                    trailingContent = {
                        Switch(
                            checked = displaySetting.skipCropImage,
                            onCheckedChange = {
                                updateDisplaySetting(displaySetting.copy(skipCropImage = it))
                            }
                        )
                    },
                )
            }

            item {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text(stringResource(R.string.setting_display_page_code_block_auto_wrap_title))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.setting_display_page_code_block_auto_wrap_desc))
                    },
                    trailingContent = {
                        Switch(
                            checked = displaySetting.codeBlockAutoWrap,
                            onCheckedChange = {
                                updateDisplaySetting(displaySetting.copy(codeBlockAutoWrap = it))
                            }
                        )
                    },
                )
            }

            item {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text(stringResource(R.string.setting_display_page_code_block_auto_collapse_title))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.setting_display_page_code_block_auto_collapse_desc))
                    },
                    trailingContent = {
                        Switch(
                            checked = displaySetting.codeBlockAutoCollapse,
                            onCheckedChange = {
                                updateDisplaySetting(displaySetting.copy(codeBlockAutoCollapse = it))
                            }
                        )
                    },
                )
            }

            stickyHeader {
                Text(
                    text = stringResource(R.string.setting_display_page_input_bar_buttons_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            item {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text(stringResource(R.string.setting_display_page_input_bar_buttons_headline))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.setting_display_page_input_bar_buttons_desc))
                    },
                )
            }

            items(displaySetting.chatInputButtons.buttons, key = { it.type }) { buttonItem ->
                val index = displaySetting.chatInputButtons.buttons.indexOf(buttonItem)
                val isFirst = index == 0
                val isLast = index == displaySetting.chatInputButtons.buttons.size - 1

                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = getButtonIcon(buttonItem.type),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    headlineContent = {
                        Text(stringResource(getButtonLabel(buttonItem.type)))
                    },
                    trailingContent = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (!isFirst) {
                                        val buttons = displaySetting.chatInputButtons.buttons.toMutableList()
                                        buttons.removeAt(index)
                                        buttons.add(index - 1, buttonItem)
                                        updateDisplaySetting(
                                            displaySetting.copy(
                                                chatInputButtons = displaySetting.chatInputButtons.copy(
                                                    buttons = buttons
                                                )
                                            )
                                        )
                                    }
                                },
                                enabled = !isFirst
                            ) {
                                Icon(
                                    imageVector = Lucide.ChevronUp,
                                    contentDescription = "Move up",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (!isLast) {
                                        val buttons = displaySetting.chatInputButtons.buttons.toMutableList()
                                        buttons.removeAt(index)
                                        buttons.add(index + 1, buttonItem)
                                        updateDisplaySetting(
                                            displaySetting.copy(
                                                chatInputButtons = displaySetting.chatInputButtons.copy(
                                                    buttons = buttons
                                                )
                                            )
                                        )
                                    }
                                },
                                enabled = !isLast
                            ) {
                                Icon(
                                    imageVector = Lucide.ChevronDown,
                                    contentDescription = "Move down",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Switch(
                                checked = buttonItem.enabled,
                                onCheckedChange = { enabled ->
                                    val buttons = displaySetting.chatInputButtons.buttons.map {
                                        if (it.type == buttonItem.type) {
                                            it.copy(enabled = enabled)
                                        } else {
                                            it
                                        }
                                    }
                                    updateDisplaySetting(
                                        displaySetting.copy(
                                            chatInputButtons = displaySetting.chatInputButtons.copy(
                                                buttons = buttons
                                            )
                                        )
                                    )
                                }
                            )
                        }
                    },
                    modifier = Modifier.animateItem()
                )
            }

            item {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.setting_display_page_font_size_title))
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Slider(
                        value = displaySetting.fontSizeRatio,
                        onValueChange = {
                            updateDisplaySetting(displaySetting.copy(fontSizeRatio = it))
                        },
                        valueRange = 0.5f..2f,
                        steps = 11,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(displaySetting.fontSizeRatio * 100).toInt()}%",
                    )
                }
                MarkdownBlock(
                    content = stringResource(R.string.setting_display_page_font_size_preview),
                    modifier = Modifier.padding(8.dp),
                    style = LocalTextStyle.current.copy(
                        fontSize = LocalTextStyle.current.fontSize * displaySetting.fontSizeRatio,
                        lineHeight = LocalTextStyle.current.lineHeight * displaySetting.fontSizeRatio,
                    )
                )
            }
        }
    }
}

@Composable
private fun InputBarIconToggle(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        FilterChip(
            selected = checked,
            onClick = { onCheckedChange(!checked) },
            label = {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(24.dp)
                )
            },
            shape = CircleShape
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun getButtonIcon(type: InputButtonType): ImageVector {
    return when (type) {
        InputButtonType.MODEL_SELECTOR -> Lucide.Sparkles
        InputButtonType.SEARCH_PICKER -> Lucide.SearchCheck
        InputButtonType.REASONING_BUTTON -> Lucide.Sparkles
        InputButtonType.MCP_PICKER -> Lucide.Server
        InputButtonType.LOCAL_TOOLS_PICKER -> Lucide.Wrench
        InputButtonType.TRANSLATE_PICKER -> Lucide.Languages
    }
}

@Composable
private fun getButtonLabel(type: InputButtonType): Int {
    return when (type) {
        InputButtonType.MODEL_SELECTOR -> R.string.setting_display_page_input_bar_model
        InputButtonType.SEARCH_PICKER -> R.string.setting_display_page_input_bar_search
        InputButtonType.REASONING_BUTTON -> R.string.setting_display_page_input_bar_thinking
        InputButtonType.MCP_PICKER -> R.string.setting_display_page_input_bar_mcp
        InputButtonType.LOCAL_TOOLS_PICKER -> R.string.setting_display_page_input_bar_tools
        InputButtonType.TRANSLATE_PICKER -> R.string.setting_display_page_input_bar_translate
    }
}

