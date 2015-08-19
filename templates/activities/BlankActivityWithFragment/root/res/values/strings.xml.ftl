<resources>
<#if !isNewProject>
    <string name="title_${activityToLayout(activityClass)}">${escapeXmlString(activityTitle)}</string>
<#else>
    <string name="action_settings">Settings</string>
</#if>
</resources>
