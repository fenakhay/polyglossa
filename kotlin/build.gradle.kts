plugins {
    id("polyglossa.kotlin-multiplatform")
    id("polyglossa.kotlin-serialization")
    id("polyglossa.published")
}

val languageData = rootProject.layout.projectDirectory.file("data/catalog.tsv")
val generatedLanguageSource = layout.buildDirectory.dir("generated/languages/kotlin")

val generateLanguageData =
    tasks.register("generateLanguageData") {
        description =
            "Writes data/catalog.tsv, English Wiktionary's languages as catalogDump recorded " +
                "them, out as Kotlin, for Languages."
        group = "build"

        val input = languageData
        val output = generatedLanguageSource
        val chunk = 16_000
        inputs.file(input).withPathSensitivity(PathSensitivity.NONE).withPropertyName("languages")
        outputs.dir(output)

        doLast {
            val literal = { text: String ->
                buildString {
                    append('"')
                    for (char in text) {
                        when (char) {
                            '\\',
                            '"',
                            '$' -> append('\\').append(char)
                            '\n' -> append("\\n")
                            '\r' -> append("\\r")
                            '\t' -> append("\\t")
                            else -> append(char)
                        }
                    }
                    append('"')
                }
            }
            val text = input.asFile.readText()
            if (!text.startsWith("polyglossa\t1")) {
                throw GradleException("data/catalog.tsv is not what catalogDump writes")
            }
            val chunks = text.chunked(chunk).joinToString(",\n") { "        ${literal(it)}" }
            val target = output.get().asFile.resolve("com/fenakhay/polyglossa")
            target.mkdirs()
            target
                .resolve("BundledLanguages.kt")
                .writeText(
                    "package com.fenakhay.polyglossa\n\n" +
                        "/** data/catalog.tsv, in pieces short enough for every platform's string constants. */\n" +
                        "internal val BUNDLED_LANGUAGES: List<String> =\n" +
                        "    listOf(\n" +
                        chunks +
                        ",\n    )\n"
                )
        }
    }

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir(generateLanguageData)
            dependencies { implementation(libs.kotlinx.serialization.json) }
        }
    }
}

dokka { moduleName = "polyglossa" }
