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

package com.android.builder.internal.packaging.sign;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.internal.packaging.sign.v2.ApkSignerV2;
import com.android.builder.internal.packaging.sign.v2.ByteArrayDigestSource;
import com.android.builder.internal.packaging.sign.v2.DigestSource;
import com.android.builder.internal.packaging.sign.v2.SignatureAlgorithm;
import com.android.builder.internal.packaging.sign.v2.ZFileDigestSource;
import com.android.builder.internal.packaging.zip.StoredEntry;
import com.android.builder.internal.packaging.zip.ZFile;
import com.android.builder.internal.packaging.zip.ZFileExtension;
import com.android.builder.internal.utils.IOExceptionRunnable;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Extension that adds full APK signing. This extension will:
 * <ul>
 *     <li>Generate a new signature if the zip is modified after the extension is added.</li>
 *     <li>Generate a new signature even if the zip is not modified after the extension is
 *     added, but a valid signature is not found.</li>
 * </ul>
 * <p>
 * The extension computes the signature, if needed, at the {@link ZFileExtension#entriesWritten()}
 * event, after all zip entries have been written, but before the central directory or EOCD have.
 * This allows the extension to set the central directory offset parameter in the zip file
 * using {@link ZFile#setExtraDirectoryOffset(long)} adding enough space in the zip file for the
 * signature block.
 * <p>
 * The signature block is written before the central directory, allowing the zip file to be written
 * in sequential order.
 */
public class FullApkSignExtension {

    /**
     * The zip file this extension is registered with.
     */
    @NonNull
    private final ZFile mFile;

    /**
     * Signer certificate.
     */
    @NonNull
    private final X509Certificate mCertificate;

    /**
     * Signer private key.
     */
    @NonNull
    private final PrivateKey mPrivateKey;

    /**
     * APK Signature Scheme v2 algorithms to use for signing the APK.
     */
    private final List<SignatureAlgorithm> mV2SignatureAlgorithms;

    /**
     * {@code true} if the zip needs its signature to be updated.
     */
    private boolean mNeedsSignatureUpdate = true;

    /**
     * The extension to register with the {@link ZFile}. {@code null} if not registered.
     */
    @Nullable
    private ZFileExtension mExtension;

    /**
     * Creates a new extension. This will not register the extension with the provided
     * {@link ZFile}. Until {@link #register()} is invoked, this extension is not used.
     *
     * @param file the zip file to register the extension with
     * @param minSdkVersion minSdkVersion of the package
     * @param certificate sign certificate
     * @param privateKey the private key to sign the jar
     *
     * @throws InvalidKeyException if the signing key is not suitable for signing this APK.
     */
    public FullApkSignExtension(@NonNull ZFile file,
            int minSdkVersion,
            @NonNull X509Certificate certificate,
            @NonNull PrivateKey privateKey) throws InvalidKeyException {
        mFile = file;
        mCertificate = certificate;
        mPrivateKey = privateKey;
        mV2SignatureAlgorithms =
                ApkSignerV2.getSuggestedSignatureAlgorithms(
                        certificate.getPublicKey(), minSdkVersion);
    }

    /**
     * Registers the extension with the {@link ZFile} provided in the constructor.
     */
    public void register() {
        Preconditions.checkState(mExtension == null, "register() has already been invoked.");

        mExtension = new ZFileExtension() {
            @Nullable
            @Override
            public IOExceptionRunnable beforeUpdate() throws IOException {
                mFile.setExtraDirectoryOffset(0);
                return null;
            }

            @Nullable
            @Override
            public IOExceptionRunnable added(@NonNull StoredEntry entry,
                    @Nullable StoredEntry replaced) {
                onZipChanged();
                return null;
            }

            @Nullable
            @Override
            public IOExceptionRunnable removed(@NonNull StoredEntry entry) {
                onZipChanged();
                return null;
            }

            @Override
            public void entriesWritten() throws IOException {
                onEntriesWritten();
            }
        };

        mFile.addZFileExtension(mExtension);
    }

    /**
     * Invoked when the zip file has been changed.
     */
    private void onZipChanged() {
        mNeedsSignatureUpdate = true;
    }

    /**
     * Invoked before the zip file has been updated.
     *
     * @throws IOException failed to perform the update
     */
    private void onEntriesWritten() throws IOException {
        if (!mNeedsSignatureUpdate) {
            return;
        }
        mNeedsSignatureUpdate = false;

        byte[] apkSigningBlock = generateApkSigningBlock();
        Verify.verify(apkSigningBlock.length > 0, "apkSigningBlock.length == 0");
        mFile.setExtraDirectoryOffset(apkSigningBlock.length);
        long apkSigningBlockOffset =
                mFile.getCentralDirectoryOffset() - mFile.getExtraDirectoryOffset();
        mFile.directWrite(apkSigningBlockOffset, apkSigningBlock);
    }

    /**
     * Generates a signature for the APK.
     *
     * @return the signature data block
     * @throws IOException failed to generate a signature
     */
    @NonNull
    private byte[] generateApkSigningBlock() throws IOException {
        byte[] centralDirectoryData = mFile.getCentralDirectoryBytes();
        byte[] eocdData = mFile.getEocdBytes();

        ApkSignerV2.SignerConfig signerConfig = new ApkSignerV2.SignerConfig();
        signerConfig.privateKey = mPrivateKey;
        signerConfig.certificates = ImmutableList.of(mCertificate);
        signerConfig.signatureAlgorithms = mV2SignatureAlgorithms;
        DigestSource centralDir = new ByteArrayDigestSource(centralDirectoryData);
        DigestSource eocd = new ByteArrayDigestSource(eocdData);
        DigestSource zipEntries =
                new ZFileDigestSource(
                        mFile,
                        0,
                        mFile.getCentralDirectoryOffset() - mFile.getExtraDirectoryOffset());
        try {
            return ApkSignerV2.generateApkSigningBlock(
                    zipEntries,
                    centralDir,
                    eocd,
                    ImmutableList.of(signerConfig));
        } catch (InvalidKeyException | SignatureException e) {
            throw new IOException("Failed to sign APK using APK Signature Scheme v2", e);
        }
    }
}
