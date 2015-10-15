<?xml version="1.0"?>
<globals>
    <global id="CollectionName" value="${extractLetters(objectKind)}List" />
    <global id="collection_name" value="${extractLetters(objectKind?lower_case)}_list" />
    <global id="DetailName" value="${extractLetters(objectKind)}Detail" />
    <global id="detail_name" value="${extractLetters(objectKind?lower_case)}_detail" />
    <globals file="../common/common_globals.xml.ftl" />
</globals>
