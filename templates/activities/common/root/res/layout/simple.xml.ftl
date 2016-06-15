<?xml version="1.0" encoding="utf-8"?>
<android.support.constraint.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/${simpleLayoutName}"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
<#if hasAppBar && appBarLayoutName??>
    tools:showIn="@layout/${appBarLayoutName}"
</#if>
    tools:context="${relativePackage}.${activityClass}">

<#if isNewProject!false>
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Hello World!"
        app:layout_constraintBottom_toBottomOf="@+id/${simpleLayoutName}"
        app:layout_constraintLeft_toLeftOf="@+id/${simpleLayoutName}"
        app:layout_constraintRight_toRightOf="@+id/${simpleLayoutName}"
        app:layout_constraintTop_toTopOf="@+id/${simpleLayoutName}" />

</#if>
</android.support.constraint.ConstraintLayout>
