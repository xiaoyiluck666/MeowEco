rootProject.name = "MeowEco"

pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "net.fabricmc.fabric-loom") {
                useModule("net.fabricmc:fabric-loom:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenLocal()
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/central")
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") {
            content {
                includeGroupByRegex("io\\.papermc(\\..*)?")
            }
        }
        maven("https://oss.sonatype.org/content/groups/public/") {
            content {
                includeGroupByRegex("org\\.spigotmc(\\..*)?")
                includeGroupByRegex("net\\.md-5(\\..*)?")
            }
        }
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") {
            content {
                includeGroup("me.clip")
            }
        }
        maven("https://jitpack.io") {
            content {
                includeGroupByRegex("com\\.github\\..*")
            }
        }
        maven("https://maven.fabricmc.net/") {
            content {
                includeGroupByRegex("net\\.fabricmc(\\..*)?")
            }
        }
    }
}

include(":meoweco-core")
include(":meoweco-paper")
include(":meoweco-fabric")
