// swift-tools-version: 6.0

import PackageDescription

let package = Package(
    name: "BtGunMacosHidHelper",
    platforms: [
        .macOS(.v15),
    ],
    products: [
        .executable(name: "BtGunMacosHidHelper", targets: ["BtGunMacosHidHelper"]),
    ],
    targets: [
        .executableTarget(
            name: "BtGunMacosHidHelper"
        ),
    ]
)
