package com.island.recorder.framework.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.island.recorder.R
import com.island.recorder.domain.recording.model.RecordingOutput
import timber.log.Timber
import kotlin.math.roundToInt

class RecordingSavedNotificationManager(
    private val context: Context
) {

    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    init {
        createNotificationChannel()
    }

    fun showRecordingSavedNotification(output: RecordingOutput) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Timber.d("Skipping saved recording notification because POST_NOTIFICATIONS is denied")
            return
        }

        val notificationId = SAVED_NOTIFICATION_ID
        val thumbnail = createThumbnail(output.uri)
        val message = context.getString(R.string.recording_saved_message)
        val expandedSummary = "${output.displayName}\n$message"
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_record)
            .setContentTitle(context.getString(R.string.recording_saved_title))
            .setContentText(message)
            .setSubText(output.displayName)
            .setContentIntent(openPendingIntent(output))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setWhen(System.currentTimeMillis())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .addAction(
                0,
                context.getString(R.string.action_share),
                sharePendingIntent(output.uri, notificationId)
            )
            .addAction(
                0,
                context.getString(R.string.action_delete),
                deletePendingIntent(output, notificationId)
            )

        if (thumbnail != null) {
            builder
                .setLargeIcon(thumbnail)
                .setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(thumbnail)
                        .bigLargeIcon(null as Bitmap?)
                        .setSummaryText(expandedSummary)
                )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(expandedSummary))
        }

        notificationManager.notify(notificationId, builder.build())
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.recording_saved_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.recording_saved_channel_desc)
            setShowBadge(true)
            enableVibration(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun openPendingIntent(output: RecordingOutput): PendingIntent {
        val intent = Intent(ACTION_REVIEW).apply {
            setPackage(PACKAGE_MIUI_GALLERY)
            setDataAndType(output.uri, MIME_TYPE_MP4)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (output.isDocumentUri) {
                clipData = ClipData.newUri(context.contentResolver, output.displayName, output.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        return PendingIntent.getActivity(
            context,
            OPEN_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun sharePendingIntent(uri: Uri, notificationId: Int): PendingIntent {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE_MP4
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(
            shareIntent,
            context.getString(R.string.recording_saved_share_chooser)
        ).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return PendingIntent.getActivity(
            context,
            notificationId + SHARE_REQUEST_OFFSET,
            chooser,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun deletePendingIntent(
        output: RecordingOutput,
        notificationId: Int
    ): PendingIntent {
        val intent = Intent(context, RecordingSavedActionReceiver::class.java).apply {
            action = ACTION_DELETE_RECORDING
            putExtra(EXTRA_URI, output.uri.toString())
            putExtra(EXTRA_IS_DOCUMENT_URI, output.isDocumentUri)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }

        return PendingIntent.getBroadcast(
            context,
            notificationId + DELETE_REQUEST_OFFSET,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createThumbnail(uri: Uri): Bitmap? {
        return try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                retriever.createNotificationCover()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to create recording thumbnail for $uri")
            null
        }
    }

    private fun MediaMetadataRetriever.createNotificationCover(): Bitmap? {
        val width = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull()
            ?: 0
        val height = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull()
            ?: 0
        val rotation = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull()
            ?: 0
        val rotated = rotation == 90 || rotation == 270
        val sourceWidth = if (rotated) height else width
        val sourceHeight = if (rotated) width else height

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 &&
            sourceWidth > 0 &&
            sourceHeight > 0
        ) {
            val scale = NOTIFICATION_COVER_MAX_SIZE_PX.toFloat() / maxOf(sourceWidth, sourceHeight)
            val targetWidth = (sourceWidth * scale).roundToInt().coerceAtLeast(1)
            val targetHeight = (sourceHeight * scale).roundToInt().coerceAtLeast(1)
            getScaledFrameAtTime(
                THUMBNAIL_FRAME_TIME_US,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                targetWidth,
                targetHeight
            )
        } else {
            getFrameAtTime(
                THUMBNAIL_FRAME_TIME_US,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )?.scaledToNotificationCover()
        }
    }

    private fun Bitmap.scaledToNotificationCover(): Bitmap {
        val maxDimension = maxOf(width, height)
        if (maxDimension <= NOTIFICATION_COVER_MAX_SIZE_PX) return this

        val scale = NOTIFICATION_COVER_MAX_SIZE_PX.toFloat() / maxDimension
        return Bitmap.createScaledBitmap(
            this,
            (width * scale).roundToInt().coerceAtLeast(1),
            (height * scale).roundToInt().coerceAtLeast(1),
            true
        )
    }

    companion object {
        const val ACTION_DELETE_RECORDING = "com.island.recorder.action.DELETE_RECORDING"
        const val EXTRA_URI = "com.island.recorder.extra.URI"
        const val EXTRA_IS_DOCUMENT_URI = "com.island.recorder.extra.IS_DOCUMENT_URI"
        const val EXTRA_NOTIFICATION_ID = "com.island.recorder.extra.NOTIFICATION_ID"
        const val SAVED_NOTIFICATION_ID = 111

        private const val CHANNEL_ID = "recording_saved_high_channel"
        private const val ACTION_REVIEW = "com.android.camera.action.REVIEW"
        private const val PACKAGE_MIUI_GALLERY = "com.miui.gallery"
        private const val MIME_TYPE_MP4 = "video/mp4"
        private const val OPEN_REQUEST_CODE = 13
        private const val SHARE_REQUEST_OFFSET = 10_000
        private const val DELETE_REQUEST_OFFSET = 20_000
        private const val NOTIFICATION_COVER_MAX_SIZE_PX = 1024
        private const val THUMBNAIL_FRAME_TIME_US = 1_000_000L
    }
}
