package io.github.mangi.eta.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.data.model.ChatBackgroundMode
import io.github.mangi.eta.data.model.ChatBackgroundSettings
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** 导入相册媒体时的失败原因，由 UI 层映射成本地化文案。 */
enum class ChatBackgroundImportFailure {
    UNREADABLE_MEDIA,
    DECODE_FAILED,
    VIDEO_TOO_LARGE,
}

sealed interface ChatBackgroundImportResult {
    data class Success(val settings: ChatBackgroundSettings) : ChatBackgroundImportResult

    data class Failure(val reason: ChatBackgroundImportFailure) : ChatBackgroundImportResult
}

/**
 * 聊天页背景的持久化与媒体导入。
 *
 * 相册文件一律复制到 `filesDir/chat_background/` 再使用：
 * 1. 相册原文件可能被用户删除或改名，直接引用 URI 会变成空白背景；
 * 2. 相册原图常见 4000x3000，每次启动都全尺寸解码有 OOM 风险，
 *    导入时降采样一次，后续渲染成本稳定；
 * 3. 私有目录不依赖 URI 授权，重启后依然可读。
 */
object ChatBackgroundRepository {
    private const val MEDIA_DIR = "chat_background"
    private const val MAX_IMAGE_EDGE = 2160
    private const val MAX_VIDEO_BYTES = 48L * 1024 * 1024
    private const val JPEG_QUALITY = 88

    fun settingsFlow(): Flow<ChatBackgroundSettings> = SettingsDataStore.chatBackgroundSettingsFlow()

    suspend fun settings(): ChatBackgroundSettings = SettingsDataStore.chatBackgroundSettings()

    suspend fun update(transform: (ChatBackgroundSettings) -> ChatBackgroundSettings) {
        SettingsDataStore.setChatBackgroundSettings(transform(SettingsDataStore.chatBackgroundSettings()))
    }

    /** 媒体目录，不存在时立即创建。 */
    fun mediaDir(context: Context): File =
        File(context.filesDir, MEDIA_DIR).apply { if (!exists()) mkdirs() }

    /** 解析当前背景文件；文件缺失（被清理或迁移丢失）时返回 null。 */
    fun mediaFile(context: Context, fileName: String?): File? {
        val name = fileName?.takeIf { it.isNotBlank() } ?: return null
        return File(mediaDir(context), name).takeIf { it.isFile && it.length() > 0L }
    }

    /**
     * 把相册里选中的媒体导入私有目录，并立刻把新设置写回。
     *
     * 导入成功后只保留最新一份文件，避免用户反复换背景把私有目录撑大。
     */
    suspend fun importFromUri(
        context: Context,
        uri: Uri,
        mode: ChatBackgroundMode,
    ): ChatBackgroundImportResult = withContext(Dispatchers.IO) {
        if (mode == ChatBackgroundMode.NONE) {
            return@withContext ChatBackgroundImportResult.Failure(
                ChatBackgroundImportFailure.UNREADABLE_MEDIA,
            )
        }
        val dir = mediaDir(context)
        val fileName = "bg_${System.currentTimeMillis()}" +
            if (mode == ChatBackgroundMode.VIDEO) ".mp4" else ".jpg"
        val target = File(dir, fileName)
        val failure = when (mode) {
            ChatBackgroundMode.VIDEO -> copyVideo(context, uri, target)
            ChatBackgroundMode.IMAGE -> copyImage(context, uri, target)
            ChatBackgroundMode.NONE -> ChatBackgroundImportFailure.UNREADABLE_MEDIA
        }
        if (failure != null) {
            target.delete()
            return@withContext ChatBackgroundImportResult.Failure(failure)
        }

        val previous = SettingsDataStore.chatBackgroundSettings()
        val next = previous.copy(mode = mode, mediaFile = fileName).normalized()
        dir.listFiles()?.forEach { file ->
            if (file.name != fileName) file.delete()
        }
        SettingsDataStore.setChatBackgroundSettings(next)
        ChatBackgroundImportResult.Success(next)
    }

    /** 清空背景设置，并删除私有目录里的媒体文件。 */
    suspend fun clear(context: Context) {
        SettingsDataStore.setChatBackgroundSettings(
            SettingsDataStore.chatBackgroundSettings().copy(
                mode = ChatBackgroundMode.NONE,
                mediaFile = null,
            ),
        )
        withContext(Dispatchers.IO) {
            mediaDir(context).listFiles()?.forEach { it.delete() }
        }
    }

    private fun copyImage(context: Context, uri: Uri, target: File): ChatBackgroundImportFailure? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val opened = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
        }.getOrNull()
        if (opened == null && bounds.outWidth <= 0) {
            return ChatBackgroundImportFailure.UNREADABLE_MEDIA
        }
        val longestEdge = max(bounds.outWidth, bounds.outHeight)
        if (longestEdge <= 0) return ChatBackgroundImportFailure.DECODE_FAILED

        var sampleSize = 1
        while (longestEdge / sampleSize > MAX_IMAGE_EDGE) sampleSize *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        }.getOrNull() ?: return ChatBackgroundImportFailure.DECODE_FAILED

        return try {
            val written = runCatching {
                target.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
            }.getOrDefault(false)
            if (written) null else ChatBackgroundImportFailure.DECODE_FAILED
        } finally {
            bitmap.recycle()
        }
    }

    private fun copyVideo(context: Context, uri: Uri, target: File): ChatBackgroundImportFailure? {
        val declaredLength = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull() ?: -1L
        if (declaredLength > MAX_VIDEO_BYTES) {
            return ChatBackgroundImportFailure.VIDEO_TOO_LARGE
        }
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)
        if (!copied) return ChatBackgroundImportFailure.UNREADABLE_MEDIA
        if (target.length() > MAX_VIDEO_BYTES) {
            return ChatBackgroundImportFailure.VIDEO_TOO_LARGE
        }
        return if (target.length() > 0L) null else ChatBackgroundImportFailure.UNREADABLE_MEDIA
    }
}