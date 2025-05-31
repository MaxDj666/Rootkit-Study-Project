plugins {
    application
    kotlin("jvm")
    id("com.github.johnrengelman.shadow")
    id("org.openjfx.javafxplugin") version "0.1.0"
}

dependencies {
    implementation(kotlin("stdlib"))
}

application {
    mainClass.set("ClientApp")
    applicationDefaultJvmArgs = listOf(
        "-Dglass.win.uiScale=100%",                          // Фиксирует масштабирование для Windows
        "-Dprism.order=sw",                                  // Использует программный рендеринг
        "-Dkotlin.io.paths.allowWindowsUnixSeparators=true", // Для работы с путями длиннее 260 символов требуется
    )
}

javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.fxml")
}

tasks.register("createClientJre", Exec::class) {
    dependsOn("shadowJar")
    val jreDir = layout.buildDirectory.dir("client-jre")
    val javafxHome = System.getenv("JAVAFX_HOME") ?: error("Set JAVAFX_HOME to JavaFX jmods path")

    commandLine = listOf(
        "jlink",
        "--module-path", "$javafxHome/lib",
        "--add-modules", "javafx.controls,javafx.fxml",
        "--output", jreDir.get().asFile.absolutePath,
        "--strip-debug",
        "--compress=2",
        "--no-header-files",
        "--no-man-pages"
    )
}
