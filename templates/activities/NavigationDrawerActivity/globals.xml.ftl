<?xml version="1.0"?>
<globals>

<!-- Override hasNoActionBar before including the common definitions -->
<#if appCompatActivity>
    <global id="resIn" type="string" value="res-v22" />
    <global id="hasNoActionBar" type="boolean" value="true" />
<#else>
    <global id="resIn" type="string" value="res" />
    <global id="hasNoActionBar" type="boolean" value="false" />
</#if>
    <global id="menuName" value="${classToResource(activityClass)}" />

    <globals file="../common/common_globals.xml.ftl" />
    <global id="simpleLayoutName" value="<#if appCompatActivity>${contentLayoutName}<#else>${layoutName}</#if>" />
</globals>
