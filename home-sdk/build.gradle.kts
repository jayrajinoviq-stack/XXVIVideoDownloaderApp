 plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
}

buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.10")
    }
}

android {
    namespace = "com.design.home"
    compileSdk = project.libs.versions.app.build.compileSDKVersion.get().toInt()

    defaultConfig {
        minSdk = project.libs.versions.app.build.minimumSDK.get().toInt()
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    sourceSets {
        getByName("main").java.directories.add("src/main/kotlin")
    }

    compileOptions {
        val currentJavaVersionFromLibs = JavaVersion.valueOf(libs.versions.app.build.javaVersion.get())
        sourceCompatibility = currentJavaVersionFromLibs
        targetCompatibility = currentJavaVersionFromLibs
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions.jvmTarget.set(
            org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(project.libs.versions.app.build.kotlinJVMTarget.get())
        )
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = true
        warningsAsErrors = false
        baseline = file("lint-baseline.xml")
        lintConfig = rootProject.file("lint.xml")
    }
}

detekt {
    baseline = file("detekt-baseline.xml")
    config.setFrom("$rootDir/detekt.yml")
    buildUponDefaultConfig = true
    allRules = false
}

dependencies {
//    api(libs.fossify.commons)
    implementation(project(":commons"))
    api(libs.bundles.room)
    ksp(libs.androidx.room.compiler)
    detektPlugins(libs.compose.detekt)
}
