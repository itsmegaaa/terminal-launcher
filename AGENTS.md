# Terminal Launcher — Developer & Agent Guidelines

Dokumen ini adalah pedoman teknis utama bagi agent AI (Gemini, Claude, Antigravity) dan developer yang berkontribusi pada repository **Terminal Launcher**.

---

## 1. Aturan Versi & Rilis (Wajib Ditaati)

> [!IMPORTANT]
> **Aturan Kenaikan Versi:**
> Setiap kali melakukan perbaikan bug (bugfix), refactoring, atau penambahan fitur baru di proyek ini, **WAJIB menaikkan versi** di file `app/build.gradle.kts` sebelum melakukan commit:
> - `versionCode`: Naikkan secara inkremental (`+1`).
> - `versionName`: Naikkan mengikuti aturan SemVer (contoh: `0.1.0-spike` -> `0.1.1-spike` -> `0.1.2-spike`).
> 
> *Tujuan:* Memastikan setiap build APK baru dapat langsung di-install/update menimpa versi sebelumnya di perangkat Android tanpa bentrok package atau cache OS.

---

## 2. Arsitektur & Prinsip Desain Inti

Aplikasi ini adalah **Android Launcher mandiri** berbasis **Kotlin + Jetpack Compose** yang menggantikan homescreen standar Android, dengan dua pilar utama:
1. **App Launcher Cepat:** Membuka aplikasi via teks dengan hierarki cerdas (Prefix -> Inisial -> Fuzzy Levenshtein).
2. **Termux Bridge (One-Shot IPC):** Menjalankan perintah bash Linux di background via `com.termux.RUN_COMMAND` tanpa membuka jendela Termux.

---

## 3. Batasan Teknis & Aturan Kritis IPC Termux

1. **Eksekusi 100% Background:**
   - Wajib menyertakan extra boolean:  
     `intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)`  
     Tanpa extra ini, Termux akan membuka jendela terminal di foreground dan mengganggu UI launcher.
2. **Anti-Injection & Quoting Aman via Array Argumen:**
   - DILARANG menyambung string secara mentah (`timeout 30 bash -c "<cmd>"`).
   - Seluruh perintah pengguna harus dilewatkan sebagai **satu elemen array mandiri** tanpa escaping yang merusak pipe atau variabel:
     - `RUN_COMMAND_PATH` = `"/data/data/com.termux/files/usr/bin/timeout"`
     - `RUN_COMMAND_ARGUMENTS` = `["-k", "2", timeoutSec.toString(), "/data/data/com.termux/files/usr/bin/bash", "-c", rawUserCmd]`
3. **Pencegahan Crash & Exception Safety:**
   - Semua pemanggilan `context.startService(intent)` WAJIB dibungkus blok `try-catch` yang menangani `SecurityException` (jika izin `RUN_COMMAND` belum aktif) dan `Exception` umum.
   - Hasil error wajib di-dispatch ke UI sebagai pesan log ramah, bukan membiarkan aplikasi *force close*.
4. **Android 11+ & 14 Package Visibility:**
   - File `app/src/main/AndroidManifest.xml` WAJIB memuat blok `<queries>`:
     ```xml
     <queries>
         <package android:name="com.termux" />
     </queries>
     ```
5. **PendingIntent Anti-Collision & Memory Safety:**
   - Gunakan counter monotonik `AtomicInteger` untuk `requestCode` PendingIntent (Android mencocokkan cache PendingIntent berbasis `requestCode`).
   - Callback PendingIntent disetel eksplisit menargetkan `TermuxResultReceiver`:
     `PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT`.
   - Entri pada map `requestCode -> ExecutionContext` wajib dibersihkan saat callback diterima atau saat watchdog timeout berakhir, dan `pendingIntent.cancel()` dipanggil.
6. **Resolusi Bundle Dinamis:**
   - Jangan berasumsi satu key string pasti. Ekstraksi dilakukan bertingkat: cek root bundle, fallback key `com.termux.app.extra.TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE`, `"result"`, atau sub-bundle apa pun yang memuat `stdout`/`exitCode`.
   - Deteksi pemotongan output via `stdout_original_length` dan `stderr_original_length` bawaan Termux.

---

## 4. Alur Kerja Git & Otomasi CI/CD

- **Repository GitHub:** `itsmegaaa/terminal-launcher` (Public).
- **Otomasi Build:** Setiap push ke branch `main` secara otomatis memicu GitHub Actions (`.github/workflows/build-apk.yml`).
- **Download APK:** Artifact `app-debug.apk` hasil build dapat langsung diunduh via CLI:
  ```bash
  gh run download <RUN_ID> -n spike-debug-apk -D ~/downloads/
  ```
- **Sync Dokumen:** Perubahan dokumen spesifikasi wajib di-sync ke folder Dokumen internal perangkat (`/sdcard/Documents/`) agar Ega dapat membacanya langsung di HP.

---

## 5. Sumber Kebenaran Dokumen

- **`terminal_launcher_plan.md`:** Spesifikasi pengujian empiris Fase 1 (Spike Bridge), matriks 17 skenario uji, kriteria gerbang GO / NO-GO, dan deliverable `SPIKE_RESULTS.md`.
- **`ROADMAP.md`:** Spesifikasi arsitektur lengkap Fase 2 sampai 5 (App Matcher, Shunting-yard Calc, DataStore, Claude Code theme, glyph prompt Unicode, dan singleTask IME focus).
