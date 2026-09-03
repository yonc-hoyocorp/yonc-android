import java.util.Properties

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// The YOnC SDK is served from a private Maven repository. Put the read-only credentials you
// received from YOnC into local.properties (yonc.repo.user / yonc.repo.token) or into the
// environment (YONC_REPO_USER / YONC_REPO_TOKEN). Every other dependency resolves from Google's
// repository and Maven Central — the SDK pulls nothing from anywhere else.
val localProps = Properties().apply {
    val file = file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun secret(property: String, env: String): String =
    System.getenv(env) ?: localProps.getProperty(property) ?: ""

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            name = "yonc"
            url = uri("https://ymm.jfrog.io/artifactory/android-yonc-gradle-release")
            credentials {
                username = secret("yonc.repo.user", "YONC_REPO_USER")
                password = secret("yonc.repo.token", "YONC_REPO_TOKEN")
            }
            // Only the SDK's own coordinates come from here.
            content { includeGroup("dev.yonc") }
        }
    }
}

rootProject.name = "yonc-android-sample"
include(":app")
