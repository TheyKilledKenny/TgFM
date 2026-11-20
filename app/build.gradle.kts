plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
}
android {
 namespace = "it.tgfm"
 compileSdk = 36
 ndkVersion = "27.2.12479018"
 defaultConfig {
  applicationId = "it.tgfm"; minSdk = 26; targetSdk = 35
  versionCode = 13; versionName = "1.5.4"
  ndk { abiFilters += "arm64-v8a" }
 }
 val releaseSigning = listOf("TGFM_KEYSTORE", "TGFM_STORE_PASSWORD", "TGFM_KEY_ALIAS", "TGFM_KEY_PASSWORD").associateWith { System.getenv(it) }
 require(releaseSigning.values.all { it.isNullOrBlank() } || releaseSigning.values.none { it.isNullOrBlank() }) {
  "Configura tutte le variabili TGFM_KEYSTORE, TGFM_STORE_PASSWORD, TGFM_KEY_ALIAS e TGFM_KEY_PASSWORD, oppure nessuna per produrre un APK release non firmato."
 }
 if (releaseSigning.values.none { it.isNullOrBlank() }) {
  signingConfigs.create("release") {
   storeFile = file(releaseSigning.getValue("TGFM_KEYSTORE")!!)
   storePassword = releaseSigning.getValue("TGFM_STORE_PASSWORD")
   keyAlias = releaseSigning.getValue("TGFM_KEY_ALIAS")
   keyPassword = releaseSigning.getValue("TGFM_KEY_PASSWORD")
  }
 }
 buildTypes {
  getByName("release") {
   isDebuggable = false
   isJniDebuggable = false
   isMinifyEnabled = false
   signingConfig = signingConfigs.findByName("release")
  }
 }
 buildFeatures { compose = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 packaging { jniLibs.useLegacyPackaging = false; resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
val verifyTdlib by tasks.registering {
 val library = layout.projectDirectory.file("src/main/jniLibs/arm64-v8a/libtdjsonjava.so")
 inputs.file(library)
 doLast { check(library.asFile.isFile && library.asFile.length() > 1_000_000) { "TDLib ARM64 mancante: ripristina src/main/jniLibs/arm64-v8a/libtdjsonjava.so oppure consulta la sezione TDLib del README." } }
}
tasks.named("preBuild") { dependsOn(verifyTdlib) }
dependencies {
 implementation("me.zhanghai.android.libarchive:library:1.1.6")
 implementation("net.lingala.zip4j:zip4j:2.11.5")
 implementation("commons-net:commons-net:3.12.0")
 implementation(platform("androidx.compose:compose-bom:2025.11.00"))
 implementation("androidx.compose.material3:material3")
 implementation("androidx.activity:activity-compose:1.11.0")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
 implementation("androidx.datastore:datastore-preferences:1.1.7")
 implementation("androidx.documentfile:documentfile:1.0.1")
 implementation("androidx.core:core-ktx:1.15.0")
}
