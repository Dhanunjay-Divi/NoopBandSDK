// swift-tools-version: 5.9

import PackageDescription

let package = Package(
    name: "NoopBandSDK",
    platforms: [
        .iOS(.v17),
        .macOS(.v13),
    ],
    products: [
        .library(name: "NoopBandCore", targets: ["NoopBandCore"]),
        .executable(
            name: "noop-band-conformance",
            targets: ["NoopBandConformance"]
        ),
    ],
    targets: [
        .target(name: "NoopBandCore"),
        .executableTarget(
            name: "NoopBandConformance",
            dependencies: ["NoopBandCore"]
        ),
        .testTarget(
            name: "NoopBandCoreTests",
            dependencies: ["NoopBandCore"]
        ),
    ]
)
