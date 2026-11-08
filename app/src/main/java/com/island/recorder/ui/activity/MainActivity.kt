package com.island.recorder.ui.activity

import android.os.Bundle
import android.os.SystemProperties
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.island.recorder.domain.device.model.PermissionType
import com.island.recorder.domain.device.provider.PermissionChecker
import com.island.recorder.domain.settings.model.AppPreferences
import com.island.recorder.domain.settings.repository.AppSettingsRepository
import com.island.recorder.ui.common.permission.PermissionRequester
import com.island.recorder.ui.page.settings.SettingsPage
import com.island.recorder.ui.theme.IslandRecorderTheme
import com.island.recorder.ui.theme.LocalWindowLayoutInfo
import com.island.recorder.ui.theme.rememberWindowLayoutInfo
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity(), KoinComponent {
    private val appSettingsRepository by inject<AppSettingsRepository>()
    private val permissionChecker by inject<PermissionChecker>()
    private lateinit var permissionRequester: PermissionRequester

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()
        var isLoaded = false
        splashScreen.setKeepOnScreenCondition { !isLoaded }

        super.onCreate(savedInstanceState)

        permissionRequester = PermissionRequester(this, permissionChecker)
        permissionRequester.requestPermissions(
            setOf(PermissionType.RecordAudio, PermissionType.PostNotifications)
        ) {
            // Best-effort startup request; feature entry points handle denied permissions.
        }

        setContent {
            val appPreferences by appSettingsRepository.preferencesFlow.collectAsStateWithLifecycle(
                AppPreferences()
            )
            isLoaded = appPreferences.isLoaded

            if (!isLoaded) return@setContent

            val layoutInfo = rememberWindowLayoutInfo()

            CompositionLocalProvider(
                LocalWindowLayoutInfo provides layoutInfo
            ) {
                IslandRecorderTheme {
                    val useGlass = remember {
                        runCatching {
                            SystemProperties.getInt("ro.mi.os.version.code", 0) == 4
                        }.getOrDefault(false)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MiuixTheme.colorScheme.surface)
                    ) {
                        SettingsPage(
                            modifier = Modifier.fillMaxSize(),
                            useGlass = useGlass
                        )
                    }
                }
            }
        }
    }
}
