package me.rerere.rikkahub.ui.pages.knowledge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Trash2
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.KnowledgeBase
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgePage(vm: KnowledgeVM = koinViewModel()) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navController = LocalNavController.current
    val toaster = LocalToaster.current
    val knowledgeBases by vm.knowledgeBases.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<KnowledgeBase?>(null) }
    var showDeleteDialog by remember { mutableStateOf<KnowledgeBase?>(null) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(text = stringResource(R.string.knowledge_base))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true }
            ) {
                Icon(Lucide.Plus, contentDescription = "Add")
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        if (knowledgeBases.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Lucide.BookOpen,
                    contentDescription = null,
                    modifier = Modifier.padding(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = stringResource(R.string.knowledge_base_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.knowledge_base_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding + PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(knowledgeBases, key = { it.id.toString() }) { base ->
                    KnowledgeBaseCard(
                        base = base,
                        onClick = {
                            navController.navigate(Screen.KnowledgeDetail(base.id.toString()))
                        },
                        onEdit = {
                            showEditDialog = base
                        },
                        onDelete = {
                            showDeleteDialog = base
                        }
                    )
                }
            }
        }
    }

    // Create Dialog
    if (showCreateDialog) {
        CreateKnowledgeBaseDialog(
            providers = settings.providers.filterIsInstance<ProviderSetting.OpenAI>(),
            onDismiss = { showCreateDialog = false },
            onCreate = { base ->
                vm.createBase(base)
                showCreateDialog = false
                toaster.show(R.string.knowledge_base_created)
            }
        )
    }

    // Edit Dialog
    showEditDialog?.let { base ->
        EditKnowledgeBaseDialog(
            base = base,
            providers = settings.providers.filterIsInstance<ProviderSetting.OpenAI>(),
            onDismiss = { showEditDialog = null },
            onUpdate = { updatedBase ->
                vm.updateBase(updatedBase)
                showEditDialog = null
                toaster.show(R.string.knowledge_base_updated)
            }
        )
    }

    // Delete Confirmation Dialog
    showDeleteDialog?.let { base ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.delete_knowledge_base)) },
            text = { Text(stringResource(R.string.delete_knowledge_base_confirm, base.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteBase(base.id)
                        showDeleteDialog = null
                        toaster.show(R.string.knowledge_base_deleted)
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun KnowledgeBaseCard(
    base: KnowledgeBase,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Lucide.BookOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = base.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!base.description.isNullOrBlank()) {
                    Text(
                        text = base.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = base.updatedAt.formatDateTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            IconButton(onClick = { showMenu = true }) {
                Icon(Lucide.EllipsisVertical, contentDescription = "More")
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit)) },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                        leadingIcon = { Icon(Lucide.Pencil, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Lucide.Trash2, null) }
                    )
                }
            }

            Icon(
                Lucide.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CreateKnowledgeBaseDialog(
    providers: List<ProviderSetting.OpenAI>,
    onDismiss: () -> Unit,
    onCreate: (KnowledgeBase) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedProvider by remember { mutableStateOf(providers.firstOrNull()) }
    var embeddingModel by remember { mutableStateOf("text-embedding-3-small") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_knowledge_base)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FormItem(label = { Text(stringResource(R.string.name)) }) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.knowledge_base_name_hint)) }
                    )
                }

                FormItem(label = { Text(stringResource(R.string.description)) }) {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        placeholder = { Text(stringResource(R.string.knowledge_base_description_hint)) }
                    )
                }

                if (providers.isNotEmpty()) {
                    FormItem(label = { Text(stringResource(R.string.embedding_provider)) }) {
                        Select(
                            options = providers,
                            selectedOption = selectedProvider,
                            onOptionSelected = { selectedProvider = it },
                            optionToString = { it?.name ?: "" },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                FormItem(label = { Text(stringResource(R.string.embedding_model)) }) {
                    OutlinedTextField(
                        value = embeddingModel,
                        onValueChange = { embeddingModel = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("text-embedding-3-small") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && selectedProvider != null) {
                        onCreate(
                            KnowledgeBase(
                                name = name.trim(),
                                description = description.trim().ifBlank { null },
                                embeddingProviderId = selectedProvider!!.id,
                                embeddingModelId = embeddingModel.trim(),
                                createdAt = Instant.now(),
                                updatedAt = Instant.now(),
                            )
                        )
                    }
                },
                enabled = name.isNotBlank() && selectedProvider != null
            ) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun EditKnowledgeBaseDialog(
    base: KnowledgeBase,
    providers: List<ProviderSetting.OpenAI>,
    onDismiss: () -> Unit,
    onUpdate: (KnowledgeBase) -> Unit,
) {
    var name by remember { mutableStateOf(base.name) }
    var description by remember { mutableStateOf(base.description ?: "") }
    var selectedProvider by remember {
        mutableStateOf(providers.find { it.id == base.embeddingProviderId } ?: providers.firstOrNull())
    }
    var embeddingModel by remember { mutableStateOf(base.embeddingModelId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_knowledge_base)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FormItem(label = { Text(stringResource(R.string.name)) }) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.knowledge_base_name_hint)) }
                    )
                }

                FormItem(label = { Text(stringResource(R.string.description)) }) {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        placeholder = { Text(stringResource(R.string.knowledge_base_description_hint)) }
                    )
                }

                if (providers.isNotEmpty()) {
                    FormItem(label = { Text(stringResource(R.string.embedding_provider)) }) {
                        Select(
                            options = providers,
                            selectedOption = selectedProvider,
                            onOptionSelected = { selectedProvider = it },
                            optionToString = { it?.name ?: "" },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                FormItem(label = { Text(stringResource(R.string.embedding_model)) }) {
                    OutlinedTextField(
                        value = embeddingModel,
                        onValueChange = { embeddingModel = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("text-embedding-3-small") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && selectedProvider != null) {
                        onUpdate(
                            base.copy(
                                name = name.trim(),
                                description = description.trim().ifBlank { null },
                                embeddingProviderId = selectedProvider!!.id,
                                embeddingModelId = embeddingModel.trim(),
                            )
                        )
                    }
                },
                enabled = name.isNotBlank() && selectedProvider != null
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun Instant.formatDateTime(): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    return formatter.format(this.atZone(ZoneId.systemDefault()))
}
