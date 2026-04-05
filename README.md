# Dreamy Rainbow RPG Android

Dreamy Rainbow RPG Android 是一个面向 Android 设备的移植工程，用于在移动端运行 Dreamy Rainbow RPG 的定制版本。

项目基于 `mkxp-z` Android 外壳进行了适配，加入了章节启动、内置资源安装、中文构建变体以及针对当前项目的界面与资源配置。

## 项目说明

- 仓库地址：`https://github.com/Ajwyunsx/Dreamy-Rainbow-RPG`
- 应用包名：`org.dreamyrainbowrpg.android`
- 当前版本：`v0.1.1`
- Release 页面：`https://github.com/Ajwyunsx/Dreamy-Rainbow-RPG/releases/tag/v0.1.1`

## 下载说明

当前 Release 提供以下 APK：

- `dreamy-rainbow-rpg-0.1.1-debug.apk`：默认构建版本
- `dreamy-rainbow-rpg-0.1.1-debug-zhcn.apk`：中文版本构建

如果你只是想下载安装，直接前往 Release 页面获取 APK 即可：

`https://github.com/Ajwyunsx/Dreamy-Rainbow-RPG/releases`

## 仓库内容

仓库主要保存以下内容：

- Android 工程源码
- 应用界面与图标资源
- 章节选择与资源安装逻辑
- payload 清单文件与安装配置

以下大文件不直接纳入 Git 仓库：

- 原始 `payload*.zip`
- 根目录下的 `.rar` 打包文件
- 本地解包目录与构建产物

这些内容更适合通过 GitHub Release 或其他制品分发方式提供。

## 本地构建

在项目根目录执行：

```bat
gradlew.bat assembleDebug
```

构建完成后，APK 默认输出到：

`app/build/outputs/apk/debug/`

## 当前状态

- 主分支使用：`main`
- GitHub Release 已发布：`v0.1.1`
- 当前 Release 已包含默认 APK 与中文 APK

## 致谢

本项目基于 `mkxp-z` Android 相关工程进行二次适配与整理，并结合 Dreamy Rainbow RPG 的实际发布需求进行了定制化修改。
