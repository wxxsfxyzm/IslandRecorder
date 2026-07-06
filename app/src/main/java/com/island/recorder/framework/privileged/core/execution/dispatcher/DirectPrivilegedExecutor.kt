package com.island.recorder.framework.privileged.core.execution.dispatcher

import com.island.recorder.framework.privileged.Authorizer
import com.island.recorder.framework.privileged.core.execution.runtime.DefaultPrivilegedService
import com.island.recorder.framework.privileged.core.execution.runtime.PrivilegedOperations
import com.island.recorder.framework.privileged.core.infrastructure.process.AppProcessTerminal
import com.island.recorder.framework.privileged.core.infrastructure.recycler.ProcessHookRecycler
import com.island.recorder.framework.privileged.core.infrastructure.recycler.ShizukuHookRecycler
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf
import timber.log.Timber

private const val DIRECT_TAG = "DirectPrivileged"

fun <T> useDirectPrivileged(
    authorizer: Authorizer,
    special: (() -> AppProcessTerminal?)? = null,
    action: (PrivilegedOperations) -> T
): T? {
    val koin = GlobalContext.get()
    return when (authorizer) {
        Authorizer.Root -> {
            val terminal = special?.invoke() ?: AppProcessTerminal.Root
            val handle = koin.get<ProcessHookRecycler> { parametersOf(terminal) }.make()
            handle.use {
                action(DefaultPrivilegedService.binderWrapped(name = terminal.runtimeName()) { binder ->
                    it.entity.binderWrapper(binder)
                })
            }
        }

        Authorizer.Shizuku -> {
            koin.get<ShizukuHookRecycler>().make().use {
                action(DefaultPrivilegedService.shizukuHook())
            }
        }
    }
}

fun <T> runDirectPrivilegedOrNull(
    authorizer: Authorizer,
    special: (() -> AppProcessTerminal?)? = null,
    action: (PrivilegedOperations) -> T
): T? =
    try {
        useDirectPrivileged(authorizer = authorizer, special = special, action = action)
    } catch (e: Exception) {
        Timber.tag(DIRECT_TAG).e(e, "Privileged action failed for $authorizer")
        null
    }

private fun AppProcessTerminal.runtimeName(): String =
    when (this) {
        AppProcessTerminal.Root -> "Root"
        AppProcessTerminal.RootSystem -> "RootSystem"
        is AppProcessTerminal.Customize -> "RootCustom"
    }
