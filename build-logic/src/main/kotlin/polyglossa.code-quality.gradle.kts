import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("com.diffplug.spotless")
    id("dev.detekt")
}

val libs = the<LibrariesForLibs>()

dependencies.components.withModule("com.facebook:ktfmt") {
    allVariants {
        withDependencies {
            filter { it.group == "org.jetbrains.kotlin" }
                .forEach { dependency -> dependency.version { require(libs.versions.ktfmtKotlin.get()) } }
        }
    }
}

spotless {
    kotlin {
        target(fileTree("src") { include("**/*.kt") })
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle().configure {
            it.setMaxWidth(110)
            it.setBlockIndent(4)
            it.setContinuationIndent(4)
            it.setRemoveUnusedImports(true)
        }
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle().configure {
            it.setMaxWidth(110)
            it.setBlockIndent(4)
            it.setContinuationIndent(4)
        }
    }
}

detekt {
    source.setFrom(layout.projectDirectory.dir("src"))
    buildUponDefaultConfig = true
    config.setFrom(rootProject.layout.projectDirectory.file("config/detekt.yml"))
    basePath = rootProject.layout.projectDirectory
}
