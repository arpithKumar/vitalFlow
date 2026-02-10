plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hiltAndroid)
    alias(libs.plugins.googleServices)
}

android {
    namespace = "com.vitalflow.app"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.vitalflow.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.androidx.work.runtime)
    implementation(libs.firebase.firestore)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation(libs.androidx.work.runtime)

    implementation(libs.mpAndroidChart)

    implementation(libs.androidx.core.splashscreen)

    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler)

    // Navigation
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)

    // Hilt (Java Annotation Processor uses 'kapt' normally, but for Java only projects we use annotationProcessor)
    implementation(libs.hilt.android)
    annotationProcessor(libs.hilt.compiler) // Using annotationProcessor for Java

    // Room
    implementation(libs.androidx.room.runtime)
    annotationProcessor(libs.androidx.room.compiler)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)

    // Lottie
    implementation(libs.lottie)

    implementation(libs.androidx.health.connect)
    implementation(libs.guava)
    implementation(libs.kotlinx.coroutines.guava)

    // Core Library Desugaring (Fixes API 26 LocalDateTime error on older phones)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.google.ai.client.generativeai)
    implementation(libs.gson)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.commons.io)

    // RxJava
    // RxJava 3 & RxAndroid
    implementation(libs.rxjava)
    implementation(libs.rxandroid)
    // Room RxJava Support (CRITICAL)
    implementation(libs.androidx.room.rxjava3)

    implementation(libs.androidx.biometric)

    // Apache POI
    implementation(libs.poi)
    implementation(libs.poi.ooxml)

//    debugImplementation(libs.leakcanary.android)
}
