import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * The version a release build carries, from `NOURIDE_HOME_VERSION` (the release workflow sets it from the tag, `v0.2.0`
 * → `0.2.0`). The version code grows with it, so Android takes each release as an update of the one before:
 * major × 1 000 000 + minor × 1 000 + patch.
 */
val releaseVersion: String? = System.getenv("NOURIDE_HOME_VERSION")?.takeIf { it.isNotBlank() }

fun versionCodeOf(version: String): Int {
  val parts = version.split('.').map { it.toInt() }
  require(parts.size == 3 && parts.all { it in 0..999 }) { "NOURIDE_HOME_VERSION is not x.y.z: $version" }
  return parts[0] * 1_000_000 + parts[1] * 1_000 + parts[2]
}

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
    versionCode = releaseVersion?.let(::versionCodeOf) ?: 1
    versionName = releaseVersion ?: "0.0.0-dev"
    ndk { abiFilters += "arm64-v8a" }
  }

  // One key for every release, kept by the owner and in the repository's secrets: Android installs an update only
  // when it is signed with the key of the app already on the phone. No key in the environment, no signed release.
  signingConfigs {
    create("release") {
      val keystore = System.getenv("NOURIDE_HOME_KEYSTORE")
      if (keystore != null) {
        storeFile = file(keystore)
        storePassword = System.getenv("NOURIDE_HOME_KEYSTORE_PASSWORD")
        keyAlias = System.getenv("NOURIDE_HOME_KEY_ALIAS")
        keyPassword = System.getenv("NOURIDE_HOME_KEYSTORE_PASSWORD")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      if (System.getenv("NOURIDE_HOME_KEYSTORE") != null) signingConfig = signingConfigs.getByName("release")
    }
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
