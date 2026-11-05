plugins {
    id("polyglossa.kotlin-jvm")
    application
}

dependencies {
    implementation(project(":polyglossa"))
    implementation(libs.kotlinx.serialization.json)
}

application {
    mainClass.set("com.fenakhay.polyglossa.tools.CatalogToolKt")
}

val catalogFile = rootProject.layout.projectDirectory.file("polyglossa/data/catalog.tsv")
val reports = layout.buildDirectory.dir("reports/catalog")
val specFile = rootProject.layout.projectDirectory.file("spec/expectations.json")
val sqlFile = layout.buildDirectory.file("sql/polyglossa.sql")

tasks.register<JavaExec>("catalogDump") {
    group = "verification"
    description =
        "Rewrites polyglossa/data/catalog.tsv from what English Wiktionary exports now, if that has changed, " +
            "with a CHANGELOG.md section for the release it makes."

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(application.mainClass)
    args(
        catalogFile.asFile.absolutePath,
        "--spec",
        specFile.asFile.absolutePath,
        "--report",
        reports.get().file("notes.md").asFile.absolutePath,
        "--changelog",
        rootProject.layout.projectDirectory.file("CHANGELOG.md").asFile.absolutePath,
    )
    providers.gradleProperty("exports").orNull?.let { args("--exports", it) }
    finalizedBy(":syncCatalogCopies")
}

tasks.register<JavaExec>("specDump") {
    group = "verification"
    description = "Rewrites spec/expectations.json, what every implementation must answer, from catalog.tsv."

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(application.mainClass)
    args(catalogFile.asFile.absolutePath, "--offline", "--spec", specFile.asFile.absolutePath)
}

tasks.register<JavaExec>("sqlDump") {
    group = "build"
    description = "Writes catalog.tsv as SQL, build/sql/polyglossa.sql, for sqlite3 and the like."

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(application.mainClass)
    args(catalogFile.asFile.absolutePath, "--offline", "--sql", sqlFile.get().asFile.absolutePath)
    outputs.file(sqlFile)
}

tasks.register<JavaExec>("catalogCheck") {
    group = "verification"
    description =
        "Reports how English Wiktionary's languages differ from polyglossa/data/catalog.tsv, and fails if they do."

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(application.mainClass)
    args(
        catalogFile.asFile.absolutePath,
        "--check",
        "--report",
        reports.get().file("catalog.md").asFile.absolutePath,
    )
    providers.gradleProperty("exports").orNull?.let { args("--exports", it) }
}
