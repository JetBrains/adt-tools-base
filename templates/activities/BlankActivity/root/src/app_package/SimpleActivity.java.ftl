package ${packageName};

import <#if appCompat?has_content>android.support.v7.app.ActionBarActivity<#else>android.app.Activity</#if>;
<#if features == 'fragment'>import android.<#if appCompat?has_content>support.v7.</#if>app.ActionBar;
import android.<#if appCompat?has_content>support.v4.</#if>app.Fragment;
</#if>
import android.os.Bundle;
<#if features == 'fragment'>import android.view.LayoutInflater;
</#if>import android.view.Menu;
import android.view.MenuItem;
<#if features == 'fragment'>import android.view.View;
import android.view.ViewGroup;
import android.os.Build;
</#if>
<#if applicationPackage??>import ${applicationPackage}.R;</#if>

public class ${activityClass} extends ${(appCompat?has_content)?string('ActionBar','')}Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.${layoutName});
        <#if features == 'fragment'>
        if (savedInstanceState == null) {
            get${Support}FragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment())
                    .commit();
        }
        </#if>
    }

    <#include "include_options_menu.java.ftl">

    <#if features == 'fragment'>
    <#include "include_fragment.java.ftl">
    </#if>
}
