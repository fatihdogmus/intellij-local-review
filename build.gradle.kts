import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.JavaExec
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.kotlin.plugin.serialization")
    jacoco
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.3.0"))
    testImplementation(platform("org.jetbrains.kotlin:kotlin-bom:2.3.0"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.valiktor:valiktor-core:0.12.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testImplementation("org.junit.platform:junit-platform-launcher:1.12.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testRuntimeOnly("junit:junit:4.13.2")

    intellijPlatform {
        intellijIdea("2026.1.1")
        bundledPlugin("Git4Idea")
        bundledPlugin("com.intellij.mcpServer")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.JUnit5)
    }
}

sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

val integrationTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.implementation.get())
    extendsFrom(configurations.runtimeOnly.get())
}

dependencies {
    "integrationTestImplementation"("org.junit.jupiter:junit-jupiter:5.12.2")
    "integrationTestImplementation"("org.junit.platform:junit-platform-launcher:1.12.2")
    "integrationTestImplementation"("org.opentest4j:opentest4j:1.3.0")
    "integrationTestImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    "integrationTestImplementation"("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    intellijPlatform {
        testFramework(TestFrameworkType.Starter, configurationName = "integrationTestImplementation")
    }
}

tasks.register<Test>("integrationTest") {
    group = "Verification"
    description = "Runs UI integration tests that need a real IDE process."
    useJUnitPlatform()
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    doFirst {
        val pluginVersion = providers.gradleProperty("version").get()
        val pluginZip = layout.buildDirectory.file("distributions/${project.name}-$pluginVersion.zip")
        systemProperty("path.to.build.plugin", pluginZip.get().asFile.absolutePath)
    }
    jvmArgs(
        "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
        "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED",
        "--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "-Dlocal.review.enable.mcp.by.default=false",
    )
    dependsOn("compileIntegrationTestKotlin", "buildPlugin")
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        jvmDefault = JvmDefaultMode.NO_COMPATIBILITY
    }
}

intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("version")
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    pluginVerification {
        ides {
            create(IntelliJPlatformType.IntellijIdea, "2026.1.1")
        }
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

tasks.named<Test>("test").configure {
    useJUnitPlatform()
    jvmArgs(
        "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
        "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED",
        "--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
    )
    extensions.configure(JacocoTaskExtension::class.java) {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(layout.buildDirectory.dir("instrumented/instrumentCode"))
    sourceDirectories.setFrom(files("src/main/kotlin"))
    executionData.setFrom(layout.buildDirectory.file("jacoco/test.exec"))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.named<JavaExec>("runIde") {
    jvmArgs("-Dlocal.review.enable.mcp.by.default=true")
}
