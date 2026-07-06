import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

val versionPropsFile = rootProject.file("app/version.properties")
val versionProps = Properties()

if (versionPropsFile.exists()) {
    versionProps.load(versionPropsFile.inputStream())
} else {
    versionProps["VERSION_MAJOR"] = "0"
    versionProps["VERSION_MINOR"] = "9"
    versionProps["VERSION_PATCH"] = "0"
    versionProps["VERSION_BUILD"] = "1"
}

val major = versionProps["VERSION_MAJOR"].toString().toInt()
val minor = versionProps["VERSION_MINOR"].toString().toInt()
val patch = versionProps["VERSION_PATCH"].toString().toInt()
val buildNum = versionProps["VERSION_BUILD"].toString().toInt()

val appVersionCode = major * 100000000 + minor * 1000000 + patch * 10000 + buildNum
val appVersionName = "$major.$minor.$patch.$buildNum"

android {
    namespace = "com.huilian.comfymobile"
    compileSdk = 34

    buildFeatures {
        buildConfig = true
        compose = true
    }

    defaultConfig {
        applicationId = "com.huilian.comfymobile"
        minSdk = 24
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField("String", "VERSION_NAME", "\"$appVersionName\"")
        buildConfigField("int", "VERSION_CODE", "$appVersionCode")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Network
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("io.ktor:ktor-client-core:2.3.11")
    implementation("io.ktor:ktor-client-okhttp:2.3.11")
    implementation("io.ktor:ktor-client-websockets:2.3.11")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.11")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.11")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.whenTaskAdded {
    if (name == "assembleDebug" || name == "assembleRelease") {
        doLast {
            val buildType = if (name == "assembleDebug") "debug" else "release"
            val sourceApk = layout.buildDirectory.file("outputs/apk/$buildType/app-$buildType.apk").get().asFile

            if (sourceApk.exists()) {
                val destDir = rootProject.file("apks")
                destDir.mkdirs()
                val destApk = File(destDir, "绘联-$appVersionName.apk")
                sourceApk.copyTo(destApk, overwrite = false)
                println("===== APK archived: ${destApk.name} =====")
            }

            val props = Properties()
            props.load(versionPropsFile.inputStream())
            val currentBuild = props["VERSION_BUILD"].toString().toInt()
            val newBuild = currentBuild + 1
            props["VERSION_BUILD"] = newBuild.toString()
            props.store(versionPropsFile.outputStream(), null)
            val vMajor = props["VERSION_MAJOR"].toString()
            val vMinor = props["VERSION_MINOR"].toString()
            val vPatch = props["VERSION_PATCH"].toString()
            println("===== Version bumped to $vMajor.$vMinor.$vPatch.$newBuild =====")
        }
    }
}
