# Photo Organizer App — 开发规格文档

## 项目概述

一个帮助用户手动整理本地照片的工具。核心工作流：展示未处理的图片 → 用户选择目标文件夹 → 归档。

目标平台：**Windows (exe)** 和 **Android (APK)**。

---

## 技术栈

| 层级 | 选型 |
|------|------|
| 框架 | Flutter (Dart) |
| 状态管理 | flutter_riverpod |
| 本地数据库 | sqflite |
| 图片预览 | photo_view |
| 文件选择 | file_picker |
| 路径工具 | path_provider, path |

---

## 核心功能

### 1. 初始化：管理源文件夹

支持**多个源文件夹**，用户可随时添加或移除。

- 用户可添加多个本地文件夹作为"待处理图片来源"，每个文件夹独立授权（Android SAF）
- 每次启动时对所有启用的源文件夹执行扫描，扫描图片格式：`.jpg`, `.jpeg`, `.png`, `.gif`, `.webp`, `.heic`
- 扫描结果写入本地 DB，状态标记为 `pending`；新增文件追加，已不存在的文件标记为 `missing`
- 支持对单个源文件夹**启用/禁用**（禁用后不再扫描，但已有记录保留）
- 支持移除源文件夹（该文件夹下所有 `pending` 记录随之删除，`done` 记录保留用于历史查看）

### 2. 主界面：图片分拣

主界面展示当前 `pending` 队列中的第一张图片。

布局：
- 上方：大图预览（支持手势缩放、双击放大）
- 下方：目标文件夹列表（用户预设的常用文件夹，可快速点击）
- 底部操作栏：`跳过` / `移动到...`（弹出文件夹选择器）/ `删除`

交互细节：
- 点击目标文件夹 → 将图片**移动**到该文件夹 → 状态更新为 `done` → 自动进入下一张
- `跳过` → 状态更新为 `skipped`，进入下一张（可在设置中重新显示被跳过的图片）
- `删除` → 将文件移入应用内回收站（见下文），状态更新为 `trashed`，进入下一张
- 支持**撤销**上一步操作（将文件移回原位，状态改回 `pending`）

进度显示：`已处理 N / 共 M 张`

### 3. 目标根目录管理

目标根目录是所有分类文件夹的父目录，应用只在根目录内创建和管理分类，不允许将根目录外的任意文件夹加入目标列表。

- 默认只有**一个**目标根目录（首次设置时配置）
- 设置页可开启**多根目录模式**，启用后允许添加多个根目录，每个根目录可设置显示名称
- 多根目录模式下，在目标文件夹管理页新建分类时，需选择归属哪个根目录
- Android 端：目标根目录必须通过 SAF 单独授权（`ACTION_OPEN_DOCUMENT_TREE`），保存 `persistedUriPermission`

### 4. 目标文件夹管理页

独立页面，用于预先建立和管理所有分类文件夹，分拣时直接选用。

**新建分类：**
- 输入分类名称（即子文件夹名）
- 单根目录模式：直接在根目录下创建，路径为 `<根目录>/<分类名>/`
- 多根目录模式：弹出选择框让用户选归属哪个根目录，再创建
- 应用在磁盘上**实际创建**该文件夹（Windows：`Directory.create()`；Android：SAF `DocumentFile.createDirectory()`）

**管理分类列表：**
- 删除列表项（仅移出列表，不删除磁盘文件夹）
- 重命名显示名称（不重命名磁盘文件夹）
- 支持拖拽排序
- 多根目录模式下，列表按根目录分组展示

### 5. 分拣界面的目标文件夹交互

- 分拣界面下方展示目标文件夹列表（来自管理页预设的分类）
- 支持在分拣时**临时新建分类**：输入名称，自动建在**默认根目录**下，并立即归档当前图片（无需进入管理页）
- 最近使用的文件夹自动置顶（可配置）

### 6. 应用内回收站

采用**软删除 + 延迟清理**方案，不依赖系统回收站 API，跨平台行为完全一致。

**删除流程：**
- 用户点击删除 → 文件被**移动**到回收站目录（不弹确认框，可通过撤销恢复）
- 回收站目录固定为默认目标根目录下的 `.trash/` 隐藏文件夹
- 移入时重命名为 `<原文件名>__<unix_timestamp>.<ext>`，避免同名冲突，同时保留时间信息
- DB 中记录原路径（`original_path`）和移入时间（`trashed_at`），状态标记为 `trashed`

**撤销删除：**
- 撤销上一步时，若操作类型为删除，将文件从 `.trash/` 移回 `original_path`，状态改回 `pending`
- 若原路径已被占用（极少数情况），追加 `_restored` 后缀

**自动清理：**
- 应用启动时检查 `.trash/` 下超过 **30 天**的文件，自动永久删除
- 设置页提供"立即清空回收站"按钮，二次确认后清空

**Android SAF 注意：**
- `.trash/` 目录通过 SAF 在默认根目录下创建（`DocumentFile.createDirectory(".trash")`）
- 文件移入/移出均使用 `DocumentFile` 的 rename + move，不依赖 `MediaStore.createTrashRequest`

### 7. 设置

- 管理源文件夹（添加、移除、启用/禁用）
- 管理目标根目录（设置默认根目录；开启多根目录模式后可添加/移除额外根目录）
- 是否递归扫描子文件夹（默认：是，可对每个源文件夹单独配置）
- 是否显示被跳过的图片
- 重置所有处理状态（重新处理全部图片）

---

## 数据模型

### DB 表：`source_folders`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | INTEGER PK | 自增主键 |
| `path` | TEXT UNIQUE | 文件夹路径（Windows）或 SAF tree URI（Android） |
| `display_name` | TEXT | 显示名称 |
| `enabled` | INTEGER | 1 = 启用，0 = 禁用 |
| `recursive` | INTEGER | 1 = 递归扫描子文件夹，0 = 仅当前层 |
| `added_at` | INTEGER | Unix timestamp |

### DB 表：`photos`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | INTEGER PK | 自增主键 |
| `source_folder_id` | INTEGER FK | 关联 `source_folders.id` |
| `path` | TEXT UNIQUE | 文件绝对路径（Windows）或 content URI（Android） |
| `status` | TEXT | `pending` / `done` / `skipped` / `trashed` / `missing` |
| `destination` | TEXT nullable | 移动后的目标路径 |
| `original_path` | TEXT nullable | 删除前的原始路径（用于撤销） |
| `trashed_at` | INTEGER nullable | 移入回收站的 Unix timestamp |
| `processed_at` | INTEGER nullable | Unix timestamp |

### DB 表：`target_root_dirs`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | INTEGER PK | 自增主键 |
| `path` | TEXT UNIQUE | 文件夹路径（Windows）或 SAF tree URI（Android） |
| `display_name` | TEXT | 显示名称 |
| `is_default` | INTEGER | 1 = 默认根目录（全局唯一），0 = 其他 |
| `added_at` | INTEGER | Unix timestamp |

### DB 表：`target_folders`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | INTEGER PK | 自增主键 |
| `root_dir_id` | INTEGER FK | 关联 `target_root_dirs.id` |
| `name` | TEXT | 子文件夹名（磁盘实际名称，如 `anime`） |
| `display_name` | TEXT | 显示名称（可与 `name` 不同） |
| `sort_order` | INTEGER | 排列顺序 |
| `last_used_at` | INTEGER nullable | 最近使用时间 |

> 完整路径由 `target_root_dirs.path + "/" + target_folders.name` 拼接得出，不单独存储，避免根目录变更时需批量更新。

---

## 平台差异处理

### Windows

- 使用 `dart:io` 的 `File` / `Directory` API 直接操作文件系统
- 文件夹选择使用 `file_picker` 的 `getDirectoryPath()`
- 文件移动：`file.renameSync(destination)` 或跨盘符时 copy + delete

### Android

**重要**：Android 10+ 强制使用 Storage Access Framework (SAF)，不能直接用文件路径。

- 用户通过 SAF 授权源文件夹，保存 `persistedUriPermission`
- 文件操作使用 `DocumentFile` API（通过 platform channel 调用原生代码，或使用 `saf` / `shared_storage` 包）
- 推荐包：[`shared_storage`](https://pub.dev/packages/shared_storage)，封装了 SAF 的授权、列举、移动、删除操作
- content URI 格式示例：`content://com.android.externalstorage.documents/tree/...`
- 注意：`file_picker` 在 Android 上返回缓存副本路径，**不适合**用于文件夹写操作，须用 SAF

`AndroidManifest.xml` 需要的权限：
```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32"/>
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="29"/>
<!-- Android 13+ 使用细分权限 -->
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES"/>
```

---

## 项目结构建议

```
lib/
├── main.dart
├── app.dart                  # MaterialApp / 路由配置
├── core/
│   ├── database/
│   │   ├── database.dart     # sqflite 初始化
│   │   └── migrations/
│   ├── models/
│   │   ├── photo.dart
│   │   ├── source_folder.dart
│   │   ├── target_root_dir.dart
│   │   └── target_folder.dart
│   └── services/
│       ├── file_service.dart         # 抽象接口（含 scanFolder、createDirectory、moveFile）
│       ├── file_service_windows.dart # Windows 实现
│       └── file_service_android.dart # Android SAF 实现
├── features/
│   ├── setup/                # 首次设置（引导添加第一个源文件夹）
│   ├── source_folders/       # 源文件夹管理（添加、移除、启用/禁用）
│   ├── organizer/            # 主分拣界面
│   │   ├── organizer_page.dart
│   │   ├── organizer_provider.dart
│   │   └── widgets/
│   │       ├── photo_viewer.dart
│   │       └── folder_button_list.dart
│   ├── target_root_dirs/     # 目标根目录管理
│   ├── target_folders/       # 目标文件夹管理页（分类预设）
│   └── settings/             # 设置页
└── shared/
    └── widgets/              # 公共组件
```

---

## 开发优先级

**P0（MVP）：**
1. Windows 端完整工作流（管理多个源文件夹 → 预览 → 移动/跳过）
2. 本地 DB 持久化处理状态（含 `source_folders`、`target_root_dirs`、`target_folders` 表）
3. 目标根目录设置 + 目标文件夹管理页（预建分类）
4. 分拣时一键归档到预设分类，支持临时新建（自动归入默认根目录）

**P1：**
5. 撤销操作（移动和删除均可撤销）
6. Android 端 SAF 集成
7. 进度统计

**P2：**
7. 递归扫描开关
8. 跳过队列重新处理
9. 键盘快捷键支持（Windows）

---

## 注意事项

- 目标文件夹完整路径由 `root_dir.path + "/" + folder.name` 运行时拼接，不入库，避免根目录变更时批量更新
- 多个源文件夹扫描应并发执行（`Future.wait`），但每个文件夹内部顺序扫描，避免 isolate 数量失控
- 文件操作需做错误处理：目标路径已存在同名文件时，追加 `_1`, `_2` 后缀，不要静默覆盖
- Android SAF 授权在应用重启后需要重新验证 `persistedUriPermissions`，每次启动检查一次
- 大文件夹扫描（数万张图）应在 isolate 中执行，避免 UI 卡顿
- Windows 跨盘符移动文件（如 C: → D:）不能用 `rename`，需要 copy + delete
- `.trash/` 目录与目标根目录同盘符（Windows）/ 同 SAF 授权树（Android），移入操作始终是 rename，不会触发跨盘符 copy
- 清理过期 trash 文件时需跳过 `original_path` 仍在 `.trash/` 内的记录（即多次删除同一文件的异常情况）
