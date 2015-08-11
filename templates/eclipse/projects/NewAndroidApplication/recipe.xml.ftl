<?xml version="1.0"?>
<recipe>
    <instantiate from="root/AndroidManifest.xml.ftl"
                   to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />

<#if copyIcons>
    <copy from="root/res/drawable-hdpi"
            to="${escapeXmlAttribute(resOut)}/drawable-hdpi" />
    <copy from="root/res/drawable-mdpi"
            to="${escapeXmlAttribute(resOut)}/drawable-mdpi" />
    <copy from="root/res/drawable-xhdpi"
            to="${escapeXmlAttribute(resOut)}/drawable-xhdpi" />
</#if>
    <instantiate from="root/res/values/styles.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/values/styles.xml" />
<#if buildApi gte 11 && baseTheme != "none">
    <instantiate from="root/res/values-v11/styles_hc.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/values-v11/styles.xml" />
</#if>
<#if buildApi gte 14 && baseTheme?contains("darkactionbar")>
    <instantiate from="root/res/values-v14/styles_ics.xml.ftl"
            to="${escapeXmlAttribute(resOut)}/values-v14/styles.xml" />
</#if>

    <instantiate from="root/res/values/strings.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/values/strings.xml" />
</recipe>
