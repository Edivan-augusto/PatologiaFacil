import java.util.Properties

val localPropsFile = rootProject.file("local.properties")
val localProps = Properties().apply { if (localPropsFile.exists()) localPropsFile.inputStream().use { load(it) } }
val openAiPropsFile = rootProject.file("openai.properties")
val openAiProps = Properties().apply { if (openAiPropsFile.exists()) openAiPropsFile.inputStream().use { load(it) } }
val openAiKey = sequenceOf(
    localProps.getProperty("openai.apiKey"),
    openAiProps.getProperty("openai.apiKey"),
    System.getenv("OPENAI_API_KEY")
).firstOrNull { !it.isNullOrBlank() } ?: ""
val openAiKeyEscaped = openAiKey.replace("\"", "\\\"")

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "com.hitsu.patologiafacil"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hitsu.patologiafacil"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "OPENAI_API_KEY", "\"$openAiKeyEscaped\"")
    }

    // Signing configuration: uses release keystore from local.properties/env if provided,
    // otherwise falls back to the debug keystore for convenience.
    signingConfigs {
        create("release") {
            val storeFileProp = sequenceOf(
                localProps.getProperty("signing.storeFile"),
                System.getenv("SIGNING_STORE_FILE")
            ).firstOrNull { !it.isNullOrBlank() }

            val storePasswordProp = sequenceOf(
                localProps.getProperty("signing.storePassword"),
                System.getenv("SIGNING_STORE_PASSWORD")
            ).firstOrNull { !it.isNullOrBlank() } ?: "android"

            val keyAliasProp = sequenceOf(
                localProps.getProperty("signing.keyAlias"),
                System.getenv("SIGNING_KEY_ALIAS")
            ).firstOrNull { !it.isNullOrBlank() } ?: "androiddebugkey"

            val keyPasswordProp = sequenceOf(
                localProps.getProperty("signing.keyPassword"),
                System.getenv("SIGNING_KEY_PASSWORD")
            ).firstOrNull { !it.isNullOrBlank() } ?: storePasswordProp

            val home = System.getProperty("user.home")
            val defaultDebugKeystore = "$home/.android/debug.keystore"

            storeFile = file(storeFileProp ?: defaultDebugKeystore)
            storePassword = storePasswordProp
            keyAlias = keyAliasProp
            keyPassword = keyPasswordProp
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        getByName("debug") { isMinifyEnabled = false }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "17"
    }
}

// KSP está habilitado; nenhuma configuração extra necessária para Room

dependencies {
    val camerax = "1.3.4"
    val lifecycleVer = "2.8.4"
    val navVer = "2.7.7"
    val roomVer = "2.6.1"
    val coroutines = "1.8.1"
    val coilVer = "2.6.0"

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVer")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVer")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycleVer")

    implementation("androidx.navigation:navigation-fragment-ktx:$navVer")
    implementation("androidx.navigation:navigation-ui-ktx:$navVer")

    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    implementation("androidx.activity:activity-ktx:1.9.2")

    implementation("androidx.room:room-runtime:$roomVer")
    implementation("androidx.room:room-ktx:$roomVer")
    ksp("androidx.room:room-compiler:$roomVer")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutines")

    implementation("io.coil-kt:coil:$coilVer")

    implementation("com.google.code.gson:gson:2.10.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}



