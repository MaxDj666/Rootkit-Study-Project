plugins {
    kotlin("jvm")
    application
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
