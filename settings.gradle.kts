pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // RuStore App Update SDK.
        maven("https://nexus-external.rustore.ru/repository/maven-rustore-exposed") {
            content { includeGroup("ru.rustore.sdk") }
        }
    }
}

rootProject.name = "ranepa-sync-android"
include(":app")
