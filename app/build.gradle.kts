plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.koyomi"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.koyomi"
        minSdk = 26
        targetSdk = 36
        // Como en Tateyoko: versionCode sube en uno con cada APK que se entrega; versionName es
        // 0.MENOR.PARCHE. La 1.0.0 es la de Play.
        versionCode = 18
        versionName = "0.16.0"
        manifestPlaceholders["appLabel"] = "Koyomi"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // La build de publicación tal cual (R8 y recorte de recursos) pero con otro id y la firma de
        // depuración: para el día a día, y para medir el rendimiento de verdad, que en depuración engaña
        create("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-beta"
            // Otro nombre, para distinguirla de la de depuración cuando conviven en el teléfono
            manifestPlaceholders["appLabel"] = "Koyomi β"
            matchingFallbacks += "release"
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions { unitTests.all { it.useJUnitPlatform() } }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// La beta para el día a día, con su versión en el nombre y sin pisar las anteriores:
// ./gradlew :app:betaApk  →  dist/koyomi-beta-<versión>.apk
tasks.register<Copy>("betaApk") {
    dependsOn("assembleStaging")
    from(layout.buildDirectory.dir("outputs/apk/staging")) { include("*.apk") }
    into(rootProject.layout.projectDirectory.dir("dist"))
    rename { "koyomi-beta-${android.defaultConfig.versionName}.apk" }
}

dependencies {
    implementation(project(":engine"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotest.assertions)
}
