package com.island.recorder.framework.privileged.core.execution.runtime

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.IActivityManager
import android.content.Context
import android.net.IConnectivityManager
import android.os.IBinder
import android.os.ServiceManager
import com.island.recorder.core.reflection.ReflectionProvider
import com.island.recorder.framework.privileged.core.context.hook.ShizukuHook
import com.island.recorder.framework.privileged.core.context.hook.resolveSettingsBinder
import com.island.recorder.framework.privileged.core.context.wrapper.ShizukuContext
import com.island.recorder.framework.privileged.core.context.wrapper.SystemContext
import timber.log.Timber

private const val TAG = "PrivilegedRuntime"

@SuppressLint("PrivateApi")
internal sealed interface PrivilegedRuntime {
    val name: String

    fun settingsResolverContext(context: Context): Context

    fun activityManager(): IActivityManager

    fun settingsBinder(reflect: ReflectionProvider, settingsClass: Class<*>): IBinder?

    fun connectivityManager(): IConnectivityManager

    fun appOpsBinder(): IBinder?

    fun appOpsManager(context: Context, reflect: ReflectionProvider): AppOpsManager {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val binder = appOpsBinder() ?: return appOps
        val service = Class.forName($$"com.android.internal.app.IAppOpsService$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)

        reflect.setFieldValue(appOps, "mService", appOps.javaClass, service)
        return appOps
    }

    data object ShizukuHooked : PrivilegedRuntime {
        override val name = "ShizukuHook"

        override fun settingsResolverContext(context: Context): Context {
            Timber.tag(TAG).d("Using ShellContextResolver for $name.")
            return ShizukuContext(context)
        }

        override fun activityManager(): IActivityManager {
            Timber.tag(TAG).d("Getting IActivityManager in $name mode.")
            return ShizukuHook.hookedActivityManager
        }

        override fun settingsBinder(
            reflect: ReflectionProvider,
            settingsClass: Class<*>
        ): IBinder? {
            Timber.tag(TAG).d("Getting Settings Binder in $name mode.")
            return ShizukuHook.hookedSettingsBinder(settingsClass)
        }

        override fun connectivityManager(): IConnectivityManager {
            Timber.tag(TAG).d("Getting IConnectivityManager in $name mode.")
            return ShizukuHook.hookedConnectivityManager
        }

        override fun appOpsBinder(): IBinder? {
            Timber.tag(TAG).d("Getting AppOps Binder in $name mode.")
            return ShizukuHook.hookedAppOpsBinder
        }
    }

    class BinderWrapped(
        override val name: String,
        private val binderWrapper: (IBinder) -> IBinder
    ) : PrivilegedRuntime {
        override fun settingsResolverContext(context: Context): Context {
            Timber.tag(TAG).d("Using SystemContextResolver for $name.")
            return SystemContext(context)
        }

        override fun activityManager(): IActivityManager {
            Timber.tag(TAG).d("Getting IActivityManager in $name mode.")
            val original = ServiceManager.getService(Context.ACTIVITY_SERVICE)
            return IActivityManager.Stub.asInterface(binderWrapper(original))
        }

        override fun settingsBinder(
            reflect: ReflectionProvider,
            settingsClass: Class<*>
        ): IBinder? {
            Timber.tag(TAG).d("Getting Settings Binder in $name mode.")
            val original = reflect.resolveSettingsBinder(settingsClass)?.originalBinder
            return original?.let(binderWrapper)
        }

        override fun connectivityManager(): IConnectivityManager {
            Timber.tag(TAG).d("Getting IConnectivityManager in $name mode.")
            val original = ServiceManager.getService(Context.CONNECTIVITY_SERVICE)
            return IConnectivityManager.Stub.asInterface(binderWrapper(original))
        }

        override fun appOpsBinder(): IBinder {
            Timber.tag(TAG).d("Getting AppOps Binder in $name mode.")
            val original = ServiceManager.getService(Context.APP_OPS_SERVICE)
            return binderWrapper(original)
        }
    }
}
