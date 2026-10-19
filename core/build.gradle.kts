plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    // kotlin("test") يختار JUnit 4 تلقائيًا مع مهمة test الافتراضية.
    testImplementation(kotlin("test"))
}
