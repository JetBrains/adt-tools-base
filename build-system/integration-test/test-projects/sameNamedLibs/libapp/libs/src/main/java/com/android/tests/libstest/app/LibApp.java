package com.android.tests.libstest.app;

import com.android.tests.libstest.libapp.R;

import android.app.Activity;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class LibApp {
    
    public static void handleTextView(Activity a) {
        TextView tv = (TextView) a.findViewById(R.id.libapp_text2);
        if (tv != null) {
            tv.setText(getContent());
        }
    }

    private static String getContent() {
        InputStream input = LibApp.class.getResourceAsStream("Libapp.txt");
        if (input == null) {
            return "FAILED TO FIND Libapp.txt";
        }

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));

            return reader.readLine();
        } catch (IOException e) {
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
        }
        
        return "FAILED TO READ CONTENT";
    }
}
