package com.gate.tracker.ui.resources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gate.tracker.data.local.entity.ResourceEntity
import com.gate.tracker.data.local.entity.ResourceType
import com.gate.tracker.data.repository.GateRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

/**
 * UI State for Resources
 */
data class ResourcesUiState(
    val resources: List<ResourceEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isEmpty: Boolean = true
)

/**
 * ViewModel for managing resources
 */
class ResourcesViewModel(
    private val repository: GateRepository,
    private val subjectId: Int,
    private val context: android.content.Context
) : ViewModel() {

    // Reactive state flow for UI
    val uiState: StateFlow<ResourcesUiState> = repository
        .getResourcesBySubject(subjectId)
        .map { resources -> calculateUiState(resources) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ResourcesUiState()
        )

    /**
     * Add a new resource
     */
    fun addResource(
        title: String,
        uri: String,
        resourceType: ResourceType,
        description: String = "",
        fileSize: Long? = null,
        driveFileId: String? = null,
        thumbnailUrl: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                if (title.isBlank()) {
                    onError("Title cannot be empty")
                    return@launch
                }
                if (uri.isBlank()) {
                    onError("URI/Link cannot be empty")
                    return@launch
                }

                
                // Create resource entity first (without drive ID)
                val resource = ResourceEntity(
                    subjectId = subjectId,
                    resourceType = resourceType,
                    title = title,
                    uri = uri,
                    description = description,
                    fileSize = fileSize,
                    driveFileId = if (resourceType == ResourceType.URL) driveFileId else null, // Use prepared ID only if URL/Folder import
                    thumbnailUrl = thumbnailUrl
                )
                
                // Add to local DB immediately
                val start = System.currentTimeMillis()
                val idLong = repository.addResource(resource)
                val localId = idLong.toInt()
                android.util.Log.d("ResourcesViewModel", "Resource added locally (ID: $localId) in ${System.currentTimeMillis() - start}ms")
                
                // Return success to UI immediately
                onSuccess()
                
                // Upload to Drive in background if needed
                if (resourceType == ResourceType.PDF || resourceType == ResourceType.IMAGE) {
                    launch(Dispatchers.IO) {
                        try {
                            // Ensure drive service is initialized
                            val initResult = repository.ensureDriveService()
                            if (initResult.isSuccess) {
                                val uriObj = android.net.Uri.parse(uri)
                                if (uriObj.scheme == "content") {
                                    val mimeType = context.contentResolver.getType(uriObj) ?: "application/octet-stream"
                                    val uploadResult = repository.uploadResourceFile(uriObj, mimeType)
                                    
                                    if (uploadResult.isSuccess) {
                                        val uploadedId = uploadResult.getOrNull()
                                        android.util.Log.d("ResourcesViewModel", "Background upload success: $uploadedId")
                                        
                                        // Update the resource with Drive ID
                                        val updatedResource = resource.copy(
                                            id = localId,
                                            driveFileId = uploadedId
                                        )
                                        repository.updateResource(updatedResource)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ResourcesViewModel", "Background upload failed", e)
                        }
                        
                        // Trigger backup of DB (JSON) after upload attempt
                        triggerBackup()
                    }
                } else {
                     // Just trigger backup for Links
                     triggerBackup()
                }
            } catch (e: Exception) {
                onError("Failed to add resource: ${e.message}")
            }
        }
    }

    /**
     * Open resource (handling download if needed)
     */
    fun openResource(
        resource: ResourceEntity,
        onLoading: (Boolean) -> Unit,
        onError: (String) -> Unit,
        onOpen: (android.net.Uri) -> Unit
    ) {
        viewModelScope.launch {
            try {
                if (resource.resourceType == ResourceType.URL) {
                    onOpen(android.net.Uri.parse(resource.uri))
                    return@launch
                }

                val uri = android.net.Uri.parse(resource.uri)
                val isContentUri = uri.scheme == "content"
                
                // If it's a content URI, check if we can open it
                if (isContentUri) {
                    try {
                        context.contentResolver.openInputStream(uri)?.close()
                        // If successful, open it
                        onOpen(uri)
                        return@launch
                    } catch (e: Exception) {
                        // File not found locally
                        android.util.Log.d("ResourcesViewModel", "Local file not found, trying download...")
                    }
                }
                
                // If we're here, we need to download from Drive
                if (resource.driveFileId != null) {
                    onLoading(true)
                    
                    // Create local file in app cache/files
                    val fileName = resource.title.replace("[^a-zA-Z0-9.-]".toRegex(), "_") + 
                        if (resource.resourceType == ResourceType.PDF) ".pdf" else ".jpg"
                        
                    val targetFile = java.io.File(context.getExternalFilesDir(null), fileName)
                    
                    // Ensure drive service is initialized (try to silence sign-in if needed)
                    val initResult = repository.ensureDriveService()
                    if (initResult.isFailure) {
                        onLoading(false)
                        onError("Not signed in to Drive. Please sign in via Settings.")
                        return@launch
                    }
                    
                    val downloadResult = repository.downloadResourceFile(resource.driveFileId, targetFile)
                    
                    onLoading(false)
                    
                    if (downloadResult.isSuccess) {
                        // Get URI for the new file
                        try {
                            val newUri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                targetFile
                            )
                            onOpen(newUri)
                        } catch (e: Exception) {
                            android.util.Log.e("ResourcesViewModel", "Error getting URI for file", e)
                            onError("Error opening file: ${e.message}")
                        }
                    } else {
                        onError("Failed to download: ${downloadResult.exceptionOrNull()?.message ?: "Unknown error"}")
                    }
                } else {
                    onError("File not found locally and not backed up to cloud")
                }
            } catch (e: Exception) {
                onLoading(false)
                onError("Error opening resource: ${e.message}")
            }
        }
    }

    /**
     * Delete a resource
     */
    fun deleteResource(
        resource: ResourceEntity,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                repository.deleteResource(resource)
                triggerBackup()
                onSuccess()
            } catch (e: Exception) {
                onError("Failed to delete resource: ${e.message}")
            }
        }
    }

    /**
     * Trigger auto-backup
     */
    private suspend fun triggerBackup() {
        try {
            android.util.Log.d("GATE_TRACKER", "triggerBackup called for subjectId: $subjectId")
            val subject = repository.getSubjectById(subjectId)
            
            if (subject != null) {
                android.util.Log.d("GATE_TRACKER", "Subject found: ${subject.name}, BranchId: ${subject.branchId}")
                com.gate.tracker.data.backup.AutoBackupWorker.scheduleBackup(
                    context = context,
                    branchId = subject.branchId,
                    branchName = getBranchName(subject.branchId)
                )
                android.util.Log.d("GATE_TRACKER", "Backup scheduled successfully")
            } else {
                android.util.Log.e("GATE_TRACKER", "Subject is NULL for id: $subjectId - Backup NOT scheduled")
            }
        } catch (e: Exception) {
            android.util.Log.e("GATE_TRACKER", "Exception in triggerBackup", e)
        }
    }

    private fun getBranchName(branchId: Int): String {
        return when (branchId) {
            1 -> "CS"
            2 -> "EC"
            3 -> "EE"
            4 -> "ME"
            5 -> "CE"
            6 -> "DA"
            else -> "CS"
        }
    }

    /**
     * Calculate UI state from resource list
     */
    private fun calculateUiState(resources: List<ResourceEntity>): ResourcesUiState {
        return ResourcesUiState(
            resources = resources,
            isLoading = false,
            isEmpty = resources.isEmpty()
        )
    }
    // State for fetched drive title
    // State for fetched drive metadata



    fun importDriveFolder(folderId: String) {
        viewModelScope.launch {
            try {
                val result = com.gate.tracker.data.drive.DriveManager(context).listFilesInFolder(folderId)
                if (result.isSuccess) {
                    val files = result.getOrNull() ?: emptyList()
                    var addedCount = 0
                    
                    files.forEach { file ->
                        val resourceType = when {
                            file.mimeType == "application/pdf" -> ResourceType.PDF
                            file.mimeType.startsWith("image/") -> ResourceType.IMAGE
                            file.mimeType == "application/vnd.google-apps.folder" -> return@forEach // Skip nested folders for now
                            else -> ResourceType.URL // Treat others as links
                        }
                        
                        val uri = if (resourceType == ResourceType.URL && file.webViewLink != null) {
                            file.webViewLink
                        } else {
                            // strictly use webLink for everything or construct drive url
                             file.webViewLink ?: "https://drive.google.com/file/d/${file.id}/view"
                        }
                        
                        val resource = ResourceEntity(
                            subjectId = subjectId,
                            resourceType = resourceType,
                            title = file.name,
                            uri = uri,
                            description = "Imported from Drive Folder",
                            driveFileId = file.id,
                            thumbnailUrl = file.thumbnailLink
                        )
                        
                        repository.addResource(resource)
                        addedCount++
                    }
                    
                    if (addedCount > 0) {
                        triggerBackup()
                        // Notify success (optional, maybe via a shared flow event)
                        // For now we rely on UI update via Flow
                    }
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    // ==========================================
    // Drive Browser Logic
    // ==========================================

    private val _driveBrowserFiles = MutableStateFlow<List<com.gate.tracker.data.drive.DriveManager.DriveFile>>(emptyList())
    val driveBrowserFiles = _driveBrowserFiles.asStateFlow()

    private val _driveBreadcrumbs = MutableStateFlow<List<Pair<String, String>>>(listOf("root" to "My Drive"))
    val driveBreadcrumbs = _driveBreadcrumbs.asStateFlow()

    private val _isDriveLoading = MutableStateFlow(false)
    val isDriveLoading = _isDriveLoading.asStateFlow()

    fun loadDriveFiles(folderId: String = "root") {
        _isDriveLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val result = com.gate.tracker.data.drive.DriveManager(context).listFilesInFolder(folderId)
            _isDriveLoading.value = false
            if (result.isSuccess) {
                _driveBrowserFiles.value = result.getOrNull() ?: emptyList()
            } else {
                 _driveBrowserFiles.value = emptyList()
            }
        }
    }

    fun navigateDriveWait(folderId: String, folderName: String) {
        val currentCrumbs = _driveBreadcrumbs.value.toMutableList()
        // Check if we are jumping back to an existing crumb
        val index = currentCrumbs.indexOfFirst { it.first == folderId }
        
        if (index != -1) {
            // Cut off everything after
            _driveBreadcrumbs.value = currentCrumbs.subList(0, index + 1)
        } else {
            // Append new crumb
            currentCrumbs.add(folderId to folderName)
            _driveBreadcrumbs.value = currentCrumbs
        }
        
        loadDriveFiles(folderId)
    }

    fun navigateDriveBack(): Boolean {
        val currentCrumbs = _driveBreadcrumbs.value
        if (currentCrumbs.size > 1) {
            val parent = currentCrumbs[currentCrumbs.size - 2]
            _driveBreadcrumbs.value = currentCrumbs.dropLast(1)
            loadDriveFiles(parent.first)
            return true
        }
        return false
    }
}
