# Terminal Launcher — Roadmap Arsitektur & UI (Fase 2 – 5)

> Dokumen ini adalah panduan implementasi komprehensif untuk Fase 2 sampai Fase 5 setelah **Fase 1 (Spike Bridge)** dinyatakan **GO**.

---

## 1. Fase 2: Vertical Slice Launcher Mandiri

### 1.1 Android Manifest & Package Lifecycle
- **Deklarasi Launcher di `AndroidManifest.xml`:**
  ```xml
  <intent-filter>
      <action android:name="android.intent.action.MAIN" />
      <category android:name="android.intent.category.HOME" />
      <category android:name="android.intent.category.DEFAULT" />
  </intent-filter>
  ```
- **Visibility Package (Android 11+):**
  ```xml
  <queries>
      <intent>
          <action android:name="android.intent.action.MAIN" />
          <category android:name="android.intent.category.LAUNCHER" />
      </intent>
  </queries>
  ```
- **Sinkronisasi Package Real-time:**  
  Menggunakan `LauncherApps.registerCallback(callback)` untuk mendeteksi instalasi, pembaruan, dan uninstal aplikasi secara instan tanpa perlu memuat ulang launcher.

### 1.2 Hierarki App Matcher & State Pemilihan Ambigu
1. **Tingkat 1 — Exact & Prefix Match:**
   - Contoh: `wa` -> `WhatsApp`, `sett` -> `Settings`.
2. **Tingkat 2 — Inisial & Subsequence Match:**
   - Contoh: `yt` -> `YouTube`, `ml` -> `Mobile Legends`.
3. **Tingkat 3 — Fuzzy Levenshtein Similarity:**
   - Dihitung menggunakan formula rasio:
     $$\text{ratio} = 1.0 - \left(\frac{\text{levenshteinDistance}}{\max(\text{len1}, \text{len2})}\right)$$
     Batas minimum (threshold): `ratio >= 0.75`.
4. **Aturan Evaluasi Ambigu & State Machine (`AwaitingSelection`):**
   - Ambiguitas terjadi jika selisih skor kandidat teratas dan kandidat kedua **kurang dari 0.10** ($\Delta \text{score} < 0.10$).
   - **Most Recently Used (MRU) Priority:** Jika input ambigu pernah dipilih sebelumnya (contoh: user pernah memilih `WhatsApp` saat mengetik `wa`), buka langsung app riwayat tersebut.
   - **Force Ambiguous List via Suffix `?`:** Menambahkan tanda tanya di akhir (contoh: `wa?`) memaksa sistem menampilkan daftar pilihan dan mengabaikan MRU.
   - **Manajemen & Pembersihan MRU:**
     - Perintah `reset mru` (atau `unalias mru <token>`) mereset preferensi riwayat.
     - Jika sebuah aplikasi di-uninstall, seluruh entri MRU yang mengarah ke package tersebut otomatis dibersihkan via `LauncherApps.Callback`.
   - **Siklus Hidup & Expiration State `AwaitingSelection`:**
     - State aktif saat daftar pilihan angka muncul: `[1] WhatsApp  [2] WhatsApp Business`.
     - Input angka valid (`1` atau `2`) membuka aplikasi dan mengembalikan state ke `Normal`.
     - State otomatis **kedaluwarsa (kembali ke `Normal`)** jika:
       - User menekan tombol Back atau mengetik perintah baru non-angka.
       - Homescreen mengalami pause / berpindah aplikasi.
       - Timeout pasif 15 detik tanpa input.

---

## 2. Fase 3: Dispatcher Engine & Command Bawaan

### 2.1 Alur Router & Aturan Alias Dua Arah
1. **Aturan Sintaks & Parsing Alias:**
   - Format: `alias <name>=<target>`
   - **Validasi Nama Alias:** Wajib diawali huruf dan berupa karakter alfanumerik/underscore/strip: regex `^[a-z][a-z0-9_-]*$`. Nama alias yang berupa angka murni **DITOLAK** agar tidak bentrok dengan input seleksi angka di `AwaitingSelection`.
   - **Parsing Nilai:** String dipotong pada tanda `=` pertama. Kutip pembungkus luar (`"` atau `'`) otomatis dikupas.
2. **Ekspansi Alias Token Pertama & Re-Tokenisasi:**
   - Ambil token pertama dari input pengguna.
   - Cek database alias. Jika ada, ganti token pertama lalu lakukan **re-tokenisasi**.
3. **Pencegahan Nested Alias Dua Arah:**
   - **Arah 1:** Tolak pembuatan `alias a=b` jika token pertama `b` sudah terdaftar sebagai alias.
   - **Arah 2:** Tolak pembuatan `alias b=wa` jika nama `b` sudah terdaftar sebagai target dalam alias yang ada sebelumnya (`a=b`).
   - Penolakan disertai error: `[Error: Nested alias tidak didukung]`.
4. **Pengecekan Reserved Words:**
   - Token pertama hasil ekspansi dicocokkan dengan daftar reserved words.
5. **Shell Prefix Check (`$`):**
   - Mendukung format `$cmd` maupun `$ cmd` (spasi opsional).

### 2.2 Spesifikasi Command Bawaan (Internal Commands)
- `apps` / `list`: Menampilkan seluruh daftar nama aplikasi yang terinstall dalam format grid/kolom ringkas.
- `open <app>`: Escape hatch untuk membuka aplikasi jika namanya bentrok dengan perintah internal (contoh: `open calc`).
- `battery` / `bat`: Menampilkan informasi status perangkat:
  - Persentase baterai (contoh: `84%`)
  - Status charging (contoh: `Charging (Fast) / Discharging`)
  - Suhu perangkat (contoh: `36.5°C`)
- `calc <expression>`:
  - Evaluator ekspresi matematika mandiri menggunakan algoritma **Shunting-yard** (Dijkstra) untuk konversi Infix ke Reverse Polish Notation (RPN).
  - Mendukung operator: `+`, `-`, `*`, `/`, `%`, `^`, tanda kurung `()`, dan bilangan desimal.
  - Penanganan pembagian dengan nol mengembalikan output aman: `[Error: Division by zero]`.
- `alias <key>=<val>`: Menyimpan alias kustom baru.
- `unalias <key>`: Menghapus alias kustom.
- `clear` / `cls`: Membersihkan layar riwayat, namun banner ASCII "ega" dan pemisah diamond tetap dipertahankan.
- `help`: Menampilkan ringkasan sintaks dan daftar perintah yang didukung.

### 2.3 Persistensi DataStore
- **Command History:** Menyimpan riwayat input teks pengguna (maksimal 500 baris), dapat ditelusuri via tombol panah keyboard atau swipe.
- **Alias Storage:** Menyimpan daftar alias kustom secara persisten menggunakan Jetpack DataStore (Preferences / Proto).

### 2.4 Ergonomi Homescreen
- **Tombol Back:** Menghapus teks pada prompt input yang sedang aktif. Jika input sudah kosong, tombol Back tidak menutup launcher.
- **Mode Aman Pengujian:** Selama tahap pengembangan, saat Android menampilkan pilihan *"Select a Home app"*, pilih opsi **"Just once"** (Hanya sekali), bukan **"Always"**, untuk mencegah homescreen terkunci jika terjadi crash.

---

## 3. Fase 4: Integrasi Termux Bridge & Watchdog Engine

### 3.1 IPC Production Settings & Override Timeout
- `RUN_COMMAND_PATH`: `/data/data/com.termux/files/usr/bin/timeout`
- Default timeout: **30 detik** (`-k 2`).
- **Override Timeout Per-Command:**  
  User dapat menentukan batas waktu kustom untuk perintah panjang (seperti `pkg install` atau `git clone`) via sintaks `$t<detik> <cmd>` (contoh: `$t120 pkg update`).
  - **Urutan Parsing:** Parser memeriksa regex `^\$t(\d+)\s` terlebih dahulu sebelum memproses prefix umum `^\$`.
  - **Validasi Rentang:** Batas waktu valid adalah `1` hingga `600` detik (maksimal 10 menit).
  - **Anti-Bentrok:** Perintah seperti `$tar -xf file.tar` diakui sebagai shell command `tar`, bukan override timeout. Input seperti `$t0` atau `$t` tanpa angka ditolak dengan validasi error.
- `RUN_COMMAND_ARGUMENTS`: `["-k", "2", "<timeout_seconds>", "/data/data/com.termux/files/usr/bin/bash", "-c", cmd]`
- Watchdog UI: `Termux timeout + 5 detik margin`.

### 3.2 Pemetaan Exit Code Berbasis Durasi
- Exit `0`: Berhasil (cetak stdout).
- Exit `124`: `[Command timed out after <timeout>s]`.
- Exit `137` (SIGKILL):
  - Jika durasi eksekusi >= batas timeout: `[Command force-killed after timeout]`.
  - Jika durasi eksekusi < batas timeout: `[Command killed by system / OOM]`.
- Exit selain di atas: Cetak stderr dan kode exit.

### 3.3 Onboarding & Status Visual
- **Indikator Proses Aktif:** Menampilkan status visual di terminal homescreen: `[Running: $ <cmd>...]`.
- **Dialog Deteksi Onboarding:**  
  Jika IPC mengembalikan error/SecurityException akibat permission `RUN_COMMAND` belum aktif atau `allow-external-apps=false`, tampilkan panduan dialog yang jelas:
  - Instruksi pengecekan `~/.termux/termux.properties` (`allow-external-apps=true`).
  - Petunjuk navigasi menu pengaturan Android untuk mengizinkan permission *Special app access*.

---

## 4. Fase 5: UI Claude Code Aesthetics & CI/CD

### 4.1 Palet Warna & Tipografi
- **Background:** Warm Charcoal (`#22211f`)
- **Surface / Selection:** Soft Clay (`#2d2b28`)
- **Teks Utama:** Sand / Bone (`#eae5d9`)
- **Accent & Prompt:** Claude Terracotta (`#d97757`)
- **Secondary / Info:** Muted Slate (`#8b8680`)
- **Success:** Sage Green (`#789a74`)
- **Error:** Crimson Rust (`#c45c50`)
- **Font:** Fira Code Nerd Font (`font.ttf`).

### 4.2 Spesifikasi Glyph Unicode & Layout Homescreen
Kodepoint Unicode eksklusif tanpa glyph mentah agar tidak rusak saat copy-paste:
- **Folder Icon:** `\uF07C`
- **Powerline Arrow Separator:** `\uE0B0`
- **Prompt Chevron:** `\u276F`
- **Diamond Separator:** `\u25C8`
- **Horizontal Rule:** `\u2500`

**Komponen Layout Homescreen:**
1. **Header Atas:**
   - Centered ASCII art font Bloody dengan teks `"ega"`.
   - Garis pemisah diamond: `\u25C8 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 \u25C8`.
2. **Area Tengah:**
   - Terminal log viewer (dapat discroll vertikal, menampung output perintah dan peluncuran app).
3. **Footer Bawah (Prompt 2-Baris):**
   ```text
   Baris 1: [\uF07C ~][\uE0B0]   <-- Background Charcoal, Text Terracotta
   Baris 2: \u276F [input_cursor]
   ```
   - Keyboard otomatis fokus saat homescreen aktif atau saat area kosong diketuk.

### 4.3 CI/CD Keystore & Otomasi Build
- **Konsistensi Keystore (Lokal & CI):**  
  File `debug.keystore` statis disimpan di repository dan dikonfigurasikan di `signingConfigs.debug` dalam `build.gradle.kts`. Ini menjamin APK hasil build lokal maupun build GitHub Actions memiliki signature identik sehingga dapat saling menimpa tanpa error *"App not installed"*.
- **Workflow GitHub Actions (`.github/workflows/build-apk.yml`):**
  - Trigger otomatis pada setiap push ke branch `main`.
  - Menggunakan runner `ubuntu-latest`.
  - Setup JDK 17 dan Android SDK target API 34.
  - Menjalankan `./gradlew assembleDebug`.
  - Mengunggah file `app-debug.apk` sebagai artifact rilis yang siap diunduh langsung ke HP.

---

## 5. Rencana Unit Test (Fase 2 – 5)

1. **`DispatcherTest`:**
   - Uji parsing token pertama pada reserved words (`calc 5+5`).
   - Uji prefix `$` dengan atau tanpa spasi.
   - Uji input whitespace murni diabaikan.
2. **`AliasEngineTest`:**
   - Ekspansi token pertama & re-tokenisasi (`gs foo` -> `$ git status foo`).
   - Penolakan nested alias Arah 1 (`alias a=b` saat `b` sudah jadi alias).
   - Penolakan nested alias Arah 2 (`alias a=b` dibuat duluan, lalu `alias b=wa` ditolak).
   - Penolakan penimpaan reserved words (`alias apps="wa"` ditolak).
   - Penolakan nama alias berupa angka (`alias 1=wa` ditolak).
   - Parsing pembersihan quote luar pada nilai alias (`alias gs="git status"`).
3. **`FuzzyMatcherTest`:**
   - Prefix match (`wa` -> WhatsApp).
   - Inisial match (`yt` -> YouTube).
   - Ambigu memicu state `AwaitingSelection` saat selisih skor < 0.10.
   - Force ambiguous list via suffix `?` (`wa?`).
   - MRU preference memilih aplikasi riwayat sebelumnya.
   - Pembersihan entri MRU saat package aplikasi di-uninstall.
   - Kedaluwarsa state `AwaitingSelection` (tombol back, timeout 15s).
4. **`CalcParserTest`:**
   - Evaluasi operator dasar `+`, `-`, `*`, `/`, `%`, `^`, tanda kurung bersarang, desimal.
   - Penanganan divide-by-zero mengembalikan error string tanpa crash.
5. **`TimeoutOverrideTest`:**
   - Parsing valid `$t120 pkg update` menghasilkan timeout 120 detik.
   - Perintah shell `$tar -xf file.tar` tidak terpengaruh override timeout.
   - Validasi batas rentang: nilai di bawah 1 detik (`$t0`) atau di atas 600 detik (`$t9999`) ditolak.
