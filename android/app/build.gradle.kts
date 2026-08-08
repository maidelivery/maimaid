import java.io.FileInputStream
import java.util.Properties

plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.ksp)
}

ksp {
	arg("room.schemaLocation", "$projectDir/schemas")
	arg("room.generateKotlin", "true")
}

android {
	namespace = "net.krtl.maimaid"
	compileSdk {
		version = release(36) {
			minorApiLevel = 1
		}
	}

    signingConfigs {
        create("release") {
            val propertiesFile = file("$rootDir/local.properties")
            if (!propertiesFile.exists()) {
                return@create
            }

            val properties = Properties().apply {
                load(FileInputStream(propertiesFile))
            }

            storeFile = file(properties["storeFile"].toString())
            storePassword = properties["storePassword"].toString()
            keyAlias = properties["keyAlias"].toString()
            keyPassword = properties["keyPassword"].toString()

            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

	defaultConfig {
		applicationId = "net.krtl.maimaid"
        minSdk = 33
        targetSdk = 36
		versionCode = 2
		versionName = "1.0"
		val backendUrl = providers.gradleProperty("BACKEND_URL").orNull ?: ""
		val backendAuthUrl = providers.gradleProperty("BACKEND_AUTH_URL").orNull ?: ""
		buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")
		buildConfigField("String", "BACKEND_AUTH_URL", "\"$backendAuthUrl\"")

		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}


	buildTypes {
		debug {
			isDebuggable = true
			applicationIdSuffix = ".debug"
			versionNameSuffix = "-debug"
			ndk {
				//noinspection ChromeOsAbiSupport
				abiFilters += setOf("arm64-v8a")
			}
		}

		release {
			isDebuggable = false
			isMinifyEnabled = false
			//noinspection NotShrinkingResources
			isShrinkResources = false
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
			signingConfig = signingConfigs.getByName("release")
			ndk {
				//noinspection ChromeOsAbiSupport
				abiFilters += setOf("arm64-v8a")
			}
		}

		create("snapshot") {
			initWith(getByName("release"))
			applicationIdSuffix = ".snapshot"
			versionNameSuffix = " Snapshot 2"
			matchingFallbacks += listOf("release")
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	buildFeatures {
		compose = true
		buildConfig = true
	}

    sourceSets {
        getByName("androidTest") {
            assets.srcDir("$rootDir/mairesult")
        }
    }

	packaging {
		resources {
			excludes += "/META-INF/{AL2.0,LGPL2.1}"
		}
	}
}

// Pin the compilation JDK so the build does not depend on the developer's JAVA_HOME.
// AGP's androidJdkImage transform runs jlink, and GraalVM's jlink cannot process the
// Android platform's core-for-system-modules.jar, so a GraalVM JAVA_HOME breaks the build.
kotlin {
	jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.annotation)
	implementation(libs.androidx.appcompat)
	implementation(libs.androidx.browser)
	implementation(libs.androidx.camera.camera2)
	implementation(libs.androidx.camera.lifecycle)
	implementation(libs.androidx.camera.view)
	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.core.splashscreen)
	implementation(libs.androidx.datastore.preferences)
	implementation(libs.androidx.lifecycle.runtime.ktx)
	implementation(libs.androidx.lifecycle.runtime.compose)
	implementation(libs.androidx.lifecycle.viewmodel.compose)
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.navigation.compose)
	implementation(libs.androidx.room.runtime)
	implementation(libs.androidx.room.ktx)
	ksp(libs.androidx.room.compiler)
	implementation(libs.androidx.work.runtime.ktx)
	implementation(libs.kotlinx.serialization.json)
	implementation(libs.retrofit)
	implementation(libs.retrofit.kotlinx.serialization)
	implementation(libs.snakeyaml)
	implementation(libs.okhttp)
	implementation(libs.okhttp.logging)
	implementation(libs.coil.compose)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.mlkit.text.recognition.japanese)
    implementation(libs.tflite.runtime)
	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.compose.material.icons.extended)
	implementation(libs.androidx.compose.ui)
	implementation(libs.androidx.compose.ui.graphics)
	implementation(libs.androidx.compose.ui.tooling.preview)
	implementation(libs.androidx.compose.material3)
	testImplementation(libs.junit)
	testImplementation(libs.truth)
	testImplementation(libs.kotlinx.coroutines.test)
	androidTestImplementation(libs.androidx.junit)
	androidTestImplementation(libs.androidx.espresso.core)
	androidTestImplementation(libs.androidx.room.testing)
	androidTestImplementation(platform(libs.androidx.compose.bom))
	androidTestImplementation(libs.androidx.compose.ui.test.junit4)
	debugImplementation(libs.androidx.compose.ui.tooling)
	debugImplementation(libs.androidx.compose.ui.test.manifest)
}
