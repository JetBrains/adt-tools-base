<?xml version="1.0"?>
<globals>
    <global id="projectOut" value="." />
    <global id="manifestOut" value="src/main" />
    <global id="appCompat" value="${(minApiLevel lt 14)?string('1','')}" />
    <!-- e.g. getSupportActionBar vs. getActionBar -->
    <global id="Support" value="${(minApiLevel lt 14)?string('Support','')}" />
    <global id="hasViewPager" value="${(navType == 'pager' || navType == 'tabs')?string('1','')}" />
    <global id="hasSections" value="${(navType == 'pager' || navType == 'tabs' || navType == 'spinner' || navType == 'drawer')?string('1','')}" />
    <global id="srcOut" value="src/main/java/${slashedPackageName(packageName)}" />
    <global id="resOut" value="src/main/res" />
    <global id="menuName" value="${classToResource(activityClass)}" />
</globals>
