plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
 namespace = "com.focus.prototype"
 compileSdk = 35
 buildToolsVersion = "35.0.0"
    defaultConfig { applicationId = "com.focus.prototype"; minSdk = 26; targetSdk = 35; versionCode = 31; versionName = "0.10.4.2" }
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
 implementation("com.materialkolor:material-kolor:1.7.1")
 implementation("com.kizitonwose.calendar:compose:2.6.1")
 implementation("com.patrykandpatrick.vico:compose-m3:1.16.0")
 implementation("androidx.health.connect:connect-client:1.1.0-alpha11")
}
