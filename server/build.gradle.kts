plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(kotlin("stdlib"))
}

application {
    mainClass.set("ServerKt")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
