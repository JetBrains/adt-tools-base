<recipe folder="root://activities/common">
    <merge from="root/res/values/no_actionbar_styles.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/styles.xml" />

<#if buildApi gte 21>
    <merge from="root/res/values-v21/no_actionbar_styles_v21.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values-v21/styles.xml" />
</#if>
</recipe>
