# 架构与数据安全

本文描述 `compose/` 中当前 Drawer v2 的实现。产品边界与尚未实现的目标以仓库根目录的 [refactor.md](../refactor.md) 为准；测试是否通过以[测试与验收](testing.md)为准。

## 总览

```text
Compose UI（共享视觉组件）
        │
Android / Windows 应用入口（目录选择、预览、会话协调）
        │
扫描器 ───── 安全移动事务 ───── 持久化仓储
        │             │                │
StorageGateway 契约       OperationJournal      SQLDelight SQLite
        │
Windows NIO ─────────── Android SAF
```

应用不通过 Ktor HTTP 调用自身核心。共享层只处理 `StorageRef` 这种平台不透明引用；它不会解析 token。Windows NIO 和 Android SAF 是唯一可将引用转换为真实文件系统对象的适配器。

## 模块职责

| 模块 | 职责 | 不负责 |
| --- | --- | --- |
| `:domain` | 存储引用、照片候选、指纹、抑制状态、操作日志模型与接口 | 文件 I/O、UI、SQL |
| `:storage-contract` | 跨平台文件系统能力契约，以及回收站递归统计/清空 | 具体路径或 URI |
| `:scanner` | 深度优先扫描、目标子树排除、签名识别、不可读项上报、抑制过滤 | 目录选择、移动文件 |
| `:storage-transaction` | 临时文件复制、校验、覆盖入回收站、删除重试、崩溃恢复、会话撤销 | 持久化实现、平台 I/O |
| `:persistence` | SQLDelight schema 与配置/日志/抑制仓储 | 媒体文件内容 |
| `:compose-ui` | 共享界面壳、照片/元数据、标签、冲突对话框 | 文件选择器、图片解码、会话状态 |
| `:storage-nio` | Windows NIO 实现；不跟随符号链接或其他 reparse point | Android URI |
| `:storage-saf` | Android 本机/SD 卡树 URI、持久化授权与 `DocumentsContract` I/O | 非本地 DocumentProvider |
| `:android-app` | SAF 目录选择、Android 预览、会话协调 | Windows 路径 |
| `:desktop-app` | Swing 目录选择、Skia 预览、会话协调 | Android 权限 |

`kotlin-core/` 的旧 Kotlin 模块、`android-native-module/` 的 React Native TurboModule，以及根目录 Flutter 工程不是 v2 依赖图的一部分。它们只能作为历史参考，不能在修复 v2 时重新接回主构建。

## 扫描与队列

扫描器是可取消的冷 `Flow`。它对每个可用源目录作深度优先遍历：

1. 目标目录和 `Drawer Trash` 作为精确排除目录，不会进入待处理队列。
2. 多源重叠时，应用入口保留父目录，静默忽略已被父目录包含的子目录；例如选中 `DCIM` 后再选 `DCIM/Camera`，只扫描 `DCIM`。
3. 文件先按魔数识别 JPEG、PNG、GIF、WebP、BMP、HEIF/HEIC，再要求平台解码器能读出宽高。扩展名本身不构成支持依据。
4. 已跳过或“保留副本”的文件，只有源根、引用、大小和修改时间均匹配时才会被抑制；文件变更会重新出现。
5. 已发现队列按修改时间倒序，当前正在显示的照片不被新发现文件打断。

扫描错误会作为会话内 `Problem` 事件计数，不会自动把文件写成“跳过”。切换源或目标目录时，UI 要求确认后取消后台扫描。

## 文件移动、覆盖与恢复

`SafeMove` 在任意时刻只允许一条活动操作，且每个阶段都会覆盖更新 `operation_journal` 中唯一的一条日志。

```text
正常移动
创建目标临时文件
  -> 流式复制源文件
  -> 比对复制字节数
  -> 将临时文件落为最终目标
  -> 验证最终目标元数据
  -> 最多三次删除源文件
  -> 清除日志

覆盖移动
将原目标安全复制到 Drawer Trash
  -> 验证并删除原目标
  -> 执行正常移动
```

关键不变量：在删除源文件之前，必须已经存在经字节数校验的最终目标；任何早期失败都应保留源文件。若最终目标已存在但源删除三次失败，应用停止在当前照片并提供重试删除、安全回退或“保留两份”选项。

### 崩溃恢复

启动时，应用在开始扫描或下一次移动前读取最后一条日志：

- 仅有临时文件：尽力清理临时文件，或恢复被覆盖的旧目标。
- 最终目标已存在、源仍存在：再次尝试删除源。
- 最终目标已存在、源不存在：确认完成并清除日志。
- 无法证明上述任一安全状态：进入“需要人工介入”，不开始新的移动。

这不是跨文件系统的原子 rename 承诺；真实实现是“复制、校验、最终落盘、再删源”的可恢复事务。

### 同一会话撤销

成功移动在内存中创建 LIFO 撤销记录。撤销先复制回源位置并校验，然后删除移动后的目标；若本次移动覆盖了同名目标，还会从 `Drawer Trash` 恢复旧目标。应用关闭后撤销记录丢失，回收站文件仍保留。

SAF 提供方可能把“刚删除的文档”查询报告为 `IllegalArgumentException` 而不是空结果。SAF 适配器将这种已验证引用的缺失情况按“不存在”处理，使覆盖后撤销能返回业务结果而不是使应用崩溃；该修复仍待真机回归测试。

## 持久化数据

SQLDelight schema 位于 `compose/persistence/src/commonMain/sqldelight/.../Drawer.sq`。本地数据库只保存索引和操作状态，不保存或重写图片内容。

| 数据 | 作用 | 重启后 |
| --- | --- | --- |
| `source_roots` | 源目录的不透明引用、显示名、可用状态 | 保留 |
| `current_target` | 唯一目标目录 | 保留 |
| `known_trash_roots` | 曾作为目标的根目录，用于聚合回收站 | 保留 |
| `category_first_seen` | 分类目录的首次发现时间，决定倒序展示 | 保留 |
| `suppressed_items` | 跳过或保留副本的文件指纹 | 保留，直至设置清除 |
| `operation_journal` | 唯一一条未完成文件操作 | 保留至恢复/完成 |
| 扫描队列和撤销栈 | 当前进程的会话状态 | 不保留 |

桌面数据库路径是 `~/.drawer-v2/drawer-v2.db`。Android 数据库位于应用私有空间；其目录访问能力来自用户选择后持久化的 SAF read/write URI grant，而不是传统存储权限。

## 平台差异与限制

### Windows

- 文件引用是规范化的绝对 `Path`。
- 目录选择使用 `JFileChooser`。
- 读取 `BasicFileAttributes`，可显示创建时间；reparse point 被当作不支持项，不会递归进入。
- 图片预览使用 Skia；宽高依赖 JVM 的 `ImageIO` reader。

### Android

- 用户通过 `ACTION_OPEN_DOCUMENT_TREE` 选择目录；只接受 `com.android.externalstorage.documents` 的本机或 SD 卡树。
- token 同时保存 tree URI 与 document URI，重新启动后继续使用持久化 read/write grant。
- grant 被撤销时，目标目录要求重新选择；失效源目录会标记为不可用。
- SAF 不可靠地提供创建时间，当前 UI 不显示 Android 创建时间；当前实现也尚未抽取 EXIF 拍摄时间。

### 当前已知限制

- 冲突对话框的“已有文件”在 Android SAF 上目前只能稳定显示名称、大小与修改时间；分辨率需要后续改为单独查询完整元数据。
- 声称支持的图片格式仍取决于各平台解码器能否读取宽高与预览。特别是 Windows 上 HEIF/HEIC 的可用性取决于运行环境中的 reader。
- 应用内不提供分类重命名/删除，也不提供回收站浏览或恢复；用户可通过系统文件管理器手动处理 `Drawer Trash`。
