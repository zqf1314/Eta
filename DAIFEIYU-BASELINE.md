# 大肥鱼基线说明（fork 自 Eta v3.3.5）

本仓库是 [`Mangi-11/Eta`](https://github.com/Mangi-11/Eta) 的 fork，从上游 tag `v3.3.5`（`6bedba9`）分叉。
本文档描述「我们这条线的成品」相对原版做了哪些事，作为后续开发的参照基准。

- 上游基线：`v3.3.5` / `6bedba9`
- 本基线分支：`feat/rename-to-daifeiyu`
- 基线 tag：`daifeiyu-baseline-v2`（上一版为 `daifeiyu-baseline-v1` / `653258b`）
- 构建产物：`Eta-v3.3.5+<sha7>.apk`（arm64-v8a，release，已签名）
- 签名：`eta-release.jks`（PKCS12 / RSA 4096 / alias `daifeiyu`），只存在本地，密钥经 GitHub Secrets 注入

## 一、品牌：Eta → 大肥鱼

- `app_name`：简体「大肥鱼」、繁体「大肥魚」、英文「Big Fat Fish」
- `strings.xml` 三语各 40 / 40 / 38 处「Eta」全量替换
- 通知侧：`execution_service.xml`（正在运行 / 返回查看进度）、`root_capabilities.xml`（6 处）
- **不改**：包名 `io.github.mangi.eta`、`Theme.Eta` / `Theme.EtaVoiceAssistBridge`、`EtaRecognitionService`、
  `EtaVoiceInteractionSessionService`、日志 TAG、User-Agent、通知渠道 ID —— 改了会崩或断掉覆盖升级。

## 二、聊天页自定义背景（原版没有）

新增文件：

| 文件 | 职责 |
| --- | --- |
| `data/model/ChatBackgroundSettings.kt` | 数据模型、默认值、范围常量、AI 文字色板 |
| `data/repository/ChatBackgroundRepository.kt` | 导入 / 落盘 / 清理；图片最长边 2160 + JPEG 88，视频上限 48MB |
| `ui/components/ChatBackgroundLayer.kt` | 渲染层：图片 `ImageBitmap` + `ColorMatrix`，视频 `MediaPlayer` + `TextureView`，零第三方依赖 |

设置项（DataStore 键 `chat_background_settings_v1`）：

| 项 | 范围 / 默认 |
| --- | --- |
| 背景 | 无 / 图片 / 视频 |
| 填充方式 | 铺满 / 完整 |
| 暗化 | 0 ~ 0.85，默认 0.35 |
| 模糊 | 0 ~ 24dp（需 runtime shader 支持，不支持自动禁用） |
| 灰度 | 开关，仅图片 |
| 顶栏透明度 | 0.05 ~ 1，默认 0.55 |
| 输入栏（玻璃）透明度 | 0.05 ~ 1，默认 0.82 |
| 空会话快捷卡片透明度 | 默认 0.65 |
| AI 回复文字颜色 | 跟随主题 + 14 个固定色 + 自定义取色器 |

AI 回复文字颜色的行为边界：

- 只覆盖 `MarkdownTone.Answer` 的正文，思考过程 / 工具调用 / 用户气泡不受影响
- **只在铺着壁纸时生效**，撤掉壁纸自动回主题色
- 自定义取色器 = 色相条 + 饱和度/明度面板 + 十六进制输入，**不做实时预览**，确认才写入
- 存储仍是一个 ARGB 整数，向前兼容

## 三、Agent 悬浮胶囊（原版没有）

单文件实现：`agent/overlay/AgentOverlayCapsule.kt`。挂载点在 `AgentRuntimeService.kt`，
窗口是 `TYPE_APPLICATION_OVERLAY` + `gravity = TOP|CENTER_HORIZONTAL`，`y` 取状态栏高度。

外观定稿（三次迭代后）：

| 项 | 值 |
| --- | --- |
| 胶囊宽度 | `CapsuleWidth = 248.dp` |
| 圆角 | `CapsuleCorner = 22.dp`，**全圆角，不斜切、不倾斜** |
| 描边 | 1.5dp `linearGradient`：青 `3EE6F0` → 紫 `9B6BFF` → 粉 `FF4FA3` |
| 底色 | `surfaceContainer` 94% + 霓虹渐变染色（粉 0.10 / 紫 0.07 / 青 0.10） |
| 高光 | 左上 radial 白 14%，模拟塑料贴纸 |
| 头像 | `AvatarSize = 26.dp` 圆形，图片相对环内缩 3dp 留缝 |
| 头像环 | 1dp accent（随四态变色）+ 外沿 1.5dp 处 1dp 白描边 38% |
| 头像光晕 | 仅 RUNNING：外扩 6dp 同色 radial，亮度 0.10↔0.34，1.8s 往返 |
| 自转 | 仅 RUNNING：3600ms 一圈 `LinearEasing`，其余状态停住 |
| 状态脉冲 | 四态切换时整颗胶囊打一次 13% 状态色，260ms 衰减 |
| 四态色 | RUNNING=主题 primary / PAUSED `FF9F0A` / FINISHED `34C759` / FAILED 主题 error |

头像来源（设置 → 外观 → 胶囊头像）：

- `SettingsDataStore` 键 `overlay_avatar_file_v1`，**只存文件名，不存相册 URI**（URI 会失效）
- `OverlayAvatarRepository`：32MiB 上限读取 → 降采样到 256 → 居中裁方 → PNG 100 重编码 →
  `filesDir/overlay_avatar/avatar-<uuid8>.png`，导入后清掉旧文件
- 没设置或读不到时回落应用图标：`applicationInfo.loadIcon`。
  **不能走 `painterResource(R.mipmap.ic_launcher)`** —— 自适应图标是 XML，那条路画不出来
- 文案共 5 个键：`overlay_avatar_group` / `_settings` / `_settings_summary` / `_reset` / `_import_failed`

视觉迭代记录（**别再走回头路**）：

| 版本 | commit | 做法 | 结论 |
| --- | --- | --- | --- |
| v1 | `d7ecf6e` | 贴纸风：右下角 45° 斜切 18dp + 整体倾斜 -0.8° | 否决。斜切在收起态的小胶囊上还行，展开成方块就是右下角一个破洞；微小倾斜在状态栏下面像在抖 |
| v2 | `28fe366` / `0022f74` | 改回全圆角、不倾斜，22dp 头像匀速自转，相册自选 | 通过 |
| v3 | `705ebdd` | 头像 26dp、呼吸光晕、环图留缝、双圈白边、状态脉冲 | 定稿，即本基线 |

回退与调参：视觉全部集中在文件尾部的常量区，改常量即可；整版回退用 `git revert <commit>`。
光晕和白描边都画在布局尺寸之外（`drawBehind` 画到边界外），所以加装饰不会挤窄文字区 —— 后续加料请保持这个做法。

## 四、CI / 发版

| 文件 | 内容 |
| --- | --- |
| `.github/workflows/build-daifeiyu.yml` | push 到 `feat/rename-to-daifeiyu` 触发；注入签名 → 构建 → `apksigner verify` → 建 tag `daifeiyu-<sha7>` → 发布 prerelease |
| `.github/workflows/publish-now.yml` | 手动补发 |

## 五、踩过的坑（别再踩回去）

- 英文资源里的裸单引号必须写成 `\'`，否则 aapt 直接报 `Invalid unicode escape sequence in string`（`Couldn't` 踩过一次，整个 build 挂掉）
- 背景层不能同时作为「绘制层」和「模糊采样源」：`layerBackdrop` 提到最外层、输入栏又拿它做 `textureBlur`，
  会形成自己采样自己，hwui 在 `SkiaDisplayList::prepareListAndChildren` 递归溢出 → RenderThread SIGSEGV
  （`reason=5 APP CRASH(NATIVE) status=11`）。已在 `d80ddba` 下沉拆链修掉。

## 六、已知遗留

- `BackgroundCommandNotification.kt` / `AssistantSpeechForeground.kt` 仍有硬编码中文渠道名与标题（非 Eta 残留）
- 应用图标沿用上游，未更换
- 视频壁纸的首帧表现、切后台恢复等细节按「可用」标准收口
- 胶囊展开态的控制按钮仍是方块圆角，未做玻璃化 / 圆形化
- 可选美化未做：边框流光（B1）、文字前渐变竖线（C2）、彩色外发光（C3）
- 上游自带的 `Eta Build` 在 `main` 上有 2 条测试失败（`AgentPromptBuilderTest` / `LocaleResourcesTest`），
  不影响我们的 `build-daifeiyu.yml` 出包
