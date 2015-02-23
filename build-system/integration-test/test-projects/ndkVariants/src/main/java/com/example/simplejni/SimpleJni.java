/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.simplejni;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class SimpleJni extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /* Create a TextView and set its content.
         * the text is retrieved by calling a native
         * function.
         */
        StringBuilder sb = new StringBuilder(
                productFlavorFromJni().equals("free") ?
                        "Free as in beer!\n" :
                        "Show me the money!\n");
        sb.append("(");
        sb.append(buildTypeFromJni());
        sb.append(")");

        TextView  tv = new TextView(this);
        tv.setText(sb.toString());
        setContentView(tv);
    }

    /* Native methods implemented by the 'simple-jni' native library.  The return value should
     * change depending on the product flavor and build type.
     */
    public native String buildTypeFromJni();

    public native String productFlavorFromJni();

    /* Load the 'simple-jni' library on application startup. */
    static {
        System.loadLibrary("simple-jni");
    }
}
