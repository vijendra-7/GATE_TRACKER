package com.gate.tracker.ui.resources

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gate.tracker.data.local.entity.ResourceEntity
import com.gate.tracker.data.local.entity.ResourceType

/**
 * Resources tab content showing all resources for a subject
 */
@Composable
fun ResourcesContent(
    viewModel: ResourcesViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var pendingResourceType by remember { mutableStateOf<ResourceType?>(null) }
    var resourceToDelete by remember { mutableStateOf<ResourceEntity?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    // File picker launcher
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedFileUri = uri
        // Take persistable permission for the URI
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                // Permission might not be available, but we can still try to use it
                android.util.Log.w("ResourcesContent", "Could not take persistable permission", e)
            }
            
            // Extract filename from URI
            try {
                context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    cursor.moveToFirst()
                    selectedFileName = cursor.getString(nameIndex)
                        ?.removeSuffix(".pdf")
                        ?.removeSuffix(".PDF")
                        ?.removeSuffix(".png")
                        ?.removeSuffix(".PNG")
                        ?.removeSuffix(".jpg")
                        ?.removeSuffix(".JPG")
                        ?.removeSuffix(".jpeg")
                        ?.removeSuffix(".JPEG")
                }
            } catch (e: Exception) {
                selectedFileName = null
            }
        }
    }
    
    // Show snackbar
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.isLoading) {
            // Loading
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (uiState.isEmpty) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No resources yet",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Add PDFs, links, or images to access them here",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Resource")
                }
            }
        } else {
            // Resources list
            ResourceList(
                resources = uiState.resources,
                onResourceClick = { /* Handled by viewModel.openResource */ },
                onDeleteClick = { resource ->
                    resourceToDelete = resource
                    showDeleteDialog = true
                },
                viewModel = viewModel
            )
        }

        // Floating Action Button
        if (!uiState.isLoading) {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Resource")
            }
        }
        
        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp) // Space for FAB
        )
    }
    

    // Add Resource Dialog
    if (showAddDialog) {
        AddResourceDialog(
            selectedFileUri = selectedFileUri?.toString(),
            selectedFileName = selectedFileName,
            onDismiss = {
                showAddDialog = false
                selectedFileUri = null
                selectedFileName = null
                pendingResourceType = null
                viewModel.resetFetchedMetadata()
            },
            resolvedDriveMetadata = viewModel.fetchedDriveMetadata.collectAsState().value,
            onCheckUrlMetadata = { },
            onConfirm = { title, uri, resourceType, thumbnailUrl ->
                val finalUri = if (resourceType == ResourceType.URL) {
                    uri
                } else {
                    selectedFileUri?.toString() ?: uri
                }
                
                // Get file size for PDFs and images
                val fileSize = if (resourceType != ResourceType.URL && selectedFileUri != null) {
                    try {
                        context.contentResolver.openInputStream(selectedFileUri!!)?.use {
                            it.available().toLong()
                        }
                    } catch (e: Exception) {
                        null
                    }
                } else null
                
                viewModel.addResource(
                    title = title,
                    uri = finalUri,
                    resourceType = resourceType,
                    description = "",
                    fileSize = fileSize,
                    thumbnailUrl = thumbnailUrl,
                    onSuccess = {
                        showAddDialog = false
                        selectedFileUri = null
                        selectedFileName = null
                        snackbarMessage = "Resource added successfully"
                    },
                    onError = { error ->
                        snackbarMessage = error
                    }
                )
            },
            onPickFile = { resourceType ->
                pendingResourceType = resourceType
                val mimeType = when (resourceType) {
                    ResourceType.PDF -> "application/pdf"
                    ResourceType.IMAGE -> "image/*"
                    else -> "*/*"
                }
                filePicker.launch(mimeType)
            }
        )
    }
    
    // Delete Confirmation Dialog
    if (showDeleteDialog && resourceToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                resourceToDelete = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete Resource?") },
            text = {
                Text("Are you sure you want to delete \"${resourceToDelete!!.title}\"? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        resourceToDelete?.let { resource ->
                            viewModel.deleteResource(
                                resource = resource,
                                onSuccess = {
                                    snackbarMessage = "Resource deleted"
                                    showDeleteDialog = false
                                    resourceToDelete = null
                                },
                                onError = { error ->
                                    snackbarMessage = error
                                }
                            )
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    resourceToDelete = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ResourceList(
    resources: List<ResourceEntity>,
    onResourceClick: (ResourceEntity) -> Unit,
    onDeleteClick: (ResourceEntity) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ResourcesViewModel? = null
) {
    val context = LocalContext.current
    var isOpeningFile by remember { mutableStateOf(false) }
    
    if (isOpeningFile) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismiss */ },
            title = { Text("Opening Resource") },
            text = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Downloading file...")
                }
            },
            confirmButton = {}
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(resources) { resource ->
            ResourceItem(
                resource = resource,
                onClick = { 
                    if (viewModel != null) {
                        viewModel.openResource(
                            resource = resource,
                            onLoading = { isLoading -> isOpeningFile = isLoading },
                            onError = { error -> 
                                android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_SHORT).show()
                            },
                            onOpen = { uri ->
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        if (resource.resourceType == ResourceType.URL) {
                                            data = uri
                                        } else {
                                            val mimeType = if (resource.resourceType == ResourceType.PDF) "application/pdf" else "image/*"
                                            setDataAndType(uri, mimeType)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "No app found to open this file", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    } else {
                        // Fallback logic
                        onResourceClick(resource)
                    }
                },
                onDeleteClick = { onDeleteClick(resource) }
            )
        }
        
        // Bottom padding for FAB
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourceItem(
    resource: ResourceEntity,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon or Thumbnail
            if (resource.thumbnailUrl != null) {
                // Determine icon fallback based on type
                val fallbackIcon = when (resource.resourceType) {
                    ResourceType.PDF -> Icons.Default.PictureAsPdf
                    ResourceType.URL -> Icons.Default.Link
                    ResourceType.IMAGE -> Icons.Default.Image
                }
                
                coil.compose.AsyncImage(
                    model = resource.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = when (resource.resourceType) {
                        ResourceType.PDF -> Icons.Default.PictureAsPdf
                        ResourceType.URL -> Icons.Default.Link
                        ResourceType.IMAGE -> Icons.Default.Image
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = resource.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (resource.description.isNotEmpty()) {
                    Text(
                        text = resource.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                
                // Show size for files
                if (resource.fileSize != null) {
                    Text(
                        text = formatFileSize(resource.fileSize),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Delete button
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}

private fun formatFileSize(size: Long): String {
    val kb = size / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format("%.2f MB", mb)
        kb >= 1.0 -> String.format("%.2f KB", kb)
        else -> "$size Bytes"
    }
}
