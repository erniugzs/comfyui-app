# 绘联 — ComfyUI 移动端客户端

> 一个基于 Jetpack Compose + Material Design 3 构建的 Android 应用，让你随时随地连接、管理和使用 ComfyUI 工作流。

[English](README.md)

---

## 功能一览

### 首页 · 服务器管理
- 一键连接 / 断开 ComfyUI 服务器
- 实时检测连接延迟，绿（≤300ms）红（>300ms）直观显示
- 支持手动输入地址，或一键扫描局域网内的 ComfyUI 实例
- 支持本地服务器集成（Termux API）

### 工作流 · 编辑与运行
- JSON 工作流编辑器，支持实时编辑、节点参数调整
- 节点级配置修改（模型、采样器、步数、CFG、尺寸等）
- 一键运行工作流，实时推送进度到队列

### 队列 · 任务管理
- 实时查看生成进度（WebSocket 推送）
- 任务列表：35×35 缩略图 + 图片名 + 完成时间 + 画布尺寸
- 绿色进度条同步任务进度
- **右滑删除**：未完成任务自动中断，已完成任务同步删除服务器图片
- 点击缩略图全屏查看，支持双击放大、双指缩放、单指平移

### 图库 · 图片浏览
- 网格布局浏览历史生成图片
- 全屏查看器支持：
  - 左右滑动切换图片
  - 双击缩放（1x ↔ 2.5x）
  - 双指自由缩放（1x ~ 5x）
  - 放大后单指平移查看细节，边缘回弹
- 支持保存到本地相册、分享

### 全局交互
- **左右边缘滑动返回**：全 App 统一手势，支持返回上一层直至退出
- 全屏查看器与全局返回手势智能互斥，不冲突
- Material Design 3 主题，支持系统深色模式

---

## 技术栈

| 层级 | 技术 |
|---|---|
| UI | Jetpack Compose + Material Design 3 |
| 网络 | Retrofit（HTTP）+ Ktor Client（WebSocket） |
| 图片加载 | Coil |
| 架构 | MVVM（ViewModel + MutableState） |
| 序列化 | Gson |
| 最低版本 | Android 8.0（API 26） |

---

## 快速开始

### 前置条件
- Android Studio Ladybug 或更新版本
- JDK 17+
- ComfyUI 服务器（本地或远程）

### 构建

```bash
git clone https://github.com/erniugzs/comfyui-app.git
cd comfyui-app
./gradlew :app:assembleDebug
```

APK 输出路径：`app/apks/`

### 配置服务器

1. 打开 App，展开 **Server** 卡片
2. 输入 ComfyUI 地址（如 `http://192.168.1.100:8188`）
3. 点击「连接服务器」
4. 状态变绿后即可使用

---

## 项目结构

```
app/src/main/java/com/huilian/comfymobile/
├── MainActivity.kt          # 主入口、全局手势、导航
├── MainViewModel.kt         # 业务逻辑、网络请求、状态管理
├── BottomNavItem.kt         # 底部导航定义
├── data/
│   ├── ComfyUIService.kt    # Retrofit API 接口
│   ├── RetrofitClient.kt    # 网络客户端配置
│   ├── WebSocketManager.kt  # WebSocket 连接与事件解析
│   └── models/              # 数据模型（QueueItem、SavedWorkflow 等）
├── screens/
│   ├── HomeScreen.kt        # 首页（服务器、快捷入口）
│   ├── WorkflowScreen.kt    # 工作流编辑与运行
│   ├── WorkflowListScreen.kt # 工作流列表
│   ├── GalleryScreen.kt     # 图库浏览
│   ├── QueueScreen.kt       # 队列管理
│   └── SettingsScreen.kt    # 设置
└── components/
    └── SwipeableImageViewer.kt  # 全屏图片查看器（缩放/平移/切换）
```

---

## 开源协议

MIT License

---

## 致谢

- [ComfyUI](https://github.com/comfyanonymous/ComfyUI) — 强大的 Stable Diffusion 工作流后端
- [Jetpack Compose](https://developer.android.com/jetpack/compose) — 现代 Android UI 工具包
- [Coil](https://coil-kt.github.io/coil/) — Kotlin 图片加载库
