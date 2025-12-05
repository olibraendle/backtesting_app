plugins {
    java
}

dependencies {
    implementation(project(":common"))
    implementation(project(":data"))
    implementation(project(":core"))
    implementation(project(":strategy"))

    // Statistics library
    implementation("org.apache.commons:commons-math3:3.6.1")
}
