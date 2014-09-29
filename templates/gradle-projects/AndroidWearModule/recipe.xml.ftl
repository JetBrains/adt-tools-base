<?xml version="1.0"?>
<recipe>
<#if !createActivity>
    <mkdir at="${escapeXmlAttribute(srcOut)}" />
</#if>

    <dependency mavenUrl="com.google.android.support:wearable:+" />
    <dependency mavenUrl="com.google.android.gms:play-services-wearable:+" />

    <mkdir at="${escapeXmlAttribute(projectOut)}/libs" />

    <merge from="settings.gradle.ftl"
             to="${escapeXmlAttribute(topOut)}/settings.gradle" />
    <instantiate from="build.gradle.ftl"
                   to="${escapeXmlAttribute(projectOut)}/build.gradle" />
    <instantiate from="AndroidManifest.xml.ftl"
                   to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />

<#if makeIgnore>
    <copy from="module_ignore"
            to="${escapeXmlAttribute(projectOut)}/.gitignore" />
</#if>
<#if enableProGuard>
    <instantiate from="proguard-rules.txt.ftl"
                   to="${escapeXmlAttribute(projectOut)}/proguard-rules.pro" />
</#if>
    <mkdir  at="${escapeXmlAttribute(resOut)}/drawable" />
    <copy from="res/drawable-hdpi"
            to="${escapeXmlAttribute(resOut)}/drawable-hdpi" />
    <copy from="res/drawable-mdpi"
            to="${escapeXmlAttribute(resOut)}/drawable-mdpi" />
    <copy from="res/drawable-xhdpi"
            to="${escapeXmlAttribute(resOut)}/drawable-xhdpi" />
    <copy from="res/drawable-xxhdpi"
            to="${escapeXmlAttribute(resOut)}/drawable-xxhdpi" />

    <instantiate from="res/values/strings.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/values/strings.xml" />
</recipe>
