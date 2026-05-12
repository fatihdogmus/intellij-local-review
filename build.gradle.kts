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

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs(
        "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
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
    jvmArgs("-Didea.mcp.server.force.port=64342")
}
