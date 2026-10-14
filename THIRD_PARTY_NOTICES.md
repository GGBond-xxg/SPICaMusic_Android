# Third-Party Notices

SPICa Music is based on
[yangSpica27/SPICaMusic_Android](https://github.com/yangSpica27/SPICaMusic_Android).
Its MIT license and original copyright notice are retained in the repository's
[LICENSE](LICENSE) file.

The in-app open-source licenses page lists the libraries distributed with the
application. The following additional notices cover adapted concepts and
reference-only projects.

## Lyricon Provider

The optional status-bar lyrics integration uses
[tomakino/lyricon](https://github.com/tomakino/lyricon), version 0.1.70.

Copyright 2026 Proify, Tomakino

Licensed under the Apache License, Version 2.0. You may obtain a copy at
<https://www.apache.org/licenses/LICENSE-2.0>.

## XiangsuPlayerHQ

[r3n011/XiangsuPlayerHQ](https://github.com/r3n011/XiangsuPlayerHQ) was used as
a product and interaction reference for cloud streaming, including the Bilibili
audio source introduced in v1.7.0. The referenced repository currently declares
GPL-3.0. The SPICa cloud adapters are implemented for this project; no
XiangsuPlayerHQ source code or binary is bundled or redistributed.

## ZXing Core

赞助二维码使用 [ZXing Core](https://github.com/zxing/zxing)（`com.google.zxing:core:3.5.3`），遵循 Apache License 2.0。二维码在设备本地生成。

## MD3Music

[zzyoxml/md3Music](https://github.com/zzyoxml/md3Music) was consulted for
Kugou service interoperability: QR/SMS login states and encryption parameters, request parameters, playlist
pagination, stream response fields and daily reward request protocols. That project declares AGPL-3.0.
SPICa's Kotlin adapter and Compose login screen were implemented within this
project's existing cloud architecture; no Flutter/Rust source, server binary,
or visual assets from MD3Music are bundled. Login QR images are generated locally
using the existing Apache-2.0-licensed ZXing dependency.

## Backdrop (AndroidLiquidGlass)

Glass appearance uses [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass),
published as `io.github.kyant0:backdrop:1.0.6`, under the Apache License, Version 2.0.
Copyright 2025 Kyant. License: <https://www.apache.org/licenses/LICENSE-2.0>.
