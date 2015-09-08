<?xml version="1.0"?>
<globals>
  <#if appCompatActivity>
    <global id="resIn" type="string" value="res-v22" />
  <#else>
    <global id="resIn" type="string" value="res" />
  </#if>
    <global id="menuName" value="${classToResource(activityClass)}" />
    <global id="simpleLayoutName" value="<#if appCompatActivity>${contentLayoutName}<#else>${layoutName}</#if>" />
</globals>
