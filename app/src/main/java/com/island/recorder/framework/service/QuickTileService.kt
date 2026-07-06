package com.island.recorder.framework.service

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.island.recorder.R
import com.island.recorder.domain.recording.model.RecordingState
import com.island.recorder.domain.recording.model.TileStyle
import com.island.recorder.domain.settings.repository.AppSettingsRepository
import com.island.recorder.ui.activity.RecordingShortcutActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import timber.log.Timber

class QuickTileService : TileService() {

    private val appSettingsRepo: AppSettingsRepository by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tileStateJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        tileStateJob?.cancel()
        tileStateJob = serviceScope.launch {
            combine(
                RecorderService.recordingState,
                appSettingsRepo.recordingSettingsFlow
            ) { recordingState, settings ->
                TileSnapshot(
                    isRecording = recordingState.isRecordingTileState(),
                    tileStyle = settings.tileStyle
                )
            }.distinctUntilChanged().collect { snapshot ->
                updateTile(snapshot)
            }
        }
    }

    override fun onStopListening() {
        tileStateJob?.cancel()
        tileStateJob = null
        super.onStopListening()
    }

    override fun onDestroy() {
        tileStateJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()

        val state = RecorderService.recordingState.value

        if (state is RecordingState.Recording || state is RecordingState.Paused) {
            Timber.d("Quick tile clicked - stopping recording")
            val intent = Intent(this, RecorderService::class.java).apply {
                action = RecorderService.ACTION_STOP_RECORDING
            }
            startService(intent)
        } else if (state is RecordingState.Stopping) {
            Timber.d("Quick tile clicked while recording cleanup is in progress")
        } else {
            Timber.d("Quick tile clicked - launching shortcut for recording")
            val intent = Intent(this, RecordingShortcutActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        }
    }

    private fun updateTile(snapshot: TileSnapshot) {
        val iconRes = if (snapshot.tileStyle == TileStyle.APP_ICON) {
            R.drawable.ic_tile_style_app_icon
        } else {
            R.drawable.ic_record
        }

        qsTile?.apply {
            state = if (snapshot.isRecording) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label =
                if (snapshot.isRecording) getString(R.string.tile_stop_recording) else getString(R.string.tile_start_recording)
            icon = Icon.createWithResource(this@QuickTileService, iconRes)
            updateTile()
        }
    }

    private fun RecordingState.isRecordingTileState(): Boolean =
        this is RecordingState.Recording ||
            this is RecordingState.Paused ||
            this is RecordingState.Stopping

    private data class TileSnapshot(
        val isRecording: Boolean,
        val tileStyle: TileStyle
    )
}
