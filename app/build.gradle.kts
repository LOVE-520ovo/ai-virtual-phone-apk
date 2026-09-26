plugins {
    id("com.android.application")
}

android {
    namespace = "com.love520ovo.aivirtualphone"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.love520ovo.aivirtualphone"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("fixed") {
            storeFile = file("../signing/aivp.keystore")
            storePassword = "aivp123456"
            keyAlias = "aivp"
            keyPassword = "aivp123456"
        }
    }
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            signingConfig = signingConfigs.getByName("fixed")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
