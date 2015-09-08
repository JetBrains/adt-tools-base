<?xml version="1.0"?>
<globals>
    <global id="manifestOut" value="${manifestDir}" />
    <global id="buildVersion" value="${buildApi}" />

<#if !appCompat>
    <global id="superClass" type="string" value="Activity"/>
    <global id="superClassFqcn" type="string" value="android.app.Activity"/>
    <global id="hasAppBar" type="boolean" value="false" />
    <global id="hasNoActionBar" type="boolean" value="false" />
    <global id="appCompatActivity" type="boolean" value="false" />
    <global id="Support" value="" />
    <global id="actionBarClassFqcn" type = "string" value="android.app.ActionBar" />
<#elseif buildApi gte 22>
    <global id="superClass" type="string" value="AppCompatActivity"/>
    <global id="superClassFqcn" type="string" value="android.support.v7.app.AppCompatActivity"/>
    <global id="hasAppBar" type="boolean" value="true" />
    <global id="hasNoActionBar" type="boolean" value="true" />
    <global id="appCompatActivity" type="boolean" value="true" />
    <global id="Support" value="Support" />
    <global id="actionBarClassFqcn" type = "string" value="android.support.v7.app.ActionBar" />
<#else>
    <global id="superClass" type="string" value="ActionBarActivity"/>
    <global id="superClassFqcn" type="string" value="android.support.v7.app.ActionBarActivity"/>
    <global id="hasAppBar" type="boolean" value="false" />
    <global id="hasNoActionBar" type="boolean" value="false" />
    <global id="appCompatActivity" type="boolean" value="false" />
    <global id="Support" value="Support" />
    <global id="actionBarClassFqcn" type = "string" value="android.support.v7.app.ActionBar" />
</#if>

    <global id="srcOut" value="${srcDir}/${slashedPackageName(packageName)}" />
    <global id="resOut" value="${resDir}" />
    <global id="menuName" value="${classToResource(activityClass!'')}" />
    <global id="simpleName" value="${activityToLayout(activityClass!'')}" />
    <global id="relativePackage" value="<#if relativePackage?has_content>${relativePackage}<#else>${packageName}</#if>" />
</globals>
