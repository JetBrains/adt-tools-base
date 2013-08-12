buildscript {
    repositories {
<#if mavenUrl == "mavenCentral">
        mavenCentral()
<#else>
        maven { url '${mavenUrl}' }
</#if>
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:${gradlePluginVersion}'
    }
}
apply plugin: 'android'

repositories {
<#if mavenUrl == "mavenCentral">
    mavenCentral()
<#else>
    maven { url '${mavenUrl}' }
</#if>
}

android {
    compileSdkVersion ${buildApi}
    buildToolsVersion "${buildToolsVersion}"

    defaultConfig {
        minSdkVersion ${minApi}
        targetSdkVersion ${targetApi}
    }
}

dependencies {
<#if androidSupportLibraryUrl??>
    compile '${androidSupportLibraryUrl}'
</#if>
}
