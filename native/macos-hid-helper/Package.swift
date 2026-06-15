// swift-tools-version: 6.0

import PackageDescription

let package = Package(
    name: "BtGunMacosHidHelper",
    platforms: [
        .macOS(.v15),
    ],
    products: [
        .executable(name: "BtGunMacosHidHelper", targets: ["BtGunMacosHidHelper"]),
        .executable(name: "BtGunMacosHidOutputProbe", targets: ["BtGunMacosHidOutputProbe"]),
    ],
    targets: [
        .executableTarget(
            name: "BtGunMacosHidHelper"
        ),
        .executableTarget(
            name: "BtGunMacosHidOutputProbe"
        ),
    ]
)
