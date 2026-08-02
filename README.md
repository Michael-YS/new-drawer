# Drawer v2

Drawer 是一款在 **Windows** 和 **Android** 上运行的本地照片分拣工具。用户选择一个或多个源目录和一个目标目录，应用在后台递归发现可处理的图片；用户为当前图片选择分类标签后，应用将图片安全地移入目标目录下对应的子目录。

当前交付路径是 `compose/` 中的 **Kotlin + Compose Multiplatform**。仓库根目录的 Flutter、`kotlin-core/`、以及 iOS/macOS/Linux/Web 目录只保留为历史行为参考，不参与 v2 构建、测试或发布。

## 用户工作流

1. 选择至少一个源目录和恰好一个目标目录。
2. 应用递归扫描源目录；若目标目录位于源目录内，会跳过整个目标子树与 `Drawer Trash`。
3. 查看当前图片及其可获得的文件元数据，点击已有分类，或就地新建分类。
4. 同名文件时选择跳过、重命名，或经二次确认后覆盖。覆盖前的目标文件会移入 `Drawer Trash`。
5. 在同一次应用进程中可以撤销已完成的移动。关闭应用会结束撤销会话，但不会清空回收站。

目录配置、分类首次发现顺序、跳过/保留副本状态和最后一条未完成文件操作会持久化。v2 不迁移旧 Flutter 的本地索引；首次使用 v2 时重新选择目录即可。

## 文档

- [架构与数据安全](docs/architecture.md)：模块边界、扫描、移动、恢复、回收站和持久化模型。
- [开发指南](docs/development.md)：环境、构建、运行、调试与代码约定。
- [测试与验收](docs/testing.md)：自动化测试、已完成的人工验收和仍待验证项目。
- [发布手册](docs/release.md)：GitHub Actions、签名密钥、标签发布和发布后检查。
- [重构计划](refactor.md)：v1 产品边界与验收设计；它是设计依据，不等同于当前验收记录。

## 快速开始

要求：JDK 21。Android 构建还需要 Android SDK（compile SDK 35）；将本机 SDK 路径写入未跟踪的 `compose/local.properties`。

在 Windows PowerShell 中：

```powershell
# 跑共享测试、Windows NIO 测试，并编译桌面端
.\kotlin-core\gradlew.bat -p compose :storage-transaction:desktopTest :persistence:desktopTest :storage-nio:test :desktop-app:compileKotlin --no-daemon

# 启动 Windows 客户端
.\kotlin-core\gradlew.bat -p compose :desktop-app:run

# 生成 Android debug APK
.\kotlin-core\gradlew.bat -p compose :android-app:assembleDebug
```

Android debug APK 输出到 `compose/android-app/build/outputs/apk/debug/`；Windows MSI 由 `:desktop-app:packageMsi` 生成。完整命令及设备测试步骤见[开发指南](docs/development.md)。

## 当前范围与状态

v1 目标平台为 Android API 26+ 和 Windows 10/11 x64。当前实现使用 Android Storage Access Framework（SAF）访问本机或 SD 卡目录，不支持网盘等文档提供方。

Windows 主交互已完成人工验收；Android 已验证目录授权、配置重启保留、扫描、分类移动、普通撤销、跳过状态以及覆盖二次确认。Android 的“覆盖后撤销”刚修复了一个 SAF 缺失文档异常，已本地编译，仍待重新安装到真机复验。完整且不把待验收项当作完成项的清单见[测试与验收](docs/testing.md)。

## CI 与发布

每次 push 和 pull request 都会运行 Compose 测试、Windows NIO 测试、Android 测试 APK 编译、Android debug APK 构建和 Windows 客户端编译。推送 `v*` 标签会在这些检查通过后构建 Android APK/AAB 与 Windows MSI，并创建 GitHub Release。签名和具体操作见[发布手册](docs/release.md)。
