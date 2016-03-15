<?xml version="1.0" encoding="utf-8"?>
<com.google.tnt.sherpa.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    xmlns:sherpa="http://schemas.android.com/apk/res-auto"
    android:id="@+id/${simpleLayoutName}"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:paddingLeft="@dimen/activity_horizontal_margin"
    android:paddingRight="@dimen/activity_horizontal_margin"
    android:paddingTop="@dimen/activity_vertical_margin"
    android:paddingBottom="@dimen/activity_vertical_margin"
<#if hasAppBar && appBarLayoutName??>
    sherpa:layout_behavior="@string/appbar_scrolling_view_behavior"
    tools:showIn="@layout/${appBarLayoutName}"
</#if>
    tools:context="${relativePackage}.${activityClass}">

<#if isNewProject!false>
    <TextView
        android:text="Hello World!"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content" />
</#if>
</com.google.tnt.sherpa.ConstraintLayout>
