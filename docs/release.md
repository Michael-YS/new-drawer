# 发布手册

## CI 事实

权威配置在 [`.github/workflows/build.yml`](../.github/workflows/build.yml)。该工作流会在每次 push 和针对 `main`/`master` 的 pull request 上执行：

1. Ubuntu：`check`、`:storage-nio:test`、`:storage-saf:assembleDebugAndroidTest`、`:android-app:assembleDebug`。
2. Windows：`:desktop-app:compileKotlin`。
3. 测试报告会作为短期 artifact 上传。

当 ref 是 `v*` 标签，且上述验证 job 均成功后，才会开始 Android/Windows 发布 job，最后创建 GitHub Release。不要把本地构建或普通 CI 绿灯表述为“已经发布”。

## Android 签名

标签发布必须在仓库 Secrets 中配置下列四项，否则 Android release job 会显式失败：

| Secret | 用途 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 编码的 release keystore |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密码 |
| `ANDROID_KEY_ALIAS` | 签名 key alias |
| `ANDROID_KEY_PASSWORD` | 签名 key 密码 |

工作流把 keystore 写入 runner 临时目录，设置 `DRAWER_RELEASE_VERSION` 为去掉 `v` 的标签名，并设置由 GitHub run number 生成的 version code。之后构建：

```text
:android-app:assembleRelease
:android-app:bundleRelease
```

产物是 release APK 和 AAB。debug APK 绝不能作为正式发布附件替代品。

## Windows MSI 与签名

工作流总会构建 `:desktop-app:packageMsi`。若同时配置：

| Secret | 用途 |
| --- | --- |
| `WINDOWS_CERTIFICATE_BASE64` | Base64 编码的 PFX 证书 |
| `WINDOWS_CERTIFICATE_PASSWORD` | PFX 密码 |

则用 `signtool` 进行 SHA-256 签名、时间戳并验证。如果未配置 Windows 证书，MSI 仍会构建，但会重命名为 `Drawer-v<version>-unsigned.msi`；它必须以未签名状态发布，不能暗示已签名。

## 发布步骤

1. 在待发布提交上执行[测试与验收](testing.md)中的发布前人工门槛。
2. 确认 Android 的四项签名 secrets 已存在；若要求 Windows 签名，也确认两项 PFX secrets 成对存在。
3. 确认工作树中没有误包含的 `local.properties`、keystore、APK、MSI 或测试照片。
4. 创建并推送形如 `v1.2.3` 的语义化版本标签。
5. 在 GitHub Actions 中等待 verify、Android release、Windows release 与 publish-release 全部成功。
6. 打开 GitHub Release，核对附件包含 release APK、AAB 和 MSI；检查 MSI 是否正确标识为 signed 或 unsigned。
7. 下载并在干净环境做最小安装/启动检查；Android 还应检查安装包版本与签名来源。

## 失败处理

- **缺 Android secret**：停止发布，补齐 secret 后以新的标签或经明确流程重跑；不要改传 debug APK。
- **只有 Windows 证书的一半**：workflow 会失败。补齐密码或删除证书值，让它明确发布 unsigned MSI。
- **构建失败或人工验收未通过**：不要创建/保留为正式 release；先修复并在新提交上重新验证。
- **产物已发布但发现严重文件安全问题**：停止推荐下载，发布说明标记风险；回滚/撤回需由仓库维护者明确决定，不能由本地脚本自动删除发布资产。

## 当前发布状态

发布 workflow 已实现并在普通 push 的验证路径中使用；截至本文更新时间，真实 `v*` 标签、签名 secrets、GitHub Release 资产和安装包的端到端发布仍是待验证状态。
