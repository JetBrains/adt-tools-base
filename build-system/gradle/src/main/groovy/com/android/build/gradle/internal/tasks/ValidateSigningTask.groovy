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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.LoggerWrapper
import com.android.builder.model.SigningConfig
import com.android.ide.common.signing.KeystoreHelper
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.tooling.BuildException

/**
 * A validate task that creates the debug keystore if it's missing.
 * It only creates it if it's in the default debug keystore location.
 *
 * It's linked to a given SigningConfig
 *
 */
class ValidateSigningTask extends BaseTask {

    SigningConfig signingConfig

    /**
     * Annotated getter for task input.
     *
     * This is an Input and not an InputFile because the file might not exist.
     * This is not actually used by the task, this is only for Gradle to check inputs.
     *
     * @return the path of the keystore.
     */
    @Input @Optional
    String getStoreLocation() {
        File f = signingConfig.getStoreFile()
        if (f != null) {
            return f.absolutePath
        }
        return null;
    }

    @TaskAction
    void validate() {

        File storeFile = signingConfig.getStoreFile()
        if (storeFile != null && !storeFile.exists()) {
            if (KeystoreHelper.defaultDebugKeystoreLocation().equals(storeFile.absolutePath)) {
                getLogger().info("Creating default debug keystore at %s" + storeFile.absolutePath)
                if (!KeystoreHelper.createDebugStore(
                        signingConfig.getStoreType(), signingConfig.getStoreFile(),
                        signingConfig.getStorePassword(), signingConfig.getKeyPassword(),
                        signingConfig.getKeyAlias(), getILogger())) {
                    throw new BuildException("Unable to recreate missing debug keystore.", null);
                }
            }
        }
    }
}
