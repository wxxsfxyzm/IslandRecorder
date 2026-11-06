package com.island.recorder.ui.page.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.island.recorder.domain.settings.repository.AppSettingsRepository
import com.island.recorder.domain.settings.repository.BooleanSetting
import com.island.recorder.framework.privileged.provider.DeviceCapabilityProvider
import com.island.recorder.framework.privileged.provider.PrivilegedOperationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(
    private val appSettingsRepository: AppSettingsRepository,
    private val capabilityProvider: DeviceCapabilityProvider,
    private val privilegedOperations: PrivilegedOperationProvider
) : ViewModel() {
    private val protectionMutex = Mutex()
    private val protectionRefresh = MutableStateFlow(0)
    private val protectionUpdating = MutableStateFlow(false)
    private val persistentProtectionBypassed = combine(
        appSettingsRepository.preferencesFlow.map { it.authorizer }.distinctUntilChanged(),
        capabilityProvider.capabilityFlow,
        protectionRefresh
    ) { _, _, _ -> Unit }.mapLatest {
        protectionMutex.withLock {
            withContext(Dispatchers.IO) {
                privilegedOperations.readPersistentScreenShareProtectionBypass()
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<SettingsState> = combine(
        appSettingsRepository.preferencesFlow,
        capabilityProvider.capabilityFlow,
        privilegedOperations.projectMediaAllowedFlow,
        persistentProtectionBypassed,
        protectionUpdating
    ) { preferences, capability, isProjectMediaGranted, protectionBypassed, protectionUpdating ->
        SettingsState(
            currentSettings = preferences.recordingSettings,
            storageTreeUri = preferences.storageTreeUri,
            selectedAuthorizer = preferences.authorizer,
            hideLauncherIcon = preferences.hideLauncherIcon,
            capability = capability,
            isProjectMediaGranted = isProjectMediaGranted,
            usesPersistentScreenShareProtectionBypass =
                privilegedOperations.usesPersistentScreenShareProtectionBypass,
            persistentScreenShareProtectionBypassed = protectionBypassed,
            screenShareProtectionUpdating = protectionUpdating
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsState(
            usesPersistentScreenShareProtectionBypass =
                privilegedOperations.usesPersistentScreenShareProtectionBypass
        )
    )

    fun refreshCapability() {
        capabilityProvider.refreshPrivilegeStatus()
        privilegedOperations.refreshProjectMediaAllowed()
        protectionRefresh.update { it + 1 }
    }

    fun setScreenShareProtectionBypassed(bypassed: Boolean) {
        if (!privilegedOperations.usesPersistentScreenShareProtectionBypass) {
            viewModelScope.launch {
                appSettingsRepository.putBoolean(BooleanSetting.BypassScreenShareProtection, bypassed)
            }
            return
        }
        if (!protectionUpdating.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                protectionMutex.withLock {
                    val success = withContext(Dispatchers.IO) {
                        privilegedOperations.setPersistentScreenShareProtectionBypass(bypassed)
                    }
                    if (!success) Timber.w("Failed to update HyperOS 4 screen share protection")
                }
            } finally {
                protectionRefresh.update { it + 1 }
                protectionUpdating.value = false
            }
        }
    }

    fun requestShizukuPermission(requestCode: Int) {
        capabilityProvider.requestShizukuPermission(requestCode)
    }

    fun addShizukuPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        capabilityProvider.addShizukuPermissionResultListener(listener)
    }

    fun removeShizukuPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        capabilityProvider.removeShizukuPermissionResultListener(listener)
    }

    fun setProjectMediaAllowed(allowed: Boolean) {
        privilegedOperations.setProjectMediaAllowedAsync(allowed = allowed)
    }
}
