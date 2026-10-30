package io.github.mangi.eta.data.model

import kotlinx.serialization.Serializable

/**
 * 聊天页背景类型。
 *
 * 只覆盖聊天主舞台，不影响其余页面。
 */
@Serializable
enum class ChatBackgroundMode(val persistedValue: String) {
    NONE("none"),
    IMAGE("image"),
    VIDEO("video"),
    ;

    companion object {
        fun fromPersistedValue(value: String?): ChatBackgroundMode =
            entries.firstOrNull { it.persistedValue == value } ?: NONE
    }
}

/** 媒体在背景区域内的填充方式。 */
@Serializable
enum class ChatBackgroundScale(val persistedValue: String) {
    COVER("cover"),
    CONTAIN("contain"),
    ;

    companion object {
        fun fromPersistedValue(value: String?): ChatBackgroundScale =
            entries.firstOrNull { it.persistedValue == value } ?: COVER
    }
}

/**
 * 聊天页背景设置。
 *
 * [mediaFile] 只保存私有目录下的文件名，不保存绝对路径，也不保存相册 URI：
 * 相册里的原文件可能被用户删除或改名，绝对路径在系统升级后也可能变化。
 */
@Serializable
data class ChatBackgroundSettings(
    val mode: ChatBackgroundMode = ChatBackgroundMode.NONE,
    val mediaFile: String? = null,
    /** 可读性遮罩的不透明度，明暗主题下自动换成黑/白。 */
    val dimAmount: Float = DEFAULT_DIM_AMOUNT,
    /** 背景虚化半径，仅对静态图生效（视频走原生层，无法套 Compose 模糊）。 */
    val blurRadiusDp: Float = MIN_BLUR_RADIUS_DP,
    val scale: ChatBackgroundScale = ChatBackgroundScale.COVER,
    val grayscale: Boolean = false,
) {
    val isActive: Boolean
        get() = mode != ChatBackgroundMode.NONE && !mediaFile.isNullOrBlank()

    /** [grayscale] 与 [blurRadiusDp] 只在静态图上有意义。 */
    val supportsImageFilters: Boolean
        get() = mode == ChatBackgroundMode.IMAGE

    fun normalized(): ChatBackgroundSettings = copy(
        dimAmount = dimAmount.coerceIn(MIN_DIM_AMOUNT, MAX_DIM_AMOUNT),
        blurRadiusDp = blurRadiusDp.coerceIn(MIN_BLUR_RADIUS_DP, MAX_BLUR_RADIUS_DP),
        mediaFile = mediaFile?.takeIf { it.isNotBlank() },
    )
}

const val MIN_DIM_AMOUNT = 0f
const val MAX_DIM_AMOUNT = 0.85f
const val DEFAULT_DIM_AMOUNT = 0.35f
const val MIN_BLUR_RADIUS_DP = 0f
const val MAX_BLUR_RADIUS_DP = 24f