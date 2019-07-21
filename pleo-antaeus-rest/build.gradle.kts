plugins {
    kotlin("jvm")
    kotlin("kapt")
}

kotlinProject()

dependencies {
    implementation(project(":pleo-antaeus-core"))
    implementation(project(":pleo-antaeus-models"))
    implementation("io.javalin:javalin:2.6.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.9.8")
    implementation("com.google.dagger:dagger:2.13")
    kapt("com.google.dagger:dagger-compiler:2.13")
}
