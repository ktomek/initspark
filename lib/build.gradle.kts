import com.vanniktech.maven.publish.SonatypeHost
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
    alias(libs.plugins.vanniktech.maven.publish)
}

group = "com.github.ktomek"
version = project.findProperty("publishVersion") as String? ?: getGitTagVersion()
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

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    coordinates("com.github.ktomek", "initspark", version.toString())

    pom {
        name.set("InitSpark")
        description.set("Startup orchestration for Kotlin-based apps")
        url.set("https://github.com/ktomek/initspark")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("ktomek")
                name.set("ktomek")
                url.set("https://github.com/ktomek")
            }
        }
        scm {
            url.set("https://github.com/ktomek/initspark")
            connection.set("scm:git:git://github.com/ktomek/initspark.git")
            developerConnection.set("scm:git:ssh://git@github.com/ktomek/initspark.git")
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