import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 正式签名优先从项目根目录 keystore.properties（不提交 Git）或环境变量读取
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use(::load)
}
val releaseStoreFilePath = keystoreProps.getProperty("storeFile") ?: System.getenv("RELEASE_STORE_FILE")
val releaseStorePassword = keystoreProps.getProperty("storePassword") ?: System.getenv("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = keystoreProps.getProperty("keyAlias") ?: System.getenv("RELEASE_KEY_ALIAS")
val releaseKeyPassword = keystoreProps.getProperty("keyPassword") ?: System.getenv("RELEASE_KEY_PASSWORD")
val hasReleaseSigning = !releaseStoreFilePath.isNullOrBlank() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank() &&
    rootProject.file(releaseStoreFilePath).isFile

android {
    namespace = "cn.xmeverylucky.pane"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "cn.xmeverylucky.pane"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    flavorDimensions += "region"
    productFlavors {
        create("cn") {
            dimension = "region"
            // 大陆渠道：保留国产 ROM 电池优化引导
            buildConfigField("boolean", "SHOW_BATTERY_GUIDE", "true")
        }
        create("global") {
            dimension = "region"
            // 海外/Google Play 渠道：剔除 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS（见 src/global/AndroidManifest.xml）
            buildConfigField("boolean", "SHOW_BATTERY_GUIDE", "false")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 未配置 keystore.properties 时暂退回 debug 签名以便本地验证构建；正式上架前务必配置 release 签名
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kyant.backdrop)
    implementation(libs.kyant.shapes)
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
