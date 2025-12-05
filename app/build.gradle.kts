plugins {
    java
    application
    id("org.openjfx.javafxplugin")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

javafx {
    version = "21.0.1"
    modules = listOf("javafx.controls", "javafx.fxml")
}

application {
    mainClass.set("com.backtester.Launcher")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":data"))
    implementation(project(":core"))
    implementation(project(":strategy"))
    implementation(project(":statistics"))
    implementation(project(":ui"))

    // Logging implementation
    runtimeOnly("ch.qos.logback:logback-classic:1.4.14")
}

tasks.named<JavaExec>("run") {
    jvmArgs = listOf(
        "--add-opens", "javafx.graphics/javafx.scene=ALL-UNNAMED",
        "--add-opens", "javafx.base/com.sun.javafx.event=ALL-UNNAMED"
    )
}

// Shadow JAR configuration for fat JAR
tasks.shadowJar {
    archiveBaseName.set("backtester")
    archiveClassifier.set("")
    archiveVersion.set("")

    manifest {
        attributes(
            "Main-Class" to "com.backtester.Launcher",
            "Implementation-Title" to "Backtester Research Terminal",
            "Implementation-Version" to project.version
        )
    }

    // Merge service files (important for SLF4J and other service loaders)
    mergeServiceFiles()
}

// jpackage task for native installer
tasks.register<Exec>("jpackage") {
    dependsOn("shadowJar")

    val jpackageDir = layout.buildDirectory.dir("jpackage").get().asFile
    val inputDir = layout.buildDirectory.dir("libs").get().asFile

    doFirst {
        jpackageDir.mkdirs()
    }

    workingDir = projectDir

    val os = System.getProperty("os.name").lowercase()
    val installerType = when {
        os.contains("win") -> "exe"
        os.contains("mac") -> "dmg"
        else -> "deb"
    }

    commandLine(
        "jpackage",
        "--input", inputDir.absolutePath,
        "--dest", jpackageDir.absolutePath,
        "--main-jar", "backtester.jar",
        "--name", "BacktesterApp",
        "--app-version", "1.0.0",
        "--vendor", "Backtester",
        "--description", "Trading Strategy Backtesting Research Terminal",
        "--type", installerType,
        // Windows-specific options
        "--win-menu",
        "--win-shortcut",
        "--win-dir-chooser",
        // JVM options for JavaFX
        "--java-options", "--add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED",
        "--java-options", "--add-opens=javafx.base/com.sun.javafx.event=ALL-UNNAMED"
    )
}
