package com.gate.tracker.data.drive

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Represents a backup file stored in Google Drive
 */
data class DriveBackupFile(
    val id: String,
    val name: String,
    val createdTime: Long,
    val size: Long
)

/**
 * Manages Google Drive operations for backup/restore
 */
class DriveManager(private val context: Context) {
    
    companion object {
        private const val APP_FOLDER_NAME = "GATE_Tracker_Backups"
        private const val BACKUP_MIME_TYPE = "application/json"
    }
    
    private var driveService: Drive? = null
    private var signInClient: GoogleSignInClient? = null
    
    /**
     * Get Google Sign-In client
     */
    fun getSignInClient(): GoogleSignInClient {
        if (signInClient == null) {
            val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(DriveScopes.DRIVE_FILE))
                .build()
            
            signInClient = GoogleSignIn.getClient(context, signInOptions)
        }
        return signInClient!!
    }
    
    /**
     * Get sign-in intent for activity result launcher
     */
    fun getSignInIntent(): Intent {
        return getSignInClient().signInIntent
    }
    
    /**
     * Initialize Drive service with signed-in account
     */
    fun initializeDriveService(account: GoogleSignInAccount): Result<Unit> {
        return try {
            val credential = GoogleAccountCredential.usingOAuth2(
                context,
                listOf(DriveScopes.DRIVE_FILE)
            )
            credential.selectedAccount = account.account
            
            driveService = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            )
                .setApplicationName("GATE Tracker")
                .build()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to initialize Drive service: ${e.message}"))
        }
    }
    
    /**
     * Check if user is currently signed in
     */
    fun isSignedIn(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        android.util.Log.d("DriveManager", "isSignedIn check: account=${account?.email}, driveService=${driveService != null}")
        return account != null
    }

    /**
     * Get the display name of the signed-in user
     */
    fun getUserName(): String? {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account?.displayName
    }
    
    /**
     * Get currently signed-in account
     */
    fun getSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }
    
    /**
     * Sign out from Google account
     */
    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            getSignInClient().signOut().await()
            driveService = null
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to sign out: ${e.message}"))
        }
    }
    
    /**
     * Ensure Drive service is initialized
     * Attempts to sign in silently if needed
     */
    suspend fun ensureInitialized(): Result<Unit> = withContext(Dispatchers.IO) {
        if (driveService != null) return@withContext Result.success(Unit)
        
        val account = getSignedInAccount()
        if (account != null) {
            return@withContext initializeDriveService(account)
        }
        
        Result.failure(Exception("User not signed in"))
    }

    /**
     * Get or create app folder in Google Drive
     */
    private suspend fun getOrCreateAppFolder(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val service = driveService ?: return@withContext Result.failure(
                Exception("Drive service not initialized. Please sign in first.")
            )
            
            // Search for existing folder
            val query = "name='$APP_FOLDER_NAME' and mimeType='application/vnd.google-apps.folder' and trashed=false"
            val result = service.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()
            
            if (result.files.isNotEmpty()) {
                return@withContext Result.success(result.files[0].id)
            }
            
            // Create folder if it doesn't exist
            val folderMetadata = com.google.api.services.drive.model.File()
            folderMetadata.name = APP_FOLDER_NAME
            folderMetadata.mimeType = "application/vnd.google-apps.folder"
            
            val folder = service.files().create(folderMetadata)
                .setFields("id")
                .execute()
            
            Result.success(folder.id)
        } catch (e: IOException) {
            Result.failure(Exception("Network error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(Exception("Failed to access app folder: ${e.message}"))
        }
    }

    /**
     * Upload a file to the app folder in Google Drive
     * @param localUri Local file URI
     * @param mimeType MIME type of the file
     * @return Result containing the Drive File ID
     */
    suspend fun uploadFile(localUri: android.net.Uri, mimeType: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val service = driveService ?: return@withContext Result.failure(
                Exception("Drive service not initialized")
            )

            // Get app folder ID
            val folderIdResult = getOrCreateAppFolder()
            if (folderIdResult.isFailure) {
                return@withContext Result.failure(folderIdResult.exceptionOrNull()!!)
            }
            val folderId = folderIdResult.getOrNull()!!

            // Get file name and stream from ContentResolver
            val contentResolver = context.contentResolver
            var fileName = "resource_${System.currentTimeMillis()}"
            
            contentResolver.query(localUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            }

            // Create file metadata
            val fileMetadata = com.google.api.services.drive.model.File()
            fileMetadata.name = fileName
            fileMetadata.parents = listOf(folderId)

            // Create media content
            val inputStream = contentResolver.openInputStream(localUri) ?: return@withContext Result.failure(
                Exception("Could not open input stream for URI")
            )
            
            val mediaContent = com.google.api.client.http.InputStreamContent(mimeType, inputStream)

            // Upload
            val file = service.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute()

            Result.success(file.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Download a file from Google Drive
     * @param fileId Drive File ID
     * @param targetFile Local target file
     */
    suspend fun downloadFile(fileId: String, targetFile: java.io.File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val service = driveService ?: return@withContext Result.failure(
                Exception("Drive service not initialized")
            )

            val outputStream = java.io.FileOutputStream(targetFile)
            service.files().get(fileId)
                .executeMediaAndDownloadTo(outputStream)
            
            outputStream.close()
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("DriveManager", "Download failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Upload backup file to Google Drive
     */
    suspend fun uploadBackup(fileName: String, content: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val service = driveService ?: return@withContext Result.failure(
                Exception("Drive service not initialized. Please sign in first.")
            )
            
            val folderIdResult = getOrCreateAppFolder()
            if (folderIdResult.isFailure) {
                return@withContext Result.failure(folderIdResult.exceptionOrNull()!!)
            }
            val folderId = folderIdResult.getOrNull()!!
            
            val fileMetadata = com.google.api.services.drive.model.File()
            fileMetadata.name = fileName
            fileMetadata.parents = listOf(folderId)
            fileMetadata.mimeType = BACKUP_MIME_TYPE
            
            val contentBytes = content.toByteArray(Charsets.UTF_8)
            val mediaContent = ByteArrayContent(BACKUP_MIME_TYPE, contentBytes)
            
            val file = service.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute()
            
            Result.success(file.id)
        } catch (e: IOException) {
            Result.failure(Exception("Network error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(Exception("Failed to upload backup: ${e.message}"))
        }
    }
    
    /**
     * List all backup files from Google Drive
     */
    suspend fun listBackups(): Result<List<DriveBackupFile>> = withContext(Dispatchers.IO) {
        try {
            val service = driveService ?: return@withContext Result.failure(
                Exception("Drive service not initialized. Please sign in first.")
            )
            
            val folderIdResult = getOrCreateAppFolder()
            if (folderIdResult.isFailure) {
                return@withContext Result.success(emptyList())
            }
            val folderId = folderIdResult.getOrNull()!!
            
            val query = "'$folderId' in parents and mimeType='$BACKUP_MIME_TYPE' and trashed=false"
            val result = service.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name, createdTime, size)")
                .setOrderBy("createdTime desc")
                .execute()
            
            val backupFiles = result.files.map { file ->
                DriveBackupFile(
                    id = file.id,
                    name = file.name,
                    createdTime = file.createdTime?.value ?: 0L,
                    size = file.getSize() ?: 0L
                )
            }
            
            Result.success(backupFiles)
        } catch (e: IOException) {
            Result.failure(Exception("Network error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(Exception("Failed to list backups: ${e.message}"))
        }
    }
    
    /**
     * Download backup file content from Google Drive
     */
    suspend fun downloadBackup(fileId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val service = driveService ?: return@withContext Result.failure(
                Exception("Drive service not initialized. Please sign in first.")
            )
            
            val outputStream = ByteArrayOutputStream()
            service.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            
            val content = outputStream.toString(Charsets.UTF_8.name())
            Result.success(content)
        } catch (e: IOException) {
            Result.failure(Exception("Network error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(Exception("Failed to download backup: ${e.message}"))
        }
    }
    
    /**
     * Delete backup file from Google Drive
     */
    suspend fun deleteBackup(fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val service = driveService ?: return@withContext Result.failure(
                Exception("Drive service not initialized. Please sign in first.")
            )
            
            service.files().delete(fileId).execute()
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(Exception("Network error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(Exception("Failed to delete backup: ${e.message}"))
        }
    }
    /**
     * Metadata from a Drive file
     */
    data class DriveFileMetadata(
        val name: String,
        val mimeType: String,
        val thumbnailLink: String?
    )

    /**
     * Get file metadata (name, mimeType, thumbnail) from Drive
     */
    suspend fun getFileMetadata(fileId: String): Result<DriveFileMetadata> = withContext(Dispatchers.IO) {
        try {
            val service = driveService ?: return@withContext Result.failure(
                Exception("Drive service not initialized")
            )

            val file = service.files().get(fileId)
                .setFields("name, mimeType, thumbnailLink")
                .execute()
            
            Result.success(
                DriveFileMetadata(
                    name = file.name,
                    mimeType = file.mimeType,
                    thumbnailLink = file.thumbnailLink
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Drive File with web link
     */
    data class DriveFile(
        val id: String,
        val name: String,
        val mimeType: String,
        val webViewLink: String?,
        val thumbnailLink: String?
    )

    /**
     * List all files in a specific folder
     */
    suspend fun listFilesInFolder(folderId: String): Result<List<DriveFile>> = withContext(Dispatchers.IO) {
        try {
            val service = driveService ?: return@withContext Result.failure(
                Exception("Drive service not initialized")
            )

            val query = "'$folderId' in parents and trashed=false"
            val result = service.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name, mimeType, webViewLink, thumbnailLink)")
                .execute()
            
            val files = result.files.map { file ->
                DriveFile(
                    id = file.id,
                    name = file.name,
                    mimeType = file.mimeType,
                    webViewLink = file.webViewLink,
                    thumbnailLink = file.thumbnailLink
                )
            }
            
            Result.success(files)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Extract Drive File ID from URL
     */
    fun extractDriveIdFromUrl(url: String): String? {
        val patterns = listOf(
            "/file/d/([a-zA-Z0-9_-]+)",
            "/folders/([a-zA-Z0-9_-]+)",
            "id=([a-zA-Z0-9_-]+)",
            "/open\\?id=([a-zA-Z0-9_-]+)"
        )
        
        for (pattern in patterns) {
            val regex = pattern.toRegex()
            val match = regex.find(url)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1]
            }
        }
        return null
    }

}
