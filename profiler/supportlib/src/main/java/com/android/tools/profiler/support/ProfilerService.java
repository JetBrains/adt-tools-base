/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.profiler.support;

import android.app.Application;
import android.util.Log;
import com.android.tools.profiler.support.profilerserver.ProfilerServer;

import java.io.IOException;

public class ProfilerService {
    public static void start(Application application) {
        Log.i(ProfilerServer.SERVER_NAME, "Advanced profiling is enabled and ready.");

        ProfilerServer.getInstance().initialize(application);
        try {
            ProfilerServer.getInstance().start();
            Log.i(ProfilerServer.SERVER_NAME, "Advanced profiling has started.");
        } catch (IOException e) {
            Log.e(ProfilerServer.SERVER_NAME, "Advanced profiling could not be started.", e);
        }
    }

    public static void stop() {
      Log.i(ProfilerServer.SERVER_NAME, "Advanced profiling is terminating.");
      try {
          ProfilerServer.getInstance().stop();
          Log.i(ProfilerServer.SERVER_NAME, "Advanced profiling terminated.");
      } catch (IOException e) {
          Log.e(ProfilerServer.SERVER_NAME, "Advanced profiling encountered an error while terminating.", e);
      }
    }
}
