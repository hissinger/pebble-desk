plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.woody.pebbledesk"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.woody.pebbledesk"
        minSdk = 28
        targetSdk = 34
        versionCode = 5
        versionName = "1.2.1"
    }

    buildTypes {
        release {
            // 쓰지 않는 코드·리소스를 빼서 APK 를 줄이고 로딩을 빠르게 한다.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 개인 기기 사이드로딩용이라 디버그 키로 서명한다.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // 빌드 결과 APK 이름에 앱 이름과 버전을 넣는다. 예: PebbleDesk-1.2.1-release.apk
    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "PebbleDesk-${versionName}-${buildType.name}.apk"
        }
    }

    // 쓰지 않는 Kotlin 메타 정보(리플렉션용)는 APK 에 넣지 않는다.
    packaging {
        resources.excludes += setOf("kotlin/**", "kotlin-tooling-metadata.json")
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

