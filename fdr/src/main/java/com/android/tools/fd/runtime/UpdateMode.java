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
package com.android.tools.fd.runtime;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static com.android.tools.fd.runtime.Server.UPDATE_MODE_COLD_SWAP;
import static com.android.tools.fd.runtime.Server.UPDATE_MODE_HOT_SWAP;
import static com.android.tools.fd.runtime.Server.UPDATE_MODE_NONE;
import static com.android.tools.fd.runtime.Server.UPDATE_MODE_WARM_SWAP;

@IntDef({UPDATE_MODE_COLD_SWAP,
        UPDATE_MODE_WARM_SWAP,
        UPDATE_MODE_HOT_SWAP,
        UPDATE_MODE_NONE})
@Retention(RetentionPolicy.SOURCE)
@interface UpdateMode {
}
