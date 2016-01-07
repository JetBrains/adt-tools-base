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

package com.android.ide.common.util;

import java.io.Closeable;
import java.net.URLClassLoader;

public final class UrlClassLoaderUtil {
    /**
     * Calls classLoader.close() on Java 7 and above. A no-op on Java 6. Fails silently.
     *
     * Work around on java 7 for http://bugs.java.com/bugdatabase/view_bug.do?bug_id=5041014 :
     * URLClassLoader, on Windows, locks the .jar file forever.
     */
    public static void attemptToClose(URLClassLoader classLoader) {
        if (classLoader instanceof Closeable) {
            try {
                ((Closeable) classLoader).close();
            } catch (Throwable e) {
                // Ignore - unable to close.
            }
        }
    }
}
