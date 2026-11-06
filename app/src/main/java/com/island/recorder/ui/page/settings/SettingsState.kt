package com.island.recorder.ui.page.settings

import com.island.recorder.domain.recording.model.RecordingSettings
import com.island.recorder.framework.privileged.Authorizer
import com.island.recorder.framework.privileged.DeviceCapability
import com.island.recorder.framework.privileged.RootMode
import com.island.recorder.framework.privileged.ShizukuMode

data class SettingsState(
    val currentSettings: RecordingSettings = RecordingSettings(),
    val storageTreeUri: String = "",
    val selectedAuthorizer: Authorizer = Authorizer.Shizuku,
    val hideLauncherIcon: Boolean = false,
    val capability: DeviceCapability = DeviceCapability(RootMode.None, ShizukuMode.NotRunning),
    val isProjectMediaGranted: Boolean = false,
    val usesPersistentScreenShareProtectionBypass: Boolean = false,
    val persistentScreenShareProtectionBypassed: Boolean? = null,
    val screenShareProtectionUpdating: Boolean = false
)
