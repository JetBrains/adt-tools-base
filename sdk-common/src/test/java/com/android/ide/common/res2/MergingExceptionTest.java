/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.ide.common.res2;

import junit.framework.TestCase;

import java.io.File;

public class MergingExceptionTest extends TestCase {
    @SuppressWarnings("ThrowableInstanceNeverThrown")
    public void testGetMessage() {
        File file = new File("/some/random/path");
        assertEquals("Error: My error message",
                new MergingException("My error message").getMessage());
        assertEquals("Error: My error message",
                new MergingException("Error: My error message").getMessage());
        assertEquals("/some/random/path: Error: My error message",
                new MergingException("My error message").setFile(file).getMessage());
        assertEquals("/some/random/path:50: Error: My error message",
                new MergingException("My error message").setFile(file).setLine(50).getMessage());
        assertEquals("/some/random/path:50:4: Error: My error message",
                new MergingException("My error message").setFile(file).setLine(50).setColumn(4)
                        .getMessage());
        assertEquals("/some/random/path:50:4: Error: My error message",
                new MergingException("My error message").setFile(file).setLine(50).setColumn(4)
                        .getLocalizedMessage());
        assertEquals("/some/random/path: Error: My error message",
                new MergingException("/some/random/path: My error message").setFile(file)
                        .getMessage());
        assertEquals("/some/random/path: Error: My error message",
                new MergingException("/some/random/path My error message").setFile(file)
                        .getMessage());

        // end of string handling checks
        assertEquals("/some/random/path: Error: ",
                new MergingException("/some/random/path").setFile(file).getMessage());
        assertEquals("/some/random/path: Error: ",
                new MergingException("/some/random/path").setFile(file).getMessage());
        assertEquals("/some/random/path: Error: ",
                new MergingException("/some/random/path:").setFile(file).getMessage());
        assertEquals("/some/random/path: Error: ",
                new MergingException("/some/random/path: ").setFile(file).getMessage());
    }
}
