package com.island.recorder.domain.settings.repository

import com.island.recorder.domain.recording.model.RecordingSettings
import com.island.recorder.domain.settings.model.AppPreferences
import kotlinx.coroutines.flow.Flow

enum class StringSetting {
    VideoQuality,
    VideoBitrate,
    ScreenOrientation,
    FrameRate,
    AudioSource,
    VideoCodec,
    TileStyle,
    StorageTreeUri,
    Authorizer
}

enum class BooleanSetting {
    ShowTouches,
    StopOnLockScreen,
    BypassFocusIsland,
    FirstLaunch
}

interface AppSettingsRepository {
    val preferencesFlow: Flow<AppPreferences>
    val currentPreferences: AppPreferences
    val recordingSettingsFlow: Flow<RecordingSettings>
    val storageTreeUriFlow: Flow<String>
    val isFirstLaunchFlow: Flow<Boolean>

    suspend fun putString(setting: StringSetting, value: String)
    fun getString(setting: StringSetting, default: String = ""): Flow<String>

    suspend fun putBoolean(setting: BooleanSetting, value: Boolean)
    fun getBoolean(setting: BooleanSetting, default: Boolean = false): Flow<Boolean>
    
    suspend fun setFirstLaunchCompleted()
}
