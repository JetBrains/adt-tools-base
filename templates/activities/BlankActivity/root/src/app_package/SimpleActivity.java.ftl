package ${packageName};

import <#if appCompat?has_content>android.support.v7.app.ActionBarActivity<#else>android.app.Activity</#if>;
import android.<#if appCompat?has_content>support.v7.</#if>app.ActionBar;
import android.<#if appCompat?has_content>support.v4.</#if>app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.os.Build;

public class ${activityClass} extends ${(appCompat?has_content)?string('ActionBar','')}Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.${layoutName});

        if (savedInstanceState == null) {
            get${Support}FragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment())
                    .commit();
        }
    }

    <#include "include_options_menu.java.ftl">

    <#include "include_fragment.java.ftl">

}
