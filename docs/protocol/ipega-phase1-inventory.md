# iPega Phase 1 Reference Archive Inventory

Scope: all local APK/XAPK refs under `docs/refs/` for DISC-01. Static inspection only; no vendor code executed.

## Tool Provenance

| Tool | Version | Path | Use |
|------|---------|------|-----|
| `apktool` | 3.0.2 | `/opt/homebrew/bin/apktool` | Decode manifests/resources into ignored `.evidence/phase1/decompile/*-apktool/`. |
| `jadx` | 1.5.5 | `/opt/homebrew/bin/jadx` | Available for Java-like static code review in clue extraction. |
| `unzip` | system `/usr/bin/unzip` | `/usr/bin/unzip` | XAPK listing/extraction and ZIP validity checks. |

Generated decode/extract output stays under ignored `.evidence/phase1/decompile/`.

## First-Pass Order

1. Strongest refs: `docs/refs/ARGun2021.apk`, `docs/refs/AR Cher_20200905_Apkpure.xapk`, `docs/refs/WorldsAR_14.0_apkcombo.com.xapk`.
2. Secondary ref: `docs/refs/ARGun Library_1.0.1_apkcombo.com.apk`.
3. Invalid local ref: `docs/refs/ARGunPro_1.0.19_apkcombo.com.xapk`; 0 bytes, do not reacquire unless strongest valid refs block discovery.

## Inventory

| Ref path | SHA-256 | Size | Archive type | Package | Label | Version | Min SDK | Target SDK | Permissions | App type | Validity | First-pass priority | Notes |
|----------|---------|------|--------------|---------|-------|---------|---------|------------|-------------|----------|----------|---------------------|-------|
| `docs/refs/ARGun2021.apk` | `6e43f52b4c8ce1589442e7aa3df903380b7a6142b5e5990ed1f9b2242a7f19da` | 319,549,922 bytes | APK | `com.lcp.arbrower` | ARGun | `2021031618` | 19 | 29 | `INTERNET`, `CAMERA`, `ACCESS_NETWORK_STATE`, `BLUETOOTH`, `BLUETOOTH_ADMIN`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `VIBRATE` | Unity AR game with `UnityPlayer*` activities, BLE feature required, native Unity/EasyAR libs | Valid ZIP/APK; apktool decode succeeded | Strongest | Best first static source: Bluetooth/BLE permissions, BLE feature, Unity bridge activity `com.lenzetec.unityandroidplugindemo.MainActivity`. |
| `docs/refs/AR Cher_20200905_Apkpure.xapk` | `0f459e11dab978b1efea1014adf75160ee6d8c6ebde7035f7e1864a9ea0fd034` | 160,402,650 bytes | XAPK with `com.lenzetech.archer.apk` and OBB | `com.lenzetech.archer` | ARCher | `20200905` | 14 | 29 | `INTERNET`, `CAMERA`, `ACCESS_NETWORK_STATE`, `WRITE_EXTERNAL_STORAGE`, `ACCESS_FINE_LOCATION`, `BLUETOOTH_ADMIN`, `BLUETOOTH` | Unity AR game with OBB, BLE feature optional, Unity bridge activity | Valid XAPK; embedded APK extracted and apktool decode succeeded | Strongest | Strongest paired source for app-specific Bluetooth path; duplicate fine-location entry present in manifest/XAPK metadata. |
| `docs/refs/WorldsAR_14.0_apkcombo.com.xapk` | `b72685d493fd8166398cacca712ae408e3ce05d77e372ebb42a6fbda61e091f1` | 157,078,685 bytes | XAPK with `com.lenze.armagic.apk` and OBB | `com.lenze.armagic` | WorldsAR | `14.0` | 14 | 22 | `INTERNET`, `CAMERA`, `ACCESS_NETWORK_STATE`, `WRITE_EXTERNAL_STORAGE`, `READ_EXTERNAL_STORAGE`, `BLUETOOTH_ADMIN`, `BLUETOOTH` | Unity AR game with OBB, BLE feature required, Unity bridge activity | Valid XAPK; embedded APK extracted and apktool decode succeeded | Strongest | Older target SDK and required BLE feature may expose shared iPega control assumptions. |
| `docs/refs/ARGun Library_1.0.1_apkcombo.com.apk` | `34ee85b8aa7b39f6396b0087fb9d94e3b240a766b367c7e80857467a1260b2af` | 9,646,854 bytes | APK | `com.argun` | ARGun | `1.0.1` | 16 | 26 | `INTERNET`, `SYSTEM_ALERT_WINDOW`, `WRITE_EXTERNAL_STORAGE`, `READ_PHONE_STATE`, `READ_EXTERNAL_STORAGE` | React Native app/library shell; no Bluetooth permissions in manifest | Valid ZIP/APK; apktool decode succeeded | Secondary | Useful only if strongest refs do not explain shared app/library behavior; manifest does not point at gun Bluetooth control. |
| `docs/refs/ARGunPro_1.0.19_apkcombo.com.xapk` | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` | 0 bytes | XAPK placeholder | Unknown | Unknown | Unknown | Unknown | Unknown | Unknown; cannot inspect | Unknown | Invalid: 0 bytes local file | Invalid/deferred | Per D-05, do not reacquire unless strongest valid refs block protocol discovery. |

## Decode Paths

| Ref | Ignored local decode path |
|-----|---------------------------|
| `docs/refs/ARGun2021.apk` | `.evidence/phase1/decompile/argun2021-apktool/` |
| `docs/refs/AR Cher_20200905_Apkpure.xapk` | `.evidence/phase1/decompile/archer-apktool/` plus extracted base APK in `.evidence/phase1/decompile/extracted/` |
| `docs/refs/WorldsAR_14.0_apkcombo.com.xapk` | `.evidence/phase1/decompile/worldsar-apktool/` plus extracted base APK in `.evidence/phase1/decompile/extracted/` |
| `docs/refs/ARGun Library_1.0.1_apkcombo.com.apk` | `.evidence/phase1/decompile/argun-library-apktool/` |

## Immediate Static Observations

- The three strongest refs are Unity AR apps and request legacy Bluetooth permissions; two declare BLE feature required and one declares BLE optional.
- `ARGun2021.apk` also requests `VIBRATE`, which makes it the first rumble-path candidate.
- `ARGun Library_1.0.1_apkcombo.com.apk` has no Bluetooth permission in its manifest, so it is secondary for protocol discovery.
- No entry here is a verified protocol finding. Verification needs static clue, hardware capture, and normalized fixture linkage.
