<div align="center">

# 🚄 JavaFx-TicketHelper

**基于 Java 17 + JavaFX 的 12306 抢票工具**

[English](README_EN.md)

> 本项目仅用于技术学习与交流，不建议大规模商用或高频滥用。

</div>

---

## 目录

- [项目简介](#-项目简介)
- [技术栈](#️-技术栈)
- [功能模块](#-功能模块)
- [快速开始](#-快速开始)
- [打包 Windows 安装包](#-打包-windows-安装包)
- [项目结构](#-项目结构)
- [开发指南](#-开发指南)
- [数据与隐私](#-数据与隐私)
- [贡献指南](#-贡献指南)
- [安全建议](#️-安全建议)
- [许可证](#-许可证)
- [English](#english)

---

## 📖 项目简介

`JavaFx-TicketHelper` 是面向 12306 出行场景的 JavaFX 桌面抢票工具，集成抢票、订单管理、候补、多渠道通知等功能。功能设计参考了 Bypass 分流抢票软件，旨在提供一个可本地运行、低依赖的抢票解决方案。

| 项目 | 说明 |
|------|------|
| **运行平台** | Windows |
| **开源协议** | MIT License |
| **作者** | jactil |

---

## 🛠️ 技术栈

| 类别 | 技术 |
|:---:|:---|
| 语言 | Java 17 |
| UI | JavaFX 17.0.8 |
| 构建 | Maven |
| JSON | Jackson |
| HTTP | OkHttp |
| 日志 | SLF4J + Logback |
| 目标平台 | Windows 10 / 11 |

---

## ✨ 功能模块

### 一、基础登录 / 账号功能

| 功能点 | 状态 | 实现原理 |
|:---|:---:|:---|
| 注册 12306（弹窗打开网页） | ✅ | JavaFX `WebView` 加载 `https://kyfw.12306.cn/otn/regist/init` 页面 |
| 忘记密码（弹窗打开网页） | ✅ | `WebView` 加载 `https://kyfw.12306.cn/otn/forgetPassword/init` 页面 |
| 12306 账号密码登录 | ✅ | 多步验证：checkLoginVerify 检查验证方式 → getMessageCode 发送短信 → SM4 加密密码 → 携带验证码提交登录 |
| 手机号验证码登录 | ✅ | 模拟网页登录请求，携带手机号 / 验证码 / 设备信息，获取会话 Cookie |
| 免登录打开 12306 官网 | ✅ | `WebView` 加载官网，OkHttp 预请求获取 Cookie/Token 注入 WebView 实现免登录 |
| 记住密码 / 分账户凭据管理 | ✅ | 每个账号独立存储凭据到 `~/.javafx-tickethelper/accounts/<username>/`，启动时自动加载上次登录用户 |
| 短信验证码弹窗 | ✅ | 登录触发短信验证时弹出验证码输入弹窗，支持倒计时与取消操作 |

### 二、核心抢票功能

| 功能点 | 状态 | 实现原理 |
|:---|:---:|:---|
| 抢票页面（车次 / 日期 / 席别筛选） | ✅ | OkHttp 调用余票查询接口 → Jackson 解析 JSON → `TableView` 展示车次 → `ComboBox/CheckBox` 筛选 |
| 成人 / 学生票切换查询 | ✅ | 查询时传递 `purpose_codes` 参数（ADULT=成人票，0X00=学生票），成人/学生复选框互斥 |
| 前端三条件筛选（车次类型 / 席别 / 发车时间） | ✅ | 12306 返回全量数据，前端根据车次首字母、席别余票、出发时间段实时过滤，筛选变化即时刷新表格 |
| 查询中转换乘 | ✅ | 点击后使用 Edge CDP（Chrome DevTools Protocol）注入 OkHttp Cookie 到浏览器，免登录打开 12306 中转换乘页面，携带出发站/目的站/日期参数 |
| 显示全部票价 / 恢复余票切换 | ✅ | 悬浮显示票价提示 Tooltip；点击后从查询响应中直接解析票价（无需额外API调用），表格席别列即时切换显示票价（¥xxx），再次点击恢复余票数量 |
| 同城车站筛选 | ✅ | 基于 12306 `cityCode` 字段，出发/到达站均可勾选同城车站过滤，查询结果仅显示选中车站的车次 |
| 查询摘要提示 | ✅ | 查询后在表格上方显示摘要（共 N 趟车 / 有票 N 趟 / 无票 N 趟），支持点击跳转中转换乘 |
| 候补订单页面 | ✅ | 调用候补查询接口，`TableView` 展示；支持继续支付 / 取消订单 / 退单操作 |
| 订单管理页面（未完成 / 已完成） | ✅ | 调用订单查询接口，分标签展示；支持继续支付 / 取消 / 退票 / 改签操作 |
| 自动提交订单（乘客 / 席别预设） | ✅ | 预存乘客信息到本地配置，查到余票后自动携带乘客 / 席别信息提交下单请求 |
| 乘客 / 席别 / 已选车次选择 | ✅ | `ListView` + `CheckBox` 多选乘客、席别、车次，支持全选联动 |
| 多任务抢票（单 / 多任务 / 多站） | ✅ | `ThreadPoolExecutor` 多线程抢票，任务间共享登录态（Cookie/Token） |
| CDN 测速 / 优选服务器 | ❌ 不做 | 个人版直接固定官方主域名，不做分流 |

### 三、通知与提醒功能

| 功能点 | 状态 | 实现原理 |
|:---|:---:|:---|
| Windows 右下角弹窗 + 音乐提醒 | ✅ | `TrayNotification` 系统托盘通知 + `AudioClip` 播放本地音频 |
| HTTP 推送通知（POST） | ✅ | OkHttp 向用户配置 URL 发送 GET/POST 请求，支持自定义参数 / Headers |
| 邮件通知（SMTP） | ✅ | JavaMail API 实现 SMTP 发送，支持自定义发件人 / 收件人 / SSL 加密 |
| 腾讯 / 微信通知 | ✅ | 调用 Server 酱 / 企业微信机器人 API；QQ 可通过第三方机器人 API |

### 四、设置与辅助功能

| 功能点 | 状态 | 实现原理 |
|:---|:---:|:---|
| 同步服务器时间 | ✅ | OkHttp 请求 12306 接口，从响应头 `Date` 字段获取服务器时间校准本地时钟 |
| 设置代理 | ✅ | OkHttp 配置 HTTP/SOCKS 代理，JavaFX 界面提供代理地址 / 端口输入 |
| 检查更新 | ✅ | 启动时请求更新地址（GitHub Releases / 自建服务器），对比版本号提示更新 |
| 公告 / 赞助作者 | ✅ | 启动时加载本地 / 远程 HTML 公告；弹窗显示赞助二维码 / 链接 |
| 创建桌面快捷方式 | ✅ | Windows 下生成 `.lnk` 快捷方式，指向程序 `.exe` |
| 日志输出 / 查找日志 | ✅ | Logback 记录日志，实时日志输出区展示 INFO 级别日志；多账号独立日志文件；提供日志目录打开按钮 |
| 抢票设置（席别 / 乘客 / 自动候补） | ✅ | `CheckBox/ComboBox/TextField` 收集配置，序列化到本地 JSON，启动时加载 |
| 邮件通知设置 | ✅ | 界面内配置 SMTP 服务器 / 端口 / 发件人 / 收件人 / SSL，支持测试发送 |
| 微信通知设置 | ✅ | 界面内配置 Server酱 SendKey / 企业微信 Webhook，支持测试发送 |
| 自动支付设置 | ⚠️ 有风险 | 界面内配置自动支付模式（提醒/自动）；**建议仅做「提醒支付」，不建议实现自动支付** |

---

## 🚀 快速开始

### 环境要求

- **JDK 17**（需包含 `jpackage`）
- **Maven 3.6+**
- **Windows 10 / 11**

> 本项目使用 Maven 引入带 `classifier=win` 的 JavaFX 依赖。请使用 JDK 17，不要使用 JDK 8 直接运行。

### 运行

```bash
git clone https://github.com/Jactil777/JavaFx-TicketHelper.git
cd JavaFx-TicketHelper
mvn exec:java
```

也可以在 IntelliJ IDEA 中打开项目，通过 Maven 面板运行：

```
Plugins -> exec -> exec:java
```

> **IDE 中直接运行**：请以 `com.jactil.javafx.tickethelper.Launcher` 作为启动类，而非 `App`。

### 构建

```bash
mvn clean package -DskipTests
```

构建产物位于 `target/`。

---

## 📦 打包 Windows 安装包

项目提供了一键打包脚本，支持两种方式：

**方式一：命令行执行**

```powershell
# 先 cd 到项目根目录
cd E:\JavaFx-TicketHelper

# 执行批处理脚本（会自动调用 PowerShell 运行 build-exe.ps1）
.\一键打包.bat
```

> **注意：** 打包前请先关闭正在运行的程序，否则图标文件可能被占用导致清理失败。

**方式二：IDEA 中右键执行**

在 IntelliJ IDEA 的项目文件树中找到 `build-exe.ps1`，右键选择 `Run 'build-exe.ps1'` 即可。

**打包依赖：**

- JDK 17+，需包含 `jpackage`
- Maven 3.6+
- WiX Toolset 3.x（生成 .msi/.exe 安装包所需。未安装则仅输出 JAR）

**安装 WiX Toolset（生成 .exe 必需）：**

1. 前往 [WiX Toolset v3 Releases](https://github.com/wixtoolset/wix3/releases) 下载最新 v3 版本（如 `wix314-binaries.zip` 或 `.msi` 安装包）
2. 安装后确保 `candle.exe` 和 `light.exe` 在系统 PATH 中（默认安装路径：`C:\Program Files (x86)\WiX Toolset v3.14\bin`）
3. 安装完成后**重启终端**，重新运行 `一键打包.bat` 即可生成 `.msi` 安装包

> 如果未安装 WiX Toolset，脚本仍会成功构建，但只会输出 JAR 文件（`dist\tickethelper-1.0.0-SNAPSHOT.jar`），可通过 `java -jar` 直接运行。

安装包默认输出到 `dist/`。由于安装包包含 JRE，文件体积较大是正常现象。

### 发布到 GitHub Releases

由于 GitHub 网页上传限制 25MB，安装包（约 69MB）需通过 `gh` CLI 上传：

**1. 安装 gh CLI**

前往 https://github.com/cli/cli/releases/latest 下载 `gh_*_windows_amd64.msi` 并安装，安装后重启终端。

**2. 生成 GitHub Token**

1. 打开 https://github.com/settings/tokens/new
2. 勾选 **`repo`** 权限（会自动包含所有子权限）
3. 点击 **Generate token**，复制生成的 Token（以 `ghp_` 开头）

**3. 登录并上传**

```powershell
# 使用 Token 登录 gh（替换为你的 Token）
echo "ghp_XXXXXXXXXXXXXXXX" | gh auth login --with-token

# 创建 Release 并上传安装包
gh release create v1.0.0 dist/JavaFx-TicketHelper-1.0.0.msi --title "JavaFx-TicketHelper v1.0.0" --notes "首发版本"
```

> 每次发布新版本时，修改版本号和描述即可，如 `v1.0.1`、`v1.1.0` 等。

---

## 📁 项目结构

```
src/main/java/com/jactil/javafx/tickethelper/
├── App.java                  # JavaFX Application 入口
├── Launcher.java             # 启动代理类（IDE 运行入口）
├── LoginStage.java           # 登录窗口（账号密码 + 记住密码 + 短信验证码）
├── MainStage.java            # 主界面（导航栏 + 标签页 + 抢票/日志/设置）
├── component/                # 自定义 UI 组件
│   └── StationAutoCompleteField.java  # 车站模糊匹配自动补全输入框
├── controller/               # FXML 控制器（占位）
│   ├── LoginController.java
│   └── MainController.java
├── config/                   # 配置管理
│   ├── AppConfig.java        # 全局单例配置（语言、代理、上次登录用户等）
│   └── AccountConfig.java    # 分账户配置（凭据、车站历史、同城筛选）
├── service/                  # 业务服务接口
│   ├── LoginService.java     # 登录服务
│   ├── TicketService.java    # 抢票 / 查询服务
│   ├── NotificationService.java  # 通知服务
│   └── impl/                 # 服务实现
│       ├── LoginServiceImpl.java   # 12306 多步登录（SM4加密 + 短信验证）
│       └── TicketServiceImpl.java  # 余票查询 + 票价本地解析
├── model/                    # 数据模型
│   ├── UserInfo.java         # 用户信息
│   └── TrainInfo.java        # 车次信息
└── util/                     # 工具类
    ├── HttpClientUtil.java   # OkHttp 封装
    ├── Sm4Util.java          # SM4 密码加密（与 12306 前端 JS 一致）
    ├── StationUtil.java      # 车站数据管理（三字码 / cityCode / 同城站）
    ├── IconGenerator.java    # 应用图标生成
    └── TimeUtil.java         # 时间工具（含服务器时间同步）

src/main/resources/
├── css/
│   └── style.css             # 全局样式（Bypass 风格网格 / 筛选区 / 票价区 / 日志区）
├── i18n/
│   ├── messages_zh_CN.properties  # 中文语言包
│   └── messages_en_US.properties  # 英文语言包
├── images/                   # 图标、二维码图片
└── logback.xml               # 日志配置（多账号独立日志文件）
```

---

## 📝 开发指南

1. **业务模块开发**：在 `service/` 目录下实现对应接口（如 `LoginService`、`TicketService`），通过 OkHttp 调用 12306 官方接口
2. **界面开发**：可使用 FXML + Controller 方式，或沿用当前的纯代码方式（`LoginStage` / `MainStage`）
3. **国际化**：在 `i18n/messages_*.properties` 中添加键值对，通过 `ResourceBundle` 读取
4. **日志**：使用 SLF4J + Logback，已在 `logback.xml` 中配置控制台 + 文件输出

---

## 🔒 数据与隐私

- 所有 HTTP 请求直接发往 **12306 官方接口**（`https://kyfw.12306.cn`），不经过任何第三方服务器
- 用户账号密码通过「记住密码」功能分账户加密存储在本地 `~/.javafx-tickethelper/accounts/` 目录
- 日志文件中不记录密码等敏感信息（敏感日志已降级为 DEBUG 级别）

---

## ⚠️ 安全建议

- ❌ **请勿在公共设备上保存账号信息**
- ⚠️ 自动支付功能存在安全风险，请谨慎开启
- ⚠️ 高频请求可能触发 12306 风控机制，建议合理设置请求间隔
- ✅ 建议仅在个人设备上使用本工具

---

## 🤝 贡献指南

欢迎提交 Issue、建议和 Pull Request。

**推荐流程：**

1. Fork 本仓库。
2. 创建特性分支。
3. 保持改动聚焦，避免混入无关格式化。
4. 提交前运行：

   ```bash
   mvn -q -DskipTests package
   ```

5. 在 PR 中说明改动内容、测试方式和可能影响。

---

##  联系方式

- **Author**: jactil
- **Email**: `jactil777@gmail.com`

---

##  许可证

本项目基于 **MIT License** 开源。

```
MIT License

Copyright (c) 2024 jactil

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

<a id="english"></a>

*See [README_EN.md](README_EN.md) for the English version.*
