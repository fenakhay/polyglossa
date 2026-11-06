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
        .fileContents(layout.projectDirectory.file("polyglossa/data/catalog.tsv"))
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

val catalog = layout.projectDirectory.file("polyglossa/data/catalog.tsv")
val catalogCopies =
    listOf(
        layout.projectDirectory.file("Sources/Polyglossa/Resources/catalog.tsv"),
        layout.projectDirectory.file("python/src/polyglossa/catalog.tsv"),
    )

val pythonVersionFile = layout.projectDirectory.file("python/src/polyglossa/_version.py")
val pythonLegal =
    listOf("LICENSE", "NOTICE").map { layout.projectDirectory.file(it) to layout.projectDirectory.file("python/$it") }

tasks.register("syncCatalogCopies") {
    group = "build"
    description =
        "Copies polyglossa/data/catalog.tsv to the Swift and Python packages, which read their own, and gives " +
            "the Python package the version and the licence files."

    val source = catalog
    val copies = catalogCopies
    val versionFile = pythonVersionFile
    val legal = pythonLegal
    val releaseVersion = providers.gradleProperty("releaseVersion").orElse("")
    inputs.file(source)
    inputs.files(legal.map { it.first })
    inputs.property("releaseVersion", releaseVersion)
    outputs.files(buildList {
        addAll(copies)
        add(versionFile)
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
    }
}

val checkCatalogCopies =
    tasks.register("checkCatalogCopies") {
        group = "verification"
        description = "Fails when the Swift or the Python package holds another catalog than the Kotlin library."

        mustRunAfter("syncCatalogCopies")
        val source = catalog
        val copies = catalogCopies
        inputs.file(source)
        inputs.files(copies)
        doLast {
            val stale = copies.filter { !it.asFile.exists() || it.asFile.readText() != source.asFile.readText() }
            if (stale.isNotEmpty()) {
                throw GradleException(
                    "these copies differ from polyglossa/data/catalog.tsv: ${stale.joinToString { it.asFile.path }}; " +
                        "run ./gradlew syncCatalogCopies"
                )
            }
        }
    }

tasks.named("check") { dependsOn(checkCatalogCopies) }
