# Apple Music Provider

目标宿主：`com.apple.android.music` 主进程。**1.3.0 起同时支持词幕（Lyricon）
和 ColorOS Live Lyrics Bridge**：通过词幕 SDK 注册歌词提供服务，同时继续输出
标准 `MediaMetadata["lyricInfo"]`。

## 词幕安装与使用

1. 覆盖安装 1.3.1 修复包，在 LSPosed 中启用模块并勾选 Apple Music。
2. 完全停止并重新打开 Apple Music，使新的 Hook 与词幕服务注册代码加载。
3. 在词幕的「歌词提供服务」中确认并启用 Apple Music Lyric Provider，再播放歌曲。

Manifest 中的 `lyricon_module` 元数据负责让词幕识别已安装模块；Apple Music
进程启动后，通过 `LyriconFactory` 注册运行时服务。两者缺一不可。
SDK 缓存当前歌曲与播放状态，连接恢复后重新同步；连接超时有有限次数重试，
后续曲目和播放状态变化也会尝试恢复注册。

词幕使用独立歌词映射，保留逐字时间、主唱翻译、背景人声及第二演唱者右对齐。
1.3.1 按官方 LyricProvider 的播放器链路同步词幕：
`LocalMediaPlayerController.onPlaybackStateChanged` 的第三个参数为 Apple 状态，
`1` 表示播放；通过 SDK 的布尔播放状态接口发送，并每约 41ms 从
`ExoMediaPlayer.getCurrentPosition` 读取真实进度。拖动调用 SDK `seekTo`，
暂停或熄屏停止轮询，亮屏/解锁后恢复。词幕链路不再混用 SDK 的
`setPlaybackState(PlaybackState)` 自动进度模式。
切歌立即发送新曲占位符，异步旧曲结果不覆盖当前歌曲。
词幕的歌词发送不依赖 ColorOS 锁屏入口、封面就绪或 `lyricInfo` 发布门禁。

## LyricProvider 合并

本次对照本地 `LyricProvider/apple-music`，将上游提交
`d6e66b49f41ae7b8867dee6a68f4ad2892e84d17` 中适用的能力合入当前
`player-apple`，保留 Proify、Tomakino 的 Apache-2.0 署名。

- 已有匹配的 `PlaybackItem` 时继续使用它请求歌词；只有歌曲 ID 时，若宿主
  `Song` 的构造函数、`setId`、`setHasLyrics` 和兼容的 `loadLyrics` 可用，则
  构造 `Song` 并主动加载歌词，无需等待打开官方歌词页面或捕获队列映射。
- 同时识别接收 `PlaybackItem`、`Song` 或其兼容基类的 `loadLyrics`。
  宿主不支持构造 `Song` 时继续走原有匹配 `PlaybackItem` 路径。
- 主线程执行请求，并在排队的请求真正执行前重新校验当前曲目与 generation。
  只有标题、没有 ID 的曲目继续等待匹配的 `PlaybackItem`；保留有限次数重试、
  缓存与异步旧曲歌词过滤。
- 统一遍历 JNI 智能指针 Vector，兼容 `get(int)` / `get(long)`，按实际参数
  类型查找反射方法；可选字段或方法缺失时安全降级。
- 补齐演唱者列表、行与词的演唱者标记、和声翻译及和声音译字段，并纳入
  现有按语言分目录的缓存模型；新增字段有默认值，旧缓存可继续读取。

ColorOS 的 `lyricInfo` 输出继续使用已有的主唱筛选、翻译与音译区分、拉丁音节
合并和 MediaSession 发布逻辑，保持主唱歌词与译文的协议。词幕单独使用上面
描述的完整歌词映射。ColorOS 的翻译开关由 Bridge 管理；词幕翻译显示跟随
Apple Music 的翻译歌词偏好设置。

## 验证

需要 JDK 21 与项目指定的 Android SDK：

```powershell
.\gradlew.bat :player-apple:testDebugUnitTest :player-apple:lintDebug :player-apple:assembleDebug
```

回归覆盖 Song 构造与请求重载、旧宿主回退、请求去重、无效 ID、JNI 两种索引
签名、演唱者与和声字段、旧缓存兼容，以及既有切歌、发布、翻译和逐字映射策略。
新增词幕回归覆盖独立发布、切歌清空、异步旧曲过滤、缓存恢复、ID 后补、
重复去重、翻译与和声分离、空白与逐字时间保留、对唱对齐。

2026-10-18 已在 Linux、JDK 21、Android SDK 37 的远端构建环境完成验证：
42 项单元测试通过，0 失败、0 错误、0 跳过；`lintDebug` 与 `assembleDebug`
通过。Lint 保留 2 条原有资源告警（`ObsoleteSdkInt`、`UnusedResources`），
未改动对应资源。`git diff --check` 通过。

2026-10-24 的 1.3.0 修复在同一远端环境通过 52 项单元测试、`lintDebug`、
调试构建及签名 Release 构建（含 R8）。检查最终 Release APK 确认：
词幕四项模块元数据、启用标记、运行时注册代码和 API 102 入口均存在；
签名证书与此前 fork 发布包相同。Lint 仍为上述两条存量资源告警。

用户设备反馈 1.3.0 服务已连接但无歌词。1.3.1 的逐项官方对照、修正原因和
日志判读见 [词幕同步核对记录](../docs/APPLE-LYRICON-1.3.1-AUDIT.md)。
本地没有连接用户手机，歌词显示和播放控制仍须真机确认。

原有真机验证记录见
[`PHASE-4-APPLE-MIGRATION-REPORT.md`](../docs/4.0/PHASE-4-APPLE-MIGRATION-REPORT.md)。
