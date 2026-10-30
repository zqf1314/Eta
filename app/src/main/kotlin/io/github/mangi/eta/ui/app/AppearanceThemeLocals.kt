package io.github.mangi.eta.ui.app

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density
import io.github.mangi.eta.data.model.AppearanceSettings
import io.github.mangi.eta.data.model.AppearanceTopBarBlurStyle
import io.github.mangi.eta.data.model.DEFAULT_INPUT_BAR_ALPHA
import io.github.mangi.eta.data.model.DEFAULT_SUGGESTION_ALPHA
import io.github.mangi.eta.data.model.DEFAULT_TOP_BAR_ALPHA

internal val LocalAppearanceSettings = staticCompositionLocalOf { AppearanceSettings() }
internal val LocalBlurEnabled = staticCompositionLocalOf { true }
internal val LocalTopBarBlurStyle = staticCompositionLocalOf { AppearanceTopBarBlurStyle.GAUSSIAN }
internal val LocalPlatformDensity = staticCompositionLocalOf<Density?> { null }

/**
 * 聊天页是否正铺着自定义背景（图片或视频）。
 *
 * 背景层被放到了 AppShell 的根容器里，才能铺满整屏（顶栏、输入栏都压在它上面）。
 * 因此顶栏和输入栏需要知道当前有没有背景，才能把自身改成半透明/透明，
 * 否则会出现"壁纸被上下两条不透明栏夹成相框"的效果。
 */
internal val LocalChatBackgroundActive = staticCompositionLocalOf { false }

/**
 * 壁纸浮层（顶栏、输入栏）的不透明度。
 *
 * 顶栏与输入栏的透明度由用户在设置里分别调整，这里一次性往下发，
 * 避免每一层都去订阅一遍 DataStore。仅当 [LocalChatBackgroundActive] 为真时有意义。
 */
internal data class ChatWallpaperBarAlphas(
    val topBar: Float = DEFAULT_TOP_BAR_ALPHA,
    val inputBar: Float = DEFAULT_INPUT_BAR_ALPHA,
    val suggestion: Float = DEFAULT_SUGGESTION_ALPHA,
)

internal val LocalChatWallpaperBarAlphas = staticCompositionLocalOf { ChatWallpaperBarAlphas() }