plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val maimaidBackendUrl = providers.gradleProperty("MAIMAID_BACKEND_URL")
    .orElse("https://api.rhythmeta.org")
val maimaidBackendAuthUrl = providers.gradleProperty("MAIMAID_BACKEND_AUTH_URL")
    .orElse("https://maimaid.rhythmeta.org")
val maimaidStaticAssetsUrl = providers.gradleProperty("MAIMAID_STATIC_ASSETS_URL")
    .orElse("https://maimaid-assets.rhythmeta.org")
val maimaidModelAssetsUrl = providers.gradleProperty("MAIMAID_MODEL_ASSETS_URL")
    .orElse("https://models.rhythmeta.org")
val maimaidBuildNumber = providers.environmentVariable("MAIMAID_BUILD_NUMBER")
    .orElse(
        providers.exec {
            commandLine(
                "sh",
                rootProject.file("../scripts/build-number.sh").absolutePath,
            )
        }.standardOutput.asText.map(String::trim),
    )
    .map { value ->
        value.toIntOrNull()?.takeIf { it > 0 }
            ?: error("Invalid MAIMAID_BUILD_NUMBER: $value")
    }
val splitReleaseApks = providers.gradleProperty("MAIMAID_SPLIT_RELEASE_APKS")
    .map { it.toBoolean() }
    .orElse(false)
val releaseKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH")
val releaseKeystorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS")
val releaseKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD")
val releaseSigningConfigured = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { provider -> provider.orNull?.isNotBlank() == true }

android {
    namespace = "org.rhythmeta.maimaid"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.rhythmeta.maimaid"
        minSdk = 28
        targetSdk = 37
        versionCode = maimaidBuildNumber.get()
		versionName = "1.2.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "BACKEND_URL", "\"${maimaidBackendUrl.get()}\"")
        buildConfigField("String", "BACKEND_AUTH_URL", "\"${maimaidBackendAuthUrl.get()}\"")
        buildConfigField("String", "STATIC_ASSETS_URL", "\"${maimaidStaticAssetsUrl.get()}\"")
        buildConfigField("String", "MODEL_ASSETS_URL", "\"${maimaidModelAssetsUrl.get()}\"")
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseKeystorePath.get())
                storePassword = releaseKeystorePassword.get()
                keyAlias = releaseKeyAlias.get()
                keyPassword = releaseKeyPassword.get()
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    splits {
        abi {
            isEnable = splitReleaseApks.get()
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = false
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
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.onnxruntime.android)
    implementation(libs.backdrop)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.protobuf.javalite)
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
