package ${packageName};

<#if hasAppBar>
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
<#else>
import ${superClassFqcn};
</#if>
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
<#if hasAppBar>
        setContentView(R.layout.${appBarLayoutName});
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        CollapsingToolbarLayout appBarLayout = (CollapsingToolbarLayout) findViewById(R.id.toolbar_layout);
        appBarLayout.setTitle(getTitle());
<#else>
        setContentView(R.layout.${layoutName});
</#if>
<#if parentActivityClass != "">
        get${Support}ActionBar().setDisplayHomeAsUpEnabled(true);
</#if>
    }

<#if isNewProject>
    <#include "include_options_menu.java.ftl">
</#if>
}
