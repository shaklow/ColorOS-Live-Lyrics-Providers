# Apple Music 1.3.1 词幕链路核对

## 反馈和验证边界

用户反馈 1.3.0 在词幕界面显示“已连接但无歌词”。后续 1.3.1 设备日志证实
本模块在 SDK 初始化时已失败，界面状态不能证明本模块注册成功。
1.3.2 修复见 [启动故障记录](APPLE-LYRICON-1.3.2-STARTUP-FIX.md)。
1.3.0 的 JVM 测试和 APK 检查没有覆盖官方播放器事件，不能作为真机显示成功的证据。

本次逐项对照本地官方 `LyricProvider/apple-music` 下的 `Apple.kt`、
`PlaybackManager.kt`、`LyricRequester.kt`、`MediaMetadataCache.kt`、
`PreferencesMonitor.kt` 和解析/映射代码。构建继续使用用户指定的
`erenche@192.168.39.159`；未在本机下载构建环境。远端无连接的 Android 设备。

## 官方事件链路对照

| 环节 | 官方 LyricProvider | 1.3.0 差异 | 1.3.1 修正 |
| --- | --- | --- | --- |
| 注册 | `onCreate` 中 `LyriconFactory.createProvider` + `register` | 创建过早，设备日志后来证实失败 | 1.3.1 保留早期时机；1.3.2 移到应用创建完成后并检查上下文 |
| 当前歌曲 | `MediaSession.setMetadata` 完成后，按媒体 ID 切歌 | 共用 ColorOS generation，队列 mapper 也能更改词幕当前曲目 | 单独在宿主 metadata 提交后更新词幕 ID，队列观测不再改写它 |
| 无缓存取词 | 构造 `Song`，设置 ID 和 `hasLyrics=true`，调用自建 ViewModel | 请求受 ColorOS 请求门禁约束，优先使用捕获的 PlaybackItem | 词幕独立按提交的媒体 ID 请求，优先构造官方的 Song，旧宿主才回退到匹配的 PlaybackItem |
| 歌词回传 | `buildTimeRangeToLyricsMap` 后解析，当前 ID 相同才 `setSong` | 已发送原生解析结果，但当前 ID 受上一行影响 | 对词幕自己的当前 ID 校验；提前完成的歌词在 metadata 到来后从缓存恢复 |
| 播放/暂停 | 控制器第三参数：Apple 的 `1=PLAYING`；SDK `setPlaybackState(Boolean)` | 使用 Android `PlaybackState` 重载，且经过 ColorOS session 选择 | 改为官方控制器事件和手动 SDK 模式，不经过 ColorOS session 选择 |
| 持续进度 | 缓存 ExoMediaPlayer，读取真实进度，约 41ms 更新 | 没有官方的播放器进度 Hook 和同步循环 | 补齐构造 Hook、位置读取和循环，重复播放事件不创建重复循环 |
| 拖动 | Exo `seekToPosition(long)` → SDK `seekTo` | 依赖系统 PlaybackState 自动模式 | 直接同步 seek，暂停拖动也更新缓存进度 |
| 屏幕 | 熄屏停轮询，亮屏/解锁恢复 | 没有独立进度循环 | 补齐屏幕广播和恢复实际位置 |
| 翻译 | 偏好初值 + `setLyricsTranslationSelected` Hook | 仅 SharedPreferences 监听 | 补上官方 setter Hook，同时保留偏好监听 |
| 映射 | 原生行、逐字、翻译、背景人声、演唱者对齐 | 已独立映射 | 保留此映射，不经过 ColorOS 主唱/封面筛选 |

这些差异说明上一版没有完整接入官方核心事件链路。在没有设备日志的情况下，
不能断言其中哪一条是用户设备上的唯一原因。ColorOS 原有取词、发布和重试路径
继续保留；词幕独立维护当前媒体 ID 和播放时钟。

## 回归范围

2026-10-24 在指定 SSH 主机（JDK 21、Android SDK 37）验证：63 项单元测试
全部通过，失败/错误/跳过均为 0；`lintDebug`、`assembleDebug` 和
`minifyReleaseWithR8` 通过。Lint 仅有原有 `ObsoleteSdkInt`、`UnusedResources`
两项资源告警。发布矩阵契约检查和 `git diff --check` 通过。

签名包交由仓库已有的 GitHub Actions 签名配置生成，SSH 主机只执行代码验证
和优化构建，无需传送发布私钥或密码。

新增测试通过实际安装的 Hook 回调和模拟的宿主签名，覆盖 Apple 播放状态枚举、
播放器构造、进度读取、seek、可选 Hook 失败隔离；时钟测试覆盖暂停、熄屏恢复、
播放器更换、读取失败和重复事件。歌曲状态测试覆盖独立取词、缓存先于 metadata
完成、当前 ID 校验及旧结果丢弃。它们不替代 Apple Music/词幕真机测试。

## 日志定位

在 LSPosed 日志中检索 `LYRICON_`：

- `CONNECTED`：仅证明服务连接。
- `PLAYER_HOOK_INSTALLED`、`PLAYBACK_HOOK_INSTALLED`、`PLAYER_READY`：播放器事件和实例就绪。
- `METADATA_BOUND`、`PLACEHOLDER_SENT`：已捕获当前媒体 ID 并发送占位曲目。
- `LYRIC_REQUESTED` / `LYRIC_REQUEST_REJECTED`：主动取词是否被接受；异步失败还会记录 `LYRIC_REQUEST_FAILED`。
- `LYRICS_SENT`：已调用 SDK 发送含歌词的歌曲；不代表设备已经显示。
- `SONG_EMPTY`、`NATIVE_SONG_INVALID`、`SONG_IGNORED_ID_MISMATCH`：空歌词、原生 ID 无效或旧曲结果。
- `PLAYING` / `PAUSED`：实际使用的 Apple 播放控制事件。
- `*_HOOK_FAILED`、`SYNC_FAILED`：查看同条异常定位失败点。

所有上述事件的实际名称均带 `LYRICON_` 前缀；日志有节流，重复事件可能不逐条输出。
