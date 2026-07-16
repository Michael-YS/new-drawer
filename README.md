# Drawer v2

Drawer 是面向 Android 与 Windows 的本地照片整理工具。v2 正在从 Flutter 迁移到 **Kotlin + Compose Multiplatform**：选择一个或多个源目录和一个目标目录，后台递归扫描图片，按标签分类移动，并在同一进程内保留安全的操作恢复信息。

当前主交付工程在 [`compose/`](compose/)；根目录的 Flutter 工程与 [`kotlin-core/`](kotlin-core/) 是迁移期间的旧实现/行为参考，不是 v2 的发布路径。

## 已实现的 v2 纵切

- Windows NIO 与 Android SAF（本地/SD 卡）目录选择及配置持久化。
- 支持 JPEG、PNG、WebP、HEIC/HEIF、GIF、BMP 的递归扫描；目标目录子树会跳过。
- 最近修改照片优先展示；分类标签对应目标目录下的直接子目录。
- 新建分类、跳过照片、重名时跳过/重命名/覆盖；覆盖把旧目标移到 `Drawer Trash`，并要求二次确认。
- 复制先写入临时文件并验证，再替换目标；只有最终目标存在才删除源。崩溃重启会恢复或重试最后一个操作。

仍在持续补全：Android 仪器测试、缩略图解码/缓存、会话内撤销、回收站设置页，以及完整的发布签名流程。

## 本地构建

需要 JDK 21。Android 构建还需要 Android SDK（compile SDK 35）；把 SDK 路径放进未提交的 [`compose/local.properties`](compose/local.properties)。

Windows PowerShell：

```powershell
# 跑共享/桌面测试并编译 Windows 客户端
.\kotlin-core\gradlew.bat -p compose :storage-transaction:desktopTest :persistence:desktopTest :storage-nio:test :desktop-app:compileKotlin --no-daemon

# 启动 Windows 客户端
.\kotlin-core\gradlew.bat -p compose :desktop-app:run

# 生成 Android debug APK
.\kotlin-core\gradlew.bat -p compose :android-app:assembleDebug
```

Android APK 输出在 `compose/android-app/build/outputs/apk/debug/`；Windows MSI 由 `:desktop-app:packageMsi` 生成。

## CI 与发布

- 每次 push/PR：GitHub Actions 运行 Compose 测试、Android debug 构建和 Windows 客户端编译。
- `v*` 标签：验证通过后构建 Windows MSI、签名 Android release APK 和 AAB，并上传至 GitHub Release。
- Android 标签发布需要仓库 Secrets：`ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`。缺少任意一个会明确失败，避免把 debug/未签名 APK 当作发布包。
- Windows 代码签名使用可选的 `WINDOWS_CERTIFICATE_BASE64`（PFX）与 `WINDOWS_CERTIFICATE_PASSWORD`。两者齐全时，工作流会签名并验证 MSI；未配置时仍会构建，但发布资产会明确命名为 `-unsigned.msi`。

完整的产品边界、数据安全约束和后续阶段见 [`refactor.md`](refactor.md)。
