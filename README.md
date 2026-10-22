# 通知随听 · Notification Reader

一个专注于“选定应用的新通知 → 系统中文 TTS → 选定蓝牙音频设备”的安卓小工具。为 Muse 和千问 N1 Pro 的通知朗读场景制作，应用没有依赖特定品牌的 SDK。

当前版本：0.1.0 个人测试版；Android 9/API 28 及以上，编译/目标 API 35。APK 已完成本地编译与签名校验，26 项核心检查通过。2026-10-22，首位使用者在小米澎湃 OS 与千问 N1 Pro 使用场景中反馈可用；尚未进行系统性的多机型兼容性测试。

**[下载 Android 安装包 v0.1.0](https://github.com/hixumeng/notification-reader/raw/refs/heads/main/dist/notification-reader-v0.1.0.apk)** · [更新记录](CHANGELOG.md) · [SHA-256 校验值](dist/SHA256SUMS.txt)

APK 保留原个人测试签名，已安装的用户使用同签名版本即可覆盖更新。项目不附带签名私钥；自行构建的 APK 会使用你本地生成的新签名。

## 安装与首次测试

1. 下载上方 APK，发到手机安装。
2. 将 N1 Pro 连接到手机，在蓝牙设置中确认它的“媒体音频”可用。
3. 打开“通知随听”，点“选择已连接的眼镜”，选 N1 Pro 对应的设备。
4. 点“测试眼镜朗读”。Android 13 及以上会请求运行状态通知权限；允许后再点一次测试按钮。
5. 确认测试句是在眼镜里听到的。界面显示路由失败时不要直接打开通知朗读，先检查手机媒体输出与设备连接。
6. 点“选择应用”，搜索并勾选 Muse，保存。
7. 点“打开通知访问设置”，为“通知随听”开启通知访问权限。
8. 回到应用，开启“通知朗读”。然后用 Muse 的一条新系统通知进行验证。

如果你用 WhatsApp 接收 Muse 消息，选择 WhatsApp 会朗读该应用的其他通知；本版没有按 WhatsApp 联系人筛选，所以优先使用 Muse 独立 App。

## 澎湃 OS 配置

按你的系统版本在“设置”中搜索：

- **通知使用权/通知访问**：授予“通知随听”。“允许发送通知”是另一个权限，不能代替读取通知的授权。
- **自启动**：允许“通知随听”自启动。
- **省电策略**：给“通知随听”设置“无限制”，必要时在最近任务中锁定。保持 Muse 及穿戴 App 的正常通知和后台运行条件。
- 安装后若系统提示通知访问属于“受限设置”，在本应用的“应用信息”中查看是否有“允许受限设置”；这是系统对侧载应用的独立确认，需要你自己决定授权。

应用开启时有一条低优先级常驻通知，可以从那里关闭朗读。重启手机后请打开应用并重新开启。系统强制停止/清理、后台限制、工作资料空间和通知脱敏都可能影响运行。

## 已实现的行为

- 默认关闭，默认不选择任何来源应用。
- 只有已选择应用的新通知进入处理流程；不获取历史通知列表。
- 优先读取 MessagingStyle 消息、展开正文或常规正文，分组摘要和常驻/进行中通知跳过。
- 同一条通知内容短时间重复更新不重复朗读；MessagingStyle 按消息时间和内容去重。
- 连续通知排队，长正文分段合成；通知正文不经过外部模型摘要或改写。
- 所选设备不在线时跳过通知，不在重连后补读。播放前静音确认音频路由，持续监测输出；断开、路由变化或失去音频焦点时停止并清空队列。
- 提供测试朗读、停止当前朗读、关闭整个服务三个操作。

## 数据和权限

本应用**未声明 INTERNET、麦克风、通讯录、短信、存储读取或蓝牙扫描权限**。通知访问由系统授权，本应用按自己的白名单过滤。它不保存通知历史，不把正文写进日志。

文字通过系统 TTS 引擎合成，优先选引擎中现成的离线中文声音。如果没有离线声音，则保留系统中文声音；**TTS 引擎是另一个应用，是否联网由那个引擎决定**。合成 WAV 暂存在本应用私有缓存中，播放结束、取消或下次服务启动时清理；应用备份关闭。

运行状态通知及前台服务权限用于在后台等待和朗读通知，并适配 Android 14/15 的服务与音频焦点规则。它不授予修改其他应用通知内容或代表用户回复消息的能力。

## 使用边界

- 只能朗读 Muse 实际发到系统通知中的文字。通知未出现、正文被隐藏、只给“你有新消息”或原文被系统裁剪时，无法读取完整聊天记录。
- 当前仅提供应用级白名单，不按联系人、会话或“主动消息/普通回复”进一步分类。
- 队列在内存中，超过五分钟的待播分段丢弃；队列最多 80 个分段（每段约 450 字符），过载会在状态中显示跳过。单次通知超过 12,000 字符会跳过。
- 没有设备端绝对零延迟的断连保证。应用已做启动前、实际路由和设备变更检查，但 OEM 音频路由仍须真机验证，测试前先用无敏感文本。
- 部分 TTS 引擎可能不生成可处理的标准 WAV；界面会报错而不尝试猜格式播放。
- 这是个人侧载测试 APK，没有完成 Google Play 商店发布或各机型兼容性认证。

## 本地构建

这是无第三方运行时依赖的原生 Java Android 项目。可以用 Android Studio 导入根目录，安装 SDK 35 后构建；Gradle 配置已包含在项目中。

本次实际使用隔离构建脚本，不修改系统 Java 或 Android SDK。Apple Silicon Mac 配合 Python 3.12+：

```sh
python3 tools/bootstrap.py
python3 tools/test.py
python3 tools/build.py
```

bootstrap 从 Adoptium 官方发行仓库和 Google Android 仓库下载固定版本，使用固定 SHA-256 校验；约需下载 330MB。目录 `.toolchain/` 仅供构建，不装进 APK。

构建脚本用 AAPT2、javac、D8、zipalign、apksigner 生成 APK。首次运行创建 `.signing/personal-test.jks` 个人测试签名，使用标准测试口令；**该签名仅适合个人测试，不是正式发布签名**。保留这个目录才能继续安装同签名更新；源码分享包排除了它，重新构建会生成新签名。

## 验证记录和下一步

- 本地编译：通过。
- 核心逻辑：26 项检查通过，覆盖去重、消息更新、真重复消息、中文/emoji 分段、WAV 静音头与损坏数据拒绝。
- Manifest 检查：通过，无网络/麦克风/存储读取权限，关闭备份，通知监听服务受系统绑定权限保护。
- APK 签名验证：通过（v3，最低 Android 9）。
- 使用者反馈：2026-10-22，实际使用反馈“还不错，好用”。没有收集手机型号、系统小版本或逐项测试记录。
- 仍需进一步验证：不同机型与 TTS 引擎、长期锁屏运行、断连停止、通知更新和连续通知等场景。

## 官方参考

- [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
- [TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech)
- [MediaPlayer 音频路由](https://developer.android.com/reference/android/media/MediaPlayer)
- [前台服务类型](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [音频焦点](https://developer.android.com/media/optimize/audio-focus)
- [小米自启动管理](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1624)
