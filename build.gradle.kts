/*
 * Copyright 2019-2021 Mamoe Technologies and contributors.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress("UnstableApiUsage", "UNUSED_VARIABLE", "NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

buildscript {
    repositories {
        mavenLocal()
        // maven(url = "https://mirrors.huaweicloud.com/repository/maven")
        mavenCentral()
        jcenter()
        google()
        maven(url = "https://dl.bintray.com/kotlin/kotlin-eap")
        maven(url = "https://kotlin.bintray.com/kotlinx")
    }

    dependencies {
        classpath("com.android.tools.build:gradle:${Versions.androidGradlePlugin}")
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:${Versions.atomicFU}")
        classpath("org.jetbrains.kotlinx:binary-compatibility-validator:${Versions.binaryValidator}")
    }
}

plugins {
    kotlin("jvm") // version Versions.kotlinCompiler
    kotlin("plugin.serialization") version Versions.kotlinCompiler
    id("org.jetbrains.dokka") version Versions.dokka
    id("net.mamoe.kotlin-jvm-blocking-bridge") version Versions.blockingBridge
    id("com.jfrog.bintray") // version Versions.bintray
    id("com.gradle.plugin-publish") version "0.12.0" apply false
}

// https://github.com/kotlin/binary-compatibility-validator
apply(plugin = "binary-compatibility-validator")

configure<kotlinx.validation.ApiValidationExtension> {
    allprojects.forEach { subproject ->
        ignoredProjects.add(subproject.name)
    }
    ignoredProjects.remove("binary-compatibility-validator")
    // Enable validator for module `binary-compatibility-validator` only.


    ignoredPackages.add("net.mamoe.mirai.internal")
    ignoredPackages.add("net.mamoe.mirai.console.internal")
    nonPublicMarkers.add("net.mamoe.mirai.MiraiInternalApi")
    nonPublicMarkers.add("net.mamoe.mirai.console.utils.ConsoleInternalApi")
    nonPublicMarkers.add("net.mamoe.mirai.console.utils.ConsoleExperimentalApi")
    nonPublicMarkers.add("net.mamoe.mirai.MiraiExperimentalApi")
}

tasks.register("publishMiraiCoreArtifactsToMavenLocal") {
    group = "mirai"
    dependsOn(
        project(":mirai-core-api").tasks.getByName("publishToMavenLocal"),
        project(":mirai-core-utils").tasks.getByName("publishToMavenLocal"),
        project(":mirai-core").tasks.getByName("publishToMavenLocal")
    )
}

allprojects {
    group = "net.mamoe"
    version = Versions.project

    repositories {
        // mavenLocal() // cheching issue cause compiler exception
        // maven(url = "https://mirrors.huaweicloud.com/repository/maven")
        jcenter()
        maven(url = "https://dl.bintray.com/kotlin/kotlin-eap")
        maven(url = "https://kotlin.bintray.com/kotlinx")
        google()
        mavenCentral()
        maven(url = "https://dl.bintray.com/karlatemp/misc")
    }

    afterEvaluate {
        configureJvmTarget()
        configureMppShadow()
        configureEncoding()
        configureKotlinTestSettings()
        configureKotlinCompilerSettings()
        configureKotlinExperimentalUsages()

        runCatching {
            blockingBridge {
                unitCoercion = net.mamoe.kjbb.compiler.UnitCoercion.COMPATIBILITY
            }
        }

        //  useIr()

        if (isKotlinJvmProject) {
            configureFlattenSourceSets()
        }
    }
}

subprojects {
    afterEvaluate {
        if (project.name == "mirai-core-api") configureDokka()
        if (project.name == "mirai-console") configureDokka()
    }
}

fun Project.configureDokka() {
    apply(plugin = "org.jetbrains.dokka")
    tasks {
        val dokkaHtml by getting(DokkaTask::class) {
            outputDirectory.set(buildDir.resolve("dokka"))
        }
        val dokkaGfm by getting(DokkaTask::class) {
            outputDirectory.set(buildDir.resolve("dokka-gfm"))
        }
    }
    tasks.withType<DokkaTask>().configureEach {
        dokkaSourceSets.configureEach {
            perPackageOption {
                matchingRegex.set("net\\.mamoe\\.mirai\\.*")
                skipDeprecated.set(true)
            }

            for (suppressedPackage in arrayOf(
                """net.mamoe.mirai.internal""",
                """net.mamoe.mirai.internal.message""",
                """net.mamoe.mirai.internal.network""",
                """net.mamoe.mirai.console.internal""",
                """net.mamoe.mirai.console.compiler.common"""
            )) {
                perPackageOption {
                    matchingRegex.set(suppressedPackage.replace(".", "\\."))
                    suppress.set(true)
                }
            }
        }
    }
}

fun Project.configureMppShadow() {
    val kotlin =
        runCatching {

            (this as ExtensionAware).extensions.getByName("kotlin") as? KotlinMultiplatformExtension
        }.getOrNull() ?: return

    if (project.configurations.findByName("jvmRuntimeClasspath") != null) {
        val shadowJvmJar by tasks.creating(ShadowJar::class) sd@{
            group = "mirai"
            archiveClassifier.set("-all")

            val compilations =
                kotlin.targets.filter { it.platformType == KotlinPlatformType.jvm }
                    .map { it.compilations["main"] }

            compilations.forEach {
                dependsOn(it.compileKotlinTask)
                from(it.output)
            }

            from(project.configurations.findByName("jvmRuntimeClasspath"))

            this.exclude { file ->
                file.name.endsWith(".sf", ignoreCase = true)
            }

            /*
            this.manifest {
                this.attributes(
                    "Manifest-Version" to 1,
                    "Implementation-Vendor" to "Mamoe Technologies",
                    "Implementation-Title" to this.name.toString(),
                    "Implementation-Version" to this.version.toString()
                )
            }*/
        }

    }

}
