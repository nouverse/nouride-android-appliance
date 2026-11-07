import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}

android {
  namespace = "tech.nouverse.nouride.android"
  compileSdk = 36

  defaultConfig {
    // The installed app's identity, fixed once anyone has it: "home" because this app makes the phone where Nouride
    // lives. A client app, if one comes, is `tech.nouverse.nouride`. The Kotlin package (`namespace`) is not the id.
    applicationId = "tech.nouverse.nouride.home"
    minSdk = 24
    // Termux's target. From 29 on, an app may not execute files in its data directory (W^X),
    // and proot maps every program of the Debian rootfs from there.
    targetSdk = 28
    versionCode = 1
    versionName = "0.1.0-spike"
    ndk { abiFilters += "arm64-v8a" }
  }

  packaging {
    // libproot.so and libproot-loader.so are executables: installed as files in
    // nativeLibraryDir, the one place the app may exec from, not mapped from the APK.
    jniLibs { useLegacyPackaging = true }
  }

  androidResources {
    // rootfs.tgz is gzip already.
    noCompress += "tgz"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  lint {
    // targetSdk 28 is deliberate (see above); the app is sideloaded, not published on Play.
    disable += "ExpiredTargetSdkVersion"
  }
}

kotlin {
  compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
  testImplementation("junit:junit:4.13.2")
  // The real org.json for unit tests; android.jar only has stubs of it.
  testImplementation("org.json:json:20260814")
}
