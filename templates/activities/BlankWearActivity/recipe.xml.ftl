<?xml version="1.0"?>
<recipe>

    <merge from="AndroidManifest.xml.ftl"
             to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />

    <merge from="res/values/strings.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/strings.xml" />

    <instantiate from="res/layout/blank_activity.xml.ftl"
            to="${escapeXmlAttribute(resOut)}/layout/${layoutName}.xml" />

    <instantiate from="res/layout/round.xml.ftl"
            to="${escapeXmlAttribute(resOut)}/layout/${roundLayout}.xml" />
    <instantiate from="res/layout/rect.xml.ftl"
            to="${escapeXmlAttribute(resOut)}/layout/${rectLayout}.xml" />

    <instantiate from="src/app_package/BlankActivity.java.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${activityClass}.java" />

    <open file="${escapeXmlAttribute(srcOut)}/${activityClass}.java" />
    <open file="${escapeXmlAttribute(resOut)}/layout/${layoutName}.xml" />
</recipe>
