package com.android.tests.basic;

import android.app.Activity;
import android.os.Bundle;

public class Main extends Activity
{
    int mId;
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mId = getResources().getIdentifier("icon", "drawable", getPackageName());
    }
}
