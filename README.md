# CATUR SUARA V2

**Satu versi saja: V2 (versionName 2.0, versionCode 2).**

Papan catur 8x8 terlihat; pemain putih melawan Stockfish offline (arm64-v8a) melalui UCI. Mesin Stockfish 17.1 dikompilasi dari sumber resmi saat GitHub Actions build dan dibundel di APK sebagai executable `libstockfish.so` di nativeLibraryDir. Tidak perlu internet untuk mesin catur setelah APK terpasang.

Perintah suara memakai layanan pengenal suara Android dengan `EXTRA_PREFER_OFFLINE`; ini **tidak menjamin** pengenalan suara offline bila paket bahasa offline belum tersedia di perangkat. Text-to-Speech juga bergantung pada mesin suara perangkat.

## Cara mengganti isi repo

Unggah seluruh isi folder ini ke root repository GitHub `CaturSuara` dan **timpa** file lama. Workflow `.github/workflows/build-apk.yml` harus ikut. Jalankan Actions → Build CATUR SUARA V2 APK. Unduh artifact `CATUR_SUARA_V2_APK` lalu ekstrak `app-debug.apk`.

## Batasan

- APK belum dikompilasi/diuji di lingkungan pembuatan ini. Jika GitHub Actions gagal, gunakan log error untuk perbaikan.
- Mendukung arm64-v8a (umumnya OPPO A5 Pro). Promosi pion otomatis menjadi ratu. Pemain selalu putih.
- Sumber Stockfish berlisensi GPLv3; distribusi APK harus menyertakan akses ke kode sumber Stockfish dan perubahan terkait sesuai lisensi. Sumber resmi: https://github.com/official-stockfish/Stockfish
