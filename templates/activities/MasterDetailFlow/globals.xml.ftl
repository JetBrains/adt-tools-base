<?xml version="1.0"?>
<globals>
<#if hasDependency('com.android.support:appcompat-v7')>
    <global id="appCompat" type="boolean" value="true" />
    <global id="superClass" type="string" value="<#if buildApi gte 22>AppCompat<#else>ActionBar</#if>Activity"/>
    <global id="superClassFqcn" type="string" value="android.support.v7.app.<#if buildApi gte 22>AppCompat<#else>ActionBar</#if>Activity"/>
<#else>
    <global id="appCompat" type="boolean" value="false" />
    <global id="superClass" type="string" value="Activity"/>
    <global id="superClassFqcn" type="string" value="android.app.Activity"/>
</#if>
    <global id="Support" value="${(hasDependency('com.android.support:appcompat-v7'))?string('Support','')}" />
    <global id="manifestOut" value="${manifestDir}" />
    <global id="srcOut" value="${srcDir}/${slashedPackageName(packageName)}" />
    <global id="resOut" value="${resDir}" />
    <global id="CollectionName" value="${extractLetters(objectKind)}List" />
    <global id="collection_name" value="${extractLetters(objectKind?lower_case)}_list" />
    <global id="DetailName" value="${extractLetters(objectKind)}Detail" />
    <global id="detail_name" value="${extractLetters(objectKind?lower_case)}_detail" />
    <global id="relativePackage" value="<#if relativePackage?has_content>${relativePackage}<#else>${packageName}</#if>" />
</globals>
