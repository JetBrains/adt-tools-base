<?xml version="1.0"?>
<recipe>

    <dependency mavenUrl="com.android.support:support-v4:+"/>
    <merge from="res/values/strings.xml" to="${escapeXmlAttribute(resOut)}/values/strings.xml" />

    <#if includeLayout>
        <instantiate from="res/layout/fragment_blank.xml.ftl"
                       to="${escapeXmlAttribute(resOut)}/layout/fragment_${classToResource(className)}.xml" />

        <open file="${escapeXmlAttribute(resOut)}/layout/fragment_${classToResource(className)}.xml" />
    </#if>

    <open file="${escapeXmlAttribute(srcOut)}/${className}.java" />

    <instantiate from="src/app_package/BlankFragment.java.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${className}.java" />

</recipe>
