package me.rerere.rikkahub.ui.pages.knowledge

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.File
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.Loader
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.StickyNote
import com.composables.icons.lucide.Trash2
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.knowledge.loader.DocumentLoaderFactory
import me.rerere.rikkahub.data.model.KnowledgeBase
import me.rerere.rikkahub.data.model.KnowledgeItem
import me.rerere.rikkahub.data.model.KnowledgeItemType
import me.rerere.rikkahub.data.model.ProcessingStatus
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import java.io.File
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeDetailPage(
    baseId: String,
    vm: KnowledgeVM = koinViewModel()
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val knowledgeBases by vm.knowledgeBases.collectAsStateWithLifecycle()
    val items by vm.currentItems.collectAsStateWithLifecycle()
    val isProcessing by vm.isProcessing.collectAsStateWithLifecycle()
    val searchResults by vm.searchResults.collectAsStateWithLifecycle()

    val baseUuid = remember { Uuid.parse(baseId) }
    val base = remember(knowledgeBases) {
        knowledgeBases.find { it.id == baseUuid }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var showAddUrlDialog by remember { mutableStateOf(false) }
    var showEditNoteDialog by remember { mutableStateOf<KnowledgeItem?>(null) }
    var showEditUrlDialog by remember { mutableStateOf<KnowledgeItem?>(null) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it)
            val fileName = it.lastPathSegment ?: "document.txt"
            val tempFile = File(context.cacheDir, fileName)
            inputStream?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            vm.addFile(baseUuid, tempFile)
            toaster.show(R.string.file_added)
        }
    }

    LaunchedEffect(baseUuid) {
        vm.selectBase(baseUuid)
    }

    if (base == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val tabs = listOf(
        stringResource(R.string.all),
        stringResource(R.string.files),
        stringResource(R.string.notes),
        stringResource(R.string.urls)
    )

    val filteredItems = remember(items, selectedTab) {
        when (selectedTab) {
            1 -> items.filter { it.type == KnowledgeItemType.FILE }
            2 -> items.filter { it.type == KnowledgeItemType.NOTE }
            3 -> items.filter { it.type == KnowledgeItemType.URL }
            else -> items
        }
    }

    val pendingCount = remember(items) {
        items.count { it.status == ProcessingStatus.PENDING }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = base.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (items.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.knowledge_item_count, items.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { showSearchDialog = true }) {
                        Icon(Lucide.Search, contentDescription = "Search")
                    }
                    if (pendingCount > 0) {
                        TextButton(
                            onClick = { vm.processAllPending(baseUuid) },
                            enabled = !isProcessing
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Lucide.Play, null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.process_all, pendingCount))
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { showAddMenu = true }
                ) {
                    Icon(Lucide.Plus, contentDescription = "Add")
                }
                DropdownMenu(
                    expanded = showAddMenu,
                    onDismissRequest = { showAddMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_file)) },
                        onClick = {
                            showAddMenu = false
                            val mimeTypes = DocumentLoaderFactory.getSupportedExtensions()
                                .joinToString("|") { "text/$it" }
                            filePicker.launch("*/*")
                        },
                        leadingIcon = { Icon(Lucide.File, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_note)) },
                        onClick = {
                            showAddMenu = false
                            showAddNoteDialog = true
                        },
                        leadingIcon = { Icon(Lucide.StickyNote, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_url)) },
                        onClick = {
                            showAddMenu = false
                            showAddUrlDialog = true
                        },
                        leadingIcon = { Icon(Lucide.Globe, null) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isProcessing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            SecondaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Lucide.FileText,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.no_items),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredItems, key = { it.id.toString() }) { item ->
                        KnowledgeItemCard(
                            item = item,
                            onProcess = { vm.processItem(item) },
                            onEdit = {
                                when (item.type) {
                                    KnowledgeItemType.NOTE -> showEditNoteDialog = item
                                    KnowledgeItemType.URL -> showEditUrlDialog = item
                                    KnowledgeItemType.FILE -> {} // Files cannot be edited
                                }
                            },
                            onDelete = { vm.removeItem(item.id) },
                            isProcessing = isProcessing
                        )
                    }
                }
            }
        }
    }

    // Add Note Dialog
    if (showAddNoteDialog) {
        AddNoteDialog(
            onDismiss = { showAddNoteDialog = false },
            onAdd = { name, content ->
                vm.addNote(baseUuid, name, content)
                showAddNoteDialog = false
                toaster.show(R.string.note_added)
            }
        )
    }

    // Edit Note Dialog
    showEditNoteDialog?.let { item ->
        EditNoteDialog(
            item = item,
            onDismiss = { showEditNoteDialog = null },
            onUpdate = { name, content ->
                vm.updateNote(item.id, baseUuid, name, content)
                showEditNoteDialog = null
                toaster.show(R.string.note_updated)
            }
        )
    }

    // Add URL Dialog
    if (showAddUrlDialog) {
        AddUrlDialog(
            onDismiss = { showAddUrlDialog = false },
            onAdd = { url, name ->
                vm.addUrl(baseUuid, url, name)
                showAddUrlDialog = false
                toaster.show(R.string.url_added)
            }
        )
    }

    // Edit URL Dialog
    showEditUrlDialog?.let { item ->
        EditUrlDialog(
            item = item,
            onDismiss = { showEditUrlDialog = null },
            onUpdate = { url, name ->
                vm.updateUrl(item.id, baseUuid, url, name)
                showEditUrlDialog = null
                toaster.show(R.string.url_updated)
            }
        )
    }

    // Search Dialog
    if (showSearchDialog) {
        SearchDialog(
            searchResults = searchResults,
            onSearch = { query -> vm.search(baseUuid, query) },
            onDismiss = {
                showSearchDialog = false
                vm.clearSearchResults()
            }
        )
    }
}

@Composable
private fun KnowledgeItemCard(
    item: KnowledgeItem,
    onProcess: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    isProcessing: Boolean,
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = item.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusChip(status = item.status)
                    if (item.errorMessage != null && item.status == ProcessingStatus.FAILED) {
                        Text(
                            text = item.errorMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            leadingContent = {
                Icon(
                    imageVector = when (item.type) {
                        KnowledgeItemType.FILE -> Lucide.File
                        KnowledgeItemType.NOTE -> Lucide.StickyNote
                        KnowledgeItemType.URL -> Lucide.Globe
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingContent = {
                Row {
                    if (item.status == ProcessingStatus.PENDING || item.status == ProcessingStatus.FAILED) {
                        IconButton(
                            onClick = onProcess,
                            enabled = !isProcessing
                        ) {
                            Icon(Lucide.Play, contentDescription = "Process")
                        }
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Lucide.EllipsisVertical, contentDescription = "More")
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            if (item.type != KnowledgeItemType.FILE) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.edit)) },
                                    onClick = {
                                        showMenu = false
                                        onEdit()
                                    },
                                    leadingIcon = { Icon(Lucide.Pencil, null) }
                                )
                            }
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
                }
            }
        )
    }
}

@Composable
private fun StatusChip(status: ProcessingStatus) {
    val (icon, color, text) = when (status) {
        ProcessingStatus.PENDING -> Triple(
            Lucide.Clock,
            MaterialTheme.colorScheme.tertiary,
            stringResource(R.string.status_pending)
        )
        ProcessingStatus.PROCESSING -> Triple(
            Lucide.Loader,
            MaterialTheme.colorScheme.primary,
            stringResource(R.string.status_processing)
        )
        ProcessingStatus.COMPLETED -> Triple(
            Lucide.CircleCheck,
            MaterialTheme.colorScheme.primary,
            stringResource(R.string.status_completed)
        )
        ProcessingStatus.FAILED -> Triple(
            Lucide.CircleAlert,
            MaterialTheme.colorScheme.error,
            stringResource(R.string.status_failed)
        )
    }

    FilterChip(
        selected = false,
        onClick = {},
        label = { Text(text, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(14.dp), tint = color) }
    )
}

@Composable
private fun AddNoteDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, content: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_note)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FormItem(label = { Text(stringResource(R.string.name)) }) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                FormItem(label = { Text(stringResource(R.string.content)) }) {
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        maxLines = 10
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name, content) },
                enabled = name.isNotBlank() && content.isNotBlank()
            ) {
                Text(stringResource(R.string.add))
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
private fun EditNoteDialog(
    item: KnowledgeItem,
    onDismiss: () -> Unit,
    onUpdate: (name: String, content: String) -> Unit,
) {
    var name by remember { mutableStateOf(item.name) }
    var content by remember { mutableStateOf(item.content ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_note)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FormItem(label = { Text(stringResource(R.string.name)) }) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                FormItem(label = { Text(stringResource(R.string.content)) }) {
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        maxLines = 10
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onUpdate(name, content) },
                enabled = name.isNotBlank() && content.isNotBlank()
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

@Composable
private fun AddUrlDialog(
    onDismiss: () -> Unit,
    onAdd: (url: String, name: String?) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_url)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FormItem(label = { Text("URL") }) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("https://example.com") }
                    )
                }
                FormItem(label = { Text(stringResource(R.string.name) + " (" + stringResource(R.string.optional) + ")") }) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(url, name.ifBlank { null }) },
                enabled = url.isNotBlank()
            ) {
                Text(stringResource(R.string.add))
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
private fun EditUrlDialog(
    item: KnowledgeItem,
    onDismiss: () -> Unit,
    onUpdate: (url: String, name: String?) -> Unit,
) {
    var url by remember { mutableStateOf(item.url ?: "") }
    var name by remember { mutableStateOf(item.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_url)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FormItem(label = { Text("URL") }) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("https://example.com") }
                    )
                }
                FormItem(label = { Text(stringResource(R.string.name) + " (" + stringResource(R.string.optional) + ")") }) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onUpdate(url, name.ifBlank { null }) },
                enabled = url.isNotBlank()
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

@Composable
private fun SearchDialog(
    searchResults: List<me.rerere.rikkahub.data.model.KnowledgeSearchResult>,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.search_knowledge_base)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_query_hint)) },
                    trailingIcon = {
                        IconButton(
                            onClick = { onSearch(query) },
                            enabled = query.isNotBlank()
                        ) {
                            Icon(Lucide.Search, null)
                        }
                    }
                )

                if (searchResults.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.search_results, searchResults.size),
                        style = MaterialTheme.typography.labelMedium
                    )
                    LazyColumn(
                        modifier = Modifier.height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchResults) { result ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = result.item?.name ?: "Unknown",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "%.2f".format(result.score),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = result.chunk.content,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
