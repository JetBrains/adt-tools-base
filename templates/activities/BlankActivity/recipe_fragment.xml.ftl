<?xml version="1.0"?>
<recipe>
    <execute file="../common/recipe_simple_dimens.xml" />
    <execute file="../common/recipe_simple_menu.xml.ftl" />

    <instantiate from="root/res/layout/activity_fragment_container.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/layout/${simpleLayoutName}.xml" />

    <instantiate from="root/res/layout/fragment_simple.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/layout/${fragmentLayoutName}.xml" />

    <instantiate from="root/src/app_package/SimpleActivityFragment.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${fragmentClass}.java" />
</recipe>
