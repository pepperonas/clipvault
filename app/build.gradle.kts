import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "io.celox.clipvault"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.celox.clipvault"
        minSdk = 29
        targetSdk = 35
        versionCode = 11
        versionName = "3.6.0"
    }

    signingConfigs {
        create("release") {
            // CI: environment variables; Local: local.properties
            val envStoreFile = System.getenv("RELEASE_STORE_FILE")
            if (!envStoreFile.isNullOrEmpty()) {
                storeFile = file(envStoreFile)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            } else {
                val props = rootProject.file("local.properties")
                if (props.exists()) {
                    val localProps = Properties().apply { props.inputStream().use { load(it) } }
                    val sf = localProps.getProperty("RELEASE_STORE_FILE", "")
                    if (sf.isNotEmpty()) {
                        storeFile = file(sf)
                        storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD", "")
                        keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS", "")
                        keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD", "")
                    }
                }
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
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
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // SQLCipher (encrypted DB)
    implementation("net.zetetic:android-database-sqlcipher:4.5.4@aar")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // Biometric + Fragment + AppCompat
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("org.mockito:mockito-core:5.8.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
