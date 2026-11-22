plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.zhishi.timetable"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.zhishi.timetable"
        minSdk = 26
        targetSdk = 35
        val run = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionCode = run
        versionName = "1.$run"
        // 测试机（模拟器）是 x86：传 -PallAbi 时不过滤
        if (!project.hasProperty("allAbi")) ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        // 预览版（非 main 分支的 CI 构建传 -Ppreview）：完全关掉应用内更新，不查、不提示、不下载正式版
        buildConfigField("boolean", "PREVIEW", if (project.hasProperty("preview")) "true" else "false")
    }
    signingConfigs {
        create("rel") {
            storeFile = file(System.getenv("KS_FILE") ?: "../zhishi.p12")
            storePassword = System.getenv("KS_PASS") ?: ""
            keyAlias = "zhishi"
            keyPassword = System.getenv("KS_PASS") ?: ""
            storeType = "pkcs12"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("rel")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources { excludes += listOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*") } }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }
}
dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
