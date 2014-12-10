<?xml version="1.0"?>
<recipe>

    <dependency mavenUrl="com.google.android.support:wearable:1.1.+" />

    <merge from="AndroidManifest.xml.ftl"
             to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />

    <merge from="res/values/strings.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/strings.xml" />

    <merge from="res/values/colors.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/colors.xml" />

    <merge from="res/values/dimens.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/dimens.xml" />

    <copy from="res/xml/watch_face.xml"
            to="${escapeXmlAttribute(resOut)}/xml/watch_face.xml" />

<#if style == "analog">
    <copy from="res/drawable-nodpi/preview_analog.png"
            to="${escapeXmlAttribute(resOut)}/drawable-nodpi/preview_analog.png" />
<#elseif style == "digital">
    <copy from="res/drawable-nodpi/preview_digital.png"
            to="${escapeXmlAttribute(resOut)}/drawable-nodpi/preview_digital.png" />
    <copy from="res/drawable-nodpi/preview_digital_circular.png"
            to="${escapeXmlAttribute(resOut)}/drawable-nodpi/preview_digital_circular.png" />
</#if>

<#if style == "analog">
    <instantiate from="src/app_package/MyAnalogWatchFaceService.java.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${serviceClass}.java" />
<#elseif style == "digital">
    <instantiate from="src/app_package/MyDigitalWatchFaceService.java.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${serviceClass}.java" />
</#if>

    <open file="${escapeXmlAttribute(srcOut)}/${serviceClass}.java" />
</recipe>
