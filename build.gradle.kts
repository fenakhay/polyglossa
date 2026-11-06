plugins {
    base
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.nmcp.aggregation)
    alias(libs.plugins.spotless) apply false
    id("polyglossa.nodejs")
}

group = "com.fenakhay.polyglossa"
val catalogDate =
    providers
        .fileContents(layout.projectDirectory.file("data/catalog.tsv"))
        .asText
        .map { it.lineSequence().first().split('\t').getOrNull(2).orEmpty() }

/** The version a catalog exported on [date], `2026-11-05`, is released as: `2026.11.5`. */
fun calendarVersion(date: String): String =
    date.split('-').takeIf { it.size == 3 }?.joinToString(".") { it.toInt().toString() } ?: "0.0.0-SNAPSHOT"

version = providers.gradleProperty("releaseVersion").orNull ?: calendarVersion(catalogDate.get())

subprojects {
    group = rootProject.group
    version = rootProject.version
}

nmcpAggregation {
    centralPortal {
        username = providers.environmentVariable("CENTRAL_PORTAL_USERNAME")
        password = providers.environmentVariable("CENTRAL_PORTAL_PASSWORD")
        publishingType = "USER_MANAGED"
    }
}

dependencies { nmcpAggregation(project(":polyglossa")) }

val catalog = layout.projectDirectory.file("data/catalog.tsv")
val catalogCopies =
    listOf(
        layout.projectDirectory.file("swift/Sources/Polyglossa/Resources/catalog.tsv"),
        layout.projectDirectory.file("python/src/polyglossa/catalog.tsv"),
        layout.projectDirectory.file("typescript/catalog.tsv"),
        layout.projectDirectory.file("go/catalog.tsv"),
        layout.projectDirectory.file("rust/catalog.tsv"),
    )

val pythonVersionFile = layout.projectDirectory.file("python/src/polyglossa/_version.py")
val npmManifest = layout.projectDirectory.file("typescript/package.json")
val cargoManifest = layout.projectDirectory.file("rust/Cargo.toml")
val packageLegal =
    listOf("python", "typescript", "go", "rust").flatMap { dir ->
        listOf("LICENSE", "NOTICE").map { layout.projectDirectory.file("$it") to layout.projectDirectory.file("$dir/$it") }
    }

tasks.register("syncCatalogCopies") {
    group = "build"
    description =
        "Copies data/catalog.tsv to the Swift, Python, TypeScript, Go and Rust packages, which read " +
            "their own, and gives them the version and the licence files."

    val source = catalog
    val copies = catalogCopies
    val versionFile = pythonVersionFile
    val manifest = npmManifest
    val crate = cargoManifest
    val legal = packageLegal
    val releaseVersion = providers.gradleProperty("releaseVersion").orElse("")
    inputs.file(source)
    inputs.files(legal.map { it.first })
    inputs.property("releaseVersion", releaseVersion)
    outputs.files(buildList {
        addAll(copies)
        add(versionFile)
        add(manifest)
        add(crate)
        legal.mapTo(this) { it.second }
    })
    doLast {
        copies.forEach { source.asFile.copyTo(it.asFile, overwrite = true) }
        legal.forEach { it.first.asFile.copyTo(it.second.asFile, overwrite = true) }
        val date = source.asFile.useLines { it.first() }.split('\t').getOrNull(2).orEmpty().split('-')
        val release =
            releaseVersion.get().ifEmpty {
                date.takeIf { it.size == 3 }?.joinToString(".") { it.toInt().toString() } ?: "0.0.0-SNAPSHOT"
            }
        versionFile.asFile.writeText("__version__ = \"$release\"\n")
        val npm = manifest.asFile
        npm.writeText(npm.readText().replaceFirst(Regex("\"version\": \"[^\"]*\""), "\"version\": \"$release\""))
        val cargo = crate.asFile
        cargo.writeText(cargo.readText().replaceFirst(Regex("(?m)^version = \"[^\"]*\""), "version = \"$release\""))
    }
}

val checkCatalogCopies =
    tasks.register("checkCatalogCopies") {
        group = "verification"
        description = "Fails when the Swift, Python, TypeScript, Go or Rust package holds another catalog than the Kotlin library."

        mustRunAfter("syncCatalogCopies")
        val source = catalog
        val copies = catalogCopies
        inputs.file(source)
        inputs.files(copies)
        doLast {
            val stale = copies.filter { !it.asFile.exists() || it.asFile.readText() != source.asFile.readText() }
            if (stale.isNotEmpty()) {
                throw GradleException(
                    "these copies differ from data/catalog.tsv: ${stale.joinToString { it.asFile.path }}; " +
                        "run ./gradlew syncCatalogCopies"
                )
            }
        }
    }

tasks.named("check") { dependsOn(checkCatalogCopies) }
