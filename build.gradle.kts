plugins {
    kotlin("jvm") version "2.1.20"
}

group = "org.organicprogramming"
version = "0.1.0"



repositories {
    mavenCentral()
}

dependencies {
    implementation("org.yaml:snakeyaml:2.2")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
