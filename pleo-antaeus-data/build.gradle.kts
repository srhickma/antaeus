plugins {
    kotlin("jvm")
    kotlin("kapt")
}

kotlinProject()

dataLibs()

dependencies {
    implementation(project(":pleo-antaeus-models"))
    implementation("com.google.dagger:dagger:2.13")
    kapt("com.google.dagger:dagger-compiler:2.13")
}
