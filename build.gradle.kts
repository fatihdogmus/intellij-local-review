import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.JavaExec

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.3.0"))
    testImplementation(platform("org.jetbrains.kotlin:kotlin-bom:2.3.0"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.valiktor:valiktor-core:0.12.0")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
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
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs(
        "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
        "--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
    )
}

tasks.named<JavaExec>("runIde") {
    jvmArgs("-Dlocal.review.enable.mcp.by.default=true")
}
