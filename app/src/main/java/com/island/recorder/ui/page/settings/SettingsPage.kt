package com.island.recorder.ui.page.settings

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.island.recorder.R
import com.island.recorder.domain.device.model.PermissionType
import com.island.recorder.domain.device.provider.PermissionChecker
import com.island.recorder.domain.recording.model.AudioSource
import com.island.recorder.domain.recording.model.FrameRate
import com.island.recorder.domain.recording.model.ScreenOrientation
import com.island.recorder.domain.recording.model.TileStyle
import com.island.recorder.domain.recording.model.VideoBitrate
import com.island.recorder.domain.recording.model.VideoCodec
import com.island.recorder.domain.recording.model.VideoQuality
import com.island.recorder.domain.settings.repository.AppSettingsRepository
import com.island.recorder.domain.settings.repository.BooleanSetting
import com.island.recorder.domain.settings.repository.StringSetting
import com.island.recorder.framework.privileged.Authorizer
import com.island.recorder.framework.privileged.RootMode
import com.island.recorder.framework.privileged.ShizukuMode
import com.island.recorder.framework.storage.SafRecordingStorageProviderImpl
import com.island.recorder.ui.components.MiuixBackButton
import com.island.recorder.ui.theme.getMiuixAppBarColor
import com.island.recorder.ui.theme.recorderMiuixBlurEffect
import com.island.recorder.ui.theme.rememberMiuixBlurBackdrop
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import rikka.shizuku.Shizuku
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun SettingsPage(
    viewModel: SettingsViewModel = koinViewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val appSettingsRepo = koinInject<AppSettingsRepository>()
    val permissionChecker = koinInject<PermissionChecker>()
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState = viewModel.state.collectAsStateWithLifecycle().value
    val currentSettings = uiState.currentSettings
    val storageTreeUri = uiState.storageTreeUri
    val selectedAuthorizer = uiState.selectedAuthorizer
    val capability = uiState.capability
    val isProjectMediaGranted = uiState.isProjectMediaGranted
    var hasRecordAudioPermission by remember {
        mutableStateOf(permissionChecker.hasPermission(PermissionType.RecordAudio))
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            scope.launch {
                appSettingsRepo.putString(StringSetting.StorageTreeUri, uri.toString())
            }
        }
    }

    val (screenW, screenH) = remember(configuration) {
        val wm = context.getSystemService(WindowManager::class.java)
        val bounds = wm.currentWindowMetrics.bounds

        bounds.width() to bounds.height()
    }

    DisposableEffect(Unit) {
        val listener =
            Shizuku.OnRequestPermissionResultListener { _, _ -> viewModel.refreshCapability() }
        viewModel.addShizukuPermissionResultListener(listener)
        onDispose {
            viewModel.removeShizukuPermissionResultListener(listener)
        }
    }

    DisposableEffect(lifecycleOwner, permissionChecker, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasRecordAudioPermission =
                    permissionChecker.hasPermission(PermissionType.RecordAudio)
                viewModel.refreshCapability()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val horizontalSafeInsets =
        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
    val backdrop = rememberMiuixBlurBackdrop(true)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                modifier = Modifier.recorderMiuixBlurEffect(backdrop),
                color = backdrop.getMiuixAppBarColor(),
                title = stringResource(R.string.settings),
                navigationIcon = {
                    MiuixBackButton(onClick = onBack)
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(backdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = horizontalSafeInsets.calculateStartPadding(layoutDirection),
                top = paddingValues.calculateTopPadding(),
                end = horizontalSafeInsets.calculateEndPadding(layoutDirection),
                bottom = 0.dp
            ),
            overscrollEffect = null
        ) {
            item { SmallTitle(text = stringResource(R.string.section_status)) }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    WindowSpinnerPreference(
                        title = stringResource(R.string.authorization_method),
                        summary = stringResource(
                            if (selectedAuthorizer == Authorizer.Shizuku) R.string.authorization_method_shizuku else R.string.authorization_method_root
                        ),
                        items = listOf(
                            DropdownItem(text = stringResource(R.string.authorization_method_shizuku)),
                            DropdownItem(text = stringResource(R.string.authorization_method_root))
                        ),
                        selectedIndex = if (selectedAuthorizer == Authorizer.Shizuku) 0 else 1,
                        onSelectedIndexChange = { index ->
                            scope.launch {
                                appSettingsRepo.putString(
                                    StringSetting.Authorizer,
                                    if (index == 0) Authorizer.Shizuku.name else Authorizer.Root.name
                                )
                            }
                        }
                    )
                    BasicComponent(
                        title = stringResource(R.string.root_status),
                        summary = when (capability.rootMode) {
                            RootMode.None -> stringResource(R.string.root_status_inactive)
                            RootMode.Magisk -> "Magisk"
                            RootMode.KernelSU -> "KernelSU"
                            RootMode.APatch -> "APatch"
                        }
                    )
                    BasicComponent(
                        title = stringResource(R.string.shizuku_status),
                        summary = when (capability.shizukuMode) {
                            ShizukuMode.Authorized -> stringResource(R.string.shizuku_status_authorized)
                            ShizukuMode.NotAuthorized -> stringResource(R.string.shizuku_status_tap_to_authorize)
                            ShizukuMode.NotRunning -> stringResource(R.string.shizuku_status_not_running)
                        },
                        onClick = {
                            if (capability.shizukuMode == ShizukuMode.NotAuthorized) {
                                viewModel.requestShizukuPermission(1001)
                            }
                        }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.grant_project_media),
                        summary = when {
                            !capability.hasPrivilegedOperations -> stringResource(R.string.permission_privilege)
                            isProjectMediaGranted -> stringResource(R.string.project_media_already_granted)
                            else -> stringResource(R.string.grant_project_media_summary)
                        },
                        checked = isProjectMediaGranted,
                        enabled = !isProjectMediaGranted && capability.hasPrivilegedOperations,
                        onCheckedChange = {
                            if (it) {
                                viewModel.setProjectMediaAllowed(allowed = true)
                            }
                        }
                    )
                }
            }

            item { SmallTitle(text = stringResource(R.string.section_recording)) }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    val qualityItems = VideoQuality.entries.map { quality ->
                        val (w, h) = quality.computeDimensions(screenW, screenH)
                        DropdownItem(
                            text = stringResource(
                                R.string.quality_label_format,
                                stringResource(quality.tierLabelResId),
                                w,
                                h
                            )
                        )
                    }
                    WindowSpinnerPreference(
                        title = stringResource(R.string.video_quality),
                        summary = stringResource(R.string.video_quality_summary),
                        items = qualityItems,
                        selectedIndex = VideoQuality.entries.indexOf(currentSettings.videoQuality),
                        onSelectedIndexChange = {
                            scope.launch {
                                appSettingsRepo.putString(
                                    StringSetting.VideoQuality,
                                    VideoQuality.entries[it].name
                                )
                            }
                        }
                    )
                    WindowSpinnerPreference(
                        title = stringResource(R.string.video_bitrate),
                        summary = stringResource(R.string.video_bitrate_summary),
                        items = VideoBitrate.entries.map { DropdownItem(text = stringResource(it.labelResId)) },
                        selectedIndex = VideoBitrate.entries.indexOf(currentSettings.videoBitrate),
                        onSelectedIndexChange = {
                            scope.launch {
                                appSettingsRepo.putString(
                                    StringSetting.VideoBitrate,
                                    VideoBitrate.entries[it].name
                                )
                            }
                        }
                    )
                    WindowSpinnerPreference(
                        title = stringResource(R.string.screen_orientation),
                        summary = stringResource(R.string.screen_orientation_summary),
                        items = ScreenOrientation.entries.map {
                            DropdownItem(
                                text = stringResource(
                                    it.labelResId
                                )
                            )
                        },
                        selectedIndex = ScreenOrientation.entries.indexOf(currentSettings.screenOrientation),
                        onSelectedIndexChange = {
                            scope.launch {
                                appSettingsRepo.putString(
                                    StringSetting.ScreenOrientation,
                                    ScreenOrientation.entries[it].name
                                )
                            }
                        }
                    )
                    WindowSpinnerPreference(
                        title = stringResource(R.string.audio_source),
                        summary = stringResource(
                            if (hasRecordAudioPermission) {
                                R.string.audio_source_summary
                            } else {
                                R.string.permission_audio_required
                            }
                        ),
                        items = AudioSource.entries.map { DropdownItem(text = stringResource(it.labelResId)) },
                        selectedIndex = AudioSource.entries.indexOf(currentSettings.audioSource),
                        enabled = hasRecordAudioPermission,
                        onSelectedIndexChange = {
                            scope.launch {
                                appSettingsRepo.putString(
                                    StringSetting.AudioSource,
                                    AudioSource.entries[it].name
                                )
                            }
                        }
                    )
                    WindowSpinnerPreference(
                        title = stringResource(R.string.frame_rate),
                        summary = stringResource(R.string.frame_rate_summary),
                        items = FrameRate.entries.map { DropdownItem(text = stringResource(it.labelResId)) },
                        selectedIndex = FrameRate.entries.indexOf(currentSettings.frameRate),
                        onSelectedIndexChange = {
                            scope.launch {
                                appSettingsRepo.putString(
                                    StringSetting.FrameRate,
                                    FrameRate.entries[it].name
                                )
                            }
                        }
                    )
                    WindowSpinnerPreference(
                        title = stringResource(R.string.video_codec),
                        summary = stringResource(R.string.video_codec_summary),
                        items = VideoCodec.entries.map { DropdownItem(text = stringResource(it.labelResId)) },
                        selectedIndex = VideoCodec.entries.indexOf(currentSettings.videoCodec),
                        onSelectedIndexChange = {
                            scope.launch {
                                appSettingsRepo.putString(
                                    StringSetting.VideoCodec,
                                    VideoCodec.entries[it].name
                                )
                            }
                        }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.show_touches),
                        summary = if (capability.hasPrivilegedOperations) stringResource(R.string.show_touches_summary)
                        else stringResource(R.string.permission_privilege),
                        checked = currentSettings.showTouches,
                        enabled = capability.hasPrivilegedOperations,
                        onCheckedChange = {
                            scope.launch {
                                appSettingsRepo.putBoolean(
                                    BooleanSetting.ShowTouches,
                                    it
                                )
                            }
                        }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.stop_on_lock_screen),
                        summary = stringResource(R.string.stop_on_lock_screen_summary),
                        checked = currentSettings.stopOnLockScreen,
                        onCheckedChange = {
                            scope.launch {
                                appSettingsRepo.putBoolean(
                                    BooleanSetting.StopOnLockScreen,
                                    it
                                )
                            }
                        }
                    )
                    val shizukuStatusString = when (capability.shizukuMode) {
                        ShizukuMode.NotRunning -> stringResource(R.string.shizuku_status_not_running)
                        ShizukuMode.NotAuthorized -> stringResource(R.string.shizuku_status_not_authorized)
                        ShizukuMode.Authorized -> stringResource(R.string.shizuku_status_authorized)
                    }
                    SwitchPreference(
                        title = stringResource(R.string.bypass_focus_island),
                        summary = stringResource(
                            R.string.bypass_focus_island_summary,
                            shizukuStatusString
                        ),
                        checked = currentSettings.bypassFocusIsland,
                        enabled = true,
                        onCheckedChange = {
                            if (it && capability.shizukuMode == ShizukuMode.NotAuthorized) {
                                viewModel.requestShizukuPermission(1001)
                            }
                            scope.launch {
                                appSettingsRepo.putBoolean(
                                    BooleanSetting.BypassFocusIsland,
                                    it
                                )
                            }
                        }
                    )
                    WindowSpinnerPreference(
                        title = stringResource(R.string.tile_style),
                        summary = stringResource(R.string.tile_style_summary),
                        items = TileStyle.entries.map { style ->
                            DropdownItem(
                                text = stringResource(style.labelResId),
                                icon = { modifier ->
                                    Icon(
                                        painter = painterResource(
                                            if (style == TileStyle.APP_ICON) {
                                                R.drawable.ic_tile_style_app_icon
                                            } else {
                                                R.drawable.ic_record
                                            }
                                        ),
                                        contentDescription = null,
                                        modifier = modifier
                                    )
                                }
                            )
                        },
                        selectedIndex = TileStyle.entries.indexOf(currentSettings.tileStyle),
                        onSelectedIndexChange = {
                            scope.launch {
                                appSettingsRepo.putString(
                                    StringSetting.TileStyle,
                                    TileStyle.entries[it].name
                                )
                            }
                        }
                    )
                }
            }

            item { SmallTitle(text = stringResource(R.string.section_storage)) }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    ArrowPreference(
                        title = stringResource(R.string.storage_path),
                        summary = storageTreeUri.toStorageDisplayPath(),
                        onClick = { folderPicker.launch(null) }
                    )
                }
            }

            item { SmallTitle(text = stringResource(R.string.section_about)) }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    ArrowPreference(
                        title = stringResource(R.string.project_url),
                        summary = stringResource(R.string.project_url_value),
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    "https://github.com/wxxsfxyzm/IslandRecoder".toUri()
                                )
                            )
                        }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.original_project),
                        summary = stringResource(R.string.original_project_value),
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    "https://github.com/Icradle-Innovations-Ltd/FluxRecorder".toUri()
                                )
                            )
                        }
                    )
                }
            }

            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

private fun String.toStorageDisplayPath(): String {
    if (isBlank()) return SafRecordingStorageProviderImpl.DEFAULT_STORAGE_PATH

    val uri = this.toUri()
    if (!DocumentsContract.isTreeUri(uri)) return this

    val treeId = DocumentsContract.getTreeDocumentId(uri)
    if (!treeId.startsWith("primary:")) return this

    val relativePath = treeId.removePrefix("primary:").trim('/')
    return buildString {
        append("/storage/emulated/0")
        if (relativePath.isNotBlank()) {
            append('/')
            append(relativePath)
        }
    }
}
