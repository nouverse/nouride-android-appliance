# RFC: Nouride Android Native Wrapper (Nouride Hub for Android)

- **RFC Number:** 001
- **Author:** Nouva (lead coordinator) & Gading (founder)
- **Implementer:** Nix (software engineer)
- **Status:** Draft / Proposed
- **Target Repository:** `nouverse/nouride-android` (new repository)
- **Created Date:** 2026-11-06

---

## 1. Problem Statement & Motivation

Saat ini, menjalankan Nouride di HP Android bekas (sebagai server mandiri / LLM Router) membutuhkan intervensi manual yang rumit bagi pengguna awam:
1. Memasang Termux secara manual dari F-Droid / GitHub Releases.
2. Memasang `proot-distro` dan image Debian via command line.
3. Menjalankan skrip instalasi bash, mengonfigurasi user non-root `nouride`, dan membuat skrip `nohup` manual.
4. Mengonfigurasi wakelock dan pengecualian penghemat baterai secara manual via terminal dan menu pengaturan OS yang berbeda-beda di tiap vendor HP.

Hambatan operasional ini membuat HP Android bekas sulit dijadikan perangkat *turnkey appliance* (seperti visi Nouride Box atau personal AI hub untuk UMKM).

### Goals
- Mengemas seluruh dependensi eksekusi (PRoot, minimal rootfs, dan binary `nouride-router`) ke dalam satu aplikasi native Android (`.apk`).
- Pengguna cukup membuka aplikasi dan menekan tombol **"Start Server"** (Zero-CLI setup).
- Aplikasi menangani manajemen background lifecycle (Foreground Service, Wakelock, pengecualian baterai) secara otomatis melalui API Android.
- Menyediakan UI ringkas untuk memantau status server, URL dashboard, IP lokal, dan gateway port.

### Non-Goals (Out of Scope untuk MVP)
- Mengompilasi binary Nouride secara penuh ke target Android NDK/Bionic (terlalu banyak dependensi runtime Node/Bun yang rumit untuk cross-compile di tahap awal).
- Menyediakan antarmuka terminal/shell interaktif di dalam aplikasi (menghindari kompleksitas UI dan potensi pelanggaran kebijakan Google Play W^X).
- Mendukung arsitektur 32-bit (ARMv7). Aplikasi hanya menargetkan arsitektur `arm64-v8a` (64-bit).

---

## 2. Technical Architecture

Aplikasi dibangun sebagai aplikasi native Android (Kotlin / Jetpack Compose minimalis) yang bertindak sebagai *supervisor process*, *host environment*, sekaligus *client shell* (via Embedded WebView).

```
+-------------------------------------------------------------------+
|                     Android Application (APK)                     |
|                                                                   |
|  +--------------------+         +-------------------------------+ |
|  |     UI Layer       |         |       Background Layer        | |
|  | (Jetpack Compose / | <-----> |   (NourideForegroundService)  | |
|  | Embedded WebView)  |         | Persistent Notification + WL  | |
|  | http://127.0.0.1   |         |                               | |
|  +--------------------+         +---------------+---------------+ |
|            |                                    |                 |
|            | HTTP/WS                            |                 |
|            v                                    v                 |
|  +--------------------------------------------------------------+ |
|  |                  Native Sandbox (context.filesDir)           | |
|  |                                                              | |
|  |  +----------------+     chroot/ptrace    +----------------+  | |
|  |  |  proot binary  | -------------------> | Minimal Rootfs |  | |
|  |  |  (arm64-v8a)   |                      | (Debian/Alpine)|  | |
|  |  +----------------+                      +-------+--------+  | |
|  |                                                  |           | |
|  |                                        exec /srv/nouride     | |
|  |                                                  v           | |
|  |                                    +-----------------------+ | |
|  |                                    | nouride-router binary | | |
|  |                                    | (port 18254 & 18256)  | | |
|  |                                    +-----------------------+ | |
|  +--------------------------------------------------------------+ |
+-------------------------------------------------------------------+
```

### 2.1. Komponen Utama

1. **Asset Extraction Engine (`AssetExtractor.kt`)**
   - File assets dibundel di dalam APK:
     - `proot` executable (`jniLibs/arm64-v8a/libproot.so` atau di `assets/bin/proot`).
     - `rootfs.tar.gz` (image minimal Debian/Alpine arm64 berukuran ~35–45 MB).
     - `nouride-router-linux-arm64` binary.
   - Pada first-launch, aplikasi mengekstrak rootfs dan binary ke direktori internal aplikasi (`/data/data/<package>/files/rootfs`).
   - Penanda versi file (`.version` atau hash) disimpan untuk menghindari ekstraksi ulang saat aplikasi dibuka kembali.

2. **Foreground Service Supervisor (`NourideServerService.kt`)**
   - Berjalan sebagai Android Foreground Service dengan notifikasi persisten bertipe `FOREGROUND_SERVICE_TYPE_DATA_SYNC` (atau `SPECIAL_USE` pada Android 14+).
   - Mengambil `PowerManager.WakeLock` (`PARTIAL_WAKE_LOCK`) selama service aktif untuk mencegah deep sleep CPU saat layar mati.
   - Menjalankan proses via `ProcessBuilder`:
     ```bash
     proot \
       -r /data/data/<package>/files/rootfs \
       -b /dev \
       -b /proc \
       -b /sys \
       -w /srv/nouride \
       /srv/nouride/nouride start
     ```
   - Mengawasi lifecycle proses (restart jika crash tak terduga, dan graceful stop saat tombol Stop ditekan melalui pengiriman sinyal `SIGTERM`).

3. **Lifecycle & Power Optimization (`PowerHelper.kt`)**
   - Memeriksa status `PowerManager.isIgnoringBatteryOptimizations()`.
   - Jika belum aktif, memunculkan intent dialog sistem `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` secara langsung tanpa membingungkan pengguna mencari menu vendor.
   - Menyediakan panduan satu-klik untuk mematikan *Phantom Process Killer* (via opsi pengembang Android 14 atau perintah ADB ringkas).

4. **UI Layer & Embedded WebView (`MainActivity.kt`)**
   - Menggunakan pendekatan hybrid: Native Shell + **Embedded Android WebView**.
   - **Boot & Loading State (Native Jetpack Compose):**
     - Saat aplikasi dibuka pertama kali atau daemon sedang starting: menampilkan layar splash elegan (Status ekstraksi assets, progress bar, dan status "Starting daemon...").
     - Melakukan polling background ke `http://127.0.0.1:18254/health`.
   - **Dashboard View (WebView):**
     - Begitu endpoint `/health` mengembalikan HTTP 200, layar otomatis berpindah/cross-fade ke WebView yang langsung me-load `http://127.0.0.1:18254/dashboard/`.
     - **Konfigurasi WebView:**
       - `javaScriptEnabled = true`, `domStorageEnabled = true`, `databaseEnabled = true`.
       - Dukungan WebSocket (`ws://` / `wss://`) penuh untuk log streaming dan live chat agent.
       - Mixed content diizinkan untuk komunikasi loopback (`127.0.0.1`).
     - **Native Quick Sheet (Bottom Sheet Overlay / Floating Action Button):**
       - Pengguna tetap bisa menggeser sheet kecil dari bawah untuk melihat status teknis:
         - IP Lokal Wi-Fi (misal `http://192.168.1.50:18254` agar bisa dibuka dari PC lain).
         - Router Gateway Port (`:18256`).
         - Tombol Restart Daemon & Stop Server.
         - Status CPU / RAM usage HP.
   - **Offline / Crash Handling:**
     - Jika daemon terhenti atau crash, WebView menampilkan layar fallback native yang jelas dengan opsi "Restart Server" daripada error page bawaan Chromium `ERR_CONNECTION_REFUSED`.

---

## 3. Evaluasi Kemampuan Nix (Apakah Nix Bisa Mengerjakan Ini?)

###  Kekuatan & Kesiapan Nix:
1. **Scaffolding Gradle & Android Tooling:**
   - Nix dapat membuat struktur repository Gradle standar (`app/src/main/...`, `build.gradle.kts`, `settings.gradle.kts`, AndroidManifest.xml).
   - Mampu menulis kode Kotlin modular (Service, ProcessBuilder runner, Asset decompression, Activity/Compose UI).
2. **Pengemasan Rootfs & PRoot:**
   - Nix memiliki akses shell untuk membuat tarball rootfs Debian/Alpine minimalis, mengekstrak binary `proot` statis `aarch64`, dan memvalidasi struktur filesystem.
3. **Automasi Build & CI:**
   - Nix dapat menyiapkan GitHub Actions workflow (`.github/workflows/build-apk.yml`) menggunakan runner Ubuntu standar dengan Android SDK/Gradle untuk mengompilasi APK debug/release secara otomatis.

### ⚠️ Batasan & Hal yang Butuh Delegasi/Gading:
1. **Host Lingkungan Daemon Saat Ini:**
   - Host LXC tempat daemon berjalan tidak memiliki Android SDK / ADB / GUI terpasang langsung.
   - Namun, Nix bisa mengompilasi menggunakan wrapper script atau Docker image Android builder (`cimg/android` / `gradle:jdk17`), atau memanfaatkan remote build di Mac Gading via SSH (`10.18.1.37`).
2. **Pengujian Fisik ke HP (Redmi 12):**
   - Nix tidak bisa langsung mencolok kabel fisik atau menyentuh layar HP. Pengujian instalasi APK (`adb install`) dan interaksi UI dilakukan lewat ADB Wireless ke IP `10.18.1.98` atau langsung oleh Gading.

**Kesimpulan:** Nix **100% mampu** mengerjakan scaffolding repo, arsitektur kode Kotlin, asset builder script, dan GitHub Actions CI untuk build APK.

---

## 4. Rencana Implementasi & Tahapan (Implementation Phases)

### Phase 1: Tooling Scaffolding & Rootfs Builder (Hari 1 / ~3 jam)
1. Inisialisasi repository baru `nouverse/nouride-android`.
2. Skrip otomatisasi persiapan assets (`scripts/prepare-assets.sh`):
   - Unduh binary `proot` statis arm64 teruji.
   - Buat minimal rootfs Debian arm64 (~35MB) yang sudah memuat binary `nouride-router`.
   - Kompres ke dalam `app/src/main/assets/rootfs.tar.gz`.
3. Setup project Android (Gradle 8.x, Java 17, Target SDK 34, Min SDK 24).

### Phase 2: Core Service & Process Runner (Hari 1 / ~3 jam)
1. Implementasi `AssetExtractor.kt`: utilitas un-tar streaming di background thread dengan progress bar.
2. Implementasi `NourideServerService.kt`:
   - Setup Foreground Notification (`Nouride is running`).
   - Eksekusi PRoot via `ProcessBuilder` dengan redirect output log ke file internal app.
   - Pengambilan `WakeLock`.
3. Endpoint Health Poller: Coroutine background yang melakukan poll ke `http://localhost:18254/health`.

### Phase 3: Minimal Dashboard UI & Power Helper (Hari 2 / ~2 jam)
1. Tampilan antarmuka Jetpack Compose ringkas:
   - Status indikator & uptime.
   - Deteksi IP lokal Wi-Fi aktif (`NetworkCapabilities`).
   - Tombol Start/Stop, tombol "Buka Dashboard" via Chrome Custom Tabs.
2. Dialog izin penghemat baterai (`PowerHelper.requestBatteryOptimizationExemption`).

### Phase 4: Validasi & Pengujian di Redmi 12 (Hari 2 / ~2 jam)
1. Build APK debug via Gradle / GitHub Actions.
2. Deploy APK ke Redmi 12 via Wireless ADB (`adb install -r app-debug.apk`).
3. Verifikasi skenario:
   - Start server dari UI -> Buka dashboard dari Mac/laptop -> Lolos.
   - Matikan layar HP selama 30 menit -> Request API router tetap responsif -> Lolos.
   - Matikan dan nyalakan ulang server via toggle UI -> Lolos.

---

## 5. Premium Extension: Nouride Hands (Accessibility Bridge)

Sebagai ekstensi komersial (Pro / Business Tier), aplikasi menyediakan kapabilitas otomasi sistem penuh tanpa memerlukan root atau koneksi ADB kabel, menggunakan **Android Accessibility Service (AAS)**.

### 5.1. Arsitektur Komponen
```
[Nouride Daemon (Agent Skill)]
          |
          | HTTP (Localhost only: 127.0.0.1:18259)
          v
[Embedded NanoHTTPD / Ktor IPC Bridge di Android Native]
          |
          v
[NourideAccessibilityService]
    ├── getRootInActiveWindow() -> UI Hierarchy (Text, IDs, Bounds) dalam format JSON
    ├── dispatchGesture()       -> Tap, Long Press, Swipe, Drag
    └── performAction()         -> Set Text, Focus, Scroll, Global Actions (Back, Home)
```

### 5.2. Keunggulan vs Vision-based Computer Use
1. **Zero Image Token Cost:** Mengirimkan representasi semantik UI murni (teks tombol, input field) ke LLM, menghemat ribuan token per aksi dibanding model vision.
2. **Akurasi Deterministik:** Tidak terpengaruh kesalahan estimasi koordinat piksel atau perubahan resolusi layar.

### 5.3. Model Monetisasi & Lisensi
- **Community Edition:** Fitur Core Server, Router Gateway, dan Embedded WebView Dashboard tersedia gratis (Free).
- **Pro / Commercial Edition (Nouride Hands):** Fitur Accessibility Bridge dikunci menggunakan verifikasi lisensi (token aktivasi Nouverse / NOU credits) yang divalidasi ke API master Nouverse.

---

## 6. Repository & Development Strategy

Disepakati menggunakan **Repository Terpisah: `nouverse/nouride-android`**, dengan pertimbangan:
1. **Isolasi Toolchain:** Memisahkan ekosistem Gradle, Java, dan Android SDK dari monorepo utama (Rust / Bun / Astro).
2. **Decoupled Release Cycle:** Wrapper Android bertindak sebagai host shell independen yang mengunduh atau memuat binary `nouride-router` rilis terbaru tanpa perlu merilis ulang APK untuk setiap perubahan kecil pada core daemon.
3. **Proteksi IP:** Memisahkan modul komersial (Accessibility bridge & lisensi) dari core daemon yang direncanakan untuk open-source.

---

## 7. Rekomendasi Langkah Selanjutnya

1. Buat repository baru di organisasi `@nouverse`: `nouverse/nouride-android`.
2. Tugaskan **Nix** untuk mengeksekusi **Phase 1 & Phase 2** (mempersiapkan rootfs bundle, project Gradle, dan native Foreground Service).
3. Evaluasi hasil build APK pertama langsung di HP Redmi 12.
