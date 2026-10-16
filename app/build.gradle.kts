import java.text.SimpleDateFormat
import java.util.Date

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.inoviq.videodownloader.videosaver.storysaver"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }


    val appVersionCode = 1
    val appVersionName = "1.0.0"
    val currentDate: String = SimpleDateFormat("yyyy-MM-dd").format(Date())

    base {
        archivesName.set("XXVI Video Downloader-$appVersionName($appVersionCode)-$currentDate")
    }

    defaultConfig {
        applicationId = "com.inoviq.videodownloader.videosaver.storysaver"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
        buildConfig = true
    }


}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("io.coil-kt:coil:2.7.0")
    implementation("com.google.android.gms:play-services-ads:23.6.0")
}
