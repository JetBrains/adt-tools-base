<?xml version="1.0"?>
<globals>
    <global id="resOut" value="${resDir}" />
    <global id="srcOut" value="${srcDir}/${slashedPackageName(packageName)}" />
    <global id="collection_name" value="${extractLetters(objectKind?lower_case)}" />
    <global id="className" value="${extractLetters(objectKind)}Fragment" />
    <global id="fragment_layout" value="fragment_${extractLetters(objectKind?lower_case)}" />
</globals>
