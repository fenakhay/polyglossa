import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("polyglossa.kotlin-multiplatform-common")
    id("org.jetbrains.kotlinx.kover")
}

val libs = the<LibrariesForLibs>()

kotlin {
    jvmToolchain(25)

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.add("-Xjdk-release=21")
        }

        testRuns.named("test") {
            executionTask.configure {
                useJUnitPlatform()
            }
        }
    }

    macosArm64()
    linuxX64()
    mingwX64()

    sourceSets {
        jvmTest.dependencies {
            implementation(kotlin("test-junit5"))
            implementation(project.dependencies.platform(libs.junit.bom))
            implementation(libs.junit.jupiter)
            runtimeOnly(libs.junit.platform.launcher)
        }
    }
}

val implementationTitle = project.name
val implementationVersion = project.version.toString()

tasks.withType<Jar>().configureEach {
    manifest {
        attributes(
            "Implementation-Title" to implementationTitle,
            "Implementation-Version" to implementationVersion,
        )
    }
}

tasks.withType<Test>().configureEach {
    val temporary = layout.buildDirectory.dir("tmp/$name/java.io.tmpdir").get().asFile
    systemProperty("java.io.tmpdir", temporary.absolutePath)
    doFirst { temporary.mkdirs() }
}

kover {
    currentProject {
        instrumentation {
            disabledForTestTasks.add("jvm21Test")
        }
    }
}

val jvmTestCompilation = kotlin.jvm().compilations.getByName("test")

tasks.register<Test>("jvm21Test") {
    description = "Runs the JVM tests on JDK 21, the oldest the library supports."
    group = "verification"

    testClassesDirs = jvmTestCompilation.output.classesDirs
    classpath = files(jvmTestCompilation.output.allOutputs, jvmTestCompilation.runtimeDependencyFiles)
    useJUnitPlatform()
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })

    failOnNoDiscoveredTests = false
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}
