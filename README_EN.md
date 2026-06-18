<div align="center">

# 🚄 JavaFx-TicketHelper

**A 12306 Ticket Booking Tool Built with Java 17 + JavaFX**

[中文](README.md)

> This project is for technical learning and communication only. Commercial use or high-frequency abuse is not recommended.

</div>

---

## Table of Contents

- [Project Description](#-project-description)
- [Tech Stack](#️-tech-stack)
- [Features](#-features)
- [Quick Start](#-quick-start)
- [Packaging Windows Installer](#-packaging-windows-installer)
- [Project Structure](#-project-structure)
- [Development Guide](#-development-guide)
- [Privacy](#-privacy)
- [Contributing](#-contributing)
- [Security Notes](#️-security-notes)
- [License](#-license)
- [中文](#chinese)

---

## 📖 Project Description

`JavaFx-TicketHelper` is a JavaFX desktop ticket-booking tool for the 12306 railway system. It integrates ticket booking, order management, waitlist, and multi-channel notifications. The design is inspired by Bypass, aiming to provide a locally runnable, low-dependency ticket booking solution.

| Item | Details |
|------|------|
| **Platform** | Windows |
| **License** | MIT License |
| **Author** | jactil |

---

## 🛠️ Tech Stack

| Category | Technology |
|:---:|:---|
| Language | Java 17 |
| UI | JavaFX 17.0.8 |
| Build | Maven |
| JSON | Jackson |
| HTTP | OkHttp |
| Logging | SLF4J + Logback |
| Target Platform | Windows 10 / 11 |

---

## ✨ Features

### 1. Login & Account Management

| Feature | Status | Implementation |
|:---|:---:|:---|
| Register 12306 account (embedded browser) | ✅ | JavaFX `WebView` loads `https://kyfw.12306.cn/otn/regist/init` |
| Forgot password (embedded browser) | ✅ | `WebView` loads `https://kyfw.12306.cn/otn/forgetPassword/init` |
| Account password login | ✅ | Multi-step: checkLoginVerify → getMessageCode (SMS) → SM4 encrypt password → submit login with SMS code |
| Phone SMS verification login | ✅ | Simulate web login with phone / SMS code / device info to obtain session Cookie |
| Open 12306 website without re-login | ✅ | `WebView` loads official site with Cookie/Token pre-injected from OkHttp |
| Remember password / per-account credentials | ✅ | Each account stores credentials independently in `~/.javafx-tickethelper/accounts/<username>/`; auto-loads last logged-in user on startup |
| SMS verification popup | ✅ | When login triggers SMS verification, a code input popup appears with countdown timer and cancel support |

### 2. Core Ticket Booking

| Feature | Status | Implementation |
|:---|:---:|:---|
| Ticket booking page (train / date / seat filter) | ✅ | OkHttp queries API → Jackson parses JSON → `TableView` displays → `ComboBox/CheckBox` filters |
| Adult / Student ticket type toggle | ✅ | Pass `purpose_codes` param (ADULT=adult, 0X00=student); adult/student checkboxes are mutually exclusive |
| Client-side 3-condition filter (train type / seat / departure time) | ✅ | 12306 returns full data; frontend filters by train prefix letter, seat availability, departure time range in real-time |
| Query transfer trains | ✅ | Click to inject OkHttp cookies into Edge browser via CDP (Chrome DevTools Protocol), opens 12306 transfer page without re-login, with from/to station and date params |
| Show all prices / restore ticket count toggle | ✅ | Hover shows price tooltip; click parses prices directly from query response (no extra API call), seat columns instantly switch to price display (¥xxx); click again to restore ticket counts |
| Same-city station filter | ✅ | Based on 12306 `cityCode` field; departure/arrival stations can filter by same-city stations; query results only show trains for selected stations |
| Query summary hint | ✅ | Shows summary above table after query (N trains total / N with tickets / N without); supports click to open transfer page |
| Waitlist order page | ✅ | Query waitlist API, display in `TableView`; support pay / cancel / refund actions |
| Order management (pending / completed) | ✅ | Query order API, tabbed display; support pay / cancel / refund / reschedule actions |
| Auto-submit order (preset passenger / seat) | ✅ | Store passenger info locally, auto-submit with passenger / seat info when ticket found |
| Passenger / seat / train selection | ✅ | `ListView` + `CheckBox` for multi-select passengers, seats, and trains; supports select-all linkage |
| Multi-task booking (single / multi / multi-station) | ✅ | `ThreadPoolExecutor` for concurrent booking, shared login state (Cookie/Token) |
| CDN speed test / server optimization | ❌ N/A | Personal version uses official domain directly, no CDN optimization needed |

### 3. Notifications & Alerts

| Feature | Status | Implementation |
|:---|:---:|:---|
| Windows tray popup + sound alert | ✅ | `TrayNotification` for system tray + `AudioClip` for local audio playback |
| HTTP push notification (POST) | ✅ | OkHttp sends GET/POST to user-configured URL with custom params / headers |
| Email notification (SMTP) | ✅ | JavaMail API for SMTP, supports custom sender / recipient / SSL encryption |
| WeChat / QQ notification | ✅ | Server Chan / WeCom robot API for WeChat; third-party robot API for QQ |

### 4. Settings & Utilities

| Feature | Status | Implementation |
|:---|:---:|:---|
| Server time synchronization | ✅ | OkHttp requests 12306 API, reads `Date` response header to calibrate local clock |
| Proxy settings | ✅ | OkHttp HTTP/SOCKS proxy config, JavaFX UI for proxy address / port input |
| Check for updates | ✅ | On startup, request update URL (GitHub Releases / custom server), compare version |
| Announcements / sponsor author | ✅ | Load local / remote HTML announcements on startup; popup with QR code / donation link |
| Create desktop shortcut | ✅ | Generate `.lnk` shortcut on Windows pointing to the program `.exe` |
| Log output / view logs | ✅ | Logback for logging; real-time log viewer shows INFO level logs; per-account independent log files; log directory open button |
| Ticket settings (seat / passenger / auto-waitlist) | ✅ | `CheckBox/ComboBox/TextField` for config, serialized to local JSON, loaded on startup |
| Email notification settings | ✅ | In-app SMTP server / port / sender / recipient / SSL configuration with test send support |
| WeChat notification settings | ✅ | In-app ServerChan SendKey / WeCom Webhook configuration with test send support |
| Auto payment settings | ⚠️ Risky | In-app auto payment mode config (reminder/auto); **recommend "payment reminder" only, not auto-payment** |

---

## 🚀 Quick Start

### Requirements

- **JDK 17** (must include `jpackage`)
- **Maven 3.6+**
- **Windows 10 / 11**

> This project uses Maven to import JavaFX dependencies with `classifier=win`. Please use JDK 17, do not run directly with JDK 8.

### Run

```bash
git clone https://github.com/Jactil777/JavaFx-TicketHelper.git
cd JavaFx-TicketHelper
mvn exec:java
```

You can also open the project in IntelliJ IDEA and run via the Maven panel:

```
Plugins -> exec -> exec:java
```

> **Running in IDE**: Use `com.jactil.javafx.tickethelper.Launcher` as the main class, not `App`.

### Build

```bash
mvn clean package -DskipTests
```

Build artifacts are located in `target/`.

---

## 📦 Packaging Windows Installer

The project provides a one-click build script with two ways to run:

**Method 1: Command Line**

```powershell
# First, cd to the project root directory
cd E:\JavaFx-TicketHelper

# Run the batch script (automatically calls PowerShell to run build-exe.ps1)
.\一键打包.bat
```

> **Note:** Please close the running application before building, otherwise icon files may be locked and cause cleanup to fail.

**Method 2: Run in IntelliJ IDEA**

In the IDEA project file tree, find `build-exe.ps1`, right-click and select `Run 'build-exe.ps1'`.

**Build dependencies:**

- JDK 17+, must include `jpackage`
- Maven 3.6+
- WiX Toolset 3.x (required for .msi/.exe installer. Without it, only JAR is output)

**Installing WiX Toolset (required for .exe):**

1. Go to [WiX Toolset v3 Releases](https://github.com/wixtoolset/wix3/releases) and download the latest v3 version (e.g. `wix314-binaries.zip` or `.msi` installer)
2. After installation, ensure `candle.exe` and `light.exe` are in your system PATH (default path: `C:\Program Files (x86)\WiX Toolset v3.14\bin`)
3. **Restart your terminal**, then re-run `一键打包.bat` to generate the `.msi` installer

> Without WiX Toolset, the build still succeeds but only outputs a JAR file (`dist\tickethelper-1.0.0-SNAPSHOT.jar`), which can be run directly with `java -jar`.

The installer outputs to `dist/` by default. Since the installer bundles the JRE, a larger file size is expected.

### Publish to GitHub Releases

Since GitHub web upload is limited to 25MB, the installer (~69MB) must be uploaded via `gh` CLI:

**1. Install gh CLI**

Go to https://github.com/cli/cli/releases/latest and download `gh_*_windows_amd64.msi`, install it, then restart your terminal.

**2. Generate GitHub Token**

1. Open https://github.com/settings/tokens/new
2. Check the **`repo`** scope (this includes all sub-permissions)
3. Click **Generate token** and copy the generated token (starts with `ghp_`)

**3. Login and Upload**

```powershell
# Login gh with your token (replace with your actual token)
echo "ghp_XXXXXXXXXXXXXXXX" | gh auth login --with-token

# Create a release and upload the installer
gh release create v1.0.0 dist/JavaFx-TicketHelper-1.0.0.msi --title "JavaFx-TicketHelper v1.0.0" --notes "Initial release"
```

> For each new version, update the version tag and description, e.g. `v1.0.1`, `v1.1.0`, etc.

---

## 📁 Project Structure

```
src/main/java/com/jactil/javafx/tickethelper/
├── App.java                  # JavaFX Application entry
├── Launcher.java             # Launcher proxy class (IDE run entry)
├── LoginStage.java           # Login window (username/password + remember + SMS popup)
├── MainStage.java            # Main window (nav bar + tabs + booking/log/settings)
├── component/                # Custom UI components
│   └── StationAutoCompleteField.java  # Station fuzzy-match autocomplete input
├── controller/               # FXML controllers (placeholder)
│   ├── LoginController.java
│   └── MainController.java
├── config/                   # Configuration management
│   ├── AppConfig.java        # Global singleton config (language, proxy, last user, etc.)
│   └── AccountConfig.java    # Per-account config (credentials, station history, city filter)
├── service/                  # Business service interfaces
│   ├── LoginService.java     # Login service
│   ├── TicketService.java    # Ticket booking / query service
│   ├── NotificationService.java  # Notification service
│   └── impl/                 # Service implementations
│       ├── LoginServiceImpl.java   # 12306 multi-step login (SM4 encrypt + SMS verify)
│       └── TicketServiceImpl.java  # Ticket query + local price parsing
├── model/                    # Data models
│   ├── UserInfo.java         # User information
│   └── TrainInfo.java        # Train information
└── util/                     # Utility classes
    ├── HttpClientUtil.java   # OkHttp wrapper
    ├── Sm4Util.java          # SM4 password encryption (matches 12306 frontend JS)
    ├── StationUtil.java      # Station data management (code / cityCode / same-city)
    ├── IconGenerator.java    # Application icon generator
    └── TimeUtil.java         # Time utility (with server time sync)

src/main/resources/
├── css/
│   └── style.css             # Global styles (Bypass-style grid / filter / price / log area)
├── i18n/
│   ├── messages_zh_CN.properties  # Chinese language pack
│   └── messages_en_US.properties  # English language pack
├── images/                   # Icons, QR code images
└── logback.xml               # Logging configuration (per-account independent log files)
```

---

## 📝 Development Guide

1. **Business module development**: Implement interfaces in the `service/` directory (e.g., `LoginService`, `TicketService`), calling 12306 official APIs via OkHttp
2. **UI development**: Can use FXML + Controller pattern, or continue with the current code-based approach (`LoginStage` / `MainStage`)
3. **Internationalization**: Add key-value pairs in `i18n/messages_*.properties`, read via `ResourceBundle`
4. **Logging**: Uses SLF4J + Logback, configured in `logback.xml` with console + file output

---

## 🔒 Privacy

- All HTTP requests go directly to the **official 12306 API** (`https://kyfw.12306.cn`) — no third-party servers involved
- User credentials are stored per-account with encryption in `~/.javafx-tickethelper/accounts/` via the "Remember Password" feature
- Sensitive information is excluded from log files (sensitive logs downgraded to DEBUG level)

---

## ⚠️ Security Notes

- ❌ **Do not save account credentials on public devices**
- ⚠️ Auto-payment feature carries security risks — use with caution
- ⚠️ High-frequency requests may trigger 12306 risk control — set reasonable intervals
- ✅ Recommended for personal device use only

---

## 🤝 Contributing

Issues, suggestions, and Pull Requests are welcome.

**Recommended workflow:**

1. Fork this repository.
2. Create a feature branch.
3. Keep changes focused, avoid unrelated formatting changes.
4. Before submitting, run:

   ```bash
   mvn -q -DskipTests package
   ```

5. Describe the changes, testing approach, and potential impact in the PR.

---

##  Contact

- **Author**: jactil
- **Email**: `jactil777@gmail.com`

---

## 📄 License

This project is licensed under the **MIT License**.

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

<a id="chinese"></a>

*See [README.md](README.md) for the Chinese version.*
