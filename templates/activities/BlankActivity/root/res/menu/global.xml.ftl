<menu xmlns:android="http://schemas.android.com/apk/res/android"<#if appCompat?has_content>
    xmlns:app="http://schemas.android.com/apk/res-auto"</#if>>
    <item android:id="@+id/action_settings"
        android:title="@string/action_settings"
        android:orderInCategory="100"
        ${(appCompat?has_content)?string('app','android')}:showAsAction="never" />
</menu>
