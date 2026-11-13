pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    // Unity's exported library declares its own repositories; only relax the rule when it's present.
    repositoriesMode.set(if (file("unity/export/android/unityLibrary").exists()) RepositoriesMode.PREFER_SETTINGS else RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        if (file("unity/export/android/unityLibrary/libs").exists()) flatDir { dirs("unity/export/android/unityLibrary/libs") }
    }
}
rootProject.name = "Pipo"
include(":app")
// Pipo's Unity world (Unity as a Library). Optional: exported by unity/PipoWorld (Pipo > Export Android
// Library). Without it the app builds exactly as before and uses its own 3D/2.5D room.
if (file("unity/export/android/unityLibrary").exists()) {
    include(":unityLibrary")
    project(":unityLibrary").projectDir = file("unity/export/android/unityLibrary")
}
