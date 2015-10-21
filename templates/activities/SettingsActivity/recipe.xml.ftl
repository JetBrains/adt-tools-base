<?xml version="1.0"?>
<recipe>
    <dependency mavenUrl="com.android.support:support-v4:${buildApi}.+" />

    <#include "../common/recipe_manifest.xml.ftl" />

    <copy from="root/res/xml/pref_data_sync.xml"
            to="${escapeXmlAttribute(resOut)}/xml/pref_data_sync.xml" />
    <copy from="root/res/xml/pref_general.xml"
            to="${escapeXmlAttribute(resOut)}/xml/pref_general.xml" />
    <copy from="root/res/xml/pref_notification.xml"
            to="${escapeXmlAttribute(resOut)}/xml/pref_notification.xml" />
    <instantiate from="root/res/xml/pref_headers.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/xml/pref_headers.xml" />

    <copy from="root/res/drawable-v21"
            to="${escapeXmlAttribute(resOut)}/drawable<#if includeImageDrawables>-v21</#if>" />

    <merge from="root/res/values/pref_strings.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/strings.xml" />

    <instantiate from="root/src/app_package/SettingsActivity.java.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${activityClass}.java" />
<#if appCompatActivity>
    <instantiate from="root/src/app_package/AppCompatPreferenceActivity.java.ftl"
                   to="${escapeXmlAttribute(srcOut)}/AppCompatPreferenceActivity.java" />
</#if>

<#if includeImageDrawables>
    <copy from="root/res/drawable-hdpi"
            to="${escapeXmlAttribute(resOut)}/drawable-hdpi" />
    <copy from="root/res/drawable-mdpi"
            to="${escapeXmlAttribute(resOut)}/drawable-mdpi" />
    <copy from="root/res/drawable-xhdpi"
            to="${escapeXmlAttribute(resOut)}/drawable-xhdpi" />
    <copy from="root/res/drawable-xxhdpi"
            to="${escapeXmlAttribute(resOut)}/drawable-xxhdpi" />
    <copy from="root/res/drawable-xxxhdpi"
            to="${escapeXmlAttribute(resOut)}/drawable-xxxhdpi" />
</#if>

    <open file="${escapeXmlAttribute(srcOut)}/${activityClass}.java" />
</recipe>
