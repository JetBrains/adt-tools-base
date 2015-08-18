<?xml version="1.0"?>
<recipe>
    <dependency mavenUrl="com.android.support:support-v4:${buildApi}.+" />

    <#if hasAppBar>
      <dependency mavenUrl="com.android.support:design:${buildApi}.+"/>
    </#if>

    <merge from="root/AndroidManifest.xml.ftl"
             to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />

    <#if minApiLevel lt 13>
      <merge from="root/res/values-large/refs.xml.ftl"
               to="${escapeXmlAttribute(resOut)}/values-large/refs.xml" />
      <merge from="root/res/values-sw600dp/refs.xml.ftl"
               to="${escapeXmlAttribute(resOut)}/values-sw600dp/refs.xml" />
    </#if>
    <merge from="root/res/values/strings.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/strings.xml" />
    <#if hasAppBar>
      <merge from="root/res/values/dimens.xml"
               to="${escapeXmlAttribute(resOut)}/values/dimens.xml" />
      <execute file="../common/recipe_no_actionbar.xml.ftl" />
    </#if>

    <instantiate from="root/res/layout/activity_content_detail.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/layout/activity_${detail_name}.xml" />
    <instantiate from="root/res/layout/activity_content_list.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/layout/activity_${collection_name}.xml" />
    <#if minApiLevel lt 13>
      <instantiate from="root/res/layout/activity_content_twopane.xml.ftl"
                     to="${escapeXmlAttribute(resOut)}/layout/activity_${extractLetters(objectKind?lower_case)}_twopane.xml" />
    <#else>
      <instantiate from="root/res/layout/activity_content_twopane.xml.ftl"
                     to="${escapeXmlAttribute(resOut)}/layout-sw600dp/activity_${extractLetters(objectKind?lower_case)}_list.xml" />
    </#if>
    <instantiate from="root/res/layout/fragment_content_detail.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/layout/fragment_${detail_name}.xml" />
    <#if hasAppBar>
      <instantiate from="root/res/layout/activity_content_master_app_bar.xml.ftl"
                     to="${escapeXmlAttribute(resOut)}/layout/activity_${extractLetters(objectKind?lower_case)}_app_bar.xml" />
    </#if>

    <instantiate from="root/src/app_package/ContentDetailActivity.java.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${DetailName}Activity.java" />
    <instantiate from="root/src/app_package/ContentDetailFragment.java.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${DetailName}Fragment.java" />
    <instantiate from="root/src/app_package/ContentListActivity.java.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${CollectionName}Activity.java" />
    <instantiate from="root/src/app_package/ContentListFragment.java.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${CollectionName}Fragment.java" />
    <instantiate from="root/src/app_package/dummy/DummyContent.java.ftl"
                   to="${escapeXmlAttribute(srcOut)}/dummy/DummyContent.java" />

    <open file="${escapeXmlAttribute(srcOut)}/${DetailName}Fragment.java" />
    <open file="${escapeXmlAttribute(resOut)}/layout/fragment_${detail_name}.xml" />
</recipe>
