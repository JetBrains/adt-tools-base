<menu xmlns:android="http://schemas.android.com/apk/res/android"<#if appCompat?has_content>
    xmlns:app="http://schemas.android.com/apk/res-auto"</#if>
    xmlns:tools="http://schemas.android.com/tools"
    tools:context="${packageName}.${activityClass}" >
    <#if features == 'drawer'><item android:id="@+id/action_example"
        android:title="@string/action_example"
        ${(appCompat?has_content)?string('app','android')}:showAsAction="withText|ifRoom" /></#if>
    <item android:id="@+id/action_settings"
        android:title="@string/action_settings"
        android:orderInCategory="100"
        ${(appCompat?has_content)?string('app','android')}:showAsAction="never" />
</menu>
