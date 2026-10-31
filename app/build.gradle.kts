plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// ভার্সন: GitHub Release ট্যাগ (যেমন v1.3) থেকে নেওয়া হয়, তাই App info-তে সবসময় রিলিজের ভার্সনই দেখায়।
// ট্যাগ ছাড়া (লোকাল/ডিবাগ) বিল্ডে নিচের baseVersion ব্যবহার হয় — নতুন রিলিজের আগে এটা বদলে রাখুন।
val baseVersion = "1.2"
val tagName: String? = if (System.getenv("GITHUB_REF_TYPE") == "tag") System.getenv("GITHUB_REF_NAME") else null
val appVersion: String = tagName?.removePrefix("v")?.takeIf { Regex("""\d+(\.\d+){0,2}""").matches(it) } ?: baseVersion
fun versionCodeOf(v: String): Int {
    val p = v.split(".").map { it.toInt() }
    return p.getOrElse(0) { 0 } * 10000 + p.getOrElse(1) { 0 } * 100 + p.getOrElse(2) { 0 }
}
// আপডেট খোঁজার রিপোজিটরি ("owner/repo"): GitHub Actions-এ নিজে সেট হয়; লোকালি -PupdateRepo=owner/repo দিন
val updateRepo: String = System.getenv("GITHUB_REPOSITORY") ?: (project.findProperty("updateRepo") as String?) ?: ""

android {
    namespace = "com.tubex.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tubex.app"
        minSdk = 24
        targetSdk = 34
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
    }

    // রিলিজ সাইনিং: কীস্টোর পরিবেশ ভেরিয়েবল থেকে আসে (নিচে .github/workflows/release.yml দেখুন)।
    // কীস্টোর না থাকলে রিলিজ APK আনসাইনড বানানো হয়।
    val keystorePath: String? = System.getenv("KEYSTORE_PATH")
    signingConfigs {
        if (keystorePath != null && file(keystorePath).exists()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true   // NewPipe Extractor-এর জন্য বাধ্যতামূলক
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    ksp { arg("room.schemaLocation", "$projectDir/schemas") }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }   // Kotlin 1.9.25-এর জন্য

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES", "META-INF/INDEX.LIST", "META-INF/LICENSE*", "META-INF/NOTICE*"
        )
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.0.4")

    // YouTube ঘন ঘন বদলায়: ভিডিও না চললে প্রথমে এই ভার্সন আপডেট করুন
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    val media3 = "1.4.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-session:$media3")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Chromecast (Google Play Services লাগে)
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0")
    implementation("androidx.mediarouter:mediarouter:1.7.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-splashscreen:1.0.1")

    testImplementation("junit:junit:4.13.2")
}
