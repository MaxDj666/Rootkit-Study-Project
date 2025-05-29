plugins {
    kotlin("jvm")
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

dependencies {
    implementation(kotlin("stdlib"))
    
    javafx {
        version = "21"
        modules = listOf("javafx.controls", "javafx.fxml")
    }
}

application {
    mainClass.set("ClientApp")
    applicationDefaultJvmArgs = listOf(
        "-Dglass.win.uiScale=100%",                          // Фиксирует масштабирование для Windows
        "-Dprism.order=sw",                                  // Использует программный рендеринг
        "-Dkotlin.io.paths.allowWindowsUnixSeparators=true", // Для работы с путями длиннее 260 символов требуется
    )
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// Для Windows (чтобы не было проблем с JavaFX)
tasks.named<JavaExec>("run") {
    jvmArgs = listOf(
        "--add-exports=javafx.graphics/com.sun.javafx.scene=ALL-UNNAMED",
        "-Dprism.order=sw"
    )
}
