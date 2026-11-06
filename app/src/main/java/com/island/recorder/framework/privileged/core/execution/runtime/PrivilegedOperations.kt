package com.island.recorder.framework.privileged.core.execution.runtime

interface PrivilegedOperations {
    fun setPackageNetworkingEnabled(uid: Int, enabled: Boolean): Boolean
    fun setShowTouches(enabled: Boolean): Boolean
    fun isScreenShareProtectionEnabled(): Boolean
    fun setScreenShareProtectionEnabled(enabled: Boolean): Boolean
    fun getMiuiMediaProjectionMode(packageName: String, uid: Int): Int?
    fun setMiuiMediaProjectionMode(packageName: String, uid: Int, mode: Int): Boolean
    fun setProjectMediaAllowed(packageName: String, uid: Int, allowed: Boolean): Boolean
    fun isProjectMediaAllowed(packageName: String, uid: Int): Boolean
}
