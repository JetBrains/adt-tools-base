<?xml version="1.0"?>
<recipe>
    <merge from="settings.gradle.ftl"
             to="${escapeXmlAttribute(topOut)}/settings.gradle" />
    <instantiate from="build.gradle.ftl"
                   to="${escapeXmlAttribute(projectOut)}/build.gradle" />
    <instantiate from="src/library_package/Placeholder.java.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${className}.java" />
<#if makeIgnore>
    <copy from="gitignore"
            to="${escapeXmlAttribute(projectOut)}/.gitignore" />
</#if>

	<mkdir at="${escapeXmlAttribute(projectOut)}/libs" />
</recipe>
