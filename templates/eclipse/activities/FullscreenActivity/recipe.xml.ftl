<?xml version="1.0"?>
<recipe>
    <dependency mavenUrl="com.android.support:support-v4:19.+" />

    <merge from="root/AndroidManifest.xml.ftl"
             to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />

    <merge from="root/res/values/attrs.xml"
             to="${escapeXmlAttribute(resOut)}/values/attrs.xml" />
    <merge from="root/res/values/colors.xml"
             to="${escapeXmlAttribute(resOut)}/values/colors.xml" />
    <merge from="root/res/values/styles.xml"
              to="${escapeXmlAttribute(resOut)}/values/styles.xml" />
    <merge from="root/res/values-v11/styles.xml"
             to="${escapeXmlAttribute(resOut)}/values-v11/styles.xml" />
    <instantiate from="root/res/layout/activity_fullscreen.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/layout/${layoutName}.xml" />

    <merge from="root/res/values/strings.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/strings.xml" />

    <instantiate from="root/src/app_package/FullscreenActivity.java.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${activityClass}.java" />
    <instantiate from="root/src/app_package/util/SystemUiHider.java.ftl"
                   to="${escapeXmlAttribute(srcOut)}/util/SystemUiHider.java" />
    <instantiate from="root/src/app_package/util/SystemUiHiderBase.java.ftl"
                   to="${escapeXmlAttribute(srcOut)}/util/SystemUiHiderBase.java" />
    <instantiate from="root/src/app_package/util/SystemUiHiderHoneycomb.java.ftl"
                   to="${escapeXmlAttribute(srcOut)}/util/SystemUiHiderHoneycomb.java" />

    <open file="${escapeXmlAttribute(resOut)}/layout/${layoutName}.xml" />
</recipe>
