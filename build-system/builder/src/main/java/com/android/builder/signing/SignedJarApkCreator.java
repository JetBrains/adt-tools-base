/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.builder.signing;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.internal.packaging.sign.DigestAlgorithm;
import com.android.builder.internal.packaging.sign.SignatureAlgorithm;
import com.android.builder.packaging.ApkCreator;
import com.android.builder.packaging.ApkCreatorFactory;
import com.android.builder.packaging.ManifestAttributes;
import com.android.builder.packaging.ZipAbortException;
import com.android.builder.packaging.ZipEntryFilter;
import com.android.builder.packaging.PackagingUtils;
import com.google.common.base.Preconditions;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.DEROutputStream;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.encoders.Base64;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.security.DigestOutputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A Jar file builder with signature support.
 */
public class SignedJarApkCreator implements ApkCreator {

    private final int mMinSdkVersion;

    /** Write to another stream and track how many bytes have been
     *  written.
     */
    private static class CountOutputStream extends FilterOutputStream {
        private int mCount = 0;

        public CountOutputStream(OutputStream out) {
            super(out);
            mCount = 0;
        }

        @Override
        public void write(int b) throws IOException {
            super.write(b);
            mCount++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            super.write(b, off, len);
            mCount += len;
        }

        public int size() {
            return mCount;
        }
    }

    private final Predicate<String> mNoCompressPredicate;
    private final PrivateKey mKey;
    private final X509Certificate mCertificate;
    private JarOutputStream mOutputJar;
    private Manifest mManifest;
    private DigestAlgorithm mDigestAlgorithm;
    private SignatureAlgorithm mSignatureAlgorithm;
    private MessageDigest mMessageDigest;

    private byte[] mBuffer = new byte[4096];


    /**
     * Creates a {@link SignedJarApkCreator} with a given data.
     *
     * @param creationData the data to create the jar
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    public SignedJarApkCreator(@NonNull ApkCreatorFactory.CreationData creationData)
            throws IOException, NoSuchAlgorithmException {
        mMinSdkVersion = creationData.getMinSdkVersion();
        mOutputJar = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(
                creationData.getApkPath())));
        mOutputJar.setLevel(9);
        mNoCompressPredicate = creationData.getNoCompressPredicate();
        mKey = creationData.getPrivateKey();
        mCertificate = creationData.getCertificate();

        if (mKey != null && mCertificate != null) {
            mManifest = new Manifest();
            Attributes main = mManifest.getMainAttributes();
            main.putValue(ManifestAttributes.MANIFEST_VERSION,
                    ManifestAttributes.CURRENT_MANIFEST_VERSION);
            if (creationData.getBuiltBy() != null) {
                main.putValue(ManifestAttributes.BUILT_BY, creationData.getBuiltBy());
            }

            if (creationData.getCreatedBy() != null) {
                main.putValue(ManifestAttributes.CREATED_BY, creationData.getCreatedBy());
            }

            mSignatureAlgorithm =
                    SignatureAlgorithm.fromKeyAlgorithm(mKey.getAlgorithm(), mMinSdkVersion);
            mDigestAlgorithm = DigestAlgorithm.findBest(mMinSdkVersion, mSignatureAlgorithm);
            mMessageDigest = MessageDigest.getInstance(mDigestAlgorithm.messageDigestName);
        }
    }

    @Override
    public void writeFile(@NonNull File inputFile, @NonNull String apkPath) throws IOException {
        // Get an input stream on the file.
        try (FileInputStream fis = new FileInputStream(inputFile)) {
            // create the zip entry
            JarEntry entry = new JarEntry(apkPath);
            entry.setTime(inputFile.lastModified());

            if (mNoCompressPredicate.test(apkPath)) {
                configureStoredEntry(entry, inputFile);
            }

            writeEntry(fis, entry);
        }
    }

    private static void configureStoredEntry(JarEntry entry, File inputFile) throws IOException {
        ByteSource byteSource = Files.asByteSource(inputFile);
        long size = inputFile.length();

        entry.setMethod(ZipEntry.STORED);
        entry.setSize(size);
        entry.setCompressedSize(size);
        entry.setCrc(byteSource.hash(Hashing.crc32()).padToLong());
    }

    /**
     * Copies the content of a Jar/Zip archive into the receiver archive.
     * @param zip the {@link InputStream} for the Jar/Zip to copy.
     * @throws IOException
     * @throws ZipAbortException if the {@link ZipEntryFilter} filter indicated that the write
     *                           must be aborted.
     */
    public void writeZip(@NonNull File zip) throws IOException, ZipAbortException {
        writeZip(zip, null, null);
    }

    @Override
    public void writeZip(@NonNull File zip, @Nullable Function<String, String> transform,
            @Nullable Predicate<String> isIgnored) throws IOException {
        Preconditions.checkArgument(transform == null, "SignedJarApkCreator does not support "
                + "name transforms");

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zip))) {
            // loop on the entries of the intermediary package and put them in the final package.
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                // do not take directories or anything inside a potential META-INF folder.
                if (entry.isDirectory()) {
                    continue;
                }

                // ignore some of the content in META-INF/ but not all
                if (name.startsWith("META-INF/")) {
                    // ignore the manifest file.
                    String subName = name.substring(9);
                    if ("MANIFEST.MF".equals(subName)) {
                        continue;
                    }

                    // special case for Maven meta-data because we really don't care about them in
                    // apks.
                    if (name.startsWith("META-INF/maven/")) {
                        continue;
                    }

                    // check for subfolder
                    int index = subName.indexOf('/');
                    if (index == -1) {
                        // no sub folder, ignores signature files.
                        if (PackagingUtils.SIGNING_EXTENSIONS
                                .stream()
                                .anyMatch(ext -> subName.endsWith("." + ext))) {
                            continue;
                        }
                    }
                }

                // if we have a filter, we check the entry against it
                if (isIgnored != null && isIgnored.test(name)) {
                    continue;
                }

                JarEntry newEntry;

                // Preserve the STORED method of the input entry.
                if (entry.getMethod() == JarEntry.STORED) {
                    newEntry = new JarEntry(entry);
                } else {
                    // Create a new entry so that the compressed len is recomputed.
                    newEntry = new JarEntry(name);
                }

                writeEntry(zis, newEntry);

                zis.closeEntry();
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (mOutputJar == null) {
            return;
        }

        if (mManifest != null) {
            // write the manifest to the jar file
            mOutputJar.putNextEntry(new JarEntry(JarFile.MANIFEST_NAME));
            mManifest.write(mOutputJar);

            try {
                // CERT.SF
                mOutputJar.putNextEntry(new JarEntry("META-INF/CERT.SF"));

                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                writeSignatureFile(byteArrayOutputStream);
                byte[] signedData = byteArrayOutputStream.toByteArray();
                mOutputJar.write(signedData);

                // CERT.*
                mOutputJar.putNextEntry(new JarEntry("META-INF/CERT." + mKey.getAlgorithm()));
                writeSignatureBlock(new CMSProcessableByteArray(signedData), mCertificate);
            } catch (Exception e) {
                throw new IOException(e);
            }
        }

        mOutputJar.close();
        mOutputJar = null;
    }

    /**
     * Adds an entry to the output jar, and write its content from the {@link InputStream}
     * @param input The input stream from where to write the entry content.
     * @param entry the entry to write in the jar.
     * @throws IOException
     */
    private void writeEntry(InputStream input, JarEntry entry) throws IOException {
        // add the entry to the jar archive
        mOutputJar.putNextEntry(entry);

        // read the content of the entry from the input stream, and write it into the archive.
        int count;
        while ((count = input.read(mBuffer)) != -1) {
            mOutputJar.write(mBuffer, 0, count);

            // update the digest
            if (mMessageDigest != null) {
                mMessageDigest.update(mBuffer, 0, count);
            }
        }

        // close the entry for this file
        mOutputJar.closeEntry();

        if (mManifest != null) {
            // update the manifest for this entry.
            Attributes attr = mManifest.getAttributes(entry.getName());
            if (attr == null) {
                attr = new Attributes();
                mManifest.getEntries().put(entry.getName(), attr);
            }
            attr.putValue(mDigestAlgorithm.entryAttributeName,
                          new String(Base64.encode(mMessageDigest.digest()), "ASCII"));
        }
    }

    /** Writes a .SF file with a digest to the manifest. */
    private void writeSignatureFile(OutputStream out)
            throws IOException, GeneralSecurityException {
        Manifest sf = new Manifest();
        Attributes main = sf.getMainAttributes();
        main.putValue("Signature-Version", "1.0");
        main.putValue("Created-By", "1.0 (Android)");

        MessageDigest md = MessageDigest.getInstance(mDigestAlgorithm.messageDigestName);
        PrintStream print = new PrintStream(
                new DigestOutputStream(new ByteArrayOutputStream(), md),
                true, SdkConstants.UTF_8);

        // Digest of the entire manifest
        mManifest.write(print);
        print.flush();
        main.putValue(mDigestAlgorithm.manifestAttributeName,
                new String(Base64.encode(md.digest()), "ASCII"));

        Map<String, Attributes> entries = mManifest.getEntries();
        for (Map.Entry<String, Attributes> entry : entries.entrySet()) {
            // Digest of the manifest stanza for this entry.
            print.print("Name: " + entry.getKey() + "\r\n");
            for (Map.Entry<Object, Object> att : entry.getValue().entrySet()) {
                print.print(att.getKey() + ": " + att.getValue() + "\r\n");
            }
            print.print("\r\n");
            print.flush();

            Attributes sfAttr = new Attributes();
            sfAttr.putValue(
                    mDigestAlgorithm.entryAttributeName,
                    new String(Base64.encode(md.digest()), "ASCII"));
            sf.getEntries().put(entry.getKey(), sfAttr);
        }
        CountOutputStream cout = new CountOutputStream(out);
        sf.write(cout);

        // A bug in the java.util.jar implementation of Android platforms
        // up to version 1.6 will cause a spurious IOException to be thrown
        // if the length of the signature file is a multiple of 1024 bytes.
        // As a workaround, add an extra CRLF in this case.
        if ((cout.size() % 1024) == 0) {
            cout.write('\r');
            cout.write('\n');
        }
    }

    /** Write the certificate file with a digital signature. */
    private void writeSignatureBlock(CMSTypedData data, X509Certificate publicKey)
            throws IOException, CertificateEncodingException, OperatorCreationException, CMSException {

        ArrayList<X509Certificate> certList = new ArrayList<X509Certificate>();
        certList.add(publicKey);
        JcaCertStore certs = new JcaCertStore(certList);

        CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
        ContentSigner sha1Signer =
                new JcaContentSignerBuilder(
                        mSignatureAlgorithm.signatureAlgorithmName(mDigestAlgorithm)).build(mKey);
        gen.addSignerInfoGenerator(
            new JcaSignerInfoGeneratorBuilder(
                new JcaDigestCalculatorProviderBuilder()
                .build())
            .setDirectSignature(true)
            .build(sha1Signer, publicKey));
        gen.addCertificates(certs);
        CMSSignedData sigData = gen.generate(data, false);

        try (ASN1InputStream asn1 = new ASN1InputStream(sigData.getEncoded())) {
            DEROutputStream dos = new DEROutputStream(mOutputJar);
            try {
                dos.writeObject(asn1.readObject());
            } finally {
                dos.flush();
                dos.close();
            }
        }
    }

    @Override
    public void deleteFile(@NonNull String apkPath) throws IOException {
        throw new UnsupportedOperationException("Cannot call deleteFile on a ApkCreator that "
                + "does not support updates.");
    }
}
