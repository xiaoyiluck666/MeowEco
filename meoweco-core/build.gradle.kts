import org.gradle.api.tasks.testing.Test

plugins {
    `java-library`
}

dependencies {
    // core currently keeps the cross-platform business/domain layer placeholder.
}

val coreEconomyRegressionTest by tasks.registering(JavaExec::class) {
    description = "Runs self-contained core economy regression tests."
    group = "verification"

    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.xiaoyiluck.meoweco.service.CoreEconomyRegressionTest")
}

tasks.named("test") {
    dependsOn(coreEconomyRegressionTest)
}

tasks.withType<Test>().configureEach {
    failOnNoDiscoveredTests = false
}
