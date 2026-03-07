pluginManagement {
    repositories {
        // 阿里云镜像放在最前
        maven { url = java.net.URI("https://maven.aliyun.com/repository/public") }
        maven { url = java.net.URI("https://maven.aliyun.com/repository/google") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 🟢 关键：阿里云镜像必须放在最前面，优先去这里找
        maven { url = java.net.URI("https://maven.aliyun.com/repository/public") }
        maven { url = java.net.URI("https://maven.aliyun.com/repository/google") }
        maven { url = java.net.URI("https://jitpack.io") }

        google()
        mavenCentral()
    }
}

rootProject.name = "yuanassist"
include(":app")