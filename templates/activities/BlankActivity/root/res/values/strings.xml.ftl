<resources>
    <#if !isNewProject>
    <string name="title_${activityToLayout(activityClass)}">${escapeXmlString(activityTitle)}</string>
    </#if>

    <#if hasSections?has_content>
    <string name="title_section1">Section 1</string>
    <string name="title_section2">Section 2</string>
    <string name="title_section3">Section 3</string>
    <#else>
    <string name="hello_world">Hello world!</string>
    </#if>
    <#if features == 'drawer'>
    <string name="navigation_drawer_open">Open navigation drawer</string>
    <string name="navigation_drawer_close">Close navigation drawer</string>

    <string name="action_example">Example action</string>
    </#if>
    <string name="action_settings">Settings</string>

</resources>
