<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="background">#000000</color>
<#if isInteractive>
    <color name="background2">#000088</color>
</#if>
<#if style == "analog">
    <color name="analog_hands">#cccccc</color>
<#elseif style == "digital">
    <color name="digital_text">#ffffff</color>
</#if>
</resources>
