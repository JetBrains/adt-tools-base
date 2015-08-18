<?xml version="1.0"?>
<recipe>

<#if appCompat && !(hasDependency('com.android.support:appcompat-v7'))>
    <dependency mavenUrl="com.android.support:appcompat-v7:${buildApi}.+"/>
</#if>

    <instantiate from="root/res/layout/simple.xml.ftl"
                 to="${escapeXmlAttribute(resOut)}/layout/${layoutName}.xml" />

<#if isNewProject!false>
    <instantiate from="root/res/menu/simple_menu.xml.ftl"
                 to="${escapeXmlAttribute(resOut)}/menu/${menuName}.xml" />
</#if>

    <merge from="root/res/values/simple_strings.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/strings.xml" />
    <merge from="root/res/values/simple_dimens.xml"
             to="${escapeXmlAttribute(resOut)}/values/dimens.xml" />
    <merge from="root/res/values-w820dp/simple_dimens.xml"
             to="${escapeXmlAttribute(resOut)}/values-w820dp/dimens.xml" />
</recipe>
