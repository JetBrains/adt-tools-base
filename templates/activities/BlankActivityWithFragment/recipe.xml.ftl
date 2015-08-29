<?xml version="1.0"?>
<recipe>

    <execute file="../common/recipe_manifest.xml.ftl" />

<#if isNewProject>
    <instantiate from="root/res/menu/main.xml.ftl"
            to="${escapeXmlAttribute(resOut)}/menu/${menuName}.xml" />
</#if>

    <merge from="root/res/values/strings.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/strings.xml" />

    <merge from="root/res/values/dimens.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/dimens.xml" />
    <merge from="root/res/values-w820dp/dimens.xml"
             to="${escapeXmlAttribute(resOut)}/values-w820dp/dimens.xml" />

<#if hasAppBar>
    <execute file="../common/recipe_app_bar.xml.ftl" />
</#if>

    <instantiate from="root/res/layout/activity_fragment_container.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/layout/${layoutName}.xml" />

    <instantiate from="root/res/layout/fragment_simple.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/layout/${fragmentLayoutName}.xml" />

    <instantiate from="root/src/app_package/SimpleActivity.java.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${activityClass}.java" />

    <instantiate from="root/src/app_package/SimpleActivityFragment.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${fragmentClass}.java" />

    <open file="${escapeXmlAttribute(srcOut)}/${activityClass}Fragment.java" />
    <open file="${escapeXmlAttribute(resOut)}/layout/${fragmentLayoutName}.xml" />
</recipe>
