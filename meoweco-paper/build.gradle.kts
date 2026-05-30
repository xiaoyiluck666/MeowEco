import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion

val paperApiVersion = "26.1.2.build.64-stable"
val earliestPaper261ApiVersion = "26.1.1.build.8-alpha"
val paper261Compatibility by configurations.creating

plugins {
    id("com.gradleup.shadow") version "8.3.9"
}

configurations.named("compileClasspath") {
    attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
}

dependencies {
    implementation(project(":meoweco-core"))

    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    paper261Compatibility("io.papermc.paper:paper-api:$earliestPaper261ApiVersion")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnly("me.clip:placeholderapi:2.11.6")

    implementation(platform("net.kyori:adventure-bom:4.26.1"))
    implementation("net.kyori:adventure-text-serializer-legacy")
    implementation("net.kyori:adventure-text-minimessage")

    implementation("com.zaxxer:HikariCP:5.1.0")
    compileOnly("org.xerial:sqlite-jdbc:3.46.0.0")
    implementation("com.mysql:mysql-connector-j:8.4.0")
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.withType<ShadowJar> {
    archiveClassifier.set("")
    relocate("com.zaxxer.hikari", "com.xiaoyiluck.meoweco.libs.hikari")
    relocate("com.mysql", "com.xiaoyiluck.meoweco.libs.mysql")
}

tasks.named<Jar>("jar") {
    enabled = false
}

tasks.named("assemble") {
    dependsOn(tasks.named("shadowJar"))
}

val compilePaper261CompatJava by tasks.registering(JavaCompile::class) {
    description = "Compiles the Paper module against the earliest available Paper 26.1.x API to guard 26.1/26.1.1/26.1.2 compatibility."
    group = "verification"

    val mainSourceSet = project.the<SourceSetContainer>()["main"]

    dependsOn(project(":meoweco-core").tasks.named("classes"))

    source = mainSourceSet.allJava
    destinationDirectory.set(layout.buildDirectory.dir("tmp/paper-26-1-compat"))
    classpath = files(
        provider { mainSourceSet.compileClasspath.files.filter { !it.name.startsWith("paper-api-") } },
        paper261Compatibility
    )

    options.encoding = "UTF-8"
    options.release.set(25)
    javaCompiler.set(javaToolchains.compilerFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
}

tasks.named("check") {
    dependsOn(compilePaper261CompatJava)
}
