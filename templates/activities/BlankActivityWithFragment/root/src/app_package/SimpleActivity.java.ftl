package ${packageName};

import ${superClassFqcn};
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
<#if applicationPackage??>
import ${applicationPackage}.R;
</#if>

public class ${activityClass} extends ${superClass} {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.${layoutName});
    }

    <#include "include_options_menu.java.ftl">
}
