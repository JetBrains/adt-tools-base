<?xml version="1.0"?>
<recipe>
<#if isGradle == "true">
    <merge from="settings.gradle.ftl"
             to="${escapeXmlAttribute(topOut)}/settings.gradle" />
    <instantiate from="build.gradle.ftl"
                   to="${escapeXmlAttribute(projectOut)}/build.gradle" />
</#if>
    <instantiate from="/src/library_package/Placeholder.java.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${className}.java" />
</recipe>
