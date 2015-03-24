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

import android.app.Activity;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import com.google.common.collect.Lists;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import org.junit.Ignore;
import org.junit.Test;

public class UnitTest {
    @Test
    public void referenceProductionCode() {
        // Reference code for the tested build type.
        DebugOnlyClass foo = new DebugOnlyClass();
        assertEquals("debug", foo.foo());
    }

    @Test
    public void resourcesOnClasspath() throws Exception {
        // resource_file.txt is only for buildTypeWithResource.
        URL url = UnitTest.class.getClassLoader().getResource("resource_file.txt");
        assertNull(url);

        InputStream stream = UnitTest.class.getClassLoader().getResourceAsStream("resource_file.txt");
        assertNull(stream);
    }

    @Test
    public void useDebugOnlyDependency() {
        List<String> strings = Lists.newArrayList();
    }
}
