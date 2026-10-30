plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
 namespace = "com.focus.prototype"
 compileSdk = 35
 buildToolsVersion = "35.0.0"
 defaultConfig { applicationId = "com.focus.prototype"; minSdk = 26; targetSdk = 35; versionCode = 7; versionName = "0.5.1" }
 defaultConfig { testInstrumentationRunner = "com.focus.prototype.BusinessChecks" }
 buildFeatures { compose = true;buildConfig = true }
 testOptions { unitTests.isIncludeAndroidResources = true }
 sourceSets { getByName("test").java.srcDir("src/checks/java");getByName("androidTest").java.srcDir("src/checks/java") }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
 testImplementation("junit:junit:4.13.2")
 testImplementation("org.robolectric:robolectric:4.14.1")
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.activity:activity-compose:1.9.3")
 implementation("androidx.compose.material3:material3")
}
