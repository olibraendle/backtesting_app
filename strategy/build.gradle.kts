plugins {
    java
}

dependencies {
    implementation(project(":common"))
    implementation(project(":data"))

    // ONNX Runtime for ML model inference
    implementation("com.microsoft.onnxruntime:onnxruntime:1.16.3")
}
