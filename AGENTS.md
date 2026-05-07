# AGENTS.md

## 项目概况

这是一个纯 Java Android 应用，应用名为 `ytools`。

- 包名 / application id：`com.example.codespaceandroid`
- 主模块：`app`
- 构建系统：Gradle Kotlin DSL
- Android Gradle Plugin 在根目录 `build.gradle.kts` 中声明
- 当前版本：`versionName = "1.1.0"`，`versionCode = 3`
- Release 签名使用 `keystore.properties` 和 `release-keystore.jks`

## Codespaces 构建注意事项

构建时使用 JDK 21 和仓库内的 Gradle user home：

```bash
GRADLE_USER_HOME=/workspaces/ytools/.gradle-home \
JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.10-ms \
gradle assembleRelease --no-daemon
```

在当前 Codespaces 环境中，Gradle 在普通沙箱内可能会失败，并出现：

```text
Could not determine a usable wildcard IP for this machine
```

遇到这个错误时，使用相同 Gradle 命令申请沙箱外执行权限即可。不要为了绕过这个环境问题去修改项目代码。

除非用户明确要求 debug 包，否则只构建 release 包。

Release APK 路径：

```text
app/build/outputs/apk/release/app-release.apk
```

## 版本号规则

用户已经明确要求：

- 只有用户明确要求更新前两位版本号时，才修改前两位。
- 普通代码修改后，如果需要打 release 包，打包前只递增最后一位小版本号。
- 示例：`1.1.0` -> `1.1.1` -> `1.1.2`。
- 每次 `versionName` 变化时，`versionCode` 也要递增 1。

## 应用流程

`MainActivity`

- 启动页。
- 显示 20 条本地 mock 的 Hacker News 风格新闻。
- 不应再请求真实 Hacker News 网络接口。
- 连续点击同一条新闻 5 次后进入 `ToolsActivity`。

`ToolsActivity`

- 工具入口页。
- `工具1`：打开 `MediaPickerActivity`，目标目录为 `/sdcard/012`。
- `工具2`：打开 `MediaPickerActivity`，目标目录为 `/sdcard/013`。
- `工具3`：打开 `ConvertedFilesActivity`。
- 涉及手机存储根目录操作，需要广义外部存储访问权限。

`MediaPickerActivity`

- 自定义媒体选择页，基于 `MediaStore` 查询媒体；不要改回 Android 系统 Photo Picker。
- 显示手机中的全部图片和视频。
- 使用 5 列宫格显示。
- 支持滑动批量选择。
- 选中态使用深色遮罩和高对比度勾选标记。
- 缩略图使用后台线程加载并配合 `LruCache` 缓存；不要在宫格缩略图中使用 `ImageView#setImageURI` 直接加载。
- 点击 `done` 后，将选中媒体复制到目标目录，目标文件名为原始显示名后追加 `1`，然后删除原文件。

`ConvertedFilesActivity`

- 工具3的文件列表页。
- 用 tab 分开显示 `/sdcard/012` 和 `/sdcard/013`。
- 列出文件名以 `1` 结尾的转换文件。
- 列表中只显示文件名。
- 点击图片文件后进入 `ImageViewerActivity`。
- 长按文件名弹出操作菜单：重命名或删除。

`ImageViewerActivity`

- App 内部图片查看器，用于浏览转换后的图片文件。
- 顶部显示当前图片文件名，下面显示图片序号。
- 长按顶部文件名弹出操作菜单：重命名或删除。
- 支持双指缩放、放大后拖动、左右滑动切换图片、双击放大 / 还原。
- 图片显示不要叠加变暗遮罩，除非用户明确要求。

## 存储规则

转换后的文件位于：

```text
/sdcard/012
/sdcard/013
```

工具1和工具2的重命名规则是：在完整原始文件名后追加 `1`。

```text
abc.jpg -> abc.jpg1
movie.mp4 -> movie.mp41
```

工具3目前只支持在 App 内浏览图片文件。视频转换文件可以出现在文件列表中，但 App 内视频播放尚未实现。

## Git 和发布注意事项

- Release keystore 文件已按用户明确要求提交到 git。
- 不要删除 `release-keystore.jks` 或 `keystore.properties`。
- 构建产物、本地 Android SDK、本地 Gradle 缓存应保持不提交。
- 如果用户要求发布 GitHub Release，使用 GitHub CLI 上传：

```text
app/build/outputs/apk/release/app-release.apk
```

