androidApplication {
    namespace = "org.example.app"

    dependencies {
        // Minimal, no extra libraries required for this implementation.
        // Keeping project structure clean and compliant with Declarative Gradle rules.
        // Test dependencies (DCL allows only implementation within dependencies {})
        implementation("androidx.test:core:1.5.0")
        implementation("androidx.test:runner:1.5.2")
        implementation("androidx.test:rules:1.5.0")
        implementation("androidx.test.ext:junit:1.1.5")
        implementation("androidx.test.espresso:espresso-core:3.5.1")
    }
}
