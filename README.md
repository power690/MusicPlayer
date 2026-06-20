# 音乐播放器 / Music Player

---

## 中文说明

⚠️ **本项目已废弃，不再维护。**

谁想接手就来吧，欢迎 fork、二次开发、二次打包、商用，随便用。

- ✅ 可商用
- ✅ 可二次开发
- ✅ 可二次打包
- ✅ 可闭源使用
- ✅ 无任何限制

采用 MIT 协议，详见 [LICENSE](./LICENSE)。

作者已决定从零重写新版本，这份代码作为参考开源出来。项目存在一些已知架构问题（详见下文），不推荐直接用于生产环境，但可作为学习、参考或二次开发的基础。

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

### 已知问题

新版本需要从架构层面重新设计：

- 音乐库长列表（数百首歌曲）在某些设备上渲染异常
- 列表项的 Compose 重组范围控制不够精细，存在性能瓶颈
- LazyColumn 在 Column 子元素中的尺寸约束处理需要更严谨
- 部分详情页（歌手、专辑）的数据查询和分类逻辑一致性需要重新设计

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

Feel free to take it over. Fork it, modify it, repackage it, use it commercially — do whatever you want.

- ✅ Commercial use allowed
- ✅ Modification allowed
- ✅ Repackaging allowed
- ✅ Closed-source use allowed
- ✅ No restrictions

Released under the MIT License. See [LICENSE](./LICENSE).

The author has decided to rewrite a new version from scratch. This codebase is open-sourced as a reference. There are some known architectural issues (see below); it is not recommended for production use directly, but it can serve as a base for learning, reference, or further development.

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

### Known Issues

The new version needs to be redesigned at the architectural level:

- Long lists in the music library (hundreds of songs) render abnormally on some devices
- Compose recomposition scope control for list items is not fine-grained enough, causing performance bottlenecks
- LazyColumn size constraint handling within Column children needs to be more rigorous
- Data query and classification logic consistency for detail pages (artists, albums) needs to be redesigned

### Build

Requires Android SDK 37 + JDK 21.

```bash
./gradlew :app:assembleDebug
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

### Disclaimer

This project does not contain any music resources and is for learning and communication purposes only. Any legal responsibilities arising from the use of this project are borne by the user.
