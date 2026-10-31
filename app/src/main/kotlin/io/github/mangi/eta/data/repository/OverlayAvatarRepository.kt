package io.github.mangi.eta.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import io.github.mangi.eta.data.datastore.SettingsDataStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** 导入悬浮胶囊头像时的失败原因，由 UI 层映射成本地化文案。 */
enum class OverlayAvatarImportFailure {
    UNREADABLE_MEDIA,
    DECODE_FAILED,
    WRITE_FAILED,
}

sealed interface OverlayAvatarImportResult {
    data object Success : OverlayAvatarImportResult

    data class Failure(val reason: OverlayAvatarImportFailure) : OverlayAvatarImportResult
}

/**
 * Agent 顶部悬浮胶囊的头像存储。
 *
 * 和聊天背景一样，相册文件不直接引用 URI：
 * 1. 相册原图可能被删除、改名或授权过期，URI 会变成一张打不开的空头像；
 * 2. 原图常见 4000x3000，每次胶囊出现都全尺寸解码会拖慢首帧，也容易 OOM；
 * 3. 头像最终只画在一个 22dp 的圆里，256 像素已经远超屏幕所需。
 */
object OverlayAvatarRepository {
    private const val AVATAR_DIR = "overlay_avatar"
    private const val MAX_EDGE = 256
    private const val MAX_SOURCE_BYTES = 32L * 1024 * 1024
    private const val FILE_PREFIX = "avatar"
    private const val PNG_QUALITY = 100

    fun fileFlow(): Flow<String?> = SettingsDataStore.overlayAvatarFileFlow()

    /** 头像目录，不存在时立即创建。 */
    fun avatarDir(context: Context): File =
        File(context.filesDir, AVATAR_DIR).apply { if (!exists()) mkdirs() }

    /** 解析当前头像文件；文件缺失（被清理或迁移丢失）时返回 null，由调用方回落到应用图标。 */
    fun avatarFile(context: Context, fileName: String?): File? {
        val name = fileName?.takeIf { it.isNotBlank() } ?: return null
        return File(avatarDir(context), name).takeIf { it.isFile && it.length() > 0L }
    }

    /**
     * 把相册里选中的图片导入私有目录，并立刻把文件名写回设置。
     *
     * 导入成功后只保留最新一份文件，避免用户反复换头像把私有目录撑大。
     */
    suspend fun importFromUri(
        context: Context,
        uri: Uri,
    ): OverlayAvatarImportResult = withContext(Dispatchers.IO) {
        val source = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytesCapped(MAX_SOURCE_BYTES) }
        }.getOrNull() ?: return@withContext OverlayAvatarImportResult.Failure(
            OverlayAvatarImportFailure.UNREADABLE_MEDIA,
        )

        val png = runCatching { normalize(source) }.getOrNull()
            ?: return@withContext OverlayAvatarImportResult.Failure(OverlayAvatarImportFailure.DECODE_FAILED)

        val directory = avatarDir(context)
        val fileName = "$FILE_PREFIX-${UUID.randomUUID().toString().take(8)}.png"
        if (runCatching { File(directory, fileName).writeBytes(png) }.isFailure) {
            return@withContext OverlayAvatarImportResult.Failure(OverlayAvatarImportFailure.WRITE_FAILED)
        }

        val previous = SettingsDataStore.overlayAvatarFile()
        SettingsDataStore.setOverlayAvatarFile(fileName)
        // 旧文件和新文件同名概率极低，这里连残留一起清掉，顺手兜住上次写入中断的孤文件。
        directory.listFiles()?.forEach { file ->
            if (file.name != fileName && file.name.startsWith(FILE_PREFIX)) file.delete()
        }
        previous?.takeIf { it != fileName }?.let { File(directory, it).delete() }
        OverlayAvatarImportResult.Success
    }

    /** 清除自定义头像，回落到应用图标。 */
    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        val previous = SettingsDataStore.overlayAvatarFile()
        SettingsDataStore.setOverlayAvatarFile(null)
        previous?.let { File(avatarDir(context), it).delete() }
        avatarDir(context).listFiles()?.forEach { file ->
            if (file.name.startsWith(FILE_PREFIX)) file.delete()
        }
        Unit
    }

    /**
     * 降采样到 [MAX_EDGE] 并居中裁成正方形，再统一重编码成 PNG。
     * 统一格式后，胶囊侧只需 `BitmapFactory.decodeFile`，不用关心 GIF/HEIC/WEBP 这些来源差异。
     */
    private fun normalize(source: ByteArray): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取图片尺寸" }

        val options = BitmapFactory.Options().apply {
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_EDGE) sample *= 2
            inSampleSize = sample
        }
        val decoded = BitmapFactory.decodeByteArray(source, 0, source.size, options)
            ?: error("无法解码图片")
        val square = centerSquare(decoded)
        return try {
            ByteArrayOutputStream().also {
                check(square.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it)) { "图片编码失败" }
            }.toByteArray()
        } finally {
            if (square !== decoded) square.recycle()
            decoded.recycle()
        }
    }

    private fun centerSquare(bitmap: Bitmap): Bitmap {
        val edge = min(bitmap.width, bitmap.height)
        if (edge <= 0) error("图片尺寸无效")
        if (bitmap.width == edge && bitmap.height == edge) return bitmap
        return Bitmap.createBitmap(bitmap, (bitmap.width - edge) / 2, (bitmap.height - edge) / 2, edge, edge)
    }

    /** 读取上限字节；超过上限返回 null，避免一张超大图把内存顶穿。 */
    private fun InputStream.readBytesCapped(limit: Long): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
