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
import com.android.builder.internal.packaging.zip.StoredEntry;
import com.android.builder.internal.packaging.zip.ZFile;
import com.android.builder.internal.packaging.zip.ZFileExtension;
import com.android.builder.internal.utils.IOExceptionFunction;
import com.android.builder.internal.utils.IOExceptionRunnable;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.common.primitives.Ints;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

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
     * {@code true} if the zip needs its signature to be updated, {@code false} if we do not know
     * whether the signature needs to be updated.
     */
    private boolean mNeedsSignatureUpdate;

    /**
     * Computed signature if no signature has been computed. Being {@code null} doesn't mean there
     * is no signature in the file. It means it has not been computed in the extension. We don't
     * cache an existing signature here.
     */
    @Nullable
    private byte[] mComputedSignature;

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
     * The extension to register with the {@link ZFile}. {@code null} if not registered.
     */
    @Nullable
    private ZFileExtension mExtension;

    /**
     * Creates a new extension. This will not register the extension with the provided
     * {@link ZFile}. Until {@link #register()} is invoked, this extension is not used.
     *
     * @param file the zip file to register the extension with
     * @param certificate sign certificate
     * @param privateKey the private key to sign the jar
     */
    public FullApkSignExtension(@NonNull ZFile file, @NonNull X509Certificate certificate,
            @NonNull PrivateKey privateKey) {
        mFile = file;
        mCertificate = certificate;
        mPrivateKey = privateKey;
    }

    /**
     * Registers the extension with the {@link ZFile} provided in the constructor.
     */
    public void register() {
        Preconditions.checkState(mExtension == null, "register() has already been invoked.");

        mExtension = new ZFileExtension() {
            @Nullable
            @Override
            public IOExceptionRunnable added(@NonNull StoredEntry entry,
                    @Nullable StoredEntry replaced) {
                zipChanged();
                return null;
            }

            @Nullable
            @Override
            public IOExceptionRunnable removed(@NonNull StoredEntry entry) {
                zipChanged();
                return null;
            }

            @Override
            public void entriesWritten() throws IOException {
                doEntriesWritten();
            }
        };

        mFile.addZFileExtension(mExtension);
    }

    /**
     * Invoked when the zip file has been changed.
     */
    private void zipChanged() {
        mNeedsSignatureUpdate = true;
        mComputedSignature = null;
    }

    /**
     * Invoked before the zip file has been updated.
     *
     * @throws IOException failed to perform the update
     */
    private void doEntriesWritten() throws IOException {
        if (mNeedsSignatureUpdate || !hasValidSignature()) {
            mComputedSignature = generateSignature();
            Verify.verify(mComputedSignature.length > 0, "mComputedSignature.length == 0");
            mFile.setExtraDirectoryOffset(mComputedSignature.length);
            mNeedsSignatureUpdate = false;
            mFile.directWrite(mFile.getCentralDirectoryOffset() - mFile.getExtraDirectoryOffset(),
                    mComputedSignature);
        }
    }

    /**
     * Checks whether the zip has a currently valid signature.
     *
     * @return does the zip have a valid signature?
     * @throws IOException failed to verify
     */
    private boolean hasValidSignature() throws IOException {
        final byte[] signature = readCurrentSignature();
        if (signature == null) {
            /*
             * No signature block.
             */
            return false;
        } else {
            final byte[] centralDirectoryData = mFile.getCentralDirectoryBytes();
            final byte[] eocdData = mFile.getEocdBytes();
            Boolean hasValid = applyToAllEntries(
                    new IOExceptionFunction<InputStream, Boolean>() {
                @Nullable
                @Override
                public Boolean apply(@Nullable InputStream input) throws IOException {
                    Verify.verifyNotNull(input);
                    return verifySignature(input, centralDirectoryData, eocdData, signature);
                }
            });

            Verify.verifyNotNull(hasValid);
            return hasValid;
        }
    }

    /**
     * Reads the current zip file's signature, if any.
     *
     * @return the signature or {@code null} if there is no signature
     * @throws IOException failed to read the signature
     */
    @Nullable
    private byte[] readCurrentSignature() throws IOException {
        int signatureBlockSize = Ints.checkedCast(mFile.getExtraDirectoryOffset());
        if (signatureBlockSize == 0) {
            return null;
        }

        long signatureStart = mFile.getCentralDirectoryOffset() - signatureBlockSize;
        Verify.verify(signatureStart >= 0, "signatureStart < 0");

        if (signatureStart + signatureBlockSize >= mFile.directSize()) {
            /*
             * Not enough contents in the zip file to read the signature.
             */
            return null;
        }

        byte[] signature = new byte[signatureBlockSize];

        mFile.directFullyRead(signatureStart, signature);
        return signature;
    }

    /**
     * Applies a function that receives an input stream with the zip contents with all entries.
     *
     * @param function the function to apply
     * @param <T> the return type
     * @return the return value returned by the function
     * @throws IOException failed to open or close the stream; also thrown by the function
     */
    private <T> T applyToAllEntries(@NonNull IOExceptionFunction<InputStream, T> function)
            throws IOException {
        long centralDirectoryStart = mFile.getCentralDirectoryOffset();
        long entriesEnd = centralDirectoryStart - mFile.getExtraDirectoryOffset();
        Verify.verify(entriesEnd >= 0);
        Verify.verify(entriesEnd <= mFile.directSize());

        InputStream allEntriesData = mFile.directOpen(0, entriesEnd);
        boolean thrown = true;
        try {
            T result = function.apply(allEntriesData);
            thrown = false;
            return result;
        } finally {
            Closeables.close(allEntriesData, thrown);
        }
    }

    /**
     * Generates a signature for the zip.
     *
     * @return the signature data block
     * @throws IOException failed to generate a signature
     */
    @NonNull
    private byte[] generateSignature() throws IOException {
        final byte[] centralDirectoryData = mFile.getCentralDirectoryBytes();
        final byte[] eocdData = mFile.getEocdBytes();

        return applyToAllEntries(new IOExceptionFunction<InputStream, byte[]>() {
            @Nullable
            @Override
            public byte[] apply(@Nullable InputStream input) throws IOException {
                Verify.verifyNotNull(input, "input == null");
                return computeSignature(input, centralDirectoryData, eocdData);
            }
        });
    }

    /**
     * Verifies that the zip signature is correct.
     *
     * @param zipEntriesContent a stream that reads the whole zip contents
     * @param centralDirectoryData the data of the zip's central directory
     * @param eocdData the data in the EOCD record
     * @param signature the signature block to verify
     * @return is the signature correct?
     * @throws IOException failed to verify the signature
     */
    private static boolean verifySignature(@NonNull InputStream zipEntriesContent,
            @NonNull byte[] centralDirectoryData, @NonNull byte[] eocdData,
            @NonNull byte[] signature) throws IOException {
        /*
         * Currently, we do not implement "verifySignature" so we always act as if the signature
         * is invalid.
         */
        return false;
    }

    /**
     * Computes the zip file signature block.
     *
     * @param zipEntriesContent a stream that reads the whole zip contents, this may come directly
     * disk or memory, depending on where the contents of the zip is.
     * @param centralDirectoryData the data of the zip's central directory
     * @param eocdData the data in the EOCD record
     * @return the signature block
     * @throws IOException failed to compute the signature or failed to read data from the
     * zip file
     */
    @NonNull
    private static byte[] computeSignature(@NonNull InputStream zipEntriesContent,
            @NonNull byte[] centralDirectoryData, @NonNull byte[] eocdData) throws IOException {
        // TODO: Implement a *real* signature here. This is just a hash...
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        ByteStreams.copy(zipEntriesContent, bytesOut);
        bytesOut.write(centralDirectoryData);
        bytesOut.write(eocdData);
        return Hashing.sha1().hashBytes(bytesOut.toByteArray()).asBytes();
    }
}
