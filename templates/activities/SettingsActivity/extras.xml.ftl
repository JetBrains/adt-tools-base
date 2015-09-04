<?xml version="1.0"?>
<globals>
    <global id="isLauncher" type="boolean" value="${isNewProject?string}" />
    <global id="includeImageDrawables" type="boolean" value="${(minApi?number lt 21)?string}" />
<#if appCompatActivity>
    <global id="preferenceSuperClass" type="string" value="AppCompatPreferenceActivity"/>
<#else>
    <global id="preferenceSuperClass" type="string" value="PreferenceActivity"/>
</#if>
</globals>
