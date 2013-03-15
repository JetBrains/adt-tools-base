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

package com.android.ant;

import com.android.sdklib.internal.build.SignedJarBuilder;
import com.android.sdklib.internal.build.SignedJarBuilder.IZipEntryFilter;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.types.Path;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.cert.X509Certificate;

/**
 * Simple Task to sign an apk.
 *
 */
public class SignApkTask extends SingleInputOutputTask {

    private String mKeystore;
    private String mStorepass;
    private String mAlias;
    private String mKeypass;

    public void setKeystore(Path keystore) {
        mKeystore = TaskHelper.checkSinglePath("keystore", keystore);
    }

    public void setStorepass(String storepass) {
        mStorepass = storepass;
    }

    public void setAlias(String alias) {
        mAlias = alias;
    }

    public void setKeypass(String keypass) {
        mKeypass = keypass;
    }

    @Override
    protected void createOutput() throws BuildException {
        PrivateKeyEntry key = loadKeyEntry(
                mKeystore, null, mStorepass.toCharArray(),
                mAlias, mKeypass.toCharArray());

        if (key == null) {
            throw new BuildException(String.format("Signing key %s not found", mAlias));
        }

        SignedJarBuilder mBuilder = null;
        try {
            mBuilder = new SignedJarBuilder(
                    new FileOutputStream(getOutput(), false /* append */),
                    key.getPrivateKey(), (X509Certificate) key.getCertificate());

            mBuilder.writeZip(new FileInputStream(getInput()), new NullZipFilter());

            mBuilder.close();
        } catch (FileNotFoundException e) {
            throw new BuildException(String.format("Keystore '%s' is not found!", mKeystore));
        } catch (Exception e) {
            throw new BuildException(e.getMessage());
        } finally {
            if (mBuilder != null) {
                mBuilder.cleanUp();
            }
        }
    }

    /**
     * Loads the debug key from the keystore.
     * @param osKeyStorePath the OS path to the keystore.
     * @param storeType an optional keystore type, or <code>null</code> if the default is to
     * be used.
     * @return <code>true</code> if success, <code>false</code> if the keystore does not exist.
     */
    private PrivateKeyEntry loadKeyEntry(String osKeyStorePath, String storeType,
            char[] storePassword, String alias, char[] aliasPassword) {
        FileInputStream fis = null;
        try {
            KeyStore keyStore = KeyStore.getInstance(
                    storeType != null ? storeType : KeyStore.getDefaultType());
            fis = new FileInputStream(osKeyStorePath);
            keyStore.load(fis, storePassword);
            return (KeyStore.PrivateKeyEntry)keyStore.getEntry(
                    alias, new KeyStore.PasswordProtection(aliasPassword));
        } catch (Exception e) {
            String msg = e.getMessage();
            String causeMsg = null;

            Throwable cause = e.getCause();
            if (cause != null) {
                causeMsg = cause.getMessage();
            }

            if (msg != null) {
                if (causeMsg == null) {
                    throw new BuildException(msg);
                } else {
                    throw new BuildException(msg + ": " + causeMsg);
                }
            } else {
                if (causeMsg == null) {
                    throw new BuildException(e);
                } else {
                    throw new BuildException(causeMsg);
                }
            }
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    // pass
                }
            }
        }
    }

    private final static class NullZipFilter implements IZipEntryFilter {

        @Override
        public boolean checkEntry(String archivePath) throws ZipAbortException {
            return true;
        }
    }
}
