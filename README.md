# HEIC 压缩

一个基于 Kotlin 与 Jetpack Compose 的 Android 图片转 HEIC / 压缩工具。

## 功能

- 从系统图片选择器导入单张或多张图片
- 支持从其他 App 分享图片进入应用
- 将 JPG / PNG / WebP / BMP 转换为 HEIC
- 可调整 HEIC 输出质量
- 可选择保留图片元数据
- 支持转换前预览压缩效果
- 支持保存到相册或分享生成的 HEIC 文件
- 可识别疑似全景图并按设置跳过
- 可选择在生成文件更小时替换原图

## 系统要求

- Android 10 / API 29 及以上
- 设备系统需要提供可用的 HEIC / HEIF 编码器

如果当前设备不支持 HEIC 编码，应用会在界面中提示。

## 构建

```bash
./gradlew assembleDebug
```

构建 release APK：

```bash
./gradlew assembleRelease
```

## 项目信息

- 包名：`com.example.heicconverter`
- 当前版本：`0.2.6`
- 技术栈：Kotlin、Jetpack Compose、Material 3、AndroidX HeifWriter

## 注意

HEIC 编码能力依赖 Android 系统和设备硬件/系统组件。不同设备上的可用性和压缩效果可能不同。
