<?xml version="1.0"?>
<recipe>
    <dependency mavenUrl="com.google.android.gms:play-services:4.2.42" />
    <dependency mavenUrl="com.android.support:appcompat-v7:19.+" />

    <merge from="AndroidManifest.xml.ftl"
             to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />

    <instantiate from="res/layout/activity_map.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/layout/${layoutName}.xml" />

    <merge from="res/values/strings.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/strings.xml" />

    <instantiate from="src/app_package/MapActivity.java.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${activityClass}.java" />

    <merge from="debugRes/values/google_maps_api.xml.ftl"
             to="${escapeXmlAttribute(debugResOut)}/values/google_maps_api.xml" />

    <merge from="releaseRes/values/google_maps_api.xml.ftl"
             to="${escapeXmlAttribute(releaseResOut)}/values/google_maps_api.xml" />

    <open file="${escapeXmlAttribute(srcOut)}/${activityClass}.java" />

    <!-- Display the API key instructions. -->
    <open file="${escapeXmlAttribute(debugResOut)}/values/google_maps_api.xml" />
</recipe>
