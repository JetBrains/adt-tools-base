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

package com.android.builder.internal.packaging.sign;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.internal.packaging.zip.StoredEntry;
import com.android.builder.internal.packaging.zip.ZFile;
import com.android.builder.internal.packaging.zip.ZFileExtension;
import com.android.builder.internal.utils.IOExceptionRunnable;
import com.google.common.base.Charsets;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.DEROutputStream;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

/**
 * {@link ZFile} extension that signs all files in the APK and generates a signature file and a
 * digital signature of the signature file. The extension registers itself automatically with the
 * {@link ZFile} upon creation.
 * <p>
 * The signature extension will recompute signatures of files already in the zip file but won't
 * update the manifest if these signatures match the ones in the manifest.
 * <p>
 * This extension does 4 main tasks: maintaining the digests of all files in the zip in the manifest
 * file, maintaining the digests of all files in the zip in the signature file, maintaining the
 * digest of the manifest in the signature file and maintaining the digital signature file. For
 * performance, the digests and signatures are only computed when needed.
 * <p>
 * These tasks are done at three different moments: when the extension
 * is created, when files are added to the zip and before the zip is updated.
 * When the extension is created: (Note that the manifest's digest is <em>not</em> checked when
 * the extension is created.)
 * <ul>
 *     <li>The signature file is read, if one exists.
 *     <li>The signature "administrative" info is read and updated if not up-to-date.
 *     <li>The digests for entries in the manifest and signature file that do not correspond to
 *     any file in the zip are removed.
 *     <li>The digests for all entries in the zip are recomputed and updated in the signature file
 *     and in the manifest, if needed.
 * </ul>
 * <p>
 * When files are added or removed:
 * <ul>
 *     <li>The signature file and manifest are updated to reflect the changes.
 *     <li>If the file was added, its digest is computed.
 * </ul>
 * <p>
 * Before updating the zip file:
 * <ul>
 *     <li>If a signature file already exists, checks the digest of the manifest and updates the
 *     signature file if needed.
 *     <li>Creates the signature file if it did not already exist.
 *     <li>Recreates the digital signature of the signature file if the signature file was created
 *     or updated.
 * </ul>
 */
public class SignatureExtension {

    /**
     * Base of signature files.
     */
    private static final String SIGNATURE_BASE = ManifestGenerationExtension.META_INF_DIR + "/CERT";

    /**
     * Path of the signature file.
     */
    private static final String SIGNATURE_FILE = SIGNATURE_BASE + ".SF";

    /**
     * Name of attribute with the signature version.
     */
    private static final String SIGNATURE_VERSION_NAME = "Signature-Version";

    /**
     * Version of the signature version.
     */
    private static final String SIGNATURE_VERSION_VALUE = "1.0";

    /**
     * Name of attribute with the "created by" attribute.
     */
    private static final String SIGNATURE_CREATED_BY_NAME = "Created-By";

    /**
     * Value of the "created by" attribute.
     */
    private static final String SIGNATURE_CREATED_BY_VALUE = "1.0 (Android)";

    /**
     * Name of the {@code X-Android-APK-Signer} attribute.
     */
    private static final String SIGNATURE_ANDROID_APK_SIGNED_NAME = "X-Android-APK-Signed";

    /**
     * Value of the {@code X-Android-APK-Signer} attribute when the APK is signed with the v2
     * scheme.
     */
    public static final String SIGNATURE_ANDROID_APK_SIGNER_VALUE_WHEN_V2_SIGNED = "2";

    /**
     * Files to ignore when signing. See
     * https://docs.oracle.com/javase/7/docs/technotes/guides/jar/jar.html
     */
    private static final Set<String> IGNORED_FILES = Sets.newHashSet(
            ManifestGenerationExtension.MANIFEST_NAME, SIGNATURE_FILE);

    /**
     * Same as {@link #IGNORED_FILES} but with all names in lower case.
     */
    private static final Set<String> IGNORED_FILES_LC = Sets.newHashSet(
            IGNORED_FILES.stream()
                    .map(i -> i.toLowerCase(Locale.US))
                    .collect(Collectors.toSet()));


    /**
     * Prefix of files in META-INF to ignore when signing. See
     * https://docs.oracle.com/javase/7/docs/technotes/guides/jar/jar.html
     */
    private static final Set<String> IGNORED_PREFIXES = Sets.newHashSet(
            "SIG-");

    /**
     * Same as {@link #IGNORED_PREFIXES} but with all names in lower case.
     */
    private static final Set<String> IGNORED_PREFIXES_LC = Sets.newHashSet(
            IGNORED_PREFIXES.stream()
                    .map(i -> i.toLowerCase(Locale.US))
                    .collect(Collectors.toSet()));

    /**
     * Suffixes of files in META-INF to ignore when signing. See
     * https://docs.oracle.com/javase/7/docs/technotes/guides/jar/jar.html
     */
    private static final Set<String> IGNORED_SUFFIXES = Sets.newHashSet(
            ".SF", ".DSA", ".RSA", ".EC");

    /**
     * Same as {@link #IGNORED_SUFFIXES} but with all names in lower case.
     */
    private static final Set<String> IGNORED_SUFFIXES_LC = Sets.newHashSet(
            IGNORED_SUFFIXES.stream()
                    .map(i -> i.toLowerCase(Locale.US))
                    .collect(Collectors.toSet()));

    /**
     * Extension maintaining the manifest.
     */
    @NonNull
    private final ManifestGenerationExtension mManifestExtension;

    /**
     * Message digest to use.
     */
    @NonNull
    private final MessageDigest mMessageDigest;

    /**
     * Signature file. Note that the signature file is itself a manifest file but it is
     * a different one from the "standard" MANIFEST.MF.
     */
    @NonNull
    private final Manifest mSignatureFile;

    /**
     * Has the signature manifest been changed?
     */
    private boolean mDirty;

    /**
     * Signer certificate.
     */
    @NonNull
    private final X509Certificate mCertificate;

    /**
     * The private key used to sign the jar.
     */
    @NonNull
    private final PrivateKey mPrivateKey;

    /**
     * Algorithm with which .SF file is signed.
     */
    @NonNull
    private final SignatureAlgorithm mSignatureAlgorithm;

    /**
     * Digest algorithm to use for MANIFEST.MF and contents of APK entries.
     */
    @NonNull
    private final DigestAlgorithm mDigestAlgorithm;

    /**
     * Value to output for the {@code X-Android-APK-Signed} header or {@code null} if the header
     * should not be output.
     */
    @Nullable
    private final String mApkSignedHeaderValue;

    /**
     * The extension registered with the {@link ZFile}. {@code null} if not registered.
     */
    @Nullable
    private ZFileExtension mExtension;

    /**
     * Creates a new signature extension.
     *
     * @param manifestExtension the extension maintaining the manifest
     * @param minSdkVersion minSdkVersion of the package
     * @param certificate sign certificate
     * @param privateKey the private key to sign the jar
     * @param apkSignedHeaderValue value of the {@code X-Android-APK-Signed} header to output into
     * the {@code .SF} file or {@code null} if the header should not be output.
     *
     * @throws NoSuchAlgorithmException failed to obtain the digest algorithm.
     */
    public SignatureExtension(@NonNull ManifestGenerationExtension manifestExtension,
            int minSdkVersion, @NonNull X509Certificate certificate, @NonNull PrivateKey privateKey,
            @Nullable String apkSignedHeaderValue)
            throws NoSuchAlgorithmException {
        mManifestExtension = manifestExtension;
        mSignatureFile = new Manifest();
        mDirty = false;
        mCertificate = certificate;
        mPrivateKey = privateKey;
        mApkSignedHeaderValue = apkSignedHeaderValue;

        mSignatureAlgorithm =
                SignatureAlgorithm.fromKeyAlgorithm(privateKey.getAlgorithm(), minSdkVersion);
        mDigestAlgorithm = DigestAlgorithm.findBest(minSdkVersion, mSignatureAlgorithm);
        mMessageDigest = MessageDigest.getInstance(mDigestAlgorithm.messageDigestName);
    }

    /**
     * Registers the extension with the {@link ZFile} provided in the
     * {@link ManifestGenerationExtension}. Note that the {@code ManifestGenerationExtension}
     * needs to be registered as a precondition for this method.
     *
     * @throws IOException failed to analyze the zip
     */
    public void register() throws IOException {
        Preconditions.checkState(mExtension == null, "register() already invoked");

        mExtension = new ZFileExtension() {
            @Nullable
            @Override
            public IOExceptionRunnable beforeUpdate() {
                return SignatureExtension.this::updateSignatureIfNeeded;
            }

            @Nullable
            @Override
            public IOExceptionRunnable added(@NonNull final StoredEntry entry,
                    @Nullable final StoredEntry replaced) {
                if (replaced != null) {
                    Preconditions.checkArgument(entry.getCentralDirectoryHeader().getName().equals(
                            replaced.getCentralDirectoryHeader().getName()));
                }

                if (isIgnoredFile(entry.getCentralDirectoryHeader().getName())) {
                    return null;
                }

                return () -> {
                    if (replaced != null) {
                        SignatureExtension.this.removed(replaced);
                    }

                    SignatureExtension.this.added(entry);
                };
            }

            @Nullable
            @Override
            public IOExceptionRunnable removed(@NonNull final StoredEntry entry) {
                if (isIgnoredFile(entry.getCentralDirectoryHeader().getName())) {
                    return null;
                }

                return () -> SignatureExtension.this.removed(entry);
            }
        };

        mManifestExtension.zFile().addZFileExtension(mExtension);
        readSignatureFile();
    }

    /**
     * Reads the signature file (if any) on the zip file.
     * <p>
     * When this method terminates, we have the following guarantees:
     * <ul>
     *      <li>An internal signature manifest exists.</li>
     *      <li>All entries in the in-memory signature file exist in the zip file.</li>
     *      <li>All entries in the zip file (with the exception of the signature-related files,
     *      as specified by https://docs.oracle.com/javase/7/docs/technotes/guides/jar/jar.html)
     *      exist in the in-memory signature file.</li>
     *      <li>All entries in the in-memory signature file have digests that match their
     *      contents in the zip.</li>
     *      <li>All entries in the in-memory signature manifest exist also in the manifest file
     *      and the digests are the same.</li>
     *      <li>The main attributes of the in-memory signature manifest are valid. The manifest's
     *      digest has not been verified and may not even exist.</li>
     *      <li>If the internal in-memory signature manifest differs in any way from the one
     *      written in the file, {@link #mDirty} will be set to {@code true}. Otherwise,
     *      {@link #mDirty} will be set to {@code false}.</li>
     * </ul>
     *
     * @throws IOException failed to read the signature file
     */
    private void readSignatureFile() throws IOException {
        boolean needsNewSignature = false;

        StoredEntry signatureEntry = mManifestExtension.zFile().get(SIGNATURE_FILE);
        if (signatureEntry != null) {
            byte[] signatureData = signatureEntry.read();
            mSignatureFile.read(new ByteArrayInputStream(signatureData));

            Attributes mainAttrs = mSignatureFile.getMainAttributes();
            String versionName = mainAttrs.getValue(SIGNATURE_VERSION_NAME);
            String createdBy = mainAttrs.getValue(SIGNATURE_CREATED_BY_NAME);
            String apkSigned = mainAttrs.getValue(SIGNATURE_ANDROID_APK_SIGNED_NAME);

            if (!SIGNATURE_VERSION_VALUE.equals(versionName)
                    || !SIGNATURE_CREATED_BY_VALUE.equals(createdBy)
                    || mainAttrs.get(mDigestAlgorithm.manifestAttributeName) != null
                    || !Objects.equal(mApkSignedHeaderValue, apkSigned)) {
                needsNewSignature = true;
            }
        } else {
            needsNewSignature = true;
        }

        if (needsNewSignature) {
            Attributes mainAttrs = mSignatureFile.getMainAttributes();

            mainAttrs.putValue(SIGNATURE_CREATED_BY_NAME, SIGNATURE_CREATED_BY_VALUE);
            mainAttrs.putValue(SIGNATURE_VERSION_NAME, SIGNATURE_VERSION_VALUE);
            if (mApkSignedHeaderValue != null) {
                mainAttrs.putValue(SIGNATURE_ANDROID_APK_SIGNED_NAME, mApkSignedHeaderValue);
            } else {
                mainAttrs.remove(SIGNATURE_ANDROID_APK_SIGNED_NAME);
            }

            mDirty = true;
        }

        /*
         * At this point we have a valid in-memory signature file with a valid header. mDirty
         * states whether this is the same as the file-based signature file.
         *
         * Now, check we have the same files in the zip as in the signature file and that all
         * digests match. While we do this, make sure the manifest is also up-do-date.
         *
         * We ignore all signature-related files that exist in the zip that are signature-related.
         * This are defined in the jar format specification.
         */
        Set<StoredEntry> allEntries =
                mManifestExtension.zFile().entries().stream()
                        .filter(se -> !isIgnoredFile(se.getCentralDirectoryHeader().getName()))
                        .collect(Collectors.toSet());

        Set<String> sigEntriesToRemove = Sets.newHashSet(mSignatureFile.getEntries().keySet());
        Set<String> manEntriesToRemove = Sets.newHashSet(mManifestExtension.allEntries().keySet());
        for (StoredEntry se : allEntries) {
            /*
             * Update the entry's digest, if needed.
             */
            setDigestForEntry(se);

            /*
             * This entry exists in the file, so remove it from the list of entries to remove
             * from the manifest and signature file.
             */
            sigEntriesToRemove.remove(se.getCentralDirectoryHeader().getName());
            manEntriesToRemove.remove(se.getCentralDirectoryHeader().getName());
        }

        for (String toRemoveInSignature : sigEntriesToRemove) {
            mSignatureFile.getEntries().remove(toRemoveInSignature);
            mDirty = true;
        }

        for (String toRemoveInManifest : manEntriesToRemove) {
            mManifestExtension.removeEntry(toRemoveInManifest);
        }
    }

    /**
     * This method will recompute the manifest's digest and will update the signature file if the
     * manifest has changed. It then writes the signature file, if dirty for any reason (including
     * from recomputing the manifest's digest).
     *
     * @throws IOException failed to read / write zip data
     */
    private void updateSignatureIfNeeded() throws IOException {
        byte[] manifestData = mManifestExtension.getManifestBytes();
        byte[] manifestDataDigest = mMessageDigest.digest(manifestData);
        String manifestDataDigestTxt = new String(Base64.encodeBase64(manifestDataDigest),
                Charsets.US_ASCII);

        if (!manifestDataDigestTxt.equals(mSignatureFile.getMainAttributes().getValue(
                mDigestAlgorithm.manifestAttributeName))) {
            mSignatureFile
                    .getMainAttributes()
                    .putValue(mDigestAlgorithm.manifestAttributeName, manifestDataDigestTxt);
            mDirty = true;
        }

        if (!mDirty) {
            return;
        }

        ByteArrayOutputStream signatureBytes = new ByteArrayOutputStream();
        mSignatureFile.write(signatureBytes);

        mManifestExtension.zFile().add(
                SIGNATURE_FILE,
                new ByteArrayInputStream(signatureBytes.toByteArray()));

        String digitalSignatureFile = SIGNATURE_BASE + "." + mPrivateKey.getAlgorithm();
        try {
            mManifestExtension.zFile().add(
                    digitalSignatureFile,
                    new ByteArrayInputStream(computePkcs7Signature(signatureBytes.toByteArray())));
        } catch (CertificateEncodingException | OperatorCreationException | CMSException e) {
            throw new IOException("Failed to digitally sign signature file.", e);
        }

        mDirty = false;
    }

    /**
     * A new file has been added.
     *
     * @param entry the entry added
     * @throws IOException failed to add the entry to the signature file (or failed to compute the
     * entry's signature)
     */
    private void added(@NonNull StoredEntry entry) throws IOException {
        setDigestForEntry(entry);
    }

    /**
     * Adds / updates the signature for an entry. If this entry has no signature, or its digest
     * doesn't match the one in the signature file (or manifest), it will be updated.
     *
     * @param entry the entry
     * @throws IOException failed to compute the entry's digest
     */
    private void setDigestForEntry(@NonNull StoredEntry entry) throws IOException {
        String entryName = entry.getCentralDirectoryHeader().getName();
        byte[] entryDigestArray = mMessageDigest.digest(entry.read());
        String entryDigest = new String(Base64.encodeBase64(entryDigestArray),
                Charsets.US_ASCII);

        Attributes signatureAttributes = mSignatureFile.getEntries().get(entryName);
        if (signatureAttributes == null) {
            signatureAttributes = new Attributes();
            mSignatureFile.getEntries().put(entryName, signatureAttributes);
            mDirty = true;
        }

        if (!entryDigest.equals(signatureAttributes.getValue(
                mDigestAlgorithm.entryAttributeName))) {
            signatureAttributes.putValue(mDigestAlgorithm.entryAttributeName, entryDigest);
            mDirty = true;
        }

        /*
         * setAttribute will not mark the manifest as changed if the attribute is already there
         * and with the same value.
         */
        mManifestExtension.setAttribute(entryName, mDigestAlgorithm.entryAttributeName,
                entryDigest);
    }

    /**
     * File has been removed.
     *
     * @param entry the entry removed
     */
    private void removed(@NonNull StoredEntry entry) {
        mSignatureFile.getEntries().remove(entry.getCentralDirectoryHeader().getName());
        mManifestExtension.removeEntry(entry.getCentralDirectoryHeader().getName());
        mDirty = true;
    }

    /**
     * Checks if a file should be ignored when signing.
     *
     * @param name the file name
     * @return should it be ignored
     */
    public static boolean isIgnoredFile(@NonNull String name) {
        String metaInfPfx = ManifestGenerationExtension.META_INF_DIR + "/";
        boolean inMetaInf = name.startsWith(metaInfPfx)
                && !name.substring(metaInfPfx.length()).contains("/");

        /*
         * Only files in META-INF can be ignored. Files in sub-directories of META-INF are not
          * ignored.
         */
        if (!inMetaInf) {
            return false;
        }

        String nameLc = name.toLowerCase(Locale.US);

        /*
         * All files with names that match (case insensitive) the ignored list are ignored.
         */
        if (IGNORED_FILES_LC.contains(nameLc)) {
            return true;
        }

        for (String pfx : IGNORED_PREFIXES_LC) {
            if (nameLc.startsWith(pfx)) {
                return true;
            }
        }

        for (String sfx : IGNORED_SUFFIXES_LC) {
            if (nameLc.endsWith(sfx)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Computes the digital signature of an array of data.
     *
     * @param data the data
     * @return the digital signature
     * @throws IOException failed to read/write signature data
     * @throws CertificateEncodingException failed to sign the data
     * @throws OperatorCreationException failed to sign the data
     * @throws CMSException failed to sign the data
     */
    private byte[] computePkcs7Signature(@NonNull byte[] data) throws IOException,
            CertificateEncodingException, OperatorCreationException, CMSException {
        CMSProcessableByteArray cmsData = new CMSProcessableByteArray(data);

        ArrayList<X509Certificate> certList = new ArrayList<>();
        certList.add(mCertificate);
        JcaCertStore certs = new JcaCertStore(certList);

        CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
        String signatureAlgName = mSignatureAlgorithm.signatureAlgorithmName(mDigestAlgorithm);
        ContentSigner shaSigner =
                new JcaContentSignerBuilder(signatureAlgName).build(mPrivateKey);
        gen.addSignerInfoGenerator(
                new JcaSignerInfoGeneratorBuilder(
                        new JcaDigestCalculatorProviderBuilder()
                                .build())
                                .setDirectSignature(true)
                                .build(shaSigner, mCertificate));
        gen.addCertificates(certs);
        CMSSignedData sigData = gen.generate(cmsData, false);

        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();

        /*
         * DEROutputStream is not closeable! OMG!
         */
        DEROutputStream dos = null;
        try (ASN1InputStream asn1 = new ASN1InputStream(sigData.getEncoded())) {
            dos = new DEROutputStream(outputBytes);
            dos.writeObject(asn1.readObject());

            DEROutputStream toClose = dos;
            dos = null;
            toClose.close();
        } catch (IOException e) {
            if (dos != null) {
                try {
                    dos.close();
                } catch (IOException ee) {
                    e.addSuppressed(ee);
                }
            }
        }

        return outputBytes.toByteArray();
    }
}
