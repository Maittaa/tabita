plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "org.maittaa.tabita"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.maittaa.tabita"
        minSdk = 28
        targetSdk = 35
        versionCode = 94
        versionName = "5.67"
    }

    signingConfigs {
        create("release") {
            // Пароли и путь к ключу — в keystore.properties (не попадает в git)
            val ks = java.util.Properties()
            val ksFile = rootProject.file("keystore.properties")
            if (ksFile.exists()) ksFile.inputStream().use { ks.load(it) }
            storeFile = file("../" + ks.getProperty("storeFile", "keystore/dnevnik.jks"))
            storePassword = ks.getProperty("storePassword", System.getenv("TABITA_STORE_PASSWORD") ?: "")
            keyAlias = ks.getProperty("keyAlias", "dnevnik")
            keyPassword = ks.getProperty("keyPassword", System.getenv("TABITA_KEY_PASSWORD") ?: "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("release")
        }
        // 5.32: копия для проверки «с нуля» — свой пакет и имя, ставится рядом с основной и не трогает её данные
        create("fresh") {
            initWith(getByName("release"))
            applicationIdSuffix = ".fresh"
            versionNameSuffix = "-fresh"
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { jvmToolchain(21) }
    buildFeatures { compose = true; buildConfig = true }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2025.08.00")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    // 5.23: тренировки (бывший Sink) — ViewModel и compose-обвязка жизненного цикла
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("androidx.biometric:biometric:1.1.0")
    // biometric тянет fragment 1.2.5, который падает на кодах запросов разрешений (>16 бит) — нужна новая версия
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}

android.testOptions.unitTests.isIncludeAndroidResources = true

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.robolectric:robolectric:4.15.1")
    testImplementation(platform("androidx.compose:compose-bom:2025.08.00"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
