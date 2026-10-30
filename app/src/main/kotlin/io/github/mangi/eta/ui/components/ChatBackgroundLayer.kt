package io.github.mangi.eta.ui.components

import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.mangi.eta.data.model.ChatBackgroundMode
import io.github.mangi.eta.data.model.ChatBackgroundScale
import io.github.mangi.eta.data.model.ChatBackgroundSettings
import io.github.mangi.eta.data.repository.ChatBackgroundRepository
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 聊天主舞台的背景层。
 *
 * 只负责铺满自身区域，不参与任何布局测量，因此可以直接作为 [Box] 的第一个子项
 * 垫在消息列表和输入栏下面。未配置背景时什么都不画，不产生额外合成层。
 */
@Composable
internal fun ChatBackgroundLayer(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings by remember {
        ChatBackgroundRepository.settingsFlow()
    }.collectAsState(initial = ChatBackgroundSettings())

    if (!settings.isActive) return

    val mediaFile = remember(settings.mediaFile) {
        ChatBackgroundRepository.mediaFile(context, settings.mediaFile)
    } ?: return

    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
        // 视频解码首帧前会露出容器底色，先垫一层不透明主题背景，避免闪黑。
        Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background))

        when (settings.mode) {
            ChatBackgroundMode.IMAGE -> ImageBackground(
                file = mediaFile,
                settings = settings,
                modifier = Modifier.fillMaxSize(),
            )
            ChatBackgroundMode.VIDEO -> VideoBackground(
                file = mediaFile,
                scale = settings.scale,
                modifier = Modifier.fillMaxSize(),
            )
            ChatBackgroundMode.NONE -> Unit
        }

        // 可读性遮罩：亮色主题压白、深色主题压黑，保证气泡间文字区域始终有对比度。
        val dark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    if (dark) Color.Black.copy(alpha = settings.dimAmount)
                    else Color.White.copy(alpha = settings.dimAmount),
                ),
        )
    }
}

@Composable
private fun ImageBackground(
    file: File,
    settings: ChatBackgroundSettings,
    modifier: Modifier = Modifier,
) {
    val image by produceState<ImageBitmap?>(initialValue = null, file.path) {
        value = withContext(Dispatchers.IO) { decodeScaledBitmap(file) }
    }
    val bitmap = image ?: return

    val contentScale = if (settings.scale == ChatBackgroundScale.COVER) {
        ContentScale.Crop
    } else {
        ContentScale.Fit
    }
    val colorFilter = if (settings.grayscale) {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    } else {
        null
    }
    val blurModifier = if (settings.blurRadiusDp > 0f) {
        Modifier.blur(settings.blurRadiusDp.dp, BlurredEdgeTreatment.Unbounded)
    } else {
        Modifier
    }

    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = contentScale,
        colorFilter = colorFilter,
        modifier = modifier.then(blurModifier),
    )
}

/**
 * 视频背景。
 *
 * 刻意不用 SurfaceView：SurfaceView 是独立图层，和 Compose 的 RenderNode 合成顺序
 * 互相打架，滚动时会闪、会被内容遮挡。TextureView 在普通 View 图层内合成，稳定。
 *
 * 项目当前没有任何 media3/ExoPlayer 依赖，这里直接用平台 MediaPlayer，
 * 避免为了一个背景功能引入整套播放栈（Release 体积与依赖风险都更划算）。
 */
@Composable
private fun VideoBackground(
    file: File,
    scale: ChatBackgroundScale,
    modifier: Modifier = Modifier,
) {
    // Holder 故意不用 State：TextureView 实例在组合期由 factory 写入，
    // 写 State 会触发「组合期间修改状态」的额外重组。
    val viewHolder = remember { arrayOfNulls<TextureView>(1) }
    var surfaceTexture by remember { mutableStateOf<SurfaceTexture?>(null) }
    var videoSize by remember { mutableStateOf(IntSize.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextureView(context).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        surfaceTexture = surface
                        viewportSize = IntSize(width, height)
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        viewportSize = IntSize(width, height)
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        surfaceTexture = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                }
                viewHolder[0] = this
            }
        },
    )

    // TextureView 默认把视频缓冲直接拉伸到控件尺寸，等比内容会变形，
    // 这里按 COVER/CONTAIN 换算缩放系数，等价于 ImageView 的 centerCrop/centerInside。
    LaunchedEffect(scale, videoSize, viewportSize) {
        val view = viewHolder[0] ?: return@LaunchedEffect
        if (videoSize.width <= 0 || videoSize.height <= 0) return@LaunchedEffect
        if (viewportSize.width <= 0 || viewportSize.height <= 0) return@LaunchedEffect
        val videoWidth = videoSize.width.toFloat()
        val videoHeight = videoSize.height.toFloat()
        val viewWidth = viewportSize.width.toFloat()
        val viewHeight = viewportSize.height.toFloat()
        val fit = if (scale == ChatBackgroundScale.COVER) {
            max(viewWidth / videoWidth, viewHeight / videoHeight)
        } else {
            min(viewWidth / videoWidth, viewHeight / videoHeight)
        }
        val matrix = Matrix().apply {
            val scaleX = videoWidth * fit / viewWidth
            val scaleY = videoHeight * fit / viewHeight
            setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
        }
        view.setTransform(matrix)
    }

    surfaceTexture?.let { surface ->
        VideoPlayerHost(
            file = file,
            surfaceTexture = surface,
            onVideoSizeChanged = { width, height -> videoSize = IntSize(width, height) },
        )
    }
}

/**
 * 把 MediaPlayer 的生命周期绑到当前 SurfaceTexture 上。
 *
 * 单拆一个 Composable 而不是在 [VideoBackground] 里提前 return，
 * 是为了保证副作用只在 surface 可用时才注册、销毁时一定成对释放。
 */
@Composable
private fun VideoPlayerHost(
    file: File,
    surfaceTexture: SurfaceTexture,
    onVideoSizeChanged: (Int, Int) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(file, surfaceTexture, lifecycleOwner) {
        val player = MediaPlayer()
        val videoSurface = Surface(surfaceTexture)
        var prepared = false
        runCatching {
            player.setDataSource(file.absolutePath)
            player.isLooping = true
            // 背景视频永远静音：聊天场景里突然出声是事故而不是效果。
            player.setVolume(0f, 0f)
            player.setSurface(videoSurface)
        }
        player.setOnVideoSizeChangedListener { _, width, height ->
            if (width > 0 && height > 0) onVideoSizeChanged(width, height)
        }
        player.setOnPreparedListener { media ->
            prepared = true
            media.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            runCatching { media.start() }
        }
        player.setOnErrorListener { _, _, _ -> true }
        runCatching { player.prepareAsync() }

        val observer = LifecycleEventObserver { _, event ->
            if (!prepared) return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_START -> runCatching {
                    if (!player.isPlaying) player.start()
                }
                Lifecycle.Event.ON_STOP -> runCatching { player.pause() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { player.stop() }
            player.release()
            videoSurface.release()
        }
    }
}

/**
 * 按文件原始尺寸降采样解码。
 *
 * 导入阶段已经把长边压到 2160，这里再兜一次，避免历史文件或手动塞进私有目录的
 * 超大图把聊天页拖进 OOM。
 */
private fun decodeScaledBitmap(file: File): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    val longestEdge = max(bounds.outWidth, bounds.outHeight)
    if (longestEdge <= 0) return null
    var sampleSize = 1
    while (longestEdge / sampleSize > MAX_DECODE_EDGE) sampleSize *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return runCatching {
        BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()
    }.getOrNull()
}

private const val MAX_DECODE_EDGE = 2880