package com.island.recorder.domain.recording.model

import android.net.Uri
import android.os.ParcelFileDescriptor

data class RecordingOutput(
    val uri: Uri,
    val displayName: String,
    val fileDescriptor: ParcelFileDescriptor,
    val isDocumentUri: Boolean,
    val filePath: String? = null
)
