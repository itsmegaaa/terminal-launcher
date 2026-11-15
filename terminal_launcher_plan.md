# Terminal Launcher (CLI-Launcher) Implementation Plan (Spike Phase Spec v3.4)

> **Status:** Ready for Spike Execution.  
> **Target SDK:** Android 14 (`targetSdk 34`), `minSdk 26`.  
> **Fokus Eksekusi:** HANYA **Fase 1 (Spike Bridge)**. Spesifikasi arsitektur & desain UI lengkap Fase 2–5 didokumentasikan terpisah di [./ROADMAP.md](./ROADMAP.md).

---

## 1. Goal Description

Membuktikan secara empiris kelayakan komunikasi IPC dua arah antara aplikasi Android mandiri (Kotlin) dan Termux via `com.termux.RUN_COMMAND` di background, sebelum melangkah ke pembuatan aplikasi launcher.

---

## 2. Batasan Teknis & Desain Kunci (Fase 1)

> [!IMPORTANT]
> **Karakteristik & Batasan Termux RUN_COMMAND Bridge:**
> - **One-Shot Only (Non-Interaktif, Tanpa TTY):** Background command via Intent tidak memiliki `stdin`. Perintah interaktif (`vim`, `nano`, `python` REPL, `ssh`, prompt `y/n`) **tidak didukung**. Jika butuh interaksi, user diarahkan membuka sesi Termux foreground.
> - **Eksekusi Background Murni (`RUN_COMMAND_BACKGROUND = true`):**  
>   Secara bawaan, Termux akan memunculkan jendela terminal foreground saat menerima intent. Aplikasi wajib menyertakan boolean extra:  
>   `intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)`  
>   agar eksekusi berjalan 100% senyap di background tanpa menginterupsi layar launcher.
> - **Format Bundle Hasil (Resolusi Dinamis & Internal Truncation):**  
>   Key bundle hasil dari Termux tidak di-hardcode kaku, melainkan diinspeksi secara dinamis dengan fallback bertingkat: root extras, `com.termux.app.extra.TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE`, `"result"`, atau sub-bundle apa pun yang memuat `stdout`/`exitCode`. Semua keys yang diterima dicatat pada Test 1.  
>   *Internal Truncation & Sinyal Error:* `termux-shared` memotong output ~100KB gabungan sebelum dikirim dan menyertakan extras `stdout_original_length` / `stderr_original_length`. Sinyal error internal diekstrak via `err` dan `errmsg`.
> - **Karakteristik Shell Non-Login & Non-Interactive (`bash -c`):**  
>   Perintah dieksekusi tanpa sourcing otomatis `~/.bashrc` atau `~/.profile`. Skenario `$PATH` diuji di Spike untuk mengevaluasi apakah dibutuhkan file environment ringan khusus launcher (hindari `bash -lc` agar banner/skrip berat tidak merusak target latensi warm < 1000ms).
> - **Quoting Aman via Array Argumen (Bukan Anti-Injection):**  
>   Perintah shell user sengaja diinterpretasikan penuh oleh bash (pipe `|`, `&&`, `$VAR`, backticks tetap berfungsi).  
>   Perintah **DILARANG** disambung sebagai string mentah ke wrapper. Perintah diteruskan apa adanya tanpa filtering/escaping sebagai **satu elemen array mandiri** melalui extras Termux resmi:
>   - `RUN_COMMAND_PATH` = `"/data/data/com.termux/files/usr/bin/timeout"`
>   - `RUN_COMMAND_ARGUMENTS` = `["-k", "2", "<timeout_seconds>", "/data/data/com.termux/files/usr/bin/bash", "-c", cmd]`  
>   *(Durasi timeout dan ExitCodeMapper menerima durasi dinamis, bukan angka mati).*
> - **Skema `requestCode` Anti-Tabrakan & Pembersihan Memori:**  
>   Android mencocokkan cache PendingIntent berdasarkan komponen, action, data, dan `requestCode` (`Int` 32-bit). UUID atau timestamp tidak boleh dipakai langsung sebagai `requestCode`.  
>   *Implementasi:* Gunakan generator counter `AtomicInteger` yang naik terus (monotonik) sebagai `requestCode`. Simpan pemetaan internal di memori app: `requestCode (Int) -> ExecutionContext (UUID, timestamp, duration, pendingIntent, callback)`.  
>   *Pembersihan Saat Timeout:* Jika watchdog UI habis atau timeout terjadi, entri di pemetaan wajib dihapus untuk mencegah kebocoran memori, dan `pendingIntent.cancel()` dipanggil.
> - **Pemetaan Exit Code Berbasis Durasi (124 & 137):**  
>   - Exit code `124`: Proses normal yang diputus oleh batas waktu `timeout` -> Pesan UI: `[Command timed out after <timeout>s]`.
>   - Exit code `137` (SIGKILL):
>     - Jika `durasi eksekusi >= batas timeout` -> Pesan UI: `[Command force-killed after timeout]`.
>     - Jika `durasi eksekusi < batas timeout` -> Pesan UI: `[Command killed by system / OOM]`.
> - **UI Watchdog Safety Net:** Watchdog UI disetel: `Termux timeout (<timeout>s) + safety margin (5s)`.
> - **Buffer Cap ~100KB:** Batas transaksi Android Intent adalah ~100KB. Perilaku aktual dicatat di Spike.

> [!WARNING]
> **Prasyarat & Keamanan Android 14+ (targetSdk 34):**
> 1. `allow-external-apps=true` wajib aktif di `~/.termux/termux.properties`.  
>    *Catatan:* Launcher tidak bisa membaca file private Termux ini; deteksi kondisi ini dilakukan secara empiris lewat sinyal IPC.
> 2. Permission `com.termux.permission.RUN_COMMAND` wajib diaktifkan pada aplikasi spike.  
>    *Catatan:* Pada beberapa varian Android, permission ini tidak muncul di dialog runtime standar, melainkan harus diaktifkan manual via menu **Special app access / Additional permissions**.
> 3. `PendingIntent.FLAG_MUTABLE` **wajib memakai Intent eksplisit** (`intent.setClass(context, TermuxResultReceiver::class.java)`).
> 4. Receiver lokal disetel `android:exported="false"`.

---

## 3. Matriks Pengujian Fase 1 (Spike Bridge)

> [!CAUTION]
> **PERINGATAN KESELAMATAN DATA:**
> DILARANG melakukan pengujian yang menghapus data Termux (Clear Data / Uninstall) pada Termux utama yang dipakai harian! Seluruh file `~/` (script, git repo, bot) akan hilang permanen. Pengujian skenario fresh install hanya boleh diuji di emulator atau perangkat sekunder.

**Aturan Evaluasi Output:**
- Bandingkan string `stdout` setelah `trimEnd()` untuk mentoleransi trailing newline alami dari utilitas shell (`echo`). Catat apakah newline mentah disertakan di bundle.
- Di fase spike ini tidak ada router pengupas `$`. Seluruh perintah dikirim sebagai string command mentah.

### Matriks Uji Runtime:
1. **Normal Execution:**  
   - Command: `echo 'bridge-ok'`  
   - Ekspektasi: `stdout.trimEnd() == "bridge-ok"`, `exitCode = 0`. Catat seluruh key bundle yang diterima untuk memverifikasi struktur payload Termux.
2. **Quoting & Shell Syntax Integrity:**  
   - Command: `echo "a'b $HOME \n c"`  
   - Ekspektasi Runtime: `$HOME` diekspansi dan `\n` literal:  
     `stdout.trimEnd() == "a'b /data/data/com.termux/files/home \n c"`, `exitCode = 0`.
3. **Delayed Execution & Non-blocking:**  
   - Command: `sleep 3; echo 'delayed-ok'`  
   - Ekspektasi: Output diterima setelah ~3 detik tanpa UI freeze, `exitCode = 0`.
4. **Stderr & Non-Zero Exit Code:**  
   - Command: `ls /nonexistent_spike_dir`  
   - Ekspektasi: `stderr` memuat `No such file or directory`, `exitCode != 0`.
5. **Standard Timeout (Exit Code 124):**  
   - Command dengan wrapper 5s: `sleep 60`  
   - Ekspektasi: Output kembali ~5 detik dengan `exitCode = 124`.
6. **Stubborn Process SIGKILL via `-k` (Exit Code 137):**  
   - Command dengan wrapper 5s (`-k 2`): `trap '' TERM; sleep 60`  
   - Ekspektasi: Output kembali ~7 detik dengan exit code 137 (atau 124/lainnya, catat aktual).
7. **Latency Benchmark (Roundtrip):**  
   - Command: `echo hi`  
   - Ekspektasi: Ukur minimal **20 kali pengujian berturut-turut**, hitung nilai **median** dan **worst-case** (ms) dari intent dikirim sampai bundle diterima. Uji pada dua kondisi: **warm** (proses Termux sudah aktif di background) vs **cold** (Termux baru di-force-stop).
8. **Output Cap Observation (~200KB):**  
   - Command: `head -c 200000 /dev/zero | tr '\0' a`  
   - Ekspektasi: Amati pemotongan bawaan Termux via `stdout_original_length` (~100KB) dan catat bagian mana yang dipertahankan (kepala vs ekor).
9. **Cold Start & Inactive Process:**  
   - **9a. Fresh Install:** *(Uji hanya di emulator/device lain)*. Catat sinyal bootstrap belum siap.  
   - **9b. Inactive Process:** *Cara Reproduksi:* Force stop Termux via Info Aplikasi Android. Kirim intent `echo hi`. Ekspektasi: Service bangun dan berhasil eksekusi.
10. **`allow-external-apps=false` Observation:**  
    - *Cara Reproduksi:* Ubah `allow-external-apps=false` di `~/.termux/termux.properties`, jalankan `termux-reload-settings`.  
    - Ekspektasi: Catat sinyal kegagalan yang diterima app (SecurityException, bundle null, atau field `err`/`errmsg`). Kembalikan ke `true` setelah tes.
11. **Negative Control (FLAG_IMMUTABLE):**  
    - Ekspektasi: PendingIntent disetel `FLAG_IMMUTABLE`. Konfirmasi bundle kembali `null`.
12. **Explicit Intent Verification (Android 14):**  
    - Ekspektasi: PendingIntent eksplisit berjalan mulus di Android 14 (`targetSdk 34`) tanpa SecurityException.
13. **Permission RUN_COMMAND Not Granted:**  
    - *Cara Reproduksi:* Cabut izin RUN_COMMAND dari app spike via Pengaturan Android. Kirim intent.  
    - Ekspektasi: Catat sinyal OS / internal Termux (`err`, `errmsg`) dan catat jalur menu persis untuk mengaktifkannya di perangkat.
14. **Rapid Back-to-Back Execution (Collision Test):**  
    - Command berturut-turut: Kirim `echo "first"` lalu 10ms kemudian `echo "second"`.  
    - Ekspektasi: Kedua hasil kembali utuh ke execution context masing-masing tanpa tertukar atau menimpa satu sama lain.
15. **(Opsional) Shared Storage Result Directory (Plan B Probe):**  
    - Command: Eksekusi dengan extra `RUN_COMMAND_RESULT_DIRECTORY` mengarah ke direktori bersama (`/sdcard/Download` atau shared cache).  
    - Ekspektasi: Uji apakah Termux mampu menulis dan app spike mampu membaca file hasil secara lintas UID. Catat izin apa yang akhirnya dibutuhkan (misal `MANAGE_EXTERNAL_STORAGE` di Android 11+) agar biayanya terlihat jelas.
16. **(Opsional) Surviving Background Process (Daemon Test):**  
    - Command: `nohup sleep 120 >/dev/null 2>&1 &`  
    - Ekspektasi: Cek melalui command kedua `ps -ef | grep sleep` apakah proses tetap hidup setelah bash selesai dan wrapper kembali, atau ikut dibunuh Termux/Android. Ini menjawab apakah launcher mampu menyalakan bot/server atau murni runner command pendek.
17. **(Observasi) Shell Environment & $PATH Coverage:**  
    - Command: `echo "$PATH"`  
    - Ekspektasi: Catat nilai `$PATH` default pada non-login `bash -c`. Verifikasi apakah tool di luar `/usr/bin` (seperti `~/.local/bin`, `~/go/bin`) dapat diakses tanpa profile file, dan evaluasi opsi `BASH_ENV` ringan jika diperlukan.

---

## 4. Gerbang Go / No-Go & Rencana Cadangan (Plan B)

### Kriteria GO (Syarat Lanjut ke Fase 2):
1. Pengujian nomor 1 sampai 5 (Normal, Quoting, Delay, Stderr, Timeout 124) dinyatakan **PASS**.
2. Pengujian nomor 9b (Termux inactive bangun normal) dan nomor 12 (Intent eksplisit di Android 14) dinyatakan **PASS**.
3. Pengujian nomor 14 (Rapid back-to-back collision test) dinyatakan **PASS**.
4. Median latensi warm `echo hi` (dari 20 sampel) berada di bawah **1000ms (1 detik)**. Jika di atas 1 detik, diskusikan kelayakan UX sebelum lanjut.
5. Pemotongan output besar (Test 8) terdeteksi secara andal via `*_ORIGINAL_LENGTH` dan UI menampilkan status `[output truncated]`. (Pola `| head -c 80000` via parameter posisi disiapkan sebagai fallback jika Termux versi lama tidak memotong internal).

### Kriteria NO-GO & Batas Investigasi:
- Jika investigasi bundle `null` atau IPC ditolak sistem berlangsung hingga **3 jam tanpa progres**, hentikan pengerjaan spike.
- Jika hasil evaluasi adalah NO-GO, file **`SPIKE_RESULTS.md`** tetap **wajib disusun lengkap** dengan seluruh data empiris, log kegagalan, dan analisis batas OS sebagai output berharga sesi ini.

### Plan B (Pivot Konkret):
- Jika bundle PendingIntent bermasalah di Android tertentu:  
  *Hipotesis yang perlu dibuktikan (termasuk izin akses storage lintas UID):* Gunakan parameter `RUN_COMMAND_RESULT_DIRECTORY` agar Termux menulis `stdout`, `stderr`, dan `exit_code` ke shared storage lokal yang kemudian dibaca oleh aplikasi.

**Deliverable Wajib Fase 1:**  
File laporan **`SPIKE_RESULTS.md`** yang memuat:
- Informasi Perangkat: Model HP, Versi Android, Versi Termux, Sumber Instalasi Termux (F-Droid/GitHub).
- Tabel Ringkasan: `| ID | Skenario | Ekspektasi | Hasil Aktual | Status (PASS/FAIL) | Catatan |`.
- Hasil benchmark latensi (20 sampel, median & max).
- Kesimpulan akhir: GO / NO-GO.

---

## 5. Rencana Unit Test Spike

1. **`BridgeCommandBuilderTest`:**
   - Memastikan input user dengan kutip tunggal, ganda, `$VAR`, pipe, newline, dan unicode diteruskan sebagai **satu elemen array tunggal** di `RUN_COMMAND_ARGUMENTS` tanpa escaping yang merusak.
2. **`ExitCodeMapperTest`:**
   - Pemetaan dinamis berbasis durasi parameter:
     - `0` -> Success
     - non-zero -> Error umum
     - `124` -> `[Command timed out after <timeout>s]`
     - `137` dengan durasi >= timeout -> `[Command force-killed after timeout]`
     - `137` dengan durasi < timeout -> `[Command killed by system / OOM]`.
3. **`ExecutionIdManagerTest`:**
   - Memastikan counter `AtomicInteger` menghasilkan `requestCode` unik hingga 1000 panggilan berurutan bebas tabrakan (diuji secara concurrent / multi-thread).
   - Memastikan pembersihan context map dan pembatalan `pendingIntent.cancel()` tereksekusi dengan benar saat terjadi timeout.
