package com.island.recorder.framework.privileged.provider

import android.content.Context
import com.island.recorder.domain.settings.repository.AppSettingsRepository
import com.island.recorder.framework.privileged.Authorizer
import com.island.recorder.framework.privileged.DeviceCapability
import com.island.recorder.framework.privileged.RootMode
import com.island.recorder.framework.privileged.ShizukuMode
import com.island.recorder.framework.privileged.core.execution.dispatcher.runDirectPrivilegedOrNull
import com.island.recorder.framework.privileged.core.execution.runtime.PrivilegedOperations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PrivilegedOperationProvider(
    private val context: Context,
    private val settingsRepository: AppSettingsRepository,
    private val capabilityProvider: DeviceCapabilityProvider,
    private val appScope: CoroutineScope
) {
    private val _projectMediaAllowedFlow = MutableStateFlow(false)
    val projectMediaAllowedFlow: StateFlow<Boolean> = _projectMediaAllowedFlow.asStateFlow()

    init {
        refreshProjectMediaAllowed()
    }

    fun capability(): DeviceCapability = capabilityProvider.current()

    fun requestShizukuPermission(requestCode: Int) {
        capabilityProvider.requestShizukuPermission(requestCode)
    }

    fun setPackageNetworkingEnabled(uid: Int, enabled: Boolean): Boolean =
        callPrivileged { it.setPackageNetworkingEnabled(uid, enabled) }

    fun setShowTouches(enabled: Boolean): Boolean =
        callPrivileged { it.setShowTouches(enabled) }

    fun isScreenShareProtectionEnabled(): Boolean =
        callPrivileged { it.isScreenShareProtectionEnabled() }

    fun setScreenShareProtectionEnabled(enabled: Boolean): Boolean =
        callPrivileged { it.setScreenShareProtectionEnabled(enabled) }

    fun setProjectMediaAllowed(
        packageName: String = context.packageName,
        uid: Int = context.applicationInfo.uid,
        allowed: Boolean
    ): Boolean =
        callPrivileged { it.setProjectMediaAllowed(packageName, uid, allowed) }

    fun isProjectMediaAllowed(
        packageName: String = context.packageName,
        uid: Int = context.applicationInfo.uid
    ): Boolean =
        callPrivileged { it.isProjectMediaAllowed(packageName, uid) }

    fun refreshProjectMediaAllowed(
        packageName: String = context.packageName,
        uid: Int = context.applicationInfo.uid
    ) {
        appScope.launch(Dispatchers.IO) {
            _projectMediaAllowedFlow.value = isProjectMediaAllowed(packageName, uid)
        }
    }

    fun setProjectMediaAllowedAsync(
        packageName: String = context.packageName,
        uid: Int = context.applicationInfo.uid,
        allowed: Boolean
    ) {
        appScope.launch(Dispatchers.IO) {
            val success = setProjectMediaAllowed(packageName, uid, allowed)
            if (success) {
                _projectMediaAllowedFlow.value = isProjectMediaAllowed(packageName, uid)
            }
        }
    }

    private fun activeAuthorizer(): Authorizer? {
        val capability = capabilityProvider.current()
        val preferred = settingsRepository.currentPreferences.authorizer

        if (preferred == Authorizer.Shizuku && capability.shizukuMode == ShizukuMode.Authorized) {
            return Authorizer.Shizuku
        }
        if (preferred == Authorizer.Root && capability.rootMode != RootMode.None) {
            return Authorizer.Root
        }
        if (capability.shizukuMode == ShizukuMode.Authorized) {
            return Authorizer.Shizuku
        }
        if (capability.rootMode != RootMode.None) {
            return Authorizer.Root
        }

        return null
    }

    private fun callPrivileged(
        block: (PrivilegedOperations) -> Boolean
    ): Boolean {
        val authorizer = activeAuthorizer() ?: return false
        return runDirectPrivilegedOrNull(authorizer, action = block) ?: false
    }
}
