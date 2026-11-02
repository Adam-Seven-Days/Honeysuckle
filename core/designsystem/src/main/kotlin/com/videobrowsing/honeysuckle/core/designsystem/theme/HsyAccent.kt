package com.videobrowsing.honeysuckle.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/** 一套强调色在亮/暗两档里的四个角色。 */
data class HsyAccentColors(val accent: Color, val onAccent: Color, val container: Color, val onContainer: Color)

/**
 * 主题色预设。
 *
 * 这是"强调色"而不是整套主色:墨黑/白仍负责行动与文字,主题色只落在选中态、
 * 进度、点赞这类需要被看见的位置。盘上只存 [storageId],词表与取色都在本模块。
 */
@Suppress("MagicNumber")
enum class HsyAccent(
    val storageId: String,
    val displayName: String,
    val light: HsyAccentColors,
    val dark: HsyAccentColors,
) {
    AMBER(
        storageId = "amber",
        displayName = "金银花琥珀",
        light = HsyAccentColors(
            accent = Color(0xFFA16207),
            onAccent = Color(0xFFFFFFFF),
            container = Color(0xFFF3E7D3),
            onContainer = Color(0xFF3B2A12),
        ),
        dark = HsyAccentColors(
            accent = Color(0xFFE0B36A),
            onAccent = Color(0xFF3A2A12),
            container = Color(0xFF4A3817),
            onContainer = Color(0xFFF5E3C4),
        ),
    ),
    ;

    companion object {
        val DEFAULT = AMBER

        fun fromStorageId(id: String): HsyAccent = entries.firstOrNull { it.storageId == id } ?: DEFAULT
    }
}
