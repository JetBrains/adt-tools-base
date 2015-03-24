<?xml version="1.0"?>
<recipe>

    <#if appCompat><dependency mavenUrl="com.android.support:appcompat-v7:${targetApi}.+"/></#if>

<#if !createActivity>
    <mkdir at="${escapeXmlAttribute(srcOut)}" />
</#if>

    <mkdir at="${escapeXmlAttribute(projectOut)}/libs" />

    <merge from="settings.gradle.ftl"
             to="${escapeXmlAttribute(topOut)}/settings.gradle" />
    <instantiate from="build.gradle.ftl"
                   to="${escapeXmlAttribute(projectOut)}/build.gradle" />
    <instantiate from="AndroidManifest.xml.ftl"
                   to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />

<mkdir at="${escapeXmlAttribute(resOut)}/drawable" />
<#if copyIcons && !isLibraryProject>
    <copy from="res/mipmap-hdpi"
            to="${escapeXmlAttribute(resOut)}/mipmap-hdpi" />
    <copy from="res/mipmap-mdpi"
            to="${escapeXmlAttribute(resOut)}/mipmap-mdpi" />
    <copy from="res/mipmap-xhdpi"
            to="${escapeXmlAttribute(resOut)}/mipmap-xhdpi" />
    <copy from="res/mipmap-xxhdpi"
            to="${escapeXmlAttribute(resOut)}/mipmap-xxhdpi" />
</#if>
<#if makeIgnore>
    <copy from="module_ignore"
            to="${escapeXmlAttribute(projectOut)}/.gitignore" />
</#if>
<#if enableProGuard>
    <instantiate from="proguard-rules.txt.ftl"
                   to="${escapeXmlAttribute(projectOut)}/proguard-rules.pro" />
</#if>
<#if !(isLibraryProject??) || !isLibraryProject>
    <instantiate from="res/values/styles.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/values/styles.xml" />
<#if buildApi gte 21 && !(appCompat)>
    <copy from="res/values-v21/styles.xml"
          to="${escapeXmlAttribute(resOut)}/values-v21/styles.xml" />
</#if>
</#if>

    <instantiate from="res/values/strings.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/values/strings.xml" />

    <instantiate from="test/app_package/ApplicationTest.java.ftl"
                   to="${testOut}/ApplicationTest.java" />

    <instantiate from="test/app_package/ExampleUnitTest.java.ftl"
                   to="${unitTestOut}/ExampleUnitTest.java" />
</recipe>
