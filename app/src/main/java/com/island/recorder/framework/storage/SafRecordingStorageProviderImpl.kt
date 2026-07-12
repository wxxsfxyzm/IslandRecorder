package com.island.recorder.framework.storage

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.island.recorder.domain.recording.model.RecordingOutput
import com.island.recorder.domain.recording.provider.RecordingStorageProvider
import com.island.recorder.domain.settings.repository.AppSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SafRecordingStorageProviderImpl(
    private val context: Context,
    private val appSettingsRepository: AppSettingsRepository
) : RecordingStorageProvider {

    companion object {
        const val DEFAULT_STORAGE_TREE_URI = ""
        const val DEFAULT_STORAGE_PATH = "/storage/emulated/0/DCIM/screenrecorder"
        private const val DEFAULT_RELATIVE_PATH = "DCIM/screenrecorder"
        private const val FILE_PREFIX = "Screenrecorder-"
        private const val FILE_EXTENSION = ".mp4"
        private const val MIME_TYPE_MP4 = "video/mp4"
        private const val PACKAGE_MIUI_GALLERY = "com.miui.gallery"
        private const val ACTION_MIUI_GALLERY_SAVE_TO_CLOUD = "com.miui.gallery.SAVE_TO_CLOUD"
        private const val EXTRA_FILE_PATH = "extra_file_path"
    }

    override suspend fun createRecordingOutput(): RecordingOutput = withContext(Dispatchers.IO) {
        val storageValue = appSettingsRepository.storageTreeUriFlow.first().trim()
        val displayName = recordingFileName()

        if (storageValue.isBlank()) {
            return@withContext createDefaultMediaStoreOutput(displayName)
        }

        val storageUri = Uri.parse(storageValue)
        if (storageUri.scheme == ContentResolver.SCHEME_CONTENT && DocumentsContract.isTreeUri(
                storageUri
            )
        ) {
            return@withContext createSafOutput(storageUri, displayName)
        }

        error("Unsupported recording storage location: $storageValue")
    }

    override suspend fun delete(output: RecordingOutput) {
        withContext(Dispatchers.IO) {
            output.fileDescriptor.close()
            if (output.isDocumentUri) {
                DocumentsContract.deleteDocument(context.contentResolver, output.uri)
            } else {
                context.contentResolver.delete(output.uri, null, null)
            }
        }
    }

    override suspend fun finalize(output: RecordingOutput) {
        withContext(Dispatchers.IO) {
            if (!output.isDocumentUri) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                context.contentResolver.update(output.uri, values, null, null)
            }
            output.filePath?.let(::notifyMiuiGalleryRecordingSaved)
        }
    }

    private fun createSafOutput(treeUri: Uri, displayName: String): RecordingOutput {
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        val outputUri = DocumentsContract.createDocument(
            context.contentResolver,
            documentUri,
            MIME_TYPE_MP4,
            displayName
        ) ?: throw IllegalStateException("Failed to create recording document")
        val descriptor = context.contentResolver.openFileDescriptor(outputUri, "w")
            ?: throw IllegalStateException("Failed to open recording document")

        return RecordingOutput(
            uri = outputUri,
            displayName = displayName,
            fileDescriptor = descriptor,
            isDocumentUri = true
        )
    }

    private fun createDefaultMediaStoreOutput(displayName: String): RecordingOutput {
        return createMediaStoreOutput(
            displayName = displayName,
            relativePath = DEFAULT_RELATIVE_PATH
        )
    }

    private fun createMediaStoreOutput(displayName: String, relativePath: String): RecordingOutput {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, MIME_TYPE_MP4)
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                relativePath.ifBlank { DEFAULT_RELATIVE_PATH })
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val outputUri = context.contentResolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            values
        ) ?: throw IllegalStateException("Failed to create recording media item")
        val descriptor = context.contentResolver.openFileDescriptor(outputUri, "w")
            ?: throw IllegalStateException("Failed to open recording media item")

        return RecordingOutput(
            uri = outputUri,
            displayName = displayName,
            fileDescriptor = descriptor,
            isDocumentUri = false,
            filePath = "$DEFAULT_STORAGE_PATH/$displayName"
        )
    }

    private fun notifyMiuiGalleryRecordingSaved(filePath: String) {
        val intent = Intent(ACTION_MIUI_GALLERY_SAVE_TO_CLOUD).apply {
            setPackage(PACKAGE_MIUI_GALLERY)
            putExtra(EXTRA_FILE_PATH, filePath)
        }
        context.sendBroadcast(intent)
    }

    private fun recordingFileName(): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(Date())
        return "$FILE_PREFIX$timestamp$FILE_EXTENSION"
    }

}
