# Apple Music Provider

目标宿主：`com.apple.android.music` 主进程；输出为标准
`MediaMetadata["lyricInfo"]`，可配合 ColorOS Live Lyrics Bridge 使用。

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

最终歌词仍使用本项目已有的主唱筛选、翻译与音译区分、拉丁音节合并和
MediaSession 发布逻辑。演唱者及和声附加字段保留在解析模型和缓存中，当前
`lyricInfo` 输出仍为主唱歌词和译文，不新增对唱左右布局或和声显示。
翻译开关继续由 Bridge 管理；播放进度直接来自宿主 MediaSession。

## 验证

需要 JDK 21 与项目指定的 Android SDK：

```powershell
.\gradlew.bat :player-apple:testDebugUnitTest :player-apple:lintDebug :player-apple:assembleDebug
```

回归覆盖 Song 构造与请求重载、旧宿主回退、请求去重、无效 ID、JNI 两种索引
签名、演唱者与和声字段、旧缓存兼容，以及既有切歌、发布、翻译和逐字映射策略。

2026-10-18 已在 Linux、JDK 21、Android SDK 37 的远端构建环境完成验证：
42 项单元测试通过，0 失败、0 错误、0 跳过；`lintDebug` 与 `assembleDebug`
通过。Lint 保留 2 条原有资源告警（`ObsoleteSdkInt`、`UnusedResources`），
未改动对应资源。`git diff --check` 通过。

新增 Song 请求路径仍需在实际 Apple Music 版本上验证加载、连续切歌与锁屏显示。

原有真机验证记录见
[`PHASE-4-APPLE-MIGRATION-REPORT.md`](../docs/4.0/PHASE-4-APPLE-MIGRATION-REPORT.md)。
