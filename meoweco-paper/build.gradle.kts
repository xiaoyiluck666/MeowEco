import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar

val paperApiVersion = "1.21.11-R0.1-SNAPSHOT"

plugins {
    id("com.gradleup.shadow") version "8.3.9"
}

configurations.named("compileClasspath") {
    attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
}

dependencies {
    implementation(project(":meoweco-core"))

    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    compileOnly("net.milkbowl.vault:VaultUnlocked:2.20.2") {
        isTransitive = false
    }
    compileOnly("me.clip:placeholderapi:2.11.6")

    implementation(platform("net.kyori:adventure-bom:4.26.1"))
    implementation("net.kyori:adventure-text-serializer-legacy")
    implementation("net.kyori:adventure-text-minimessage")

    implementation("com.zaxxer:HikariCP:5.1.0")
    compileOnly("org.xerial:sqlite-jdbc:3.46.0.0")
    implementation("com.mysql:mysql-connector-j:8.4.0")
    implementation("org.bstats:bstats-bukkit:3.1.0")

    testImplementation(project(":meoweco-core"))
    testCompileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    testImplementation("net.milkbowl.vault:VaultUnlocked:2.20.2") {
        isTransitive = false
    }
    testImplementation("com.zaxxer:HikariCP:5.1.0")
    testRuntimeOnly("org.xerial:sqlite-jdbc:3.46.0.0")
    testRuntimeOnly("org.slf4j:slf4j-nop:2.0.16")
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
    relocate("org.bstats", "com.xiaoyiluck.meoweco.libs.bstats")
}

tasks.named<Jar>("jar") {
    enabled = false
}

tasks.named("assemble") {
    dependsOn(tasks.named("shadowJar"))
}

val sqliteRegressionTest by tasks.registering(JavaExec::class) {
    description = "Runs self-contained SQLite database regression tests."
    group = "verification"

    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.xiaoyiluck.meoweco.database.SQLiteDatabaseRegressionTest")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

val migrationRegressionTest by tasks.registering(JavaExec::class) {
    description = "Runs migration parser regression tests."
    group = "verification"

    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.xiaoyiluck.meoweco.migration.MigrationServiceRegressionTest")
}

val vaultUnlockedV2RegressionTest by tasks.registering(JavaExec::class) {
    description = "Runs Classic Vault and VaultUnlocked v2 interoperability regression tests."
    group = "verification"

    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath + sourceSets["main"].compileClasspath
    mainClass.set("com.xiaoyiluck.meoweco.api.VaultUnlockedV2RegressionTest")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

val mysqlMultiInstanceRegressionTest by tasks.registering(JavaExec::class) {
    description = "Runs shared-MySQL multi-instance balance consistency regression tests."
    group = "verification"

    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.xiaoyiluck.meoweco.database.MySQLMultiInstanceRegressionTest")
}

tasks.named("test") {
    dependsOn(sqliteRegressionTest)
    dependsOn(migrationRegressionTest)
    dependsOn(vaultUnlockedV2RegressionTest)
}

tasks.withType<Test>().configureEach {
    failOnNoDiscoveredTests = false
}
