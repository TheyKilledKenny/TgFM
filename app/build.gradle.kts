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
  versionCode = 10; versionName = "1.5.2"
  ndk { abiFilters += "arm64-v8a" }
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
