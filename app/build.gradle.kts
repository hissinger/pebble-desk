plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.woody.pebblehome"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.woody.pebblehome"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 개인 기기 사이드로딩용이라 디버그 키로 서명한다.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // 빌드 결과 APK 이름에 앱 이름과 버전을 넣는다. 예: PebbleHome-1.0.0-release.apk
    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "PebbleHome-${versionName}-${buildType.name}.apk"
        }
    }

    lint {
        disable += "QueryAllPackagesPermission"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
