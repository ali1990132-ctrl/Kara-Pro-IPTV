# A&S TV Pro - update channel

- `A&S_TV_pro.apk` = latest version (stable link for sharing).
- `A&S_TV_pro_vNN.apk` = same file with the version number; `update.json` points here so phones never get an old cached copy.

To publish an update: build with a higher versionCode (same signing key), upload it both as `A&S_TV_pro.apk` and `A&S_TV_pro_vNN.apk`, delete the previous `_v` file, then set `versionCode`, `versionName` and `url` in `update.json`.
