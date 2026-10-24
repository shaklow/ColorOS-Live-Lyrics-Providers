# Apple Music 1.3.2：词幕 SDK 初始化时机修复

## 日志证据和原因

设备日志在多次 Apple Music 启动中均出现 `LYRICON_INITIALIZATION_FAILED`，
异常为对 null Context 调用 `registerReceiver`。调用栈为
`AppleLyriconPublisher.<init>` → `ApplePlayerHooker.onHook` →
`Application.attach`。使用与设备栈相同的 1.3.1 R8 映射确认，`gf.J:41`
对应 `ContextCompat.Api33Impl.registerReceiver`。

同一进程随后安装取词与歌词构建 Hook，并成功发布 ColorOS `lyricInfo`。
但没有本模块的 `LYRICON_CONNECTED`、`LYRICON_LYRICS_SENT`，也没有安装
依赖输出器的进度 Hook。这说明故障发生在创建词幕 SDK 时，早于歌词发送。
用户界面的连接状态不能代替本模块的运行日志。

API 102 公共入口在 `Application.attach` 后安装业务 Hook，此时应用对象虽存在，
`applicationContext` 仍可能为空。词幕 SDK 会用它注册广播，故直接在该阶段
创建 SDK 会抛出上述异常。官方 LyricProvider 在应用 `onCreate` 生命周期中
初始化，不存在这一提前创建的调用路径。

## 修复

- 保留公共 API 102 入口；只推迟 Apple 的词幕 SDK 创建，其他 Provider 不受影响。
- Hook 宿主实际 Application 的 `onCreate`（包含不调用父类的覆盖方法），
  完成后进入主线程队列，明确确认上下文非空才初始化 SDK。
- 初始化失败不再永久留下 null 输出器；最多尝试三次。生命周期 Hook 安装失败
  时使用主队列回退，仍检查上下文。
- 播放器、播放控制和翻译 Hook 在早期独立安装，保留当前播放器和播放状态。
- 缓存 SDK 就绪前最新的媒体元数据；歌词结果继续保存在原生内存/磁盘缓存中。
  SDK 就绪后按当前媒体 ID 恢复歌词，并补发播放/暂停及实际进度，包括暂停时的 seek。
- 启动日志标记模块版本；`LYRICON_INITIALIZED` 与服务真实连接日志区分各阶段。

## 验证

在用户指定的 SSH 主机、JDK 21 和 Android SDK 37 上通过 71 项单元测试，
失败、错误、跳过均为 0。`lintDebug`、`assembleDebug`、`minifyReleaseWithR8`
通过；Lint 仅有存量的 `ObsoleteSdkInt`、`UnusedResources` 两条资源告警。
发布矩阵契约检查通过。

启动回归通过安装的真实业务 Hook 回调验证：attach 阶段不构造 SDK、onCreate
完成后排队、上下文为空时不进入 SDK、恢复和有限重试、其他 Application 实例和
失败的 onCreate 不触发初始化、宿主覆盖方法不调用父类的情况。另覆盖 SDK 创建
前的播放进度补发和暂停拖动恢复，并继续执行全部原有歌词解析、缓存和发送回归。

这些测试验证修复对应日志中的失败路径。本地未连接用户设备，1.3.2 的实际
连接、歌词显示和播放控制结果仍须真机确认。

后续用户已确认 1.3.2 歌词显示正常。
