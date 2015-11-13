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

package com.android.sdklib.repositoryv2;

import com.android.annotations.NonNull;

import javax.xml.bind.annotation.XmlTransient;

/**
 * A string with both user-friendly and easily-parsed versions.
 * Contains stubs to be overridden by xjc-generated classes.
 */
@SuppressWarnings("NullableProblems")
@XmlTransient
public abstract class IdDisplay {

    /**
     * Sets the machine-friendly version of the string.
     */
    public abstract void setId(@NonNull String id);
    /**
     * Sets the user-friendly version of the string.
     */
    public abstract void setDisplay(@NonNull String display);

    /**
     * Gets the machine-friendly version of the string.
     */
    @NonNull
    public abstract String getId();

    /**
     * Gets the user-friendly version of the string.
     */
    @NonNull
    public abstract String getDisplay();
}
