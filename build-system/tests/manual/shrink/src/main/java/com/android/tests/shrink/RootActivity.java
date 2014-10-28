package com.android.tests.shrink;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Menu;

public class RootActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.used1);
        ResourceReferences.referenceResources(this);
        System.out.println(R.layout.used7);
    }

    public void unusedMethod() {
        Drawable drawable = getResources().getDrawable(R.drawable.unused10);
        System.out.println(drawable);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.used13, menu);
        return true;
    }}
