rootProject.name = "MeowEco"

pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenLocal()
        maven("https://maven.aliyun.com/repository/public") {
            content {
                excludeGroupByRegex("net\\.kyori(\\..*)?")
            }
        }
        maven("https://maven.aliyun.com/repository/central") {
            content {
                excludeGroupByRegex("net\\.kyori(\\..*)?")
            }
        }
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") {
            content {
                includeGroupByRegex("io\\.papermc(\\..*)?")
                includeGroupByRegex("net\\.md-5(\\..*)?")
            }
        }
        maven("https://libraries.minecraft.net/") {
            content {
                includeGroupByRegex("com\\.mojang(\\..*)?")
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
    }
}

include(":meoweco-core")
include(":meoweco-paper")
