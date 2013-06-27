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
package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Issue;

// Temporary compatibility for ADT until prebuilts are updated
@Deprecated
public class ManifestOrderDetector {
    @Deprecated
    public static final Issue ALLOW_BACKUP = ManifestDetector.ALLOW_BACKUP;
    @Deprecated
    public static final Issue TARGET_NEWER = ManifestDetector.TARGET_NEWER;

}
