import java.io.StringReader
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

// Lee `keystore.properties` (ignorado en Git) si ya creaste tu llave privada de autor.
// Usamos `providers.fileContents` para que el Configuration Cache de Gradle detecte cambios al instante.
val keystorePropsFile = rootProject.layout.projectDirectory.file("keystore.properties")
val keystorePropsText = providers.fileContents(keystorePropsFile).asText.orNull
val keystoreProps = Properties().apply {
  if (keystorePropsText != null) {
    load(StringReader(keystorePropsText))
  }
}
val configuredStorePath = keystoreProps.getProperty("storeFile")?.trim().orEmpty()
val hasAuthorKeystore = configuredStorePath.isNotEmpty() &&
  rootProject.file(configuredStorePath).exists()

android {
  namespace = "cl.antillamadas.app"
  compileSdk = 36
  defaultConfig {
    applicationId = "cl.antillamadas.app"
    minSdk = 29
    targetSdk = 36
    versionCode = 3
    versionName = "2.1"
  }

  signingConfigs {
    if (hasAuthorKeystore) {
      create("release") {
        storeFile = rootProject.file(configuredStorePath)
        storePassword = keystoreProps.getProperty("storePassword")
        keyAlias = keystoreProps.getProperty("keyAlias")
        keyPassword = keystoreProps.getProperty("keyPassword")
        enableV1Signing = true
        enableV2Signing = true
        enableV3Signing = true
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      signingConfig = if (hasAuthorKeystore) {
        signingConfigs.getByName("release")
      } else {
        signingConfigs.getByName("debug")
      }
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = false
    shaders = false
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Local JSON storage & rule export/import
  implementation(libs.kotlinx.serialization.json)
}
