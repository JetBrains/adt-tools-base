// Copyright 2015 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.android.tools.fd.runtime;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

// This is based on the reflection parts of
//     com.google.devtools.build.android.incrementaldeployment.StubApplication,
// plus changes to compile on JDK 6.
//
// (The code to handle resource loading etc is different; see FileManager.)
//
// The original is
// https://cs.corp.google.com/codesearch/f/piper///depot/google3/third_party/bazel/src/tools/android/java/com/google/devtools/build/android/incrementaldeployment/StubApplication.java?cl=93287264
// Public (May 11 revision, ca96e11)
// https://github.com/google/bazel/blob/master/src/tools/android/java/com/google/devtools/build/android/incrementaldeployment/StubApplication.java

/**
 * A stub application that patches the class loader, then replaces itself with the real application
 * by applying a liberal amount of reflection on Android internals.
 * <p/>
 * <p>This is, of course, terribly error-prone. Most of this code was tested with API versions
 * 8, 10, 14, 15, 16, 17, 18, 19 and 21 on the Android emulator, a Nexus 5 running Lollipop LRX22C
 * and a Samsung GT-I5800 running Froyo XWJPE. The exception is {@code monkeyPatchAssetManagers},
 * which only works on Kitkat and Lollipop.
 * <p/>
 * <p>Note that due to a bug in Dalvik, this only works on Kitkat if ART is the Java runtime.
 * <p/>
 * <p>Unfortunately, if this does not work, we don't have a fallback mechanism: as soon as we
 * build the APK with this class as the Application, we are committed to going through with it.
 * <p/>
 * <p>This class should use as few other classes as possible before the class loader is patched
 * because any class loaded before it cannot be incrementally deployed.
 */
public class BootstrapApplication extends Application {
    public static final String LOG_TAG = "fd";

    private String externalResourcePath;
    private Application realApplication;

    public BootstrapApplication() {
        if (Log.isLoggable(LOG_TAG, Log.INFO)) {
            Log.i(LOG_TAG, String.format(
                    "BootstrapApplication created. Android package is %s, real application class is %s.",
                    AppInfo.applicationId, AppInfo.applicationClass));
        }
    }

    private void createResources() {
        File file = FileManager.getExternalResourceFile();
        externalResourcePath = file != null ? file.getPath() : null;

        if (Log.isLoggable(LOG_TAG, Log.INFO)) {
            Log.i(LOG_TAG, "Resource override is" + externalResourcePath);
        }
    }

    private void createRealApplication(String codeCacheDir) {
        List<String> dexList = FileManager.getDexList();

        // Make sure class loader finds these
        Class<Server> server = Server.class;
        Class<MonkeyPatcher> patcher = MonkeyPatcher.class;

        ClassLoader classLoader = BootstrapApplication.class.getClassLoader();
        if (!dexList.isEmpty()) {
            if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                Log.i(LOG_TAG, "Bootstrapping class loader with dex list " + dexList);
            }

            String nativeLibraryPath = FileManager.getNativeLibraryFolder().getPath();
            IncrementalClassLoader.inject(
                    classLoader,
                    nativeLibraryPath,
                    codeCacheDir,
                    dexList);
        } else {
            if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                Log.i(LOG_TAG, "No override .dex files found");
            }
        }

        if (AppInfo.applicationClass != null) {
            if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                Log.i(LOG_TAG, "About to create real application of class name = " +
                        AppInfo.applicationClass);
            }

            try {
                @SuppressWarnings("unchecked")
                Class<? extends Application> realClass =
                        (Class<? extends Application>) Class.forName(AppInfo.applicationClass);
                if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                    Log.i(LOG_TAG, "Created delegate app class successfully : " + realClass +
                            " with class loader " + realClass.getClassLoader());
                }
                Constructor<? extends Application> constructor = realClass.getConstructor();
                realApplication = constructor.newInstance();
                if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                    Log.i(LOG_TAG, "Created real app instance successfully :" + realApplication);
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        } else {
            realApplication = new Application();
        }
    }

    @Override
    protected void attachBaseContext(Context context) {
        createResources();
        createRealApplication(context.getCacheDir().getPath());

        // This is called from ActivityThread#handleBindApplication() -> LoadedApk#makeApplication().
        // Application#mApplication is changed right after this call, so we cannot do the monkey
        // patching here. So just forward this method to the real Application instance.
        super.attachBaseContext(context);

        if (realApplication != null) {
            try {
                Method attachBaseContext =
                        ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
                attachBaseContext.setAccessible(true);
                attachBaseContext.invoke(realApplication, context);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Override
    public void onCreate() {
        MonkeyPatcher.monkeyPatchApplication(BootstrapApplication.this, realApplication,
                externalResourcePath);
        MonkeyPatcher.monkeyPatchExistingResources(externalResourcePath, null);
        super.onCreate();
        Server.create(AppInfo.applicationId, BootstrapApplication.this);
        //CrashHandler.startCrashCatcher(this);

        if (realApplication != null) {
            realApplication.onCreate();
        }
    }
}
