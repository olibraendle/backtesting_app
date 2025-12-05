plugins {
    java
}

dependencies {
    implementation(project(":common"))

    // CSV parsing
    implementation("com.univocity:univocity-parsers:2.9.1")
}
