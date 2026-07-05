pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
	}
	plugins {
		id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
	}
}

plugins {
	id("org.gradle.toolchains.foojay-resolver-convention")
}

rootProject.name = "buildSrc"

dependencyResolutionManagement {
	versionCatalogs {
		create("libs") {
			from(files("../gradle/libs.versions.toml"))
		}
	}
}
