# CATUR SUARA — Proyek Android (versi awal)

Aplikasi Android Kotlin untuk permainan catur dua pemain, papan interaktif, input koordinat melalui suara bahasa Indonesia, dan Text-to-Speech. Validasi langkah memakai chesslib.

## Build
Buka folder ini di Android Studio, sinkronkan Gradle, lalu **Build > Build APK(s)**. APK debug biasanya berada di `app/build/outputs/apk/debug/app-debug.apk`.

## Batasan penting
- **Belum ada mesin Stockfish** dan belum ada lawan komputer. Ini fondasi dua pemain, bukan APK final sesuai seluruh spesifikasi.
- Pengenalan suara memakai layanan Android; **tidak dijamin offline**. Untuk offline penuh perlu mengintegrasikan model ASR lokal.
- Promosi pion saat ini otomatis memilih ratu.
- Proyek ini belum diuji build karena lingkungan penyusunan tidak menyediakan Android SDK/Gradle.
