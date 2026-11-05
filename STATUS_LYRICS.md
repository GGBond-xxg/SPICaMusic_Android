# Status lyrics compatibility and implementation evidence

## Output modes

- Off: stops all top-display outputs. The normal Media3 playback notification and the separately controlled desktop lyrics remain independent.
- Status lyrics: uses Flyme's native ticker flags on a Meizu device exposing both documented fields. Otherwise uses a permission-gated `TYPE_APPLICATION_OVERLAY` single-line window. It is an application overlay below protected system UI, not a universal native status-bar API.
- Live updates: existing Android 16+ promoted ongoing notification, subject to notification/promotion permission and ROM support.
- Lyricon (词幕): existing module integration. The persisted `status_lyric` value still means Lyricon; the new module-free route uses `system_status_lyric`. Unknown stored values resolve to Off.

Flyme controls the native ticker's placement. Only the application-overlay route exposes horizontal/vertical position, width, font size and text opacity. Its window is non-focusable/non-touchable, bounded to the current usable display area, and hidden when paused, stopped, locked or the screen is off. Android's obscuring-opacity limit is respected; other overlapping overlays can still affect OS touch policy.

The playback service owns the shared lyric loader and position clock. The player screen and background outputs consume the same source/offset selection; late results for another track are rejected. Rendering is deduplicated by displayed text/layout or native notification content.

## Primary platform sources

- [Flyme notification integration, section 7](https://open.flyme.cn/docs?id=239): reflect `FLAG_ALWAYS_SHOW_TICKER` and `FLAG_ONLY_UPDATE_TICKER`, set ticker text and `FLAG_NO_CLEAR`, reuse a notification ID, clear ticker flags on removal. The document explicitly says support varies by Flyme system. No private Xiaomi flags or system-signature permissions are requested by this implementation.
- [Android application overlay contract](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY): requires overlay permission and is below critical system windows such as the status bar.
- [Lyricon developer integration](https://tomakino.github.io/lyricon/zh-cn/developer/provider/quick-start): separate module/provider route, not a module-free system capability.

## REA inspection of installed Xiaomi Music

Inspected on 2026-11-05 using [REA](https://github.com/morluto/rea), its ADB package pull and JADX Android tools. The target APK was read from the connected Xiaomi phone; no proprietary code or APK is included in the repository or releases.

- Package: `com.miui.player`
- Version: `4.48.0.1` (`4480001`), target SDK 36
- Base APK SHA-256: `1ef4fedd2144abec950d6ee310c819d10ee84ca7fc044139474dc6bf00c0dfff`
- Engine: REA JADX bridge 0.7.1; static analysis, not proof that every discovered branch runs on this phone.

| Observation | Evidence |
| --- | --- |
| APK identity and manifest summary | `ev_e420b6c376b31bcce58a089f34bf5270bae0128ddb7e23effd0c7c731067202b` |
| `StatusBarLyricWindowManager.addWindowIfNotExist` checks float-window permission before calling `WindowManager.addView` | `ev_f0b91c9350245d7adc0250021d692ddfc6d0f7a101bbababdd8e1a181ab07363` |
| `StatusBarLyricWindowManager.generateWindowParams` obtains the window type from `FloatWinOpManager`, configures geometry and obscuring opacity | `ev_36c6ccabafb342170472b14ad86d5dd532a620990d42e8a2baf778a1938578d0` |
| `MIUIFloatWinOpManager.getFloatWinType` returns 2038 (`TYPE_APPLICATION_OVERLAY`) on API 26+ | `ev_86ae2647db60ff83004fba6b148e26acd7d2570e36dfac88fb2c8beb71da602d` |
| Generic `FloatWinOpManager.getFloatWinType` also returns 2038 on API 26+ | `ev_a680eec41cec9cc0e0103bd8aabb24972a52ce99f5ad4a51ef923b0879809a72` |
| `MeizuStatusBarLyricManager.checkMeiZuLyric` probes the same Flyme flag fields | `ev_12a7a4459788b29ff199e6f46a2169f2493b0e1f46dcd767f9d2a86cc2047136` |

The Meizu method has a decompiler warning and an incompletely reconstructed local branch; it is corroborating evidence for the field names only. The implementation follows the official Flyme contract instead. The APK's manifest also declares OEM privileges, but declaration alone does not establish grants, and the identified overlay path does not justify assuming third-party apps can draw over system UI.

No Meizu device was connected during development. Capability probing and notification construction are implemented, but native Flyme appearance still needs validation on a supported Meizu/Flyme device.

## Validation

- Debug build and all 64 app JVM tests passed, including mode migration, stale-track rejection, lyric offsets/backward seeks and geometry bounds.
- On the connected vivo Android 15 device: the overlay appeared with window type 2038, `NOT_FOCUSABLE | NOT_TOUCHABLE` and alpha 0.8; background playback and switching tracks updated its lyrics without opening the player/lyrics page. Pausing and revoking overlay permission both removed the window (zero remaining windows).
- The permission-settings round trip refreshed correctly. English strings, 1.6x system font and the ROM's forced landscape viewport remained scrollable. A 98dp requested horizontal offset was clamped to the actual 135px available window offset. Font size/geometry were recomputed on rotation. This does not change the application's existing portrait-orientation policy.
- Xiaomi Android 16 accepted installation and read-only UI inspection but rejected ADB touch injection (`INJECT_EVENTS`). Interactive verification therefore used the vivo device, not Xiaomi. Device locale, font size, rotation policy, media volume, lyric mode and temporary overlay permission were restored after testing.
