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
<#elseif buildApi gte 22>
    <global id="superClass" type="string" value="AppCompatActivity"/>
    <global id="superClassFqcn" type="string" value="android.support.v7.app.AppCompatActivity"/>
    <global id="hasAppBar" type="boolean" value="${(hasAppBarSelected!false)?string}" />
    <global id="hasNoActionBar" type="boolean" value="${(hasAppBarSelected!false)?string}" />
    <global id="appCompatActivity" type="boolean" value="true" />
<#else>
    <global id="superClass" type="string" value="ActionBarActivity"/>
    <global id="superClassFqcn" type="string" value="android.support.v7.app.ActionBarActivity"/>
    <global id="hasAppBar" type="boolean" value="false" />
    <global id="hasNoActionBar" type="boolean" value="false" />
    <global id="appCompatActivity" type="boolean" value="false" />
</#if>

    <global id="srcOut" value="${srcDir}/${slashedPackageName(packageName)}" />
    <global id="resOut" value="${resDir}" />
    <global id="menuName" value="${classToResource(activityClass!'')}" />
    <global id="simpleName" value="${activityToLayout(activityClass!'')}" />
    <global id="relativePackage" value="<#if relativePackage?has_content>${relativePackage}<#else>${packageName}</#if>" />
    <global id="Support" value="${(isNewProject || hasDependency('com.android.support:appcompat-v7'))?string('Support','')}" />
</globals>
