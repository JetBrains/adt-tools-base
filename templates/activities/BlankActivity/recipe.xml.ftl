<?xml version="1.0"?>
<recipe>
    <execute file="../common/recipe_manifest.xml.ftl" />

<#if useFragment>
    <execute file="recipe_fragment.xml.ftl" />
<#else>
    <execute file="../common/recipe_simple.xml.ftl" />
</#if>

<#if hasAppBar>
    <execute file="../common/recipe_app_bar.xml.ftl" />
</#if>

    <instantiate from="root/src/app_package/SimpleActivity.java.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${activityClass}.java" />

    <open file="${escapeXmlAttribute(srcOut)}/${activityClass}.java" />
    <open file="${escapeXmlAttribute(resOut)}/layout/${simpleLayoutName}.xml" />
</recipe>
