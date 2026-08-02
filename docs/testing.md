# 测试与验收

本文是当前分支的验收账本，不是产品愿景。`已验证` 只代表下列证据已经获得；`待验证` 不应在 README、发布说明或讨论中被说成已经完成。

最后更新：2026-07-20。

## 自动化测试

| 层 | 覆盖 | 运行方式 | 状态 |
| --- | --- | --- | --- |
| `:scanner` | 图片签名识别 | `:scanner:desktopTest`（由 `check` 触发） | 已纳入 CI |
| `:storage-transaction` | 正常移动、复制/删除失败、恢复、覆盖、撤销等故障注入 | `:storage-transaction:desktopTest` | 已纳入 CI |
| `:persistence` | 配置、抑制记录、唯一操作日志的持久化 | `:persistence:desktopTest` | 已纳入 CI |
| `:storage-nio` | Windows 文件系统契约、排序路径与移动/撤销 | `:storage-nio:test` | 已纳入 CI |
| `:storage-saf` | SAF URI/token 的仪器测试 | `:storage-saf:assembleDebugAndroidTest` 在 CI；`connectedDebugAndroidTest` 需设备 | 设备运行已完成一次 |
| 应用入口 | Android debug 与 Windows desktop 编译 | `:android-app:assembleDebug`、`:desktop-app:compileKotlin` | 已纳入 CI |

一次本地构建已经确认 `:storage-saf:assembleDebug` 和 `:android-app:assembleDebug` 能通过。构建通过不能替代真实 SAF provider、真实存储介质或交互验收。

## 已完成的人工验收

### Windows

Windows 端主交互已按 v1 工作流完成手工验收：选择目录、扫描、分类、移动、普通撤销、跳过、设置和回收站主路径均已实际操作确认。

### Android 真机

以下行为已在实际 Android 设备上完成交互确认：

- 通过 SAF 选择源目录与目标目录。
- 图片预览及可获得的大小、分辨率、修改时间显示。
- 强制停止后配置与目录授权仍被恢复，并能重新扫描。
- 新建分类、正常移动、普通撤销。
- 跳过后重启不再显示；在设置中清除跳过后重新进入待处理队列。
- 同名冲突页、重命名/跳过入口、覆盖二次确认的勾选门槛。
- 覆盖会把已有目标放入 `Drawer Trash`，而不是直接永久删除。

## 待验证或已知问题

| 项目 | 状态 | 原因与下一步 |
| --- | --- | --- |
| Android 覆盖后的撤销 | **待回归** | 曾在查询已删除的 SAF document 时崩溃。适配器已将该 provider 的缺失文档异常转换为“不存在”，本地 Android 编译已通过；需要重新安装 debug APK 后验证：源恢复、新目标删除、旧目标从 `Drawer Trash` 恢复、应用不退出。 |
| Android 源删除失败 | 待真机故障注入 | 单元测试已有故障注入；还需在真实设备确认三次重试、重试/安全回退/保留两份入口。 |
| 授权撤销、SD 卡移除、跨卷复制 | 待真机 | 需要可控设备和测试目录；不可用权限或存储介质时应保留安全副本并提示用户。 |
| 冲突页已有文件的分辨率 | 已知缺口 | 当前 `StorageEntry` 在 SAF 列表中不含宽高；对话框需要单独读取完整元数据。 |
| EXIF 拍摄时间 | 未实现 | `MediaMetadata.capturedAtEpochMs` 当前没有填充。 |
| Windows HEIF/HEIC | 环境相关 | 扫描会检查魔数，但宽高与预览仍依赖本机 JVM/Skia 解码器；应在目标发行环境验收。 |
| 标签发布与签名产物 | 待验证 | workflow 已配置，但尚未以真实 `v*` 标签、仓库 secrets 和 GitHub Release 完整跑通。 |

## 真机回归步骤：覆盖后撤销

此用例应在独立测试目录进行，避免使用唯一副本的照片。

1. 源目录和目标分类目录各放一张同名但内容不同的图片。
2. 在应用中对源图片选择该分类，进入冲突页。
3. 选择覆盖，勾选理解复选框，确认覆盖。
4. 验证源文件已消失、分类目录是新图片、`Drawer Trash` 有旧图片。
5. 点击“Undo last move”。
6. 验证应用保持运行；源目录恢复新图片，分类目录恢复旧图片，回收站中的旧图片副本被移除。

若任一步失败，记录四个位置（源、目标分类、`Drawer Trash`、应用状态）的结果，不要继续新的移动，以免覆盖故障现场。

## 发布前人工门槛

每个正式标签至少应完成：

1. 当前提交的 CI 全绿。
2. Windows 主交互手工冒烟。
3. Android 真机：授权、重启、扫描、普通移动/撤销、同名冲突、覆盖/撤销、跳过清除。
4. 检查回收站清空的高风险确认及删除结果。
5. 检查 APK、AAB、MSI 的版本号、签名和 GitHub Release 附件。
