import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.intellij.platform") version "2.1.0"
    kotlin("jvm") version "2.1.21"
}

group = "com.dragold.plugin"
version = "1.2.4"

repositories {
    // 当前环境访问 Maven Central 会在 HEAD 请求阶段被拒绝，优先使用已验证可访问的镜像
    maven("https://maven.aliyun.com/repository/public")
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // 使用 IntelliJ IDEA Community 作为编译平台
        // 版本对应 Android Studio Panda 2025.3.1 的基础 IntelliJ 版本
        intellijIdeaCommunity("2025.1")

        // 捆绑插件
        bundledPlugin("com.intellij.java")
//        bundledPlugin("org.jetbrains.android")
//        plugin("org.jetbrains.android:251.25410.115")

        pluginVerifier()
        zipSigner()
        instrumentationTools()
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.dragold.plugin.viewbinding-findusages"
        name = "ViewBinding Find Usages"
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "233"   // Android Studio Iguana (2023.2) 及以上均可安装
            // untilBuild 不设置 = 不限制未来版本，新版 AS 发布后无需更新插件即可使用
        }}

    signing {
        // 发布到 Marketplace 时配置，本地调试可忽略
    }

    publishing {
        // 发布到 Marketplace 时配置
    }
}

kotlin {
    jvmToolchain(21)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.named("buildSearchableOptions") {
    // 当前插件没有 Settings/Configurable 页面，无需生成 searchable options
    enabled = false
}
