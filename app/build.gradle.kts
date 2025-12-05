plugins {
    java
    application
    id("org.openjfx.javafxplugin")
}

javafx {
    version = "21.0.1"
    modules = listOf("javafx.controls", "javafx.fxml")
}

application {
    mainClass.set("com.backtester.BacktesterApp")
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
