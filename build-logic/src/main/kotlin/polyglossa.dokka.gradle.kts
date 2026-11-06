plugins {
    id("org.jetbrains.dokka")
}

dokka {
    pluginsConfiguration.html {
        customAssets.from(rootProject.layout.projectDirectory.file("brand/logo-icon.svg"))
        customStyleSheets.from(rootProject.layout.projectDirectory.file("dokka/styles/polyglossa.css"))
        templatesDir.set(rootProject.layout.projectDirectory.dir("dokka/templates"))
        footerMessage.set("© 2026 fenakhay. Code under the MIT licence; data under CC BY-SA 4.0.")
    }
}
