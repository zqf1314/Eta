package io.github.mangi.eta.agent.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mangi.eta.R
import io.github.mangi.eta.data.repository.OverlayAvatarRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 顶部状态胶囊：收起时只显示一句当前步骤，点击展开补充、暂停与停止。
 * 位置固定在状态栏下方居中，不随 Agent 的点击位置移动，避免和目标控件重叠。
 * 状态文字只在语义变化时以上滑淡入切换，流式正文不进入胶囊。
 */
@Composable
internal fun AgentOverlayCapsule(
    state: AgentOverlayState,
    expanded: Boolean,
    yielded: Boolean,
    onToggleExpanded: () -> Unit,
    onCollapsedSettled: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onSupplementModeChange: (Boolean) -> Unit,
    onSupplement: (String) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    var supplementMode by remember { mutableStateOf(false) }
    var supplementText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    fun setSupplementMode(enabled: Boolean) {
        onSupplementModeChange(enabled)
        supplementMode = enabled
        if (!enabled) supplementText = ""
    }

    // 收起键盘后再切换窗口焦点，避免输入法在窗口失焦瞬间闪退或残留。
    fun leaveSupplementMode(then: () -> Unit = {}) {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        scope.launch {
            delay(80)
            setSupplementMode(false)
            then()
        }
    }

    LaunchedEffect(expanded) {
        if (!expanded && supplementMode) leaveSupplementMode()
    }

    // 为 Agent 手势让路：离开时快速上移收小淡出，回来时用弹簧落回，只改绘制层不触发重排。
    val clearance by animateFloatAsState(
        targetValue = if (yielded) 1f else 0f,
        animationSpec = if (yielded) {
            tween(durationMillis = 180, easing = FastOutLinearInEasing)
        } else {
            spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "capsule_clearance",
    )
    val accent by animateColorAsState(overlayPhaseAccent(state.phase), tween(240), label = "capsule_accent")

    // 皮肤只改绘制参数：换皮肤不改尺寸、不改排版，也不会让状态文字变窄。
    val capsuleStyle = OverlayCapsuleSkinRepository.flow()
        .collectAsState(initial = OverlayCapsuleSkin.ANIME)
        .value
        .style()

    // 四态切换原本只有文字和外环换色，安静得像没反应；这里给整颗胶囊打一次状态色脉冲。
    val phaseFlash = remember { Animatable(0f) }
    LaunchedEffect(state.phase) {
        phaseFlash.snapTo(1f)
        phaseFlash.animateTo(0f, tween(durationMillis = 260, easing = LinearEasing))
    }
    val statusText = state.status.localizedText()
    val expandDescription = stringResource(R.string.overlay_capsule_toggle, statusText)
    // 展开进度只驱动绘制层的透明度与位移，不参与测量；控制区用 AnimatedVisibility 只在两端各测一次。
    val expansion by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow),
        label = "capsule_expansion",
        finishedListener = { value -> if (value == 0f) onCollapsedSettled() },
    )

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)) { -it } +
            fadeIn(tween(160)),
        exit = slideOutVertically(tween(160)) { -it } + fadeOut(tween(140)),
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .width(CapsuleWidth)
                    .graphicsLayer {
                        // 点击时轻微回弹，展开后保持原尺寸；只改绘制层，不触发重新布局。
                        val press = 1f - 0.03f * (1f - expansion) * expansion * 4f
                        val shrink = 1f - 0.08f * clearance
                        scaleX = press * shrink
                        scaleY = press * shrink
                        translationY = -12.dp.toPx() * clearance
                        alpha = 1f - clearance
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
                    // 二次元贴纸：粗白外描边画在裁剪之外，胶囊本体再压上去。
                    .shadow(
                        elevation = 10.dp,
                        shape = capsuleStyle.corners,
                        ambientColor = NeonPurple,
                        spotColor = accent,
                    )
                    .drawBehind { drawStickerOutline(capsuleStyle) }
                    .clip(capsuleStyle.corners)
                    .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = capsuleStyle.backgroundAlpha))
                    .background(StickerTintBrush)
                    .drawBehind {
                        drawPhaseFlash(accent, phaseFlash.value)
                        drawStickerSheen()
                        if (capsuleStyle.showCornerStar) drawCornerStar(state.phase)
                    }
                    .border(capsuleStyle.borderWidth, StickerBorderBrush, capsuleStyle.corners),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggleExpanded,
                        )
                        .semantics { contentDescription = expandDescription }
                        .padding(start = 12.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CapsuleAvatar(phase = state.phase, accent = accent)
                    Spacer(Modifier.width(9.dp))
                    Column(modifier = Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = statusText,
                        transitionSpec = {
                            (slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(220))) togetherWith
                                (slideOutVertically(tween(180)) { -it / 2 } + fadeOut(tween(160))) using
                                SizeTransform(clip = true) { _, _ -> snap() }
                        },
                        label = "capsule_status",
                    ) { text ->
                        Text(
                            text = text,
                            color = MiuixTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    ThoughtLine(thought = state.thought.takeIf { state.phase == AgentOverlayPhase.RUNNING }.orEmpty())
                    }
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow), clip = true) +
                        fadeIn(tween(180, delayMillis = 40)),
                    exit = shrinkVertically(spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium), clip = true) +
                        fadeOut(tween(90)),
                ) {
                    Column(
                        modifier = Modifier
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                            .graphicsLayer { translationY = (1f - expansion) * -6.dp.toPx() },
                    ) {
                        if (supplementMode) {
                            OverlaySupplementInput(
                                value = supplementText,
                                onValueChange = { supplementText = it },
                                onCancel = { leaveSupplementMode() },
                                onSend = {
                                    val text = supplementText.trim()
                                    if (text.isNotBlank()) leaveSupplementMode { onSupplement(text) }
                                },
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                            ) {
                                CapsuleAction(
                                    icon = Icons.Rounded.Edit,
                                    label = stringResource(R.string.overlay_supplement),
                                    tint = MiuixTheme.colorScheme.onSurface,
                                    onClick = { setSupplementMode(true) },
                                )
                                if (state.phase == AgentOverlayPhase.RUNNING) {
                                    CapsuleAction(
                                        icon = Icons.Rounded.Pause,
                                        label = stringResource(R.string.overlay_pause),
                                        tint = MiuixTheme.colorScheme.onSurface,
                                        onClick = onPause,
                                    )
                                } else if (state.phase == AgentOverlayPhase.PAUSED) {
                                    CapsuleAction(
                                        icon = Icons.Rounded.PlayArrow,
                                        label = stringResource(R.string.overlay_resume),
                                        tint = MiuixTheme.colorScheme.primary,
                                        onClick = onResume,
                                    )
                                }
                                CapsuleAction(
                                    icon = Icons.Rounded.Stop,
                                    label = stringResource(R.string.action_stop),
                                    tint = MiuixTheme.colorScheme.error,
                                    onClick = onStop,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 胶囊头像：圆形裁切，RUNNING 时匀速自转，一圈 3.6 秒。
 *
 * 只转绘制层，不触发重新布局；暂停与结束时停住，靠外环颜色区分四态。
 * 自转速度刻意放慢：转太快看不清脸，反而像加载图标。
 *
 * 光晕与白描边都用 `drawBehind` 画在布局尺寸之外，不占 Row 的宽度，
 * 文字区不会因为多了装饰而变窄。
 */
@Composable
private fun CapsuleAvatar(phase: AgentOverlayPhase, accent: Color) {
    val context = LocalContext.current
    val fileName by OverlayAvatarRepository.fileFlow().collectAsState(initial = null)
    val image = remember(fileName) { loadAvatarImage(context, fileName) }
    val running = phase == AgentOverlayPhase.RUNNING
    // 两个无限动画常驻，但只在 RUNNING 时读值：非运行态不订阅，也就不会每帧重组。
    val transition = rememberInfiniteTransition(label = "capsule_avatar")
    val spin = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing)),
        label = "capsule_avatar_spin",
    )
    val glow = transition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.34f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "capsule_avatar_glow",
    )
    Box(
        modifier = Modifier
            .size(AvatarSize)
            .drawBehind {
                val radius = size.minDimension / 2f
                // 呼吸光晕：运行时有轻微的“亮着”感，比一根细环明显得多。
                if (running) {
                    val glowRadius = radius + AvatarGlowRadius.toPx()
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = glow.value), Color.Transparent),
                            center = center,
                            radius = glowRadius,
                        ),
                        radius = glowRadius,
                        center = center,
                    )
                }
                // 白描边：贴在霓虹环外沿，做成贴纸那种“双圈”边。
                drawCircle(
                    color = Color.White.copy(alpha = 0.38f),
                    radius = radius + AvatarRingGap.toPx(),
                    center = center,
                    style = Stroke(width = AvatarRingWidth.toPx()),
                )
            }
            .graphicsLayer { rotationZ = if (running) spin.value else 0f }
            .clip(CircleShape)
            .border(AvatarRingWidth, accent.copy(alpha = 0.9f), CircleShape),
    ) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .padding(AvatarInset)
                .clip(CircleShape),
        )
    }
}

/** 读私有目录里的头像；没设置或文件读不出来时回落到应用图标，保证胶囊永远有东西可画。 */
private fun loadAvatarImage(context: Context, fileName: String?): ImageBitmap {
    val file = OverlayAvatarRepository.avatarFile(context, fileName)
    val bitmap = file?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
    return bitmap?.asImageBitmap() ?: fallbackAvatarImage(context)
}

/**
 * 应用图标转位图。
 * 不用 `painterResource(R.mipmap.ic_launcher)`：自适应图标是 XML，那条路画不出来。
 */
private fun fallbackAvatarImage(context: Context): ImageBitmap {
    val drawable = context.applicationInfo.loadIcon(context.packageManager)
    val edge = FALLBACK_AVATAR_PX
    val bitmap = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).also { canvas ->
        drawable.setBounds(0, 0, edge, edge)
        drawable.draw(canvas)
    }
    return bitmap.asImageBitmap()
}

/**
 * 思考尾句：单行、从左侧裁掉旧内容，让最新的字始终可见。
 * 流式增量只改文字，不做逐字动画；行的出现与消失用高度不变的淡入淡出，避免胶囊上下跳动。
 */
@Composable
private fun ThoughtLine(thought: String) {
    val shown = thought.isNotBlank()
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(220), label = "thought_alpha")
    var lastThought by remember { mutableStateOf("") }
    if (shown) lastThought = thought
    Text(
        text = lastThought,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        maxLines = 1,
        // 起始端省略：保留尾部最新的推理，像滚动字幕一样向前推进。
        overflow = TextOverflow.StartEllipsis,
        modifier = Modifier
            .padding(top = 1.dp)
            .graphicsLayer { this.alpha = alpha },
    )
}

/**
 * 展开态的操作按钮：圆形玻璃质感，白描边 + 内高光。
 *
 * 原来的方块圆角按钮和胶囊的贴纸圆角打架，换成圆形后整颗胶囊没有直角。
 */
@Composable
private fun CapsuleAction(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(CapsuleActionSize)
            .shadow(6.dp, CircleShape, ambientColor = NeonPurple, spotColor = NeonPink)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.26f), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(18.dp), tint = tint)
    }
}

private val CapsuleCorner = 22.dp
private val CapsuleWidth = 248.dp

/** 展开态操作按钮直径。 */
private val CapsuleActionSize = 36.dp

/** 头像直径。比原来的 14dp 状态点大一圈，圆形头像要看得清脸。 */
private val AvatarSize = 26.dp

/** 头像与霓虹环之间的缝，露出胶囊底色；贴边画在 26dp 上会显挤。 */
private val AvatarInset = 3.dp

/** 呼吸光晕在头像外的延伸量。 */
private val AvatarGlowRadius = 6.dp

/** 霓虹环粗细，以及外层白描边与霓虹环的间距。 */
private val AvatarRingWidth = 1.dp
private val AvatarRingGap = 1.5.dp

/** 应用图标转位图的边长，按最大绘制尺寸的 4 倍留余量。 */
private const val FALLBACK_AVATAR_PX = 96

private val NeonCyan = Color(0xFF3EE6F0)
private val NeonPurple = Color(0xFF9B6BFF)
private val NeonPink = Color(0xFFFF4FA3)

/** 边框霓虹渐变：左青 → 中紫 → 右粉。 */
private val StickerBorderBrush = Brush.linearGradient(listOf(NeonCyan, NeonPurple, NeonPink))

/** 贴纸底色染的霓虹渐变，只走低透明度，保证状态文字对比度不变。 */
private val StickerTintBrush = Brush.linearGradient(
    listOf(
        NeonPink.copy(alpha = 0.10f),
        NeonPurple.copy(alpha = 0.07f),
        NeonCyan.copy(alpha = 0.10f),
    ),
)

/** 状态切换时整颗胶囊打一次状态色脉冲，260ms 内衰减到零。 */
private fun DrawScope.drawPhaseFlash(accent: Color, amount: Float) {
    if (amount <= 0.01f) return
    drawRoundRect(
        color = accent.copy(alpha = 0.13f * amount),
        cornerRadius = CornerRadius(CapsuleCorner.toPx()),
    )
}

/** 左上角一团白色柔光，让扁平底色有一点塑料贴纸的高光。 */
private fun DrawScope.drawStickerSheen() {
    val radius = (size.height * 1.2f).coerceAtLeast(1f)
    val center = Offset(size.width * 0.16f, -radius * 0.45f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/**
 * 粗白外描边：先画一层比胶囊大 3dp 的白色圆角块，胶囊本体随后压上去，露出来的就是白边。
 *
 * 必须挂在 `clip` 之前，否则会被自身圆角裁掉，只剩一圈发光没有白边。
 */
private fun DrawScope.drawStickerOutline(style: OverlayCapsuleStyle) {
    if (style.outlineWidth <= 0.dp) return
    val w = style.outlineWidth.toPx()
    drawRoundRect(
        color = Color.White.copy(alpha = 0.92f),
        topLeft = Offset(-w, -w),
        size = Size(size.width + w * 2f, size.height + w * 2f),
        cornerRadius = CornerRadius(style.outlineCornerRadius.toPx()),
    )
}

/**
 * 右上角四角星：二次元贴纸的角标。
 *
 * 只用静态透明度区分状态，不额外挂无限动画——胶囊上的常驻动画已经够多了，
 * 再开会挤压 overlay 窗口的帧预算。
 */
private fun DrawScope.drawCornerStar(phase: AgentOverlayPhase) {
    val r = 4.4.dp.toPx()
    val cx = size.width - 10.dp.toPx()
    val cy = 8.dp.toPx()
    val waist = r * 0.22f
    val path = Path().apply {
        moveTo(cx, cy - r)
        quadraticBezierTo(cx + waist, cy - waist, cx + r, cy)
        quadraticBezierTo(cx + waist, cy + waist, cx, cy + r)
        quadraticBezierTo(cx - waist, cy + waist, cx - r, cy)
        quadraticBezierTo(cx - waist, cy - waist, cx, cy - r)
        close()
    }
    val alpha = if (phase == AgentOverlayPhase.RUNNING) 0.95f else 0.42f
    drawPath(path, Color.White.copy(alpha = alpha))
}
