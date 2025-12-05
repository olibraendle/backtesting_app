plugins {
    java
    id("org.openjfx.javafxplugin")
}

javafx {
    version = "21.0.1"
    modules = listOf("javafx.controls", "javafx.fxml")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":data"))
    implementation(project(":core"))
    implementation(project(":strategy"))
    implementation(project(":statistics"))

    // High-performance charts
    implementation("io.fair-acc:chartfx:11.3.0")

    // Enhanced controls
    implementation("org.controlsfx:controlsfx:11.2.0")
}
