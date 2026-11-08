package com.island.recorder.ui.page.settings

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.island.recorder.ui.theme.IslandTheme
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassDefaults
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import top.yukonga.miuix.kmp.glass.GlassPopupDefaults
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference

@Composable
internal fun SettingsDropdownPreference(
    title: String,
    items: List<DropdownItem>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    useGlass: Boolean,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true
) {
    if (useGlass) {
        val selectedIcon = items.getOrNull(selectedIndex)?.icon
        GlassDropdownPreference(
            title = title,
            summary = summary,
            items = items.map { it.text },
            selectedIndex = selectedIndex,
            onSelectedIndexChange = onSelectedIndexChange,
            backdrop = backdrop,
            modifier = modifier,
            enabled = enabled,
            visuals = GlassPopupDefaults.visuals(style = GlassDefaults.style(IslandTheme.isDark)),
            startAction = if (selectedIcon != null) {
                { selectedIcon(Modifier.size(24.dp)) }
            } else {
                null
            }
        )
    } else {
        WindowSpinnerPreference(
            title = title,
            summary = summary,
            items = items,
            selectedIndex = selectedIndex,
            onSelectedIndexChange = onSelectedIndexChange,
            modifier = modifier,
            enabled = enabled
        )
    }
}
