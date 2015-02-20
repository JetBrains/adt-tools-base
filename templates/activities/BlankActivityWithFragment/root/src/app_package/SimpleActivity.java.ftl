package ${packageName};

import <#if appCompat>android.support.v7.app.ActionBarActivity<#else>android.app.Activity</#if>;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

<#if applicationPackage??>import ${applicationPackage}.R;</#if>

public class ${activityClass} extends ${appCompat?string('ActionBar','')}Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.${layoutName});
    }

    <#include "include_options_menu.java.ftl">
}
