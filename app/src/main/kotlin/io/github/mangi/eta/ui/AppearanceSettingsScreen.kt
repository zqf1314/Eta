package io.github.mangi.eta.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.AppearanceAccentColor
import io.github.mangi.eta.data.model.AppearancePaletteStyle
import io.github.mangi.eta.data.model.AppearanceSettings
import io.github.mangi.eta.data.model.AppearanceThemeMode
import io.github.mangi.eta.data.model.AppearanceTopBarBlurStyle
import io.github.mangi.eta.data.model.ChatBackgroundMode
import io.github.mangi.eta.data.model.ChatBackgroundScale
import io.github.mangi.eta.data.model.ChatBackgroundSettings
import io.github.mangi.eta.data.model.MAX_DIM_AMOUNT
import io.github.mangi.eta.data.model.MAX_BLUR_RADIUS_DP
import io.github.mangi.eta.data.model.MAX_BAR_ALPHA
import io.github.mangi.eta.data.model.MIN_BAR_ALPHA
import io.github.mangi.eta.data.model.MAX_INTERFACE_SCALE
import io.github.mangi.eta.data.model.MIN_BLUR_RADIUS_DP
import io.github.mangi.eta.data.model.MIN_DIM_AMOUNT
import io.github.mangi.eta.data.model.MIN_INTERFACE_SCALE
import io.github.mangi.eta.data.model.normalizeInterfaceScale
import io.github.mangi.eta.data.repository.AppearanceSettingsRepository
import io.github.mangi.eta.data.repository.ChatBackgroundImportFailure
import io.github.mangi.eta.data.repository.ChatBackgroundImportResult
import io.github.mangi.eta.data.repository.ChatBackgroundRepository
import io.github.mangi.eta.ui.app.LocalAppearanceSettings
import io.github.mangi.eta.ui.components.EtaArrowPreference
import io.github.mangi.eta.ui.components.EtaCard
import io.github.mangi.eta.ui.components.EtaOverlayDropdownPreference
import io.github.mangi.eta.ui.components.EtaPreferenceDivider
import io.github.mangi.eta.ui.components.EtaPreferenceGroup
import io.github.mangi.eta.ui.components.EtaPreferenceGroupTitle
import io.github.mangi.eta.ui.components.EtaSwitchPreference
import io.github.mangi.eta.ui.components.EtaWindowDialog
import io.github.mangi.eta.ui.components.MiuixDialogActions
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AppearanceSettingsScreen(onBack: () -> Unit) {
    val appearance = LocalAppearanceSettings.current
    val coroutineScope = rememberCoroutineScope()
    var scaleDraft by remember(appearance.interfaceScale) {
        mutableFloatStateOf(appearance.interfaceScale * 100f)
    }
    var showScaleDialog by remember { mutableStateOf(false) }
    var scaleInput by remember { mutableStateOf("") }
    val blurSupported = isRuntimeShaderSupported()

    fun update(transform: (AppearanceSettings) -> AppearanceSettings) {
        coroutineScope.launch {
            AppearanceSettingsRepository.update(transform)
        }
    }

    // ---- 聊天页背景 ----
    val context = LocalContext.current
    val chatBackground by remember {
        ChatBackgroundRepository.settingsFlow()
    }.collectAsState(initial = ChatBackgroundSettings())
    var dimDraft by remember(chatBackground.dimAmount) {
        mutableFloatStateOf(chatBackground.dimAmount * 100f)
    }
    var blurDraft by remember(chatBackground.blurRadiusDp) {
        mutableFloatStateOf(chatBackground.blurRadiusDp)
    }
    var topBarAlphaDraft by remember(chatBackground.topBarAlpha) {
        mutableFloatStateOf(chatBackground.topBarAlpha * 100f)
    }
    var inputBarAlphaDraft by remember(chatBackground.inputBarAlpha) {
        mutableFloatStateOf(chatBackground.inputBarAlpha * 100f)
    }
    // 选择器的结果回调跑在 DataStore 写入完成之前，读不到刚刚切换的 mode，
    // 因此单独记住本次要导入的媒体类型。
    var pendingImportMode by remember { mutableStateOf(ChatBackgroundMode.IMAGE) }

    fun updateBackground(transform: (ChatBackgroundSettings) -> ChatBackgroundSettings) {
        coroutineScope.launch { ChatBackgroundRepository.update(transform) }
    }

    fun importPicked(uri: Uri?) {
        if (uri == null) return
        val mode = pendingImportMode
        coroutineScope.launch {
            when (val result = ChatBackgroundRepository.importFromUri(context, uri, mode)) {
                is ChatBackgroundImportResult.Success -> Unit
                is ChatBackgroundImportResult.Failure ->
                    Toast.makeText(context, result.reason.messageRes(), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> importPicked(uri) }
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> importPicked(uri) }

    fun launchBackgroundPicker(mode: ChatBackgroundMode) {
        pendingImportMode = mode
        when (mode) {
            ChatBackgroundMode.IMAGE -> imagePicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
            ChatBackgroundMode.VIDEO -> videoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
            )
            ChatBackgroundMode.NONE -> Unit
        }
    }

    val backgroundModes = ChatBackgroundMode.entries
    val backgroundModeLabels = listOf(
        stringResource(R.string.chat_background_mode_none),
        stringResource(R.string.chat_background_mode_image),
        stringResource(R.string.chat_background_mode_video),
    )
    val backgroundScales = ChatBackgroundScale.entries
    val backgroundScaleLabels = listOf(
        stringResource(R.string.chat_background_scale_cover),
        stringResource(R.string.chat_background_scale_contain),
    )
    val backgroundPickSummary = when {
        !chatBackground.isActive -> stringResource(R.string.chat_background_pick_empty)
        chatBackground.mode == ChatBackgroundMode.VIDEO ->
            stringResource(R.string.chat_background_pick_video)
        else -> stringResource(R.string.chat_background_pick_image)
    }
    val backgroundEnabled = chatBackground.mode != ChatBackgroundMode.NONE

    fun commitScale(percent: Float) {
        val scale = normalizeInterfaceScale(percent.roundToInt() / 100f)
        scaleDraft = scale * 100f
        update { current -> current.copy(interfaceScale = scale) }
    }

    val themeModes = AppearanceThemeMode.entries
    val themeModeLabels = listOf(
        stringResource(R.string.appearance_theme_system),
        stringResource(R.string.appearance_theme_light),
        stringResource(R.string.appearance_theme_dark),
    )
    val paletteStyles = AppearancePaletteStyle.entries
    val paletteLabels = listOf(
        stringResource(R.string.appearance_palette_tonal_spot),
        stringResource(R.string.appearance_palette_neutral),
        stringResource(R.string.appearance_palette_vibrant),
        stringResource(R.string.appearance_palette_expressive),
        stringResource(R.string.appearance_palette_rainbow),
        stringResource(R.string.appearance_palette_fruit_salad),
        stringResource(R.string.appearance_palette_monochrome),
        stringResource(R.string.appearance_palette_fidelity),
        stringResource(R.string.appearance_palette_content),
    )
    val accentColors = AppearanceAccentColor.entries
    val accentLabels = listOf(
        stringResource(R.string.appearance_accent_system),
        stringResource(R.string.appearance_accent_blue),
        stringResource(R.string.appearance_accent_purple),
        stringResource(R.string.appearance_accent_pink),
        stringResource(R.string.appearance_accent_red),
        stringResource(R.string.appearance_accent_orange),
        stringResource(R.string.appearance_accent_yellow),
        stringResource(R.string.appearance_accent_green),
        stringResource(R.string.appearance_accent_teal),
    )
    val blurStyles = AppearanceTopBarBlurStyle.entries
    val blurStyleLabels = listOf(
        stringResource(R.string.appearance_blur_style_gaussian),
        stringResource(R.string.appearance_blur_style_progressive),
    )
    val blurStyleSummaries = listOf(
        stringResource(R.string.appearance_blur_style_gaussian_summary),
        stringResource(R.string.appearance_blur_style_progressive_summary),
    )

    MiuixScaffoldPage(
        title = stringResource(R.string.appearance_title),
        onBack = onBack,
    ) {
        item(key = "appearance_color_title") {
            EtaPreferenceGroupTitle(text = stringResource(R.string.appearance_group_color))
        }
        item(key = "appearance_color_card") {
            EtaPreferenceGroup(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                EtaOverlayDropdownPreference(
                    title = stringResource(R.string.appearance_theme_mode),
                    summary = themeModeLabels[appearance.themeMode.ordinal],
                    items = themeModeLabels,
                    selectedIndex = appearance.themeMode.ordinal,
                    onSelectedIndexChange = { index ->
                        themeModes.getOrNull(index)?.let { mode ->
                            update { current -> current.copy(themeMode = mode) }
                        }
                    },
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaSwitchPreference(
                    title = stringResource(R.string.appearance_monet),
                    summary = stringResource(R.string.appearance_monet_summary),
                    checked = appearance.monetEnabled,
                    onCheckedChange = { enabled ->
                        update { current -> current.copy(monetEnabled = enabled) }
                    },
                )
                AnimatedVisibility(
                    visible = appearance.monetEnabled,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                ) {
                    Column {
                        EtaPreferenceDivider(hasLeading = false)
                        EtaOverlayDropdownPreference(
                            title = stringResource(R.string.appearance_palette_style),
                            summary = paletteLabels[appearance.paletteStyle.ordinal],
                            items = paletteLabels,
                            selectedIndex = appearance.paletteStyle.ordinal,
                            onSelectedIndexChange = { index ->
                                paletteStyles.getOrNull(index)?.let { style ->
                                    update { current -> current.copy(paletteStyle = style) }
                                }
                            },
                        )
                        EtaPreferenceDivider(hasLeading = false)
                        EtaOverlayDropdownPreference(
                            title = stringResource(R.string.appearance_accent_color),
                            summary = accentLabels[appearance.accentColor.ordinal],
                            items = accentLabels,
                            selectedIndex = appearance.accentColor.ordinal,
                            onSelectedIndexChange = { index ->
                                accentColors.getOrNull(index)?.let { accent ->
                                    update { current -> current.copy(accentColor = accent) }
                                }
                            },
                        )
                        EtaPreferenceDivider(hasLeading = false)
                        EtaSwitchPreference(
                            title = stringResource(R.string.appearance_pure_black),
                            summary = stringResource(R.string.appearance_pure_black_summary),
                            checked = appearance.pureBlackEnabled,
                            onCheckedChange = { enabled ->
                                update { current -> current.copy(pureBlackEnabled = enabled) }
                            },
                        )
                    }
                }
            }
        }

        item(key = "appearance_interface_title") {
            EtaPreferenceGroupTitle(text = stringResource(R.string.appearance_group_interface))
        }
        item(key = "appearance_interface_card") {
            EtaPreferenceGroup(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                EtaSwitchPreference(
                    title = stringResource(R.string.appearance_blur),
                    summary = stringResource(R.string.appearance_blur_summary),
                    checked = appearance.blurEnabled && blurSupported,
                    onCheckedChange = { enabled ->
                        update { current -> current.copy(blurEnabled = enabled) }
                    },
                    enabled = blurSupported,
                )
                AnimatedVisibility(
                    visible = appearance.blurEnabled && blurSupported,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                ) {
                    // AnimatedVisibility 的内容按层叠摆放，分割线和行必须包进 Column 才会上下排列。
                    Column {
                        EtaPreferenceDivider(hasLeading = false)
                        EtaOverlayDropdownPreference(
                            title = stringResource(R.string.appearance_blur_style),
                            summary = blurStyleSummaries[appearance.topBarBlurStyle.ordinal],
                            items = blurStyleLabels,
                            selectedIndex = appearance.topBarBlurStyle.ordinal,
                            onSelectedIndexChange = { index ->
                                blurStyles.getOrNull(index)?.let { style ->
                                    update { current -> current.copy(topBarBlurStyle = style) }
                                }
                            },
                        )
                    }
                }
                EtaPreferenceDivider(hasLeading = false)
                EtaSwitchPreference(
                    title = stringResource(R.string.appearance_swipe_dismiss),
                    summary = stringResource(R.string.appearance_swipe_dismiss_summary),
                    checked = appearance.swipeDismissEnabled,
                    onCheckedChange = { enabled ->
                        update { current -> current.copy(swipeDismissEnabled = enabled) }
                    },
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaSwitchPreference(
                    title = stringResource(R.string.appearance_predictive_back),
                    summary = stringResource(R.string.appearance_predictive_back_summary),
                    checked = appearance.predictiveBackEnabled,
                    onCheckedChange = { enabled ->
                        update { current -> current.copy(predictiveBackEnabled = enabled) }
                    },
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaArrowPreference(
                    title = stringResource(R.string.appearance_interface_scale),
                    summary = stringResource(R.string.appearance_interface_scale_summary),
                    endActions = {
                        Text(
                            text = "${scaleDraft.roundToInt()}%",
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                    },
                    bottomAction = {
                        Slider(
                            value = scaleDraft.coerceIn(
                                MIN_INTERFACE_SCALE * 100f,
                                MAX_INTERFACE_SCALE * 100f,
                            ),
                            onValueChange = { scaleDraft = it },
                            modifier = Modifier.fillMaxWidth(),
                            valueRange = (MIN_INTERFACE_SCALE * 100f)..(MAX_INTERFACE_SCALE * 100f),
                            onValueChangeFinished = { commitScale(scaleDraft) },
                            showKeyPoints = true,
                            keyPoints = listOf(80f, 90f, 100f, 110f),
                            magnetThreshold = 0.01f,
                            hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                        )
                    },
                    onClick = {
                        scaleInput = scaleDraft.roundToInt().toString()
                        showScaleDialog = true
                    },
                    holdDownState = showScaleDialog,
                )
            }
        }

        item(key = "appearance_chat_background_title") {
            EtaPreferenceGroupTitle(text = stringResource(R.string.appearance_group_chat_background))
        }
        item(key = "appearance_chat_background_card") {
            EtaPreferenceGroup(
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
            ) {
                EtaOverlayDropdownPreference(
                    title = stringResource(R.string.chat_background_mode),
                    summary = backgroundModeLabels[chatBackground.mode.ordinal],
                    items = backgroundModeLabels,
                    selectedIndex = chatBackground.mode.ordinal,
                    onSelectedIndexChange = { index ->
                        backgroundModes.getOrNull(index)?.let { mode ->
                            if (mode != chatBackground.mode) {
                                // 图片和视频文件不能互相顶替，切换类型时清空旧媒体，
                                // 顺手把选择器拉起来，省掉用户再点一次。
                                updateBackground { it.copy(mode = mode, mediaFile = null) }
                                if (mode != ChatBackgroundMode.NONE) launchBackgroundPicker(mode)
                            }
                        }
                    },
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaArrowPreference(
                    title = stringResource(R.string.chat_background_pick),
                    summary = backgroundPickSummary,
                    enabled = backgroundEnabled,
                    onClick = { launchBackgroundPicker(chatBackground.mode) },
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaArrowPreference(
                    title = stringResource(R.string.chat_background_clear),
                    enabled = chatBackground.isActive,
                    onClick = {
                        coroutineScope.launch { ChatBackgroundRepository.clear(context) }
                    },
                )
                AnimatedVisibility(
                    visible = backgroundEnabled,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                ) {
                    Column {
                        EtaPreferenceDivider(hasLeading = false)
                        EtaArrowPreference(
                            title = stringResource(R.string.chat_background_dim),
                            summary = stringResource(R.string.chat_background_dim_summary),
                            endActions = {
                                Text(
                                    text = "${dimDraft.roundToInt()}%",
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                )
                            },
                            bottomAction = {
                                Slider(
                                    value = dimDraft.coerceIn(
                                        MIN_DIM_AMOUNT * 100f,
                                        MAX_DIM_AMOUNT * 100f,
                                    ),
                                    onValueChange = { dimDraft = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    valueRange = (MIN_DIM_AMOUNT * 100f)..(MAX_DIM_AMOUNT * 100f),
                                    onValueChangeFinished = {
                                        val next = dimDraft.roundToInt() / 100f
                                        dimDraft = next * 100f
                                        updateBackground { it.copy(dimAmount = next) }
                                    },
                                    hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                                )
                            },
                            onClick = null,
                        )
                        EtaPreferenceDivider(hasLeading = false)
                        EtaArrowPreference(
                            title = stringResource(R.string.chat_background_blur),
                            summary = stringResource(R.string.chat_background_blur_summary),
                            endActions = {
                                Text(
                                    text = "${blurDraft.roundToInt()}dp",
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                )
                            },
                            bottomAction = {
                                Slider(
                                    value = blurDraft.coerceIn(
                                        MIN_BLUR_RADIUS_DP,
                                        MAX_BLUR_RADIUS_DP,
                                    ),
                                    onValueChange = { blurDraft = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    valueRange = MIN_BLUR_RADIUS_DP..MAX_BLUR_RADIUS_DP,
                                    onValueChangeFinished = {
                                        val next = blurDraft.roundToInt().toFloat()
                                        blurDraft = next
                                        updateBackground { it.copy(blurRadiusDp = next) }
                                    },
                                    hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                                )
                            },
                            enabled = chatBackground.supportsImageFilters,
                            onClick = null,
                        )
                        EtaPreferenceDivider(hasLeading = false)
                        EtaArrowPreference(
                            title = stringResource(R.string.chat_background_top_bar_alpha),
                            summary = stringResource(R.string.chat_background_top_bar_alpha_summary),
                            endActions = {
                                Text(
                                    text = "${topBarAlphaDraft.roundToInt()}%",
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                )
                            },
                            bottomAction = {
                                Slider(
                                    value = topBarAlphaDraft.coerceIn(
                                        MIN_BAR_ALPHA * 100f,
                                        MAX_BAR_ALPHA * 100f,
                                    ),
                                    onValueChange = { topBarAlphaDraft = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    valueRange = (MIN_BAR_ALPHA * 100f)..(MAX_BAR_ALPHA * 100f),
                                    onValueChangeFinished = {
                                        val next = topBarAlphaDraft.roundToInt() / 100f
                                        topBarAlphaDraft = next * 100f
                                        updateBackground { it.copy(topBarAlpha = next) }
                                    },
                                    hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                                )
                            },
                            onClick = null,
                        )
                        EtaPreferenceDivider(hasLeading = false)
                        EtaArrowPreference(
                            title = stringResource(R.string.chat_background_input_bar_alpha),
                            summary = stringResource(R.string.chat_background_input_bar_alpha_summary),
                            endActions = {
                                Text(
                                    text = "${inputBarAlphaDraft.roundToInt()}%",
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                )
                            },
                            bottomAction = {
                                Slider(
                                    value = inputBarAlphaDraft.coerceIn(
                                        MIN_BAR_ALPHA * 100f,
                                        MAX_BAR_ALPHA * 100f,
                                    ),
                                    onValueChange = { inputBarAlphaDraft = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    valueRange = (MIN_BAR_ALPHA * 100f)..(MAX_BAR_ALPHA * 100f),
                                    onValueChangeFinished = {
                                        val next = inputBarAlphaDraft.roundToInt() / 100f
                                        inputBarAlphaDraft = next * 100f
                                        updateBackground { it.copy(inputBarAlpha = next) }
                                    },
                                    hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                                )
                            },
                            onClick = null,
                        )
                        EtaPreferenceDivider(hasLeading = false)
                        EtaOverlayDropdownPreference(
                            title = stringResource(R.string.chat_background_scale),
                            summary = backgroundScaleLabels[chatBackground.scale.ordinal],
                            items = backgroundScaleLabels,
                            selectedIndex = chatBackground.scale.ordinal,
                            onSelectedIndexChange = { index ->
                                backgroundScales.getOrNull(index)?.let { scale ->
                                    updateBackground { it.copy(scale = scale) }
                                }
                            },
                        )
                        EtaPreferenceDivider(hasLeading = false)
                        EtaSwitchPreference(
                            title = stringResource(R.string.chat_background_grayscale),
                            summary = stringResource(R.string.chat_background_grayscale_summary),
                            checked = chatBackground.grayscale,
                            enabled = chatBackground.supportsImageFilters,
                            onCheckedChange = { enabled ->
                                updateBackground { it.copy(grayscale = enabled) }
                            },
                        )
                    }
                }
            }
        }
    }

    val parsedScale = scaleInput.toIntOrNull()
    EtaWindowDialog(
        show = showScaleDialog,
        title = stringResource(R.string.appearance_interface_scale_dialog_title),
        summary = stringResource(R.string.appearance_interface_scale_dialog_summary),
        onDismissRequest = { showScaleDialog = false },
    ) {
        Column {
            TextField(
                value = scaleInput,
                onValueChange = { value -> scaleInput = value.filter(Char::isDigit).take(3) },
                label = stringResource(R.string.appearance_interface_scale_input_label),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            MiuixDialogActions(
                confirmText = stringResource(R.string.action_confirm),
                confirmEnabled = parsedScale != null && parsedScale in 80..110,
                onCancel = { showScaleDialog = false },
                onConfirm = {
                    parsedScale?.let { commitScale(it.toFloat()) }
                    showScaleDialog = false
                },
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

private fun ChatBackgroundImportFailure.messageRes(): Int = when (this) {
    ChatBackgroundImportFailure.UNREADABLE_MEDIA -> R.string.chat_background_error_unreadable
    ChatBackgroundImportFailure.DECODE_FAILED -> R.string.chat_background_error_decode
    ChatBackgroundImportFailure.VIDEO_TOO_LARGE -> R.string.chat_background_error_video_too_large
}