import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.intellij.platform") version "2.1.0"
    kotlin("jvm") version "2.1.21"
}

group = "com.dragold.plugin"
version = "1.2.5"

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
            untilBuild = provider { null } // 不限制上限，兼容所有未来版本
        }}

    signing {
        // 签名所需的三个值从环境变量读取，不硬编码到代码里
        // 发布前在命令行设置：
        //   $env:PLUGIN_PRIVATE_KEY    = (Get-Content certs\private.pem -Raw)
        //   $env:PLUGIN_CERTIFICATE    = (Get-Content certs\chain.crt -Raw)
        //   $env:PLUGIN_KEY_PASSPHRASE = ""   # 本次生成时未设密码，留空即可
        certificateChain = System.getenv("PLUGIN_CERTIFICATE")
        privateKey        = System.getenv("PLUGIN_PRIVATE_KEY")
        password          = System.getenv("PLUGIN_KEY_PASSPHRASE") ?: ""
    }

    publishing {
        // Marketplace Token：登录 https://plugins.jetbrains.com → 头像 → My Tokens → 生成
        // 发布前设置：$env:PLUGIN_PUBLISH_TOKEN = "perm:xxxxx"
        token = System.getenv("PLUGIN_PUBLISH_TOKEN")

        // 发布渠道：stable（正式）或 beta / eap（测试）
        channels = listOf(System.getenv("PLUGIN_PUBLISH_CHANNEL") ?: "stable")
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
