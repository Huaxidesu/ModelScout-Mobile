import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.modelscout.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.modelscout.mobile"
        minSdk = 26
        targetSdk = 35
        // 版本号唯一来源：gradle/libs.versions.toml 的 [versions]
        versionCode = libs.versions.appVersionCode.get().toInt()
        versionName = libs.versions.appVersionName.get()
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // 打开后 AppInfo 直接读 BuildConfig.VERSION_NAME，不再手写版本字符串
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
        )
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// 真实网关冒烟测试的凭证：优先读 gitignored 的 local.secrets.properties，其次读命令行 -P。
// 走文件是为了避免 Key 明文落进 Gradle 的 executionHistory 缓存。
val localSecrets = Properties().apply {
    val file = rootProject.file("local.secrets.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun secret(propName: String): String =
    providers.gradleProperty(propName).getOrElse(localSecrets.getProperty(propName, ""))

tasks.withType<Test>().configureEach {
    systemProperty("modelscout.real", secret("realGateway").ifBlank { "0" })
    systemProperty("modelscout.baseUrl", secret("realBaseUrl"))
    systemProperty("modelscout.apiKey", secret("realApiKey"))
    systemProperty("modelscout.keyhubBaseUrl", secret("keyhubBaseUrl"))
    systemProperty("modelscout.keyhubApiKey", secret("keyhubApiKey"))
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
