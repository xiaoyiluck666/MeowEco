import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile

group = "com.xiaoyiluck"
version = "26.10.1"

subprojects {
    apply(plugin = "java")
    group = rootProject.group
    version = rootProject.version

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    configurations.configureEach {
        resolutionStrategy.cacheChangingModulesFor(12, "hours")
        resolutionStrategy.cacheDynamicVersionsFor(12, "hours")
    }
}

project(":meoweco-paper") {
    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(25)
    }
}
