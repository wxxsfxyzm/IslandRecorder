package com.island.recorder.framework.privileged.core.execution.runtime

import android.annotation.SuppressLint
import android.content.AttributionSource
import android.content.Context
import android.net.IConnectivityManager
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.IWindowManager
import com.android.internal.app.IAppOpsService
import com.island.recorder.core.reflection.ReflectionProvider
import com.island.recorder.core.reflection.invoke
import com.island.recorder.core.reflection.invokeStatic
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

@SuppressLint("PrivateApi")
class DefaultPrivilegedService private constructor(
    private val runtime: PrivilegedRuntime
) : PrivilegedOperations, KoinComponent {
    companion object {
        private const val TAG = "PrivilegedService"
        private const val FIREWALL_CHAIN_OEM_DENY_3 = 9
        private const val FIREWALL_RULE_DEFAULT = 0
        private const val FIREWALL_RULE_DENY = 2
        private const val MODE_ALLOWED = 0
        private const val MODE_IGNORED = 1
        private const val OP_PROJECT_MEDIA = 46
        private const val OP_MIUI_MEDIA_PROJECTION = 10042
        private const val OPSTR_PROJECT_MEDIA = "android:project_media"
        private const val SETTING_SHOW_TOUCHES = "show_touches"
        private const val SETTING_SCREEN_SHARE_PROTECTION = "screen_share_protection"
        private const val SETTING_SCREEN_SHARE_PROTECTION_ON = "screen_share_protection_on"
        private const val SETTING_ENABLED = "1"
        private const val SETTING_DISABLED = "0"
        private const val CALL_METHOD_GET_SECURE = "GET_secure"
        private const val CALL_METHOD_PUT_SECURE = "PUT_secure"
        private const val CALL_METHOD_PUT_SYSTEM = "PUT_system"
        private const val CALL_METHOD_USER_KEY = "_user"
        private const val CALL_METHOD_OVERRIDEABLE_BY_RESTORE_KEY = "_overrideable_by_restore"
        private val SCREEN_SHARE_PROJECT_BLACKLIST = arrayListOf(
            "NotificationShade",
            "StatusBar",
            "InputMethod",
            "com.miui.securitycenter/com.miui.permcenter.capsule.ScreenShareProtectionActivity"
        )

        fun shizukuHook() = DefaultPrivilegedService(PrivilegedRuntime.ShizukuHooked)

        fun binderWrapped(
            name: String,
            binderWrapper: (IBinder) -> IBinder
        ) = DefaultPrivilegedService(PrivilegedRuntime.BinderWrapped(name, binderWrapper))
    }

    private val context by inject<Context>()
    private val reflect by inject<ReflectionProvider>()

    private val connectivityManager: IConnectivityManager by lazy {
        runtime.connectivityManager()
    }

    private val windowManager: IWindowManager by lazy {
        runtime.windowManager()
    }

    private val appOpsService: IAppOpsService? by lazy {
        runtime.appOpsBinder()?.let { IAppOpsService.Stub.asInterface(it) }
    }

    override fun setPackageNetworkingEnabled(uid: Int, enabled: Boolean): Boolean {
        return try {
            val rule = if (enabled) FIREWALL_RULE_DEFAULT else FIREWALL_RULE_DENY

            if (!enabled) {
                connectivityManager.setFirewallChainEnabled(FIREWALL_CHAIN_OEM_DENY_3, true)
            }

            connectivityManager.setUidFirewallRule(FIREWALL_CHAIN_OEM_DENY_3, uid, rule)
            Timber.tag(TAG).d("Set UID $uid networking to $enabled via ${runtime.name}")
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to set networking via ${runtime.name}")
            false
        }
    }

    override fun setShowTouches(enabled: Boolean): Boolean {
        return try {
            val targetValue = if (enabled) SETTING_ENABLED else SETTING_DISABLED
            putSystemSettingWithHookedProvider(SETTING_SHOW_TOUCHES, targetValue)
            Timber.tag(TAG).i("Set show_touches to $targetValue via ${runtime.name}.")
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to set show_touches via ${runtime.name}")
            false
        }
    }

    override fun isScreenShareProtectionEnabled(): Boolean {
        return try {
            val userEnabled =
                getSecureSettingWithHookedProvider(SETTING_SCREEN_SHARE_PROTECTION)
            val runtimeEnabled =
                getSecureSettingWithHookedProvider(SETTING_SCREEN_SHARE_PROTECTION_ON)

            Timber.tag(TAG).d(
                "Read screen share protection via ${runtime.name}: " +
                        "$SETTING_SCREEN_SHARE_PROTECTION=$userEnabled, " +
                        "$SETTING_SCREEN_SHARE_PROTECTION_ON=$runtimeEnabled."
            )

            userEnabled == SETTING_ENABLED ||
                    runtimeEnabled == SETTING_ENABLED
        } catch (e: Exception) {
            Timber.tag(TAG).e(
                e,
                "Failed to read screen share protection via ${runtime.name}"
            )
            false
        }
    }

    override fun setScreenShareProtectionEnabled(enabled: Boolean): Boolean {
        return try {
            val targetValue = if (enabled) SETTING_ENABLED else SETTING_DISABLED
            val previousEnabled =
                getSecureSettingWithHookedProvider(SETTING_SCREEN_SHARE_PROTECTION)
            val previousRuntimeEnabled =
                getSecureSettingWithHookedProvider(SETTING_SCREEN_SHARE_PROTECTION_ON)
            Timber.tag(TAG).i(
                "Updating screen share protection via ${runtime.name}: enabled=$enabled " +
                        "before[$SETTING_SCREEN_SHARE_PROTECTION=$previousEnabled, " +
                        "$SETTING_SCREEN_SHARE_PROTECTION_ON=$previousRuntimeEnabled]."
            )
            putSecureSettingWithHookedProvider(
                SETTING_SCREEN_SHARE_PROTECTION,
                targetValue
            )
            Timber.tag(TAG).d(
                "Wrote $SETTING_SCREEN_SHARE_PROTECTION=$targetValue via ${runtime.name}."
            )
            putSecureSettingWithHookedProvider(
                SETTING_SCREEN_SHARE_PROTECTION_ON,
                targetValue
            )
            Timber.tag(TAG).d(
                "Wrote $SETTING_SCREEN_SHARE_PROTECTION_ON=$targetValue via ${runtime.name}."
            )
            val blacklistUpdated = setScreenShareProjectBlackList(enabled)
            val actualValue =
                getSecureSettingWithHookedProvider(SETTING_SCREEN_SHARE_PROTECTION)
            val actualRuntimeValue =
                getSecureSettingWithHookedProvider(SETTING_SCREEN_SHARE_PROTECTION_ON)
            val actualEnabled = actualValue == SETTING_ENABLED
            val actualRuntimeEnabled = actualRuntimeValue == SETTING_ENABLED
            Timber.tag(TAG).i(
                "Screen share protection update result via ${runtime.name}: " +
                        "expected=$targetValue " +
                        "after[$SETTING_SCREEN_SHARE_PROTECTION=$actualValue, " +
                        "$SETTING_SCREEN_SHARE_PROTECTION_ON=$actualRuntimeValue] " +
                        "windowBlacklistUpdated=$blacklistUpdated."
            )
            if (actualEnabled != enabled || actualRuntimeEnabled != enabled) {
                Timber.tag(TAG).w(
                    "Screen share protection readback mismatch via ${runtime.name}: " +
                            "expected=$enabled actual=$actualEnabled " +
                            "runtimeActual=$actualRuntimeEnabled " +
                            "windowBlacklistUpdated=$blacklistUpdated."
                )
                return false
            }
            Timber.tag(TAG).i(
                "Set screen share protection to $targetValue via ${runtime.name}; " +
                        "windowBlacklistUpdated=$blacklistUpdated."
            )
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to set screen share protection via ${runtime.name}")
            false
        }
    }

    private fun setScreenShareProjectBlackList(enabled: Boolean): Boolean {
        return try {
            if (enabled) {
                windowManager.setScreenShareProjectBlackList(SCREEN_SHARE_PROJECT_BLACKLIST)
            } else {
                windowManager.setScreenShareProjectBlackList(null)
            }
            Timber.tag(TAG).d(
                "Set screen share project blacklist enabled=$enabled via ${runtime.name}."
            )
            true
        } catch (e: Throwable) {
            Timber.tag(TAG).e(
                e,
                "Failed to set screen share project blacklist via ${runtime.name}"
            )
            false
        }
    }

    private fun putSystemSettingWithHookedProvider(name: String, value: String) {
        putSettingWithHookedProvider(
            settingsClass = Settings.System::class.java,
            authority = requireNotNull(Settings.System.CONTENT_URI.authority),
            callMethod = CALL_METHOD_PUT_SYSTEM,
            name = name,
            value = value
        )
    }

    private fun putSecureSettingWithHookedProvider(name: String, value: String) {
        putSettingWithHookedProvider(
            settingsClass = Settings.Secure::class.java,
            authority = requireNotNull(Settings.Secure.CONTENT_URI.authority),
            callMethod = CALL_METHOD_PUT_SECURE,
            name = name,
            value = value
        )
    }

    private fun putSettingWithHookedProvider(
        settingsClass: Class<*>,
        authority: String,
        callMethod: String,
        name: String,
        value: String
    ) {
        val targetBinder = runtime.settingsBinder(reflect, settingsClass)
            ?: throw IllegalStateException("Privileged Settings binder is unavailable")
        val provider = hookedContentProvider(targetBinder)
        val extras = Bundle().apply {
            putString(Settings.NameValueTable.VALUE, value)
            putInt(CALL_METHOD_USER_KEY, android.os.Process.myUid() / 100000)
            putBoolean(CALL_METHOD_OVERRIDEABLE_BY_RESTORE_KEY, true)
        }

        reflect.invoke<Bundle>(
            obj = provider,
            name = "call",
            clazz = provider.javaClass,
            parameterTypes = arrayOf(
                AttributionSource::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                Bundle::class.java
            ),
            runtime.settingsResolverContext(context).attributionSource,
            authority,
            callMethod,
            name,
            extras
        )
    }

    private fun getSecureSettingWithHookedProvider(name: String): String? {
        val targetBinder = runtime.settingsBinder(reflect, Settings.Secure::class.java)
            ?: throw IllegalStateException("Privileged Settings.Secure binder is unavailable")
        val provider = hookedContentProvider(targetBinder)
        val extras = Bundle().apply {
            putInt(CALL_METHOD_USER_KEY, android.os.Process.myUid() / 100000)
        }

        val result = reflect.invoke<Bundle>(
            obj = provider,
            name = "call",
            clazz = provider.javaClass,
            parameterTypes = arrayOf(
                AttributionSource::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                Bundle::class.java
            ),
            runtime.settingsResolverContext(context).attributionSource,
            requireNotNull(Settings.Secure.CONTENT_URI.authority),
            CALL_METHOD_GET_SECURE,
            name,
            extras
        )

        return result?.getString(Settings.NameValueTable.VALUE)
    }

    private fun hookedContentProvider(binder: IBinder): Any {
        val contentProviderNative = Class.forName("android.content.ContentProviderNative")
        return reflect.invokeStatic(
            name = "asInterface",
            clazz = contentProviderNative,
            parameterTypes = arrayOf(IBinder::class.java),
            binder
        ) ?: throw IllegalStateException("Failed to create hooked Settings provider")
    }

    override fun getMiuiMediaProjectionMode(packageName: String, uid: Int): Int? = try {
        appOpsService?.checkOperation(OP_MIUI_MEDIA_PROJECTION, uid, packageName)
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Failed to read MIUI MEDIA_PROJECTION via ${runtime.name}")
        null
    }

    override fun setMiuiMediaProjectionMode(packageName: String, uid: Int, mode: Int): Boolean {
        val service = appOpsService ?: return false
        return try {
            service.setMode(OP_MIUI_MEDIA_PROJECTION, uid, packageName, mode)
            val actualMode = service.checkOperation(OP_MIUI_MEDIA_PROJECTION, uid, packageName)
            Timber.tag(TAG).i(
                "Set MIUI MEDIA_PROJECTION via ${runtime.name}: expected=$mode actual=$actualMode"
            )
            actualMode == mode
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to set MIUI MEDIA_PROJECTION via ${runtime.name}")
            false
        }
    }

    override fun setProjectMediaAllowed(packageName: String, uid: Int, allowed: Boolean): Boolean {
        val service = appOpsService ?: return false
        return try {
            service.setMode(
                projectMediaOpCode(),
                uid,
                packageName,
                if (allowed) MODE_ALLOWED else MODE_IGNORED
            )
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to set PROJECT_MEDIA via ${runtime.name}")
            false
        }
    }

    override fun isProjectMediaAllowed(packageName: String, uid: Int): Boolean {
        val service = appOpsService ?: return false
        return try {
            service.checkOperation(projectMediaOpCode(), uid, packageName) == MODE_ALLOWED
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to check PROJECT_MEDIA via ${runtime.name}")
            false
        }
    }

    private fun projectMediaOpCode(): Int {
        val appOpsManager = Class.forName("android.app.AppOpsManager")
        return reflect.invokeStatic(
            name = "strOpToOp",
            clazz = appOpsManager,
            parameterTypes = arrayOf(String::class.java),
            OPSTR_PROJECT_MEDIA
        ) ?: OP_PROJECT_MEDIA
    }
}
