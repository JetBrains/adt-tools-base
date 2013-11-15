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
<#if isLibraryProject?? && isLibraryProject>
apply plugin: 'android-library'
<#else>
apply plugin: 'android'
</#if>

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
<#if javaVersion?? && javaVersion != "1.6">

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_${javaVersion?replace('.','_','i')}
        targetCompatibility JavaVersion.VERSION_${javaVersion?replace('.','_','i')}
    }
</#if>
<#if enableProGuard>
    <#if isLibraryProject>
    release {
        runProguard false
        proguardFile 'proguard-rules.txt'
        proguardFile getDefaultProguardFile('proguard-android.txt')
    }
    <#else>
    buildTypes {
        release {
            runProguard false
            proguardFile getDefaultProguardFile('proguard-android.txt')
        }
    }
    productFlavors {
        defaultFlavor {
            proguardFile 'proguard-rules.txt'
        }
    }
    </#if>
</#if>
}

dependencies {
    <#if dependencyList?? >
    <#list dependencyList as dependency>
    compile '${dependency}'
    </#list>
    </#if>
}
