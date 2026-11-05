plugins {
    id("polyglossa.kotlin-multiplatform-base")
    id("polyglossa.nodejs")
}

kotlin {
    js {
        nodejs { testTask { useMocha { timeout = "60s" } } }
        useCommonJs()
        compilerOptions {
            target.set("es2015")
        }
    }
}
