# 大肥鱼基线说明（fork 自 Eta v3.3.5）

本仓库是 [`Mangi-11/Eta`](https://github.com/Mangi-11/Eta) 的 fork，从上游 tag `v3.3.5`（`6bedba9`）分叉。
本文档描述「我们这条线的成品」相对原版做了哪些事，作为后续开发的参照基准。

- 上游基线：`v3.3.5` / `6bedba9`
- 本基线分支：`feat/rename-to-daifeiyu`
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

## 三、CI / 发版

| 文件 | 内容 |
| --- | --- |
| `.github/workflows/build-daifeiyu.yml` | push 到 `feat/rename-to-daifeiyu` 触发；注入签名 → 构建 → `apksigner verify` → 建 tag `daifeiyu-<sha7>` → 发布 prerelease |
| `.github/workflows/publish-now.yml` | 手动补发 |

## 四、踩过的坑（别再踩回去）

- 背景层不能同时作为「绘制层」和「模糊采样源」：`layerBackdrop` 提到最外层、输入栏又拿它做 `textureBlur`，
  会形成自己采样自己，hwui 在 `SkiaDisplayList::prepareListAndChildren` 递归溢出 → RenderThread SIGSEGV
  （`reason=5 APP CRASH(NATIVE) status=11`）。已在 `d80ddba` 下沉拆链修掉。

## 五、已知遗留

- `BackgroundCommandNotification.kt` / `AssistantSpeechForeground.kt` 仍有硬编码中文渠道名与标题（非 Eta 残留）
- 应用图标沿用上游，未更换
- 视频壁纸的首帧表现、切后台恢复等细节按「可用」标准收口
