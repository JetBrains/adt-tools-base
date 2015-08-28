<?xml version="1.0"?>
<globals>
    <globals file="../common/common_globals.xml.ftl" />

    <global id="simpleName" value="${activityToLayout(activityClass)}" />
    <global id="isLauncher" type="boolean" value="${isNewProject?string}" />
    <global id="includeImageDrawables" type="boolean" value="${(minApi?number lt 21)?string}" />
<#if (isNewProject || hasDependency('com.android.support:appcompat-v7'))>
    <global id="preferenceSuperClass" type="string" value="AppCompatPreferenceActivity"/>
<#else>
    <global id="preferenceSuperClass" type="string" value="PreferenceActivity"/>
</#if>
</globals>
