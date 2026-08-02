# 开发指南

## 先读什么

当前产品代码位于 `compose/`，不是仓库根目录的 Flutter 工程。修改行为前先阅读：

1. [架构与数据安全](architecture.md)：模块边界和不可破坏的数据安全规则。
2. [重构计划](../refactor.md)：产品边界和 v1 验收意图。
3. [测试与验收](testing.md)：哪些结论已验证，哪些仍是待办。

不要将 Flutter、旧 `kotlin-core`、Ktor 或 React Native TurboModule 重新加入 v2 的依赖或 CI，除非产品方向另行变更。

## 环境

| 项目 | 要求 |
| --- | --- |
| JDK | 21 |
| Gradle | 使用 `kotlin-core/gradlew.bat` 或 `kotlin-core/gradlew` wrapper |
| Android | Android SDK，compile SDK 35；最低 API 26 |
| Windows | Windows 10/11 x64；本地运行需要可用的桌面 JVM |

在 Windows 上创建未提交的 `compose/local.properties`：

```properties
sdk.dir=C:\\Android\\Sdk
```

该文件与 `compose/**/build/` 已被 `.gitignore` 排除。不要把本机 SDK 路径、keystore 或签名密码提交进仓库。

## 常用命令

以下命令从仓库根目录执行。

```powershell
# 所有配置的 Compose 检查，加上 Windows 文件系统契约测试
.\kotlin-core\gradlew.bat -p compose check :storage-nio:test --no-daemon --console=plain

# 聚焦文件移动与持久化测试
.\kotlin-core\gradlew.bat -p compose :storage-transaction:desktopTest :persistence:desktopTest --no-daemon --console=plain

# 运行 Windows 客户端
.\kotlin-core\gradlew.bat -p compose :desktop-app:run

# 编译 Android debug APK
.\kotlin-core\gradlew.bat -p compose :android-app:assembleDebug --no-daemon --console=plain

# 编译 Android 仪器测试 APK（不运行设备测试）
.\kotlin-core\gradlew.bat -p compose :storage-saf:assembleDebugAndroidTest --no-daemon --console=plain

# 在已连接且授权的设备/模拟器上运行 SAF 仪器测试
.\kotlin-core\gradlew.bat -p compose :storage-saf:connectedDebugAndroidTest --no-daemon --console=plain

# 生成 Windows MSI
.\kotlin-core\gradlew.bat -p compose :desktop-app:packageMsi --no-daemon --console=plain
```

输出位置：

- Android debug APK：`compose/android-app/build/outputs/apk/debug/`
- Android release APK：`compose/android-app/build/outputs/apk/release/`
- Android AAB：`compose/android-app/build/outputs/bundle/release/`
- Windows MSI：`compose/desktop-app/build/compose/binaries/main/msi/`

## 本地运行与手工检查

### Windows

启动 `:desktop-app:run` 后：选择一个含测试图片的源目录和空目标目录；创建分类、移动、撤销、跳过并清除跳过记录。用第二张同名图片检查冲突页面与覆盖确认。不要用真实且未备份的个人照片做故障注入。

### Android

1. 通过系统目录选择器选择源目录和目标目录；它们必须是本机或 SD 卡目录。
2. Android 会保存对所选目录的读写授权。强制停止并重新打开应用，确认配置仍在；若系统撤销授权，应提示重新选择。
3. 使用测试目录验证扫描、移动、普通撤销、跳过/清除跳过、冲突重命名、覆盖二次确认和回收站统计。
4. 覆盖、源删除失败和权限/SD 卡故障会影响真实文件；执行前复制测试素材，并在[测试矩阵](testing.md)中记录结果。

设备测试不应在用户要求暂停后继续执行，也不应自动安装或替换用户设备上的 APK。

## 代码约定

- `StorageRef.token` 是平台私有的不透明值。共享代码只能持久化、比较和传回它，不能解析它。
- 所有真实文件复制必须流式进行，并在删除源前验证复制字节数与最终目标可读。
- 新文件操作阶段必须先写入唯一 `operation_journal`；不要为并发操作增加第二条活动日志。
- 回收站目录固定叫 `Drawer Trash`，不能作为分类名或扫描输入。
- 分类名、同名检测与文件名输入必须同时满足 Windows 与 SAF 的跨平台命名限制。
- 公共接口、失败分支和安全不变量需要解释“为什么”的 KDoc/注释；私有 UI 的显而易见布局不要逐行注释。

## 修改后最低验证

| 改动 | 至少运行 |
| --- | --- |
| 纯共享模型/扫描/事务 | `check` 与 `:storage-nio:test` |
| SQLDelight schema 或仓储 | `:persistence:desktopTest`，并检查新旧数据库语义 |
| NIO 适配器 | `:storage-nio:test`，必要时 Windows 手工流程 |
| SAF 适配器 | `:storage-saf:assembleDebugAndroidTest`；涉及 provider 行为还要 `connectedDebugAndroidTest` |
| Android UI/入口 | `:android-app:assembleDebug`；影响文件操作时真机验收 |
| Desktop UI/入口 | `:desktop-app:compileKotlin`；影响工作流时手工验收 |
| CI/发布流程 | 核对 `.github/workflows/build.yml`，按[发布手册](release.md)进行非生产预检 |
