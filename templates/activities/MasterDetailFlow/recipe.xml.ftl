<?xml version="1.0"?>
<recipe>
    <dependency mavenUrl="com.android.support:support-v4:${targetApi}.+" />

    <merge from="AndroidManifest.xml.ftl"
             to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />

    <#if minApiLevel lt 13>
      <merge from="res/values-large/refs.xml.ftl"
               to="${escapeXmlAttribute(resOut)}/values-large/refs.xml" />
      <merge from="res/values-sw600dp/refs.xml.ftl"
               to="${escapeXmlAttribute(resOut)}/values-sw600dp/refs.xml" />
    </#if>
    <merge from="res/values/strings.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/strings.xml" />

    <instantiate from="res/layout/activity_content_detail.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/layout/activity_${detail_name}.xml" />
    <instantiate from="res/layout/activity_content_list.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/layout/activity_${collection_name}.xml" />
    <#if minApiLevel lt 13>
      <instantiate from="res/layout/activity_content_twopane.xml.ftl"
                     to="${escapeXmlAttribute(resOut)}/layout/activity_${extractLetters(objectKind?lower_case)}_twopane.xml" />
    <#else>
      <instantiate from="res/layout/activity_content_twopane.xml.ftl"
                     to="${escapeXmlAttribute(resOut)}/layout-sw600dp/activity_${extractLetters(objectKind?lower_case)}_list.xml" />
    </#if>
    <instantiate from="res/layout/fragment_content_detail.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/layout/fragment_${detail_name}.xml" />

    <instantiate from="src/app_package/ContentDetailActivity.java.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${DetailName}Activity.java" />
    <instantiate from="src/app_package/ContentDetailFragment.java.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${DetailName}Fragment.java" />
    <instantiate from="src/app_package/ContentListActivity.java.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${CollectionName}Activity.java" />
    <instantiate from="src/app_package/ContentListFragment.java.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${CollectionName}Fragment.java" />
    <instantiate from="src/app_package/dummy/DummyContent.java.ftl"
                   to="${escapeXmlAttribute(srcOut)}/dummy/DummyContent.java" />

    <open file="${escapeXmlAttribute(srcOut)}/${DetailName}Fragment.java" />
    <open file="${escapeXmlAttribute(resOut)}/layout/fragment_${detail_name}.xml" />
</recipe>
