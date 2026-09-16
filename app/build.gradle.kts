import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.helpmeread.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.helpmeread.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
        viewBinding = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.5"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.1")
    
    // Compose
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core:1.6.0")
    implementation("androidx.compose.material:material-icons-extended:1.6.0")
    
    // Kotlin Coroutines for Play Services (for Task.await())
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    
    // CameraX
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    
    // ML Kit - Text Recognition
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.0")
    implementation("com.google.mlkit:text-recognition-japanese:16.0.0")
    implementation("com.google.mlkit:text-recognition-korean:16.0.0")
    
    // Tesseract for Arabic-script OCR (ML Kit has no Arabic recognizer)
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    
    // ML Kit - Document Scanner (edge detection + crop, requires Play Services)
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")
    
    // ML Kit - Translation
    implementation("com.google.mlkit:translate:17.0.2")
    
    // ML Kit - Language Identification
    implementation("com.google.mlkit:language-id:17.0.4")
    
    // EXIF orientation for captured photos
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    
    // Accompanist permissions
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")
    
    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
