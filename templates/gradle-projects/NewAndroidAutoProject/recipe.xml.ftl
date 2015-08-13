<?xml version="1.0"?>
<recipe>
    <instantiate from="build.gradle.ftl"
                   to="${escapeXmlAttribute(topOut)}/build.gradle" />

<#if makeIgnore>
    <copy from="project_ignore"
            to="${escapeXmlAttribute(topOut)}/.gitignore" />
</#if>

    <instantiate from="settings.gradle.ftl"
                   to="${escapeXmlAttribute(topOut)}/settings.gradle" />

    <instantiate from="gradle.properties.ftl"
                   to="${escapeXmlAttribute(topOut)}/gradle.properties" />

    <copy from="${templateRoot}/gradle/wrapper"
        to="${escapeXmlAttribute(topOut)}/" />

<#if sdkDir??>
  <instantiate from="local.properties.ftl"
           to="${escapeXmlAttribute(topOut)}/local.properties" />
</#if>
</recipe>
