# RouterOS Dashboard for Android

[![CI](https://github.com/al-yuanshan/routeros-dashboard-android/actions/workflows/ci.yml/badge.svg)](https://github.com/al-yuanshan/routeros-dashboard-android/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

一个把闲置 Android 设备变成 MikroTik RouterOS 常亮状态屏的监控 App。

它面向横屏、无触摸的小尺寸显示设备（主要适配 5.5 英寸 1280×720），所有关键信息一屏展示、自动刷新，无需本地操作；连接参数、外观和亮度等配置都通过局域网内的 Web 页面完成。

## 功能

- **系统概览**：设备名称、RouterOS 版本、运行时间、CPU 温度。
- **资源占用**：CPU、内存、存储使用率。
- **网络吞吐**：PPPoE / WAN 实时上下行速率；填写签约带宽后显示占用率。
- **WAN 信息**：公网 IP 地址，PPPoE 重拨后立即更新。
- **链路质量**：由路由器发起 Ping 探测延迟和丢包，并显示连接跟踪数量。
- **Web 配置**：电脑与手机浏览器均可访问，支持访问码保护。
- **显示设置**：自定义壁纸、卡片透明度、主/次文字颜色，日间与夜间定时亮度。
- **常亮屏友好**：开机自启、数据过期提示、定时微移防烧屏、失败自动退避重连。

## 兼容性

| 项目 | 要求 |
|---|---|
| RouterOS | v6 / v7，需启用明文 API 服务（默认 `8728`）；暂不支持 api-ssl（`8729`） |
| Android | 7.0（API 24）及以上 |
| 屏幕 | 仅横屏；主要适配 1280×720，其他横屏分辨率按 `dp` 自适应 |
| 网络 | 显示设备与路由器、配置用的浏览器处于同一可信局域网 |

## 快速开始

1. **在 RouterOS 上创建只读账号**（见下方 [RouterOS 侧建议](#routeros-侧建议)）。
2. **安装 APK**：从 [Releases](https://github.com/al-yuanshan/routeros-dashboard-android/releases) 下载最新的 `routeros-dashboard-v*.apk` 安装；也可以自行构建：

   ```shell
   ./gradlew :app-routeros:assembleDebug
   adb install -r app-routeros/build/outputs/apk/debug/app-routeros-debug.apk
   ```

   Release 与 Debug 的签名不同，两者不能互相覆盖安装，切换时需先卸载（会清空配置）。
3. **打开 App**，记下监控屏顶部显示的 6 位访问码。
4. **在浏览器访问** `http://<设备 IP>:8081`，输入访问码，填写 RouterOS 地址、端口、用户名和密码后保存。
5. 如需开机自动显示，参考 [开机自启](#开机自启)。

## 构建

需要 JDK 17。项目通过 [mise](https://mise.jdx.dev/) 固定工具版本，安装后可直接运行：

```shell
mise install
mise run build
```

常用任务：

```shell
mise run test       # JVM 单元测试
mise run lint       # Android Lint
mise run check      # 测试、Lint、Debug 构建
mise run release    # Release 构建
mise run clean      # 清理构建产物
```

不使用 mise 时，在 JDK 17 环境下直接调用 Gradle Wrapper 即可，例如 `./gradlew :app-routeros:assembleDebug`。

Release 构建默认启用 R8 和资源压缩。Debug APK 使用 Android Debug 签名，可直接安装测试。

### 发布

推送 `v` 开头的语义化版本 tag 后，`Release` 工作流会构建签名 APK 并发布到 GitHub Releases，带 `-` 的 tag（如 `v1.1.0-beta.1`）会标记为预发布：

```shell
git tag v1.0.0
git push origin v1.0.0
```

`versionName` 取自 tag，`versionCode` 按 `major×10000 + minor×100 + patch` 计算。工作流需要在仓库 Secrets 中配置 `RELEASE_KEYSTORE_BASE64`、`RELEASE_KEYSTORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`。

本地构建签名 Release 时，在项目根目录创建 `keystore.properties`（已被 Git 忽略）：

```properties
storeFile=/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

## 项目结构

| 模块 | 作用 |
|---|---|
| `app-routeros` | RouterOS API 客户端、指标解析、ViewModel、监控界面和应用入口 |
| `core` | 共享主题、基础 Activity、格式化工具和 Web 配置服务 |

应用 ID 为 `com.yuanshan.routeros`，Web 配置服务端口为 `8081`。

## 运行策略

- 主连接串行轮询，每轮只发 2 条命令：系统资源，以及在路由器端过滤好的 PPPoE 接口计数器。
- IP 地址和温度每 30 秒刷新一次；PPPoE 接口变化（例如重拨）时立即刷新。
- Ping 和连接跟踪使用独立连接，每 60 秒探测一次，不阻塞主刷新。
- 刷新间隔默认 1 秒，最小 100 毫秒；连续请求失败时逐步退避，最长等待 60 秒。
- 超过 3 个刷新周期（至少 10 秒）没有新数据时，界面变暗并显示数据已过期多久。
- 界面每 3 分钟整体平移 2dp，降低常亮屏烧屏风险。
- Activity 进入后台后停止轮询并释放网络连接。

## 开机自启

App 收到开机广播后会自动打开监控界面。Android 10 及以上限制后台启动界面，需要授予悬浮窗权限：

```shell
adb shell appops set com.yuanshan.routeros SYSTEM_ALERT_WINDOW allow
```

首次安装后需要手动打开一次 App，之后才能收到开机广播。

## 安全

### Web 配置访问码

- 首次启动时随机生成 6 位访问码，打开 `http://<设备 IP>:8081` 后需先输入访问码。
- 访问码显示在监控屏顶部：App 启动后的前 3 分钟内，以及未连接 RouterOS 时。
- 可在配置页“安全”分组中修改（6-32 位，不含空格）；修改后其他已登录浏览器需要重新验证。
- 连续输错 5 次会临时锁定，锁定时长逐次翻倍，最长 15 分钟。
- 忘记访问码时，重启 App 并在 3 分钟内查看屏幕即可。

### 凭据处理

- 代码中不内置 RouterOS 密码，首次使用需在 Web 配置页填写。
- 配置页和 `/api/config` 不会回显任何密码类字段，留空提交表示保持原值。
- 配置服务和 RouterOS API 均使用明文传输（App 因此开启了 `usesCleartextTraffic`），只应在可信局域网内使用，不要做端口映射到公网。

### RouterOS 侧建议

为监控单独创建只读 API 账号，并限制 API 服务的来源地址：

```routeros
/user group add name=monitor policy=read,api,test,!write,!policy,!sensitive
/user add name=monitor group=monitor password=<强密码>
/ip service set api address=<显示屏 IP>/32
```

`test` 权限用于从路由器发起链路 Ping 探测；不需要延迟指标时可去掉。

## 常见问题

**连接超时或登录失败**
确认 `/ip service` 中 `api` 已启用、端口填写的是明文 API 端口（通常 `8728`，不是 api-ssl 的 `8729`），并检查 `address` 限制是否包含显示设备的 IP。

**延迟和丢包不显示**
检查监控账号是否有 `test` 权限，以及 Web 配置中的“链路探测目标”（默认 `223.5.5.5`）从路由器是否可达。

**带宽占用率不显示**
在 Web 配置的“签约带宽”中填写上下行带宽（Mbps），填 `0` 表示不显示占用率。

**界面变暗并提示数据过期**
App 已暂时收不到路由器数据，会自动退避重连；网络恢复后界面自动恢复。

**开机后没有自动打开**
确认已手动打开过一次 App，并已执行上方的 `SYSTEM_ALERT_WINDOW` 授权命令。

## 参与贡献

欢迎提交 Issue 和 Pull Request。提交前请运行 `mise run check`（或 `./gradlew :app-routeros:testDebugUnitTest lint :app-routeros:assembleDebug`），提交信息建议使用 [Conventional Commits](https://www.conventionalcommits.org/) 格式，例如 `feat(routeros): add latency history`。涉及界面改动时请附上前后截图。

报告问题时请注明 RouterOS 版本、路由器型号、Android 版本和显示设备分辨率。

## 许可证

本项目基于 [MIT License](LICENSE) 开源。

### 第三方资源

- [Oxanium](https://github.com/sevmeyer/oxanium) 字体（用于数字显示）基于 SIL Open Font License 1.1 授权，许可证见 [`core/src/main/assets/licenses/OFL-Oxanium.txt`](core/src/main/assets/licenses/OFL-Oxanium.txt)。

## 免责声明

本项目为个人开源项目，与 MikroTik 无任何关联，也未获其认可。MikroTik 和 RouterOS 是 MikroTik 的商标。
