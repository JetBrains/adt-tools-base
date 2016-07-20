<recipe folder="root://activities/common">

<#if appCompat && !(hasDependency('com.android.support:appcompat-v7'))>
    <dependency mavenUrl="com.android.support:appcompat-v7:${buildApi}.+"/>
</#if>
    <instantiate from="root/res/layout/simple.xml.ftl"
                 to="${escapeXmlAttribute(resOut)}/layout/${simpleLayoutName}.xml" />

<#if (isNewProject!false) && !(excludeMenu!false)>
    <#include "recipe_simple_menu.xml.ftl" />
</#if>

    <#include "recipe_simple_dimens.xml" />
</recipe>
