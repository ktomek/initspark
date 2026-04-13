import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.ajoberstar.grgit.Grgit

plugins {
    alias(libs.plugins.jetbrains.kotlin.multiplatform)
    alias(libs.plugins.dokka)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
    alias(libs.plugins.grgit)
    id("maven-publish")
}

group = "com.github.ktomek"
version = getGitTagVersion()
base.archivesName.set("initspark")

kotlin {
    jvmToolchain(17)
    
    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
            freeCompilerArgs.add("-XXLanguage:+ExplicitBackingFields")
        }
    }
    
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlin.stdlib)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.funktional)
        }
        
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        
        jvmTest.dependencies {
            implementation(libs.junit.jupiter)
            implementation(libs.mockk)
        }
    }
}

dependencies {
    detektPlugins(libs.detekt.formatting)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.register<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    from(tasks.dokkaHtml)
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        // Kotlin Multiplatform automatically sets up artifactIds like initspark-jvm, etc.
        // We ensure that if the base artifact is "lib", we change it to "initspark" just in case.
        if (artifactId.startsWith("lib")) {
            artifactId = artifactId.replaceFirst("lib", "initspark")
        }

        artifact(tasks.named("javadocJar"))

        pom {
            name.set("InitSpark")
            description.set("Startup orchestration for Kotlin-based apps")
            url.set("https://github.com/ktomek/initspark")
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

fun getGitTagVersion(): String = runCatching {
    Grgit
        .open(mapOf("dir" to project.rootDir))
        .tag
        .list()
        .maxByOrNull { it.commit.dateTime }
        ?.name
        ?.removePrefix("v")
        ?: "0.0.1-SNAPSHOT"
}
    .onFailure { e -> logger.error("Failed to read git tag", e) }
    .getOrElse { "0.0.1-SNAPSHOT" }