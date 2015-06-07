<manifest xmlns:android="http://schemas.android.com/apk/res/android" >

    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application>
        <uses-library android:name="com.google.android.wearable" android:required="false" />
        <activity android:name="${relativePackage}.${activityClass}"
            <#if isNewProject>
            android:label="@string/app_name"
            <#else>
            android:label="@string/title_${activityToLayout(activityClass)}"
            </#if>
            android:theme="@android:style/Theme.DeviceDefault.Light"
            >
            <#if isLauncher>
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            </#if>
        </activity>
    </application>

</manifest>
