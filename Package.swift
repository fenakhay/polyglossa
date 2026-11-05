// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "Polyglossa",
    platforms: [.macOS(.v12), .iOS(.v15), .tvOS(.v15), .watchOS(.v8)],
    products: [.library(name: "Polyglossa", targets: ["Polyglossa"])],
    targets: [
        .target(name: "Polyglossa", resources: [.copy("Resources/catalog.tsv")]),
        .testTarget(name: "PolyglossaTests", dependencies: ["Polyglossa"]),
    ]
)
