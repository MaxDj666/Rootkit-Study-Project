plugins {
    application
    kotlin("jvm")
    id("com.github.johnrengelman.shadow")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("net.java.dev.jna:jna:5.13.0")
    implementation("net.java.dev.jna:jna-platform:5.13.0")
}

application {
    mainClass.set("ServerKt")
}

tasks.register("createServerJre", Exec::class) {
    dependsOn("shadowJar")
    val jreDir = layout.buildDirectory.dir("server-jre")

    commandLine = listOf(
        "jlink",
        "--add-modules", "jdk.unsupported",
        "--output", jreDir.get().asFile.absolutePath,
        "--strip-debug",
        "--compress=2",
        "--no-header-files",
        "--no-man-pages"
    )
}
