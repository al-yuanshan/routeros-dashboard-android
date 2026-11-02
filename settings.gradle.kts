pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()  // 优先使用 Maven Central
        google()
        maven {
            setUrl("https://jitpack.io")
        }
    }
}

rootProject.name = "routeros-dashboard-android"
include(":core")
include(":app-routeros")
