package ${packageName};

import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;
<#if parentActivityClass != "">
import android.view.MenuItem;
import android.support.v4.app.NavUtils;
</#if>

public class ${activityClass} extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.${layoutName});
        <#if parentActivityClass != "">
        // Show the Up button in the action bar.
        getActionBar().setDisplayHomeAsUpEnabled(true);
        </#if>
    }

    <#include "include_onCreateOptionsMenu.java.ftl">
    <#include "include_onOptionsItemSelected.java.ftl">

}
