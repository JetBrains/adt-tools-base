/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.builder.internal;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.BaseConfig;
import com.android.builder.model.ClassField;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * An object that contain a BuildConfig configuration
 */
public abstract class BaseConfigImpl implements Serializable, BaseConfig {
    private static final long serialVersionUID = 1L;

    private final Map<String, ClassField> mBuildConfigFields = Maps.newTreeMap();
    private final Map<String, ClassField> mResValues = Maps.newTreeMap();
    private final List<File> mProguardFiles = Lists.newArrayList();
    private final List<File> mConsumerProguardFiles = Lists.newArrayList();
    private final Map<String, String> mManifestPlaceholders = Maps.newHashMap();
    @Nullable
    private Boolean mMultiDex;

    public void addBuildConfigField(@NonNull ClassField field) {
        mBuildConfigFields.put(field.getName(), field);
    }

    public void addResValue(@NonNull ClassField field) {
        mResValues.put(field.getName(), field);
    }

    public void addResValues(@NonNull Map<String, ClassField> values) {
        mResValues.putAll(values);
    }

    @Override
    @NonNull
    public Map<String, ClassField> getBuildConfigFields() {
        return mBuildConfigFields;
    }

    public void addBuildConfigFields(@NonNull Map<String, ClassField> fields) {
        mBuildConfigFields.putAll(fields);
    }

    @NonNull
    @Override
    public Map<String, ClassField> getResValues() {
        return mResValues;
    }

    @Override
    @NonNull
    public List<File> getProguardFiles() {
        return mProguardFiles;
    }

    @Override
    @NonNull
    public List<File> getConsumerProguardFiles() {
        return mConsumerProguardFiles;
    }


    @NonNull
    @Override
    public Map<String, String> getManifestPlaceholders() {
        return mManifestPlaceholders;
    }

    public void addManifestPlaceHolders(@NonNull Map<String, String> manifestPlaceholders) {
        mManifestPlaceholders.putAll(manifestPlaceholders);
    }

    public void setManifestPlaceholders(@NonNull Map<String, String> manifestPlaceholders) {
        mManifestPlaceholders.clear();
        this.mManifestPlaceholders.putAll(manifestPlaceholders);
    }

    protected void _initWith(@NonNull BaseConfig that) {
        setBuildConfigFields(that.getBuildConfigFields());
        setResValues(that.getResValues());

        mProguardFiles.clear();
        mProguardFiles.addAll(that.getProguardFiles());

        mConsumerProguardFiles.clear();
        mConsumerProguardFiles.addAll(that.getConsumerProguardFiles());

        mManifestPlaceholders.clear();
        mManifestPlaceholders.putAll(that.getManifestPlaceholders());

        mMultiDex = that.getMultiDex();
    }

    private void setBuildConfigFields(@NonNull Map<String, ClassField> fields) {
        mBuildConfigFields.clear();
        mBuildConfigFields.putAll(fields);
    }

    private void setResValues(@NonNull Map<String, ClassField> fields) {
        mResValues.clear();
        mResValues.putAll(fields);
    }

    @Override
    @Nullable
    public Boolean getMultiDex() {
        return mMultiDex;
    }

    public void setMultiDex(@Nullable Boolean multiDex) {
        mMultiDex = multiDex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BaseConfigImpl)) {
            return false;
        }

        BaseConfigImpl that = (BaseConfigImpl) o;

        if (!mBuildConfigFields.equals(that.mBuildConfigFields)) {
            return false;
        }
        if (!mConsumerProguardFiles.equals(that.mConsumerProguardFiles)) {
            return false;
        }
        if (!mManifestPlaceholders.equals(that.mManifestPlaceholders)) {
            return false;
        }
        if (mMultiDex != null ? !mMultiDex.equals(that.mMultiDex) : that.mMultiDex != null) {
            return false;
        }
        if (!mProguardFiles.equals(that.mProguardFiles)) {
            return false;
        }
        if (!mResValues.equals(that.mResValues)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = mBuildConfigFields.hashCode();
        result = 31 * result + mResValues.hashCode();
        result = 31 * result + mProguardFiles.hashCode();
        result = 31 * result + mConsumerProguardFiles.hashCode();
        result = 31 * result + mManifestPlaceholders.hashCode();
        result = 31 * result + (mMultiDex != null ? mMultiDex.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "BaseConfigImpl{" +
                "mBuildConfigFields=" + mBuildConfigFields +
                ", mResValues=" + mResValues +
                ", mProguardFiles=" + mProguardFiles +
                ", mConsumerProguardFiles=" + mConsumerProguardFiles +
                ", mManifestPlaceholders=" + mManifestPlaceholders +
                ", mMultiDex=" + mMultiDex +
                '}';
    }
}
