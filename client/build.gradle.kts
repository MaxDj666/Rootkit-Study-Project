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
