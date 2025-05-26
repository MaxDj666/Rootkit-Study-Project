plugins {
    kotlin("jvm")
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("net.java.dev.jna:jna:5.13.0")
}

application {
    mainClass.set("ServerKt")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
