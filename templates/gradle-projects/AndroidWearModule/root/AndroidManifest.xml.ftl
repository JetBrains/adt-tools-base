<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="${packageName}">
    <uses-feature android:name="android.hardware.type.watch" />
    <application android:allowBackup="true"
        <#if buildApi gte 17>android:supportsRtl="true"</#if>
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher"
        android:theme="@android:style/Theme.DeviceDefault">

    </application>

</manifest>
