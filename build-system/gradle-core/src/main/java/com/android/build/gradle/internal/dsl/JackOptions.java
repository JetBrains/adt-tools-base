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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Objects;
import com.google.common.collect.Maps;

import java.util.Map;

/**
 * DSL object for configuring Jack options.
 *
 * <p>See <a href="http://tools.android.com/tech-docs/jackandjill">Jack and Jill</a>
 */
@SuppressWarnings("UnnecessaryInheritDoc")
public class JackOptions implements CoreJackOptions {
    @Nullable
    private Boolean isEnabledFlag;
    @Nullable
    private Boolean isJackInProcessFlag;
    @NonNull
    private Map<String, String> additionalParameters = Maps.newHashMap();

    void _initWith(CoreJackOptions that) {
        isEnabledFlag = that.isEnabled();
        isJackInProcessFlag = that.isJackInProcess();
        additionalParameters = Maps.newHashMap(that.getAdditionalParameters());
    }

    /** {@inheritDoc} */
    @Override
    @Nullable
    public Boolean isEnabled() {
        return isEnabledFlag;
    }

    public void setEnabled(@Nullable Boolean enabled) {
        isEnabledFlag = enabled;
    }

    /** {@inheritDoc} */
    @Override
    @Nullable
    public Boolean isJackInProcess() {
        return isJackInProcessFlag;
    }

    public void setJackInProcess(@Nullable Boolean jackInProcess) {
        isJackInProcessFlag = jackInProcess;
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public Map<String, String> getAdditionalParameters() {
        return additionalParameters;
    }

    public void setAdditionalParameters(@NonNull Map<String, String> additionalParameters) {
        this.additionalParameters.clear();
        this.additionalParameters.putAll(additionalParameters);
    }

    public void additionalParameters(@NonNull Map<String, String> additionalParameters) {
        this.additionalParameters.putAll(additionalParameters);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JackOptions that = (JackOptions) o;
        return Objects.equal(isEnabledFlag, that.isEnabledFlag)
                && Objects.equal(isJackInProcessFlag, that.isJackInProcessFlag)
                && Objects.equal(additionalParameters, that.additionalParameters);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(isEnabledFlag, isJackInProcessFlag, additionalParameters);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("isEnabled", isEnabledFlag)
                .add("isJackInProcess", isJackInProcessFlag)
                .add("additionalParameters", isJackInProcessFlag)
                .toString();
    }
}
