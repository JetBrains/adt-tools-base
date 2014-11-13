<resources>

    <!--
        Base application theme for API 11+. This theme completely replaces
        AppBaseTheme from res/values/styles.xml on API 11+ devices.
    -->
    <style name="AppBaseTheme" parent="<#if
            appCompat>Theme.AppCompat<#else
            >android:Theme.Holo</#if><#if baseTheme?contains("light")>.Light</#if>">
        <!-- API 11 theme customizations can go here. -->
    </style>

</resources>
