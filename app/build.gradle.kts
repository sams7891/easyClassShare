import groovy.json.JsonSlurper

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Online classes use Firebase. Its settings come from app/google-services.json
// (downloaded from the Firebase console, see README). Without that file the app
// still builds and works, just without the online class mode.
val appPackage = "com.easyclassshare.app"
val firebaseConfig: Map<String, String> = run {
    val jsonFile = file("google-services.json")
    if (!jsonFile.exists()) {
        logger.warn("app/google-services.json not found: online classes are disabled in this build.")
        return@run emptyMap()
    }
    @Suppress("UNCHECKED_CAST")
    val json = JsonSlurper().parse(jsonFile) as Map<String, Any?>
    @Suppress("UNCHECKED_CAST")
    val project = json["project_info"] as Map<String, Any?>
    @Suppress("UNCHECKED_CAST")
    val clients = json["client"] as List<Map<String, Any?>>
    @Suppress("UNCHECKED_CAST")
    val client = clients.firstOrNull { c ->
        val info = c["client_info"] as Map<String, Any?>
        val android = info["android_client_info"] as Map<String, Any?>
        android["package_name"] == appPackage
    } ?: error("google-services.json has no Android app with package $appPackage")
    @Suppress("UNCHECKED_CAST")
    val clientInfo = client["client_info"] as Map<String, Any?>
    @Suppress("UNCHECKED_CAST")
    val apiKeys = client["api_key"] as List<Map<String, Any?>>
    val databaseUrl = (project["firebase_url"] as String?).orEmpty()
    if (databaseUrl.isEmpty()) {
        logger.warn("google-services.json has no database URL: create the Realtime Database first, then download the file again.")
    }
    mapOf(
        "apiKey" to apiKeys.first()["current_key"] as String,
        "appId" to clientInfo["mobilesdk_app_id"] as String,
        "projectId" to project["project_id"] as String,
        "databaseUrl" to databaseUrl,
    )
}

android {
    namespace = appPackage
    compileSdk = 34

    defaultConfig {
        applicationId = appPackage
        minSdk = 26
        targetSdk = 34
        // Bump both of these for every release (see CHANGELOG.md).
        versionCode = 6
        versionName = "1.3.0-beta.3"

        buildConfigField("String", "FIREBASE_API_KEY", "\"${firebaseConfig["apiKey"].orEmpty()}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${firebaseConfig["appId"].orEmpty()}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${firebaseConfig["projectId"].orEmpty()}\"")
        buildConfigField("String", "FIREBASE_DATABASE_URL", "\"${firebaseConfig["databaseUrl"].orEmpty()}\"")
    }

    // Release signing key is supplied by CI (GitHub secrets) through env vars.
    // Without it (e.g. local builds) release APKs fall back to the debug key.
    val releaseKeystore = System.getenv("ANDROID_KEYSTORE_PATH")
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storeType = "pkcs12"
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    // Online classes (Firebase Realtime Database + anonymous sign-in)
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    testImplementation("junit:junit:4.13.2")
    // Real org.json implementation for JVM unit tests (Android's is a stub there).
    testImplementation("org.json:json:20240303")
}
