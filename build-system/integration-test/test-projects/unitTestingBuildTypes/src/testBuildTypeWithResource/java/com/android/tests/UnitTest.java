/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tests;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Ignore;
import android.app.Application;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import org.junit.Test;

import java.io.InputStream;
import java.net.URL;

public class UnitTest {
    @Test
    public void javaResourcesOnClasspath() throws Exception {
        URL url = UnitTest.class.getClassLoader().getResource("resource_file.txt");
        assertNotNull(url);

        InputStream stream = UnitTest.class.getClassLoader().getResourceAsStream("resource_file.txt");
        assertNotNull("expected resource_file.txt to be opened as a stream", stream);
        byte[] line = new byte[1024];
        assertTrue("Expected >0 bytes read from input stream", stream.read(line) > 0);
        String s = new String(line, "UTF-8").trim();
        assertEquals("success", s);
    }

    @Test
    public void prodJavaResourcesOnClasspath() throws Exception {
        URL url = UnitTest.class.getClassLoader().getResource("prod_resource_file.txt");
        assertNotNull(url);

        InputStream stream = UnitTest.class.getClassLoader().getResourceAsStream("prod_resource_file.txt");
        assertNotNull("expected resource_file.txt to be opened as a stream", stream);
        byte[] line = new byte[1024];
        assertTrue("Expected >0 bytes read from input stream", stream.read(line) > 0);
        String s = new String(line, "UTF-8").trim();
        assertEquals("prod", s);
    }
}
