<?xml version="1.0"?>
<recipe>

    <dependency mavenUrl="com.google.android.gms:play-services-wearable:7.5.0" />
    <dependency mavenUrl="com.google.android.gms:play-services-maps:7.5.0" />
    <dependency mavenUrl="com.google.android.support:wearable:1.2.0" />


    <merge from="root/AndroidManifest.xml.ftl"
             to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />
    <merge from="root/AndroidManifestPermissions.xml"
             to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />
<#if appManifestOut??>
    <merge from="root/AndroidManifestPermissions.xml"
             to="${escapeXmlAttribute(appManifestOut)}/AndroidManifest.xml" />
</#if>

    <instantiate from="root/res/layout/activity_map.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/layout/${layoutName}.xml" />

    <merge from="root/res/values/strings.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/strings.xml" />

    <instantiate from="root/res/layout/activity_map.xml.ftl"
            to="${escapeXmlAttribute(resOut)}/layout/${layoutName}.xml" />

    <instantiate from="root/src/app_package/MapActivity.java.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${activityClass}.java" />

    <open file="${escapeXmlAttribute(srcOut)}/${activityClass}.java" />

    <merge from="root/debugRes/values/google_maps_api.xml.ftl"
             to="${escapeXmlAttribute(debugResOut)}/values/google_maps_api.xml" />

    <merge from="root/releaseRes/values/google_maps_api.xml.ftl"
             to="${escapeXmlAttribute(releaseResOut)}/values/google_maps_api.xml" />

    <!-- Display the API key instructions. -->
    <open file="${escapeXmlAttribute(debugResOut)}/values/google_maps_api.xml" />
</recipe>
