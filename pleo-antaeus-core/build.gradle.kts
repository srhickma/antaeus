plugins {
    kotlin("jvm")
    kotlin("kapt")
}

kotlinProject()

dataLibs()

dependencies {
    implementation(project(":pleo-antaeus-data"))
    compile(project(":pleo-antaeus-models"))
    compile("com.github.shyiko.skedule:skedule:0.4.0")
    implementation("com.google.dagger:dagger:2.13")
    kapt("com.google.dagger:dagger-compiler:2.13")
    kaptTest("com.google.dagger:dagger-compiler:2.13")
}
