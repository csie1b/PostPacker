# PostPacker - Instagram & Facebook 貼文多媒體一鍵打包 Android App

**PostPacker** 是一款專為 Android 開發的社群貼文保存與歸檔工具，支援 **Instagram** 與 **Facebook** 貼文的全文內容、高解析度輪播圖片、Reels / 影片以及元數據一鍵打包下載，並可直接匯出成標準結構的 `.zip` 壓縮包或直接存入手機系統相簿。

---

## ✨ 核心特色與功能

1. **📲 系統分享選單無縫串接 (Android Share Target)**
   - 在 Instagram 或 Facebook 官方 App 中瀏覽任何貼文時，只需點擊 **「分享至...」** 並選擇 **「PostPacker」**，App 即會自動喚醒並載入網址進行分析，無需手動複製貼上。

2. **📦 結構化 ZIP 檔案打包**
   - 點擊「打包成 ZIP」，App 將自動建立包含以下結構的壓縮包：
     ```text
     PostPacker_instagram_username_20261103_120000.zip
     ├── media/
     │   ├── 01_image.jpg (最高解析度原圖)
     │   ├── 02_image.jpg
     │   └── 03_video.mp4 (高位元率影片)
     ├── post_content.txt (純文字版本，方便文案複製與搜尋)
     ├── README.md (格式化 Markdown 文檔，含圖片排版與原始連結)
     └── metadata.json (結構化 JSON 數據，供自動化備份或二次開發使用)
     ```

3. **🖼️ 直存手機相簿 (MediaStore API)**
   - 支援將貼文中的所有圖片與影片直接保存至 Android 系統相簿（`Pictures/PostPacker` 或 `Movies/PostPacker`），在 Google 相簿或手機內建相簿中即刻可見。

4. **🌐 內嵌瀏覽器輔助擷取 (WebView Session Bridge)**
   - 針對需要登入權限、年齡限制或私密帳號的貼文，提供內嵌瀏覽器功能。
   - 使用者可在 App 內登入自己的帳號，透過 JavaScript Bridge 智慧辨識 DOM 結構中的高畫質圖片與影音串流進行擷取。

5. **🎨 Modern Jetpack Compose & Material 3**
   - 採用現代 Android 官方推薦架構（Kotlin 2.0 + Jetpack Compose + Coroutines + MVVM）。
   - 支援輪播圖無縫滑動預覽（Coil AsyncImage）、動態標籤顯示、內文展開/收合。

6. **📁 本機歷史備份庫**
   - 提供「歷史檔案」頁面，隨時查看、重新分享已打包的 ZIP 壓縮檔，或快速釋放儲存空間。

---

## 🛠️ 技術架構

- **UI 框架**: Jetpack Compose (Material 3)
- **網路通訊與解析**:
  - `OkHttp 4`: 高效串流下載與連線池管理
  - `Jsoup`: HTML DOM 解析與 OpenGraph 標籤擷取
- **圖片加載**: `Coil 2.6.0` (Compose 專用非同步圖片加載庫)
- **JSON 序列化**: `Google Gson`
- **架構模式**: MVVM (Model-View-ViewModel) + Kotlin Coroutines & StateFlow
- **系統整合**:
  - `Android Share Sheet` (`Intent.ACTION_SEND`)
  - `FileProvider` (安全跨應用分享 ZIP 壓縮包)
  - `MediaStore API` (Android 10+ Scoped Storage 規範相容)

---

## 📂 專案目錄結構

```text
PostPacker/
├── app/
│   ├── build.gradle.kts           # 模組構建配置與依賴項
│   ├── proguard-rules.pro         # Proguard 混淆與反射保留規則
│   └── src/main/
│       ├── AndroidManifest.xml    # 權限宣告、Activity 與 FileProvider 配置
│       ├── java/com/postpacker/app/
│       │   ├── MainActivity.kt    # 主入口 Activity 與 Intent 攔截
│       │   ├── model/             # 資料模型
│       │   │   ├── Platform.kt    # 平台列舉 (Instagram / Facebook / Web)
│       │   │   ├── PostMedia.kt   # 媒體定義 (圖片 / 影片 / 縮圖)
│       │   │   ├── PostData.kt    # 貼文結構化資訊
│       │   │   └── PackagedArchive.kt # 歷史打包紀錄
│       │   ├── extractor/         # 網頁與貼文數據解析引擎
│       │   │   ├── PostExtractorFacade.kt # 總分發器
│       │   │   ├── InstagramExtractor.kt  # IG 解析 (OpenGraph / ld+json / Script)
│       │   │   └── FacebookExtractor.kt   # FB 解析 (影片 / 照片 / 文案)
│       │   ├── packager/          # 打包與儲存邏輯
│       │   │   ├── ZipPackager.kt         # 媒體下載 + ZIP 壓縮產生器
│       │   │   └── MediaGallerySaver.kt   # MediaStore 相簿儲存工具
│       │   ├── viewmodel/
│       │   │   └── MainViewModel.kt       # 狀態管理與業務邏輯
│       │   └── ui/
│       │       ├── theme/                 # Material 3 主題設定
│       │       ├── components/            # 可重複使用的 Compose 元件
│       │       │   ├── MediaCarousel.kt   # 輪播媒體檢視器
│       │       │   ├── PostPreviewCard.kt # 貼文預覽卡片與操作按鈕
│       │       │   └── InAppBrowserDialog.kt # 內嵌瀏覽器輔助視窗
│       │       └── screens/
│       │           ├── HomeScreen.kt      # 主畫面 (輸入 / 解析 / 打包)
│       │           └── HistoryScreen.kt   # 打包歷史記錄庫
│       └── res/
│           ├── drawable/                  # 向量圖示與 Logo
│           ├── values/                    # 色彩、字串、主題
│           └── xml/file_paths.xml         # FileProvider 分享路徑設定
├── gradle/
│   ├── libs.versions.toml         # Gradle Version Catalog 依賴管理
│   └── wrapper/gradle-wrapper.properties
├── build.gradle.kts               # 頂層 Gradle 配置
├── settings.gradle.kts            # 模組包含與倉庫源設定
└── gradlew.bat / gradlew          # Gradle Wrapper 執行檔
```

---

## 🚀 如何編譯與執行

### 方法一：使用 GitHub Actions 自動編譯 (最推薦，免本機環境)
本專案已配置完整 GitHub CI/CD 工作流 (`.github/workflows/build-apk.yml`)：
1. 將專案推送到 GitHub。
2. GitHub 會自動使用雲端虛擬機編譯出 APK。
3. 到專案倉庫的 **Actions** 分頁點擊最新的工作流程，在 **Artifacts (產物)** 處即可直接下載 `PostPacker-debug.apk` 安裝到手機！

### 方法二：使用 Android Studio
1. 開啟 **Android Studio** (建議 Hedgehog / Iguana / Jellyfish 或更新版本)。
2. 點擊 **File -> Open...**，選取此專案資料夾：
   `C:\Users\IEC980343\.gemini\antigravity\scratch\PostPacker`
3. 等待 Gradle 依賴項自動下載與同步完成。
4. 連接實體 Android 手機（開啟 USB 偵錯）或啟動 Android 虛擬機 (AVD)。
5. 點擊綠色 **Run** 按鈕（Shift + F10）即可安裝並執行。

### 方法二：命令列編譯 APK (Gradle CLI)
在專案目錄下執行：
```powershell
# Windows
.\gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```
編譯完成後，生成的 APK 會位於：
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📖 使用情境與操作說明

1. **情境 A：日常隨手收藏備份**
   - 在 IG / FB App 中滑到重要圖文、菜單、食譜或旅遊資訊。
   - 點擊貼文下方的 **「分享 (飛機圖示)」** -> **「分享至...」** -> 點擊 **「PostPacker」**。
   - 點擊 **「📦 打包成 ZIP」**，完成後點擊 **「📤 分享 ZIP」**，直接上傳到 Google 雲端硬碟或 LINE 備份。

2. **情境 B：設計師 / 內容創作者靈感素材保存**
   - 想要保留多張高畫質輪播圖。
   - 輸入連結並解析後，點擊 **「🖼️ 存入相簿」**，所有原圖自動存入手機相簿，方便在修圖軟體中調用。

3. **情境 C：受限貼文登入解析**
   - 若遇到 Instagram 要求登入才能瀏覽的貼文，在主畫面點擊 **「網頁瀏覽器輔助擷取」**。
   - 在內嵌視窗中登入帳號後瀏覽到該貼文，點擊右上角 **「擷取貼文」** 即可自動把內容帶回 App 打包！
