<?xml version="1.0"?>
<recipe>
    <merge from="root/res/values/no_actionbar_styles.xml"
             to="${escapeXmlAttribute(resOut)}/values/styles.xml" />

<#if buildApi gte 21>
    <merge from="root/res/values-v21/no_actionbar_styles.xml"
             to="${escapeXmlAttribute(resOut)}/values-v21/styles.xml" />
</#if>
</recipe>
