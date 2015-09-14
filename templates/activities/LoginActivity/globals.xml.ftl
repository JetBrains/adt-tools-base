<?xml version="1.0"?>
<globals>
    <global id="isLauncher" type="boolean" value="${isNewProject?string}" />
    <global id="includePermissionCheck" type="boolean" value="${(targetApi gte 23)?string}" />
    <global id="GenericStringArgument" type="string" value="<#if buildApi lt 19>String</#if>" />
    <globals file="../common/common_globals.xml.ftl" />
</globals>
