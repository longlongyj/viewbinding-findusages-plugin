pluginManagement {
	repositories {
		// 当前环境对 Maven Central 的 HEAD 请求会返回 403，这里增加可用镜像作为优先回退源
		maven("https://maven.aliyun.com/repository/gradle-plugin")
		maven("https://maven.aliyun.com/repository/public")
		gradlePluginPortal()
		mavenCentral()
	}
}

rootProject.name = "viewbinding-findusages-plugin"
