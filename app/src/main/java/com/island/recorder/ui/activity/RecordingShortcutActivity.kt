package com.island.recorder.ui.activity

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.island.recorder.R
import com.island.recorder.domain.device.model.PermissionType
import com.island.recorder.domain.device.provider.PermissionChecker
import com.island.recorder.domain.recording.model.AudioSource
import com.island.recorder.domain.recording.model.RecordingSettings
import com.island.recorder.domain.recording.model.RecordingState
import com.island.recorder.domain.settings.model.AppPreferences
import com.island.recorder.domain.settings.repository.AppSettingsRepository
import com.island.recorder.domain.settings.repository.BooleanSetting
import com.island.recorder.domain.settings.repository.StringSetting
import com.island.recorder.framework.privileged.Authorizer
import com.island.recorder.framework.privileged.DeviceCapability
import com.island.recorder.framework.privileged.RootMode
import com.island.recorder.framework.privileged.ShizukuMode
import com.island.recorder.framework.privileged.provider.DeviceCapabilityProvider
import com.island.recorder.framework.privileged.core.infrastructure.lifecycle.RecordingRootProcessSession
import com.island.recorder.framework.service.RecorderService
import com.island.recorder.ui.common.permission.PermissionRequester
import com.island.recorder.ui.theme.IslandRecorderTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject
import timber.log.Timber
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.VerticalDivider
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

class RecordingShortcutActivity : ComponentActivity() {

    private val appSettingsRepo: AppSettingsRepository by inject()
    private val permissionChecker: PermissionChecker by inject()
    private val appScope: CoroutineScope by inject()
    private val rootProcessSession: RecordingRootProcessSession by inject()
    private var rootProcessSessionOwner = 0L
    private lateinit var permissionRequester: PermissionRequester

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            lifecycleScope.launch {
                val settings = appSettingsRepo.recordingSettingsFlow.first()
                val intent =
                    Intent(this@RecordingShortcutActivity, RecorderService::class.java).apply {
                        action = RecorderService.ACTION_START_RECORDING
                        putExtra(RecorderService.EXTRA_RESULT_CODE, result.resultCode)
                        putExtra(RecorderService.EXTRA_RESULT_DATA, result.data)
                        putExtra(RecorderService.EXTRA_SETTINGS, settings)
                    }
                if (appSettingsRepo.currentPreferences.authorizer == Authorizer.Root) {
                    withContext(Dispatchers.IO) {
                        rootProcessSession.handOffToRecorderService(rootProcessSessionOwner)
                    }
                }
                try {
                    startService(intent)
                } catch (e: Exception) {
                    withContext(Dispatchers.IO) {
                        rootProcessSession.releaseRecorderService()
                    }
                    throw e
                }
                finish()
            }
        } else {
            Timber.w("MediaProjection permission denied")
            finish()
        }
    }

    private fun startRecording() {
        lifecycleScope.launch {
            val settings = appSettingsRepo.recordingSettingsFlow.first()
            if (!hasPostNotificationsPermission()) {
                Timber.w("Post notifications permission is required")
                return@launch
            }
            if (settings.audioSource.usesMicrophone() && !hasRecordAudioPermission()) {
                Timber
                    .w("Record audio permission is required for ${settings.audioSource}")
                return@launch
            }
            launchMediaProjection()
        }
    }

    private fun launchMediaProjection() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun hasRecordAudioPermission(): Boolean =
        permissionChecker.hasPermission(PermissionType.RecordAudio)

    private fun hasPostNotificationsPermission(): Boolean =
        permissionChecker.hasPermission(PermissionType.PostNotifications)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rootProcessSessionOwner = savedInstanceState?.getLong(ROOT_SESSION_OWNER_KEY)
            ?.takeIf { it != 0L }
            ?: rootProcessSession.newShortcutOwner()
        permissionRequester = PermissionRequester(this, permissionChecker)

        if (RecorderService.recordingState.value !is RecordingState.Idle) {
            finish()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val preferences = appSettingsRepo.preferencesFlow.first()
            if (preferences.authorizer == Authorizer.Root) {
                rootProcessSession.warmUpForShortcut(rootProcessSessionOwner)
            }
        }

        var hasRecordAudioPermissionState by mutableStateOf(hasRecordAudioPermission())
        var hasPostNotificationsPermissionState by mutableStateOf(hasPostNotificationsPermission())
        if (!hasRecordAudioPermissionState || !hasPostNotificationsPermissionState) {
            permissionRequester.requestPermissions(
                setOf(PermissionType.RecordAudio, PermissionType.PostNotifications)
            ) { results ->
                hasRecordAudioPermissionState = results[PermissionType.RecordAudio] == true
                hasPostNotificationsPermissionState =
                    results[PermissionType.PostNotifications] == true
            }
        }

        setContent {
            IslandRecorderTheme {
                val appSettingsRepo = koinInject<AppSettingsRepository>()
                val capabilityProvider = koinInject<DeviceCapabilityProvider>()
                val scope = rememberCoroutineScope()
                LaunchedEffect(capabilityProvider) {
                    capabilityProvider.refreshPrivilegeStatus()
                }
                val preferences by appSettingsRepo.preferencesFlow.collectAsStateWithLifecycle(
                    AppPreferences()
                )
                val settings = preferences.recordingSettings
                val capability by capabilityProvider.capabilityFlow.collectAsStateWithLifecycle()
                val showTouchesEnabled = capability.isAuthorizerAvailable(preferences.authorizer)
                val audioSourceRequiresPermission = settings.audioSource.usesMicrophone()
                val permissionWarning = when {
                    !hasPostNotificationsPermissionState ->
                        R.string.permission_notifications_required

                    audioSourceRequiresPermission && !hasRecordAudioPermissionState ->
                        R.string.permission_audio_required

                    else -> null
                }
                val canStart = permissionWarning == null

                var showDialog by remember { mutableStateOf(true) }

                val windowInfo = LocalWindowInfo.current
                val density = LocalDensity.current
                val containerSize = windowInfo.containerSize

                val isLandscape = containerSize.width > containerSize.height
                val windowWidth = with(density) { containerSize.width.toDp() }

                val dialogMaxWidth = if (isLandscape) {
                    (windowWidth - 48.dp)
                        .coerceAtLeast(560.dp)
                        .coerceAtMost(720.dp)
                } else {
                    DialogDefaults.MaxWidth
                }

                WindowDialog(
                    show = showDialog,
                    onDismissRequest = {
                        showDialog = false
                        finish()
                    },
                    title = null,
                    insideMargin = DpSize.Zero,
                    maxWidth = dialogMaxWidth
                ) {
                    if (isLandscape) {
                        // Landscape: left(title+switches) | divider | right(buttons)
                        Row(
                            modifier = Modifier
                                .height(IntrinsicSize.Min)
                                .padding(vertical = DialogContentVerticalPadding)
                        ) {
                            // Left: title + switches
                            Column(
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = stringResource(R.string.dialog_record_title),
                                    style = MiuixTheme.textStyles.title4,
                                    color = MiuixTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = DialogContentHorizontalPadding)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.dialog_record_summary),
                                    style = MiuixTheme.textStyles.body1,
                                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                    modifier = Modifier.padding(horizontal = DialogContentHorizontalPadding)
                                )
                                permissionWarning?.let {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(it),
                                        style = MiuixTheme.textStyles.body1,
                                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                        modifier = Modifier.padding(horizontal = DialogContentHorizontalPadding)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                RecordingAudioSourceSpinner(
                                    settings = settings,
                                    enabled = hasRecordAudioPermissionState,
                                    onSelected = { source ->
                                        scope.launch {
                                            appSettingsRepo.putString(
                                                StringSetting.AudioSource,
                                                source.name
                                            )
                                        }
                                    }
                                )
                                ShowTouchesSwitch(
                                    settings = settings,
                                    enabled = showTouchesEnabled,
                                    onCheckedChange = {
                                        scope.launch {
                                            appSettingsRepo.putBoolean(
                                                BooleanSetting.ShowTouches,
                                                it
                                            )
                                        }
                                    }
                                )
                            }

                            // Divider
                            VerticalDivider(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 16.dp)
                            )

                            // Right: buttons stacked vertically
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(horizontal = DialogContentHorizontalPadding),
                                verticalArrangement = Arrangement.spacedBy(
                                    8.dp,
                                    Alignment.CenterVertically
                                )
                            ) {
                                TextButton(
                                    text = stringResource(R.string.cancel),
                                    onClick = {
                                        showDialog = false
                                        finish()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                TextButton(
                                    text = stringResource(R.string.dialog_record_start),
                                    onClick = {
                                        showDialog = false
                                        startRecording()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = canStart,
                                    colors = ButtonDefaults.textButtonColorsPrimary()
                                )
                            }
                        }
                    } else {
                        // Portrait: original layout
                        Column(
                            modifier = Modifier.padding(vertical = DialogContentVerticalPadding)
                        ) {
                            Text(
                                text = stringResource(R.string.dialog_record_title),
                                style = MiuixTheme.textStyles.title4,
                                color = MiuixTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = DialogContentHorizontalPadding)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.dialog_record_summary),
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                modifier = Modifier.padding(horizontal = DialogContentHorizontalPadding)
                            )
                            permissionWarning?.let {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(it),
                                    style = MiuixTheme.textStyles.body1,
                                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                    modifier = Modifier.padding(horizontal = DialogContentHorizontalPadding)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            RecordingAudioSourceSpinner(
                                settings = settings,
                                enabled = hasRecordAudioPermissionState,
                                onSelected = { source ->
                                    scope.launch {
                                        appSettingsRepo.putString(
                                            StringSetting.AudioSource,
                                            source.name
                                        )
                                    }
                                }
                            )
                            ShowTouchesSwitch(
                                settings = settings,
                                enabled = showTouchesEnabled,
                                onCheckedChange = {
                                    scope.launch {
                                        appSettingsRepo.putBoolean(BooleanSetting.ShowTouches, it)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = DialogContentHorizontalPadding)
                            ) {
                                TextButton(
                                    text = stringResource(R.string.cancel),
                                    onClick = {
                                        showDialog = false
                                        finish()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                TextButton(
                                    text = stringResource(R.string.dialog_record_start),
                                    onClick = {
                                        showDialog = false
                                        startRecording()
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = canStart,
                                    colors = ButtonDefaults.textButtonColorsPrimary()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun AudioSource.usesMicrophone(): Boolean =
        this == AudioSource.MICROPHONE || this == AudioSource.BOTH

    override fun onDestroy() {
        if (!isChangingConfigurations) {
            appScope.launch(Dispatchers.IO) {
                rootProcessSession.releaseShortcut(rootProcessSessionOwner)
            }
        }
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong(ROOT_SESSION_OWNER_KEY, rootProcessSessionOwner)
        super.onSaveInstanceState(outState)
    }

    private companion object {
        private const val ROOT_SESSION_OWNER_KEY = "root_session_owner"
        val DialogContentHorizontalPadding = 24.dp
        val DialogContentVerticalPadding = 24.dp
    }
}

private fun DeviceCapability.isAuthorizerAvailable(authorizer: Authorizer): Boolean =
    when (authorizer) {
        Authorizer.Shizuku -> shizukuMode == ShizukuMode.Authorized
        Authorizer.Root -> rootMode != RootMode.None
    }

@Composable
private fun ShowTouchesSwitch(
    settings: RecordingSettings,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SwitchPreference(
        title = stringResource(R.string.show_touches),
        summary = if (!enabled) stringResource(R.string.permission_privilege) else null,
        checked = settings.showTouches,
        enabled = enabled,
        onCheckedChange = onCheckedChange
    )
}

@Composable
private fun RecordingAudioSourceSpinner(
    settings: RecordingSettings,
    enabled: Boolean,
    onSelected: (AudioSource) -> Unit
) {
    WindowSpinnerPreference(
        title = stringResource(R.string.audio_source),
        summary = if (!enabled) stringResource(R.string.permission_audio_required) else null,
        items = AudioSource.entries.map {
            DropdownItem(text = stringResource(it.labelResId))
        },
        selectedIndex = AudioSource.entries.indexOf(settings.audioSource),
        enabled = enabled,
        onSelectedIndexChange = { onSelected(AudioSource.entries[it]) }
    )
}
