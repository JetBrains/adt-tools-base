<manifest xmlns:android="http://schemas.android.com/apk/res/android" >


    <uses-permission android:name="android.permission.INTERNET" />

    <uses-feature
        android:name="android.hardware.touchscreen"
        android:required="false" />

    <application
            <#if isNewProject>
            android:label="@string/app_name"
            <#else>
            android:label="@string/title_${activityToLayout(activityClass)}"
            </#if>
            android:theme="@style/Theme.Leanback"
            android:allowBackup="false">

        <activity android:name="${packageName}.${activityClass}"
            android:logo="@drawable/app_icon_quantum"
            android:screenOrientation="landscape"
            <#if isNewProject>
            android:label="@string/app_name"
            <#else>
            android:label="@string/title_${activityToLayout(activityClass)}"
            </#if>
            <#if buildApi gte 16 && parentActivityClass != "">android:parentActivityName="${parentActivityClass}"</#if>>
            <#if parentActivityClass != "">
            <meta-data android:name="android.support.PARENT_ACTIVITY"
                android:value="${parentActivityClass}" />
            </#if>
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>

        <activity
            android:name="PlayerActivity"
            android:theme="@android:style/Theme.NoTitleBar.Fullscreen" />

        <activity android:name="${packageName}.${detailsActivity}" />

    </application>

</manifest>
