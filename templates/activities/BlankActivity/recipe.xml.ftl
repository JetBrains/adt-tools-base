<?xml version="1.0"?>
<recipe>

    <#if appCompat?has_content><dependency mavenUrl="com.android.support:appcompat-v7:+"/></#if>
    <#if !appCompat?has_content && hasViewPager?has_content><dependency mavenUrl="com.android.support:support-v13:+"/></#if>
    <#if !appCompat?has_content && navType == 'drawer'><dependency mavenUrl="com.android.support:support-v4:+"/></#if>

    <merge from="AndroidManifest.xml.ftl"
             to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />

    <instantiate from="res/menu/main.xml.ftl"
            to="${escapeXmlAttribute(resOut)}/menu/${menuName}.xml" />

    <merge from="res/values/strings.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/strings.xml" />

    <merge from="res/values/dimens.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/dimens.xml" />
    <merge from="res/values-w820dp/dimens.xml"
             to="${escapeXmlAttribute(resOut)}/values-w820dp/dimens.xml" />

    <!-- TODO: switch on Holo Dark v. Holo Light -->
    <#if navType == 'drawer'>
        <copy from="res/drawable-hdpi"
                to="${escapeXmlAttribute(resOut)}/drawable-hdpi" />
        <copy from="res/drawable-mdpi"
                to="${escapeXmlAttribute(resOut)}/drawable-mdpi" />
        <copy from="res/drawable-xhdpi"
                to="${escapeXmlAttribute(resOut)}/drawable-xhdpi" />
        <copy from="res/drawable-xxhdpi"
                to="${escapeXmlAttribute(resOut)}/drawable-xxhdpi" />

        <instantiate from="res/menu/global.xml.ftl"
                to="${escapeXmlAttribute(resOut)}/menu/global.xml" />

    </#if>

    <!-- Decide what kind of layout(s) to add -->
    <#if hasViewPager?has_content>
        <instantiate from="res/layout/activity_pager.xml.ftl"
                       to="${escapeXmlAttribute(resOut)}/layout/${layoutName}.xml" />

    <#elseif navType == 'drawer'>
        <instantiate from="res/layout/activity_drawer.xml.ftl"
                       to="${escapeXmlAttribute(resOut)}/layout/${layoutName}.xml" />
        <instantiate from="res/layout/fragment_navigation_drawer.xml.ftl"
                       to="${escapeXmlAttribute(resOut)}/layout/fragment_navigation_drawer.xml" />

    <#else>
        <instantiate from="res/layout/activity_fragment_container.xml.ftl"
                       to="${escapeXmlAttribute(resOut)}/layout/${layoutName}.xml" />

    </#if>

    <!-- Always add the simple/placeholder fragment -->
    <instantiate from="res/layout/fragment_simple.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/layout/${fragmentLayoutName}.xml" />

    <!-- Decide which activity code to add -->
    <#if navType == "none">
        <instantiate from="src/app_package/SimpleActivity.java.ftl"
                       to="${escapeXmlAttribute(srcOut)}/${activityClass}.java" />

    <#elseif navType == "tabs" || navType == "pager">
        <instantiate from="src/app_package/TabsAndPagerActivity.java.ftl"
                       to="${escapeXmlAttribute(srcOut)}/${activityClass}.java" />

    <#elseif navType == "drawer">
        <instantiate from="src/app_package/DrawerActivity.java.ftl"
                       to="${escapeXmlAttribute(srcOut)}/${activityClass}.java" />
        <instantiate from="src/app_package/NavigationDrawerFragment.java.ftl"
                       to="${escapeXmlAttribute(srcOut)}/NavigationDrawerFragment.java" />

    <#elseif navType == "spinner">
        <instantiate from="src/app_package/DropdownActivity.java.ftl"
                       to="${escapeXmlAttribute(srcOut)}/${activityClass}.java" />

    </#if>

    <open file="${escapeXmlAttribute(srcOut)}/${activityClass}.java" />
    <open file="${escapeXmlAttribute(resOut)}/layout/${fragmentLayoutName}.xml" />
</recipe>
