<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="${packageName}">

    <application android:allowBackup="true"
        <#if buildApi gte 17>android:supportsRtl="true"</#if>
        android:label="@string/app_name"<#if copyIcons>
        android:icon="@mipmap/ic_launcher"<#else>
        android:icon="@drawable/${assetName}"</#if>
        android:theme="@style/AppTheme">

    </application>

</manifest>
