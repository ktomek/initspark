import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.dokka)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
    id("maven-publish")
}
group = "com.github.ktomek"
version = "1.0.0"
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
    jvmToolchain(17)

}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.funktional)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    detektPlugins(libs.detekt.formatting)
}

tasks.register<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    from(tasks.dokkaHtml)
}

// Create a JAR of the source files
val sourceJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(tasks.named("sourceJar"))
            artifact(tasks.named("javadocJar"))

            pom {
                name.set("InitSpark")
                description.set("Startup orchestration for Kotlin-based apps")
                url.set("https://github.com/ktomek/initspark")
                artifactId = "initspark"
            }
        }
    }
}

// Kotlin DSL
tasks.withType<Detekt>().configureEach {
    jvmTarget = "1.8"
}
tasks.withType<DetektCreateBaselineTask>().configureEach {
    jvmTarget = "1.8"
}

tasks.named("check").configure {
    this.setDependsOn(
        this.dependsOn.filterNot {
            it is TaskProvider<*> && it.name == "detekt"
        }
    )
}

detekt {
    toolVersion = "1.23.8"
    config.setFrom(file("../config/detekt-config.yml"))
    buildUponDefaultConfig = true
    autoCorrect = true
}
