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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.builder.BuilderConstants;
import com.android.builder.model.SigningConfig;
import com.android.builder.signing.DefaultSigningConfig;
import com.android.prefs.AndroidLocation;
import com.google.common.base.Objects;
import org.gradle.api.Named;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.tooling.BuildException;

import java.io.File;
import java.io.Serializable;

/**
 * DSL overlay for {@link DefaultSigningConfig}.
 */
public class SigningConfigDsl extends DefaultSigningConfig implements Serializable, Named {
    private static final long serialVersionUID = 1L;

    /**
     * Creates a SigningConfig with a given name.
     *
     * @param name the name of the signingConfig.
     *
     */
    public SigningConfigDsl(@NonNull String name) {
        super(name);

        if (BuilderConstants.DEBUG.equals(name)) {
            try {
                initDebug();
            } catch (AndroidLocation.AndroidLocationException e) {
                throw new BuildException("Failed to get default debug keystore location", e);
            }
        }
    }

    public SigningConfigDsl initWith(SigningConfig that) {
        setStoreFile(that.getStoreFile());
        setStorePassword(that.getStorePassword());
        setKeyAlias(that.getKeyAlias());
        setKeyPassword(that.getKeyPassword());
        return this;
    }

    /**
     * Store file getter override to annotate it with Gradle's input annotation.
     */
    @Override
    @InputFile @Optional
    public File getStoreFile() {
        return super.getStoreFile();
    }

    /**
     * Store password getter override to annotate it with Gradle's input annotation.
     */
    @Override
    @Input
    public String getStorePassword() {
        return super.getStorePassword();
    }

    /**
     * Key alias getter override to annotate it with Gradle's input annotation.
     */
    @Override
    @Input
    public String getKeyAlias() {
        return super.getKeyAlias();
    }

    /**
     * Key password getter override to annotate it with Gradle's input annotation.
     */
    @Override
    @Input
    public String getKeyPassword() {
        return super.getKeyPassword();
    }

    /**
     * Store Type getter override to annotate it with Gradle's input annotation.
     */
    @Override
    @Input
    public String getStoreType() {
        return super.getStoreType();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        SigningConfigDsl that = (SigningConfigDsl) o;

        if (!mName.equals(that.mName)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + mName.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("name", mName)
                .add("storeFile", getStoreFile() != null ? getStoreFile().getAbsolutePath() : "null")
                .add("storePassword", getStorePassword())
                .add("keyAlias", getKeyAlias())
                .add("keyPassword", getKeyPassword())
                .add("storeType", getStoreFile())
                .toString();
    }
}
