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

package com.android.jack.api;

import org.junit.Assert;
import org.junit.Test;

/**
 * Whenever the jack-api module is change, the version must also change.
 *
 * There isn't really a good way to check if the module is changed.  Assume any changes in this
 * module is most likely due to addition of new ApiConfig.  The test only serves as a reminder to
 * update the module version.
 */
public class VersionTest {

    @Test
    public void checkApi04Config() {
        try {
            Class.forName("com.android.jack.api.v04.Api04Config");
            Assert.fail("Reminder to update test and module version when Api04Config is added.");
        } catch (ClassNotFoundException ignored) {
        }
    }
}
