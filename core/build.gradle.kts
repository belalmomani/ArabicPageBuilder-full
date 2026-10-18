plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // kotlin("test") يختار JUnit 4 تلقائيًا مع مهمة test الافتراضية.
    testImplementation(kotlin("test"))
}
