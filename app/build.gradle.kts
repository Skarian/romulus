import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("dev.detekt")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.kapt")
}

fun gradleStringConfig(name: String, envName: String): String? =
    providers.gradleProperty(name).orNull?.takeIf(String::isNotBlank)
        ?: providers.environmentVariable(envName).orNull?.takeIf(String::isNotBlank)

fun gradleIntConfig(name: String, envName: String): Int? =
    gradleStringConfig(name, envName)?.toIntOrNull()

val configuredVersionCode = gradleIntConfig("romulusVersionCode", "ROMULUS_VERSION_CODE") ?: 1
val configuredVersionName = gradleStringConfig("romulusVersionName", "ROMULUS_VERSION_NAME") ?: "1.0"
val releaseSigningStoreFile = gradleStringConfig("romulusSigningStoreFile", "ROMULUS_SIGNING_STORE_FILE")
val releaseSigningStorePassword = gradleStringConfig("romulusSigningStorePassword", "ROMULUS_SIGNING_STORE_PASSWORD")
val releaseSigningKeyAlias = gradleStringConfig("romulusSigningKeyAlias", "ROMULUS_SIGNING_KEY_ALIAS")
val releaseSigningKeyPassword = gradleStringConfig("romulusSigningKeyPassword", "ROMULUS_SIGNING_KEY_PASSWORD")
val releaseSigningConfigured = listOf(
    releaseSigningStoreFile,
    releaseSigningStorePassword,
    releaseSigningKeyAlias,
    releaseSigningKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.romulus.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.romulus.mobile"
        minSdk = 33
        targetSdk = 36
        versionCode = configuredVersionCode
        versionName = configuredVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseSigningStoreFile!!)
                storePassword = releaseSigningStorePassword
                keyAlias = releaseSigningKeyAlias
                keyPassword = releaseSigningKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

val generatedSourceSchemaAssetsDir = layout.buildDirectory.dir("generated/source-schema-assets")
val syncSourceSchema by tasks.registering(Copy::class) {
    from(rootProject.file("docs/schema.json"))
    into(generatedSourceSchemaAssetsDir)
    rename { "source-schema.json" }
}

tasks.named("preBuild").configure {
    dependsOn(syncSourceSchema)
}

kapt {
    correctErrorTypes = true
}

val detektBaselineFile = rootProject.file("config/detekt/baseline.xml")

detekt {
    toolVersion = "2.0.0-alpha.1"
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
    ignoreFailures = false
    parallel = true
    basePath.set(rootDir)
    if (detektBaselineFile.exists()) {
        baseline = detektBaselineFile
    }
}

android.sourceSets.getByName("main").assets.srcDir(generatedSourceSchemaAssetsDir)

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    reports {
        checkstyle.required.set(true)
        html.required.set(true)
        sarif.required.set(true)
        markdown.required.set(false)
    }
}

dependencies {
    detektPlugins("dev.detekt:detekt-rules-ktlint-wrapper:2.0.0-alpha.1")
    detektPlugins("io.nlopez.compose.rules:detekt:0.5.0")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.1")
    implementation("androidx.work:work-runtime-ktx:2.10.2")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    kapt("androidx.room:room-compiler:2.8.4")
    kaptTest("androidx.room:room-compiler:2.8.4")
    kaptAndroidTest("androidx.room:room-compiler:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.networknt:json-schema-validator:2.0.1")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("com.github.omicronapps:7-Zip-JBinding-4Android:Release-16.02-2.03")
    val composeBom = platform("androidx.compose:compose-bom:2026.01.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core-ktx:1.7.0")
    testImplementation("androidx.room:room-testing:2.8.4")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
