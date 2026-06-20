# 音乐播放器 / Music Player

---

## 中文说明

⚠️ **本项目已废弃，不再维护。**

谁想接手就来吧，可二次开发、二次打包、商用，随便用。

- ✅ 可商用
- ✅ 可二次开发
- ✅ 可二次打包
- ✅ 可闭源使用
- ✅ 无任何限制

采用 MIT 协议，详见 [LICENSE](./LICENSE)。

作者已决定从零重写新版本，这份代码作为参考开源出来。

### 技术栈

- Kotlin + Jetpack Compose
- ExoPlayer (Media3) 播放内核
- Room 数据库
- DataStore 偏好存储
- Coil 图片加载
- Haze 模糊效果
- 自定义通知栏 + MediaSession
- 悬浮歌词服务

### 功能

- 本地音乐扫描（MP3 / FLAC / WAV / OGG / M4A 等）
- 播放控制（播放/暂停/上一首/下一首/拖动进度/速度调节）
- 播放模式（顺序 / 单曲循环 / 随机）
- 队列管理
- 收藏
- 自定义播放列表
- 歌词显示（内嵌歌词 + .lrc 文件 + 悬浮歌词）
- 推荐引擎（基于播放历史、收藏、时段加权打分）
- 主题色切换
- 深色模式
- 个人资料（昵称 + 头像）
- 通知栏控制
- 音频焦点管理

### 构建

需要 Android SDK 37 + JDK 21。

```bash
./gradlew :app:assembleDebug
```

输出 APK：`app/build/outputs/apk/debug/app-debug.apk`

### 免责声明

本项目不包含任何音乐资源，仅供学习交流使用。使用本项目产生的任何法律责任由使用者自行承担。

---

## English

⚠️ **This project is deprecated and no longer maintained.**

Feel free to take it over. Modify it, repackage it, use it commercially — do whatever you want.

- ✅ Commercial use allowed
- ✅ Modification allowed
- ✅ Repackaging allowed
- ✅ Closed-source use allowed
- ✅ No restrictions

Released under the MIT License. See [LICENSE](./LICENSE).

The author has decided to rewrite a new version from scratch. This codebase is open-sourced as a reference.

### Tech Stack

- Kotlin + Jetpack Compose
- ExoPlayer (Media3) playback engine
- Room database
- DataStore preferences
- Coil image loading
- Haze blur effects
- Custom notification bar + MediaSession
- Floating lyrics service

### Features

- Local music scanning (MP3 / FLAC / WAV / OGG / M4A, etc.)
- Playback controls (play/pause/prev/next/seek/speed)
- Play modes (sequential / single loop / shuffle)
- Queue management
- Favorites
- Custom playlists
- Lyrics display (embedded lyrics + .lrc files + floating lyrics)
- Recommendation engine (weighted scoring based on play history, favorites, time of day)
- Theme color switching
- Dark mode
- User profile (nickname + avatar)
- Notification bar controls
- Audio focus management

### Build

Requires Android SDK 37 + JDK 21.

```bash
./gradlew :app:assembleDebug
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

### Disclaimer

This project does not contain any music resources and is for learning and communication purposes only. Any legal responsibilities arising from the use of this project are borne by the user.
