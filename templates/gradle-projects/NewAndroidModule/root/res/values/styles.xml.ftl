<resources>

    <!-- Base application theme. -->
    <style name="AppTheme" parent="Theme.AppCompat<#if
            baseTheme?contains("light")>.Light<#if
            baseTheme?contains("darkactionbar")>.DarkActionBar</#if></#if>">
        <!-- Customize your theme here. -->
<#if buildApi gte 22>
        <item name="colorPrimary">@color/colorPrimary</item>
        <item name="colorPrimaryDark">@color/colorPrimaryDark</item>
        <item name="colorAccent">@color/colorAccent</item>
</#if>
    </style>

</resources>
