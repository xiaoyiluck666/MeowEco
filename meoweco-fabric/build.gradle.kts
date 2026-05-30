plugins {
    id("net.fabricmc.fabric-loom") version "1.15.0-alpha.25"
}

dependencies {
    implementation(project(":meoweco-core"))

    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${property("fabric_loader_build_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")

    implementation("org.yaml:snakeyaml:2.2")
    runtimeOnly("org.xerial:sqlite-jdbc:3.46.0.0")
    runtimeOnly("com.mysql:mysql-connector-j:8.4.0")
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version,
        "fabric_loader_min_version" to project.property("fabric_loader_min_version")
    )
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("fabric.mod.json") {
        expand(props)
    }
}
