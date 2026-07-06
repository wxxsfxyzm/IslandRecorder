package com.island.recorder.data.settings.repository

import androidx.datastore.preferences.core.Preferences
import com.island.recorder.data.settings.local.datastore.AppDataStore
import com.island.recorder.domain.recording.model.AudioSource
import com.island.recorder.domain.recording.model.FrameRate
import com.island.recorder.domain.recording.model.RecordingSettings
import com.island.recorder.domain.recording.model.ScreenOrientation
import com.island.recorder.domain.recording.model.TileStyle
import com.island.recorder.domain.recording.model.VideoBitrate
import com.island.recorder.domain.recording.model.VideoCodec
import com.island.recorder.domain.recording.model.VideoQuality
import com.island.recorder.domain.settings.model.AppPreferences
import com.island.recorder.domain.settings.repository.AppSettingsRepository
import com.island.recorder.domain.settings.repository.BooleanSetting
import com.island.recorder.domain.settings.repository.StringSetting
import com.island.recorder.framework.privileged.Authorizer
import com.island.recorder.framework.storage.SafRecordingStorageProviderImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppSettingsRepositoryImpl(
    private val appDataStore: AppDataStore,
    appScope: CoroutineScope
) : AppSettingsRepository {

    private val _preferences = MutableStateFlow(AppPreferences())

    override val preferencesFlow: Flow<AppPreferences> =
        _preferences.asStateFlow()

    override val currentPreferences: AppPreferences
        get() = _preferences.value

    init {
        appScope.launch {
            appDataStore.data
                .map(::toAppPreferences)
                .collect { _preferences.value = it }
        }
    }

    override val recordingSettingsFlow: Flow<RecordingSettings> =
        preferencesFlow.filter { it.isLoaded }.map { it.recordingSettings }

    override val storageTreeUriFlow: Flow<String> =
        preferencesFlow.filter { it.isLoaded }.map { it.storageTreeUri }

    override val isFirstLaunchFlow: Flow<Boolean> =
        preferencesFlow.filter { it.isLoaded }.map { it.isFirstLaunch }

    override suspend fun putString(setting: StringSetting, value: String) {
        appDataStore.putString(stringKey(setting), value)
        _preferences.update { applyString(it, setting, value) }
    }

    override fun getString(setting: StringSetting, default: String): Flow<String> =
        appDataStore.getString(stringKey(setting), default)

    override suspend fun putBoolean(setting: BooleanSetting, value: Boolean) {
        appDataStore.putBoolean(booleanKey(setting), value)
        _preferences.update { applyBoolean(it, setting, value) }
    }

    override fun getBoolean(setting: BooleanSetting, default: Boolean): Flow<Boolean> =
        appDataStore.getBoolean(booleanKey(setting), default)

    override suspend fun setFirstLaunchCompleted() {
        appDataStore.putBoolean(AppDataStore.FIRST_LAUNCH, false)
        _preferences.update { it.copy(isFirstLaunch = false) }
    }

    private fun toAppPreferences(prefs: Preferences): AppPreferences =
        AppPreferences(
            recordingSettings = RecordingSettings(
                videoQuality = safeValueOf(prefs[AppDataStore.VIDEO_QUALITY], VideoQuality.FHD),
                videoBitrate = safeValueOf(
                    prefs[AppDataStore.VIDEO_BITRATE],
                    VideoBitrate.AUTO
                ),
                screenOrientation = safeValueOf(
                    prefs[AppDataStore.SCREEN_ORIENTATION],
                    ScreenOrientation.AUTO
                ),
                frameRate = safeValueOf(prefs[AppDataStore.FRAME_RATE], FrameRate.AUTO),
                audioSource = safeValueOf(prefs[AppDataStore.AUDIO_SOURCE], AudioSource.NONE),
                videoCodec = safeValueOf(prefs[AppDataStore.VIDEO_CODEC], VideoCodec.H264),
                showTouches = prefs[AppDataStore.SHOW_TOUCHES] ?: false,
                stopOnLockScreen = prefs[AppDataStore.STOP_ON_LOCK_SCREEN] ?: false,
                bypassFocusIsland = prefs[AppDataStore.BYPASS_FOCUS_ISLAND] ?: false,
                tileStyle = safeValueOf(prefs[AppDataStore.TILE_STYLE], TileStyle.DEFAULT)
            ),
            storageTreeUri = prefs[AppDataStore.STORAGE_TREE_URI]
                ?: SafRecordingStorageProviderImpl.DEFAULT_STORAGE_TREE_URI,
            isFirstLaunch = prefs[AppDataStore.FIRST_LAUNCH] ?: true,
            authorizer = safeValueOf(prefs[AppDataStore.AUTHORIZER], Authorizer.Shizuku),
            isLoaded = true
        )

    private fun applyString(
        preferences: AppPreferences,
        setting: StringSetting,
        value: String
    ): AppPreferences =
        when (setting) {
            StringSetting.VideoQuality -> preferences.copy(
                recordingSettings = preferences.recordingSettings.copy(
                    videoQuality = safeValueOf(value, VideoQuality.FHD)
                )
            )

            StringSetting.VideoBitrate -> preferences.copy(
                recordingSettings = preferences.recordingSettings.copy(
                    videoBitrate = safeValueOf(value, VideoBitrate.AUTO)
                )
            )

            StringSetting.ScreenOrientation -> preferences.copy(
                recordingSettings = preferences.recordingSettings.copy(
                    screenOrientation = safeValueOf(value, ScreenOrientation.AUTO)
                )
            )

            StringSetting.FrameRate -> preferences.copy(
                recordingSettings = preferences.recordingSettings.copy(
                    frameRate = safeValueOf(value, FrameRate.AUTO)
                )
            )

            StringSetting.AudioSource -> preferences.copy(
                recordingSettings = preferences.recordingSettings.copy(
                    audioSource = safeValueOf(value, AudioSource.NONE)
                )
            )

            StringSetting.VideoCodec -> preferences.copy(
                recordingSettings = preferences.recordingSettings.copy(
                    videoCodec = safeValueOf(value, VideoCodec.H264)
                )
            )

            StringSetting.TileStyle -> preferences.copy(
                recordingSettings = preferences.recordingSettings.copy(
                    tileStyle = safeValueOf(value, TileStyle.DEFAULT)
                )
            )

            StringSetting.StorageTreeUri -> preferences.copy(storageTreeUri = value)
            StringSetting.Authorizer -> preferences.copy(
                authorizer = safeValueOf(value, Authorizer.Shizuku)
            )
        }

    private fun applyBoolean(
        preferences: AppPreferences,
        setting: BooleanSetting,
        value: Boolean
    ): AppPreferences =
        when (setting) {
            BooleanSetting.ShowTouches -> preferences.copy(
                recordingSettings = preferences.recordingSettings.copy(showTouches = value)
            )

            BooleanSetting.StopOnLockScreen -> preferences.copy(
                recordingSettings = preferences.recordingSettings.copy(stopOnLockScreen = value)
            )

            BooleanSetting.BypassFocusIsland -> preferences.copy(
                recordingSettings = preferences.recordingSettings.copy(bypassFocusIsland = value)
            )

            BooleanSetting.FirstLaunch -> preferences.copy(isFirstLaunch = value)
        }

    private fun stringKey(setting: StringSetting): Preferences.Key<String> =
        when (setting) {
            StringSetting.VideoQuality -> AppDataStore.VIDEO_QUALITY
            StringSetting.VideoBitrate -> AppDataStore.VIDEO_BITRATE
            StringSetting.ScreenOrientation -> AppDataStore.SCREEN_ORIENTATION
            StringSetting.FrameRate -> AppDataStore.FRAME_RATE
            StringSetting.AudioSource -> AppDataStore.AUDIO_SOURCE
            StringSetting.VideoCodec -> AppDataStore.VIDEO_CODEC
            StringSetting.TileStyle -> AppDataStore.TILE_STYLE
            StringSetting.StorageTreeUri -> AppDataStore.STORAGE_TREE_URI
            StringSetting.Authorizer -> AppDataStore.AUTHORIZER
        }

    private fun booleanKey(setting: BooleanSetting): Preferences.Key<Boolean> =
        when (setting) {
            BooleanSetting.ShowTouches -> AppDataStore.SHOW_TOUCHES
            BooleanSetting.StopOnLockScreen -> AppDataStore.STOP_ON_LOCK_SCREEN
            BooleanSetting.BypassFocusIsland -> AppDataStore.BYPASS_FOCUS_ISLAND
            BooleanSetting.FirstLaunch -> AppDataStore.FIRST_LAUNCH
        }

    private inline fun <reified T : Enum<T>> safeValueOf(name: String?, default: T): T {
        if (name == null) return default
        return try {
            java.lang.Enum.valueOf(T::class.java, name)
        } catch (_: IllegalArgumentException) {
            default
        }
    }
}
