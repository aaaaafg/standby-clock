# 待机时钟（iOS StandBy 风格桌面充电时钟）

一个独立的安卓应用（APK），仿 iOS StandBy 待机模式：**手机充电中 + 横放时，自动显示全屏横屏时钟**；拔掉充电线或拿起手机，自动关闭并回到锁屏。

- 优先适配：小米澎湃OS 4（HyperOS 4），兼容通用安卓 8.0+（minSdk 26）
- 界面：纯黑背景、白色大号无衬线字体，苹果 StandBy 风格
- 无需联网账号、无需注册、无广告，本地运行；APK 仅约 2.5 MB

---

## 功能一览

| 功能 | 说明 |
|---|---|
| 自动启动 | 充电中 + **侧立横放** 或 **平放朝上**（去抖约 1.5 秒）→ 自动全屏时钟 |
| 自动退出 | 拔充电线 / 拿起手机（竖放）→ 自动关闭，回到锁屏；双击屏幕可手动退出（本次充电内不再弹出） |
| 时钟页 | 超大数字时钟（时:分 + 秒）、日期、星期、电池电量（含充电图标）、天气摘要一行 |
| 日历任务页 | iOS 同款布局：左侧月历（今天=红圈白字），右侧当日日程（读取系统日历） |
| 天气页 | 当前温度、天气现象、最高/最低温、城市、未来 6 小时逐时预报（Open-Meteo 免费数据源） |
| 左右滑动 | 三页滑动切换 + iOS 风格翻页指示点 |
| 防烧屏 | 内容沿缓慢路径周期性像素偏移（最大约 6dp，25 秒缓动，60 秒一换） |
| 夜间低亮度 | 夜间时段（默认 22:00–07:00）自动把亮度降到极低，保护屏幕与眼睛 |
| 自定义样式 | 字体颜色（6 种预设 + 自定义色值）、**时钟数字大小 60%–180% 滑块**、待机亮度、显示秒、12/24 小时制 |
| 权限状态面板 | 自动检测各项权限开关状态；缺失项一键直达设置页；内置澎湃OS4 精确设置路径 |
| 分层设置界面 | iOS「设置」风格分组列表，外观/防烧屏/天气/权限/诊断分页进入 |
| 诊断信息 | 实时检测状态（充电/放置姿态）、监控服务状态、测试弹出、最近异常记录 |
| 配置导出 | 一键导出当前配置为 JSON 文件 |

## 权限清单（为什么需要）

| 权限 | 用途 | 必需 |
|---|---|---|
| 悬浮窗 | 备用显示模式（系统禁止后台弹出时） | 建议 |
| 后台运行 / 前台服务 | 充电状态与横放检测 | 必需 |
| 电池不优化 | 防止系统杀掉监控服务 | 必需 |
| 自启动 / 后台弹出界面 / 锁屏显示（澎湃OS） | 自动恢复监控、自动弹出时钟 | 必需（小米） |
| 通知 | 前台服务通知 | 必需 |
| 读取电池电量 | 时钟页显示电量（系统接口，**无需授权**） | 自动 |
| 重力传感器 | 横竖屏/平放检测（系统接口，**无需授权**） | 自动 |
| 日历 | 日历任务页读取当日日程 | 可选 |
| 定位 | 天气页自动定位（也可手动设城市） | 可选 |
| 修改系统设置 | 全局亮度调节（本应用用窗口亮度，**可不授权**） | 可选 |

> 详细开启步骤见 [docs/权限开启步骤.md](docs/权限开启步骤.md)。

## 如何构建 APK

### 现成安装包

`dist/待机时钟-v1.4.0.apk` 是**已签名、可直接安装**的正式版（本机实测构建通过，签名校验通过）。直接传到手机安装即可。也可在 [Releases](https://github.com/aaaaafg/standby-clock/releases) 下载。

### 自行重新构建

**方式 A：Android Studio（推荐，最简单）**

1. 安装 [Android Studio](https://developer.android.google.cn/studio)（自带 JDK 与 SDK）。
2. 打开本目录，等待 Gradle 同步（已配置阿里云/腾讯镜像，国内速度快）。
3. 菜单 **Build → Build Bundle(s) / APK(s) → Build APK(s)**。
4. 产物：`app/build/outputs/apk/debug/app-debug.apk`。

**方式 B：命令行**

```bash
# 需本机已装 JDK 17+，且配置好 ANDROID_HOME（SDK 34）
gradlew.bat assembleDebug        # Windows
./gradlew assembleDebug          # macOS / Linux
```

**签名说明**：本仓库**不包含签名密钥**（`standby-release.jks` 已加入 .gitignore）。debug 构建自动用调试签名，可直接安装。如需正式签名，`keytool -genkeypair` 生成 `standby-release.jks` 放在项目根目录即可（`app/build.gradle.kts` 检测到密钥后会启用 release 签名）。

## 安装方法

1. 把 APK 传到手机（微信/QQ/数据线均可），点击安装；出现"未知来源"提示时选择**允许安装**。
   - 澎湃OS4 路径：设置 → 安全 → 更多安全设置 → 安装未知来源应用（或"应用包安装程序"允许）。
2. 打开「待机时钟」→ 按提示开启权限（见 [docs/安装说明.md](docs/安装说明.md)）。
3. 手机充电时**侧立横放或平放桌面**，约 2 秒后自动进入待机时钟。

## 目录结构

```
待机时钟/
├─ app/src/main/AndroidManifest.xml          # 权限、组件声明
├─ app/src/main/java/com/deskclock/standby/
│  ├─ App.kt                                 # 应用入口：自动启动监控服务
│  ├─ MainActivity.kt                        # iOS 风格主列表（分层入口）
│  ├─ DetailActivity.kt                      # 设置子页（外观/防烧屏/天气/权限/诊断）
│  ├─ ClockActivity.kt                       # 全屏待机时钟（锁屏上显示，多级降级兜底）
│  ├─ StandbyService.kt                      # 充电/放置检测前台服务（含屏幕点亮重弹）
│  ├─ StandbyStateMonitor.kt                 # 电池 + 重力传感器状态机
│  ├─ StandbyPagerView.kt / SwipePagerView.kt# 自研滑动翻页 + iOS 指示点（无三方依赖）
│  ├─ StandbyOverlay.kt                      # 悬浮窗备用模式
│  ├─ BurnInShifter.kt                       # 防烧屏像素偏移
│  ├─ CrashLog.kt / Diagnostics.kt           # 崩溃日志与诊断信息
│  ├─ Prefs.kt / StandbyColors.kt / RomInfo.kt
│  ├─ clock/ClockPageView.kt / BatteryView.kt# 时钟页
│  ├─ calendar/CalendarPageView.kt …         # 日历任务页
│  ├─ weather/WeatherPageView.kt …           # 天气页
│  ├─ perm/PermissionChecker.kt              # 权限自动检测
│  ├─ perm/SettingsJumper.kt                 # 澎湃OS4 设置页跳转
│  └─ receiver/BootReceiver.kt / UpdateReceiver.kt
├─ docs/安装说明.md
├─ docs/权限开启步骤.md
└─ README.md
```

## 常见问题

- **充电横放没反应**：1) 打开应用后停留几秒（应用会自动启动监控）；2) 检查「诊断信息」页确认「监控服务：运行中」且「实时检测」显示充电=是、放置=可触发；3) 点「测试弹出时钟」验证弹窗链路；4) 检查权限页红色项；小米务必开"自启动"+"后台弹出界面"+"锁屏显示"+"省电策略=无限制"。
- **解锁后再锁屏不弹出**：时钟界面离开前台后会自动复位显示标志，屏幕再次点亮时会重新判断并弹出；若仍未弹出，请确认监控服务未被系统杀掉。
- **天气不显示**：天气页会显示具体失败原因（无网络/超时/定位未授权等）；可在「天气与定位」页点「测试天气」查看结果。
- **时钟没显示在锁屏上**：本应用使用 showWhenLocked 显示，锁屏状态下正常；部分 ROM 需允许"锁屏显示"。
- **界面异常/闪退**：进入「诊断信息」页查看最近异常记录（本应用已加入多级降级，单个页面故障不影响时钟显示）。

## 声明

- 天气数据来自 [Open-Meteo](https://open-meteo.com/)（免费开放 API）。
- 本项目仅供个人学习使用；iOS StandBy 为 Apple Inc. 的设计概念。
- 授权许可：MIT。
