package io.github.mangi.eta.agent.overlay

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.data.datastore.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 悬浮胶囊的贴纸皮肤。
 *
 * 皮肤只影响「描边、圆角、角标、底透明度」这几项绘制参数，不改胶囊的尺寸、
 * 排版与交互——换皮肤永远不该让文字变窄或者按钮移位。
 *
 * `wireName` 是持久化用的稳定标识；用户设置里存它而不是 `ordinal`，
 * 后续往中间插新皮肤也不会把老设置读串。
 */
internal enum class OverlayCapsuleSkin(val wireName: String) {
    /** 二次元贴纸：粗白外描边 + 角标星 + 右下收窄的不对称圆角。 */
    ANIME("anime"),

    /** 原版贴纸：只有渐变描边，四角同圆，没有白边和角标。 */
    CLASSIC("classic"),
    ;

    companion object {
        fun fromWire(value: String?): OverlayCapsuleSkin =
            entries.firstOrNull { it.wireName == value } ?: ANIME
    }
}

/** 一种皮肤对应的全部绘制参数。 */
internal data class OverlayCapsuleStyle(
    val outlineWidth: Dp,
    val outlineCornerRadius: Dp,
    val corners: RoundedCornerShape,
    val borderWidth: Dp,
    val showCornerStar: Boolean,
    val backgroundAlpha: Float,
)

internal fun OverlayCapsuleSkin.style(): OverlayCapsuleStyle = when (this) {
    OverlayCapsuleSkin.ANIME -> OverlayCapsuleStyle(
        outlineWidth = 3.dp,
        // 白边比本体外扩，圆角也要跟着加，否则白边内侧会出现尖角。
        outlineCornerRadius = 25.dp,
        corners = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 10.dp, bottomStart = 22.dp),
        borderWidth = 1.5.dp,
        showCornerStar = true,
        backgroundAlpha = 0.96f,
    )

    OverlayCapsuleSkin.CLASSIC -> OverlayCapsuleStyle(
        outlineWidth = 0.dp,
        outlineCornerRadius = 22.dp,
        corners = RoundedCornerShape(22.dp),
        borderWidth = 1.5.dp,
        showCornerStar = false,
        backgroundAlpha = 0.94f,
    )
}

internal object OverlayCapsuleSkinRepository {
    fun flow(): Flow<OverlayCapsuleSkin> =
        SettingsDataStore.overlayCapsuleSkinFlow().map(OverlayCapsuleSkin::fromWire)

    suspend fun set(skin: OverlayCapsuleSkin) = SettingsDataStore.setOverlayCapsuleSkin(skin.wireName)
}
