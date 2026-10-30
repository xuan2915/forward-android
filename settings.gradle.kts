pluginManagement {
 repositories {
  maven("https://maven.aliyun.com/repository/google") {
   content { includeGroupByRegex("com\\.android(\\..*)?"); includeGroupByRegex("androidx\\..*"); includeGroupByRegex("com\\.google\\.android(\\..*)?") }
  }
  mavenCentral()
  gradlePluginPortal()
  maven("https://maven.aliyun.com/repository/gradle-plugin")
  google()
 }
}
dependencyResolutionManagement {
 repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
 repositories {
  maven("https://maven.aliyun.com/repository/google") {
   content { includeGroupByRegex("com\\.android(\\..*)?"); includeGroupByRegex("androidx\\..*"); includeGroupByRegex("com\\.google\\.android(\\..*)?") }
  }
  mavenCentral()
  maven("https://maven.aliyun.com/repository/central")
  google()
 }
}
rootProject.name = "FocusPrototype"
include(":app")
