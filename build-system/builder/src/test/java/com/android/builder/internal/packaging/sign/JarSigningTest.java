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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.android.builder.internal.packaging.zip.StoredEntry;
import com.android.builder.internal.packaging.zip.ZFile;
import com.android.utils.Pair;
import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;

import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v1CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import javax.security.auth.x500.X500Principal;

public class JarSigningTest {
    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    private static Pair<PrivateKey, X509Certificate> generateSignaturePre18() throws Exception {
        return generateSignature("RSA", "SHA1withRSA");
    }

    private static Pair<PrivateKey, X509Certificate> generateSignaturePos18() throws Exception {
        return generateSignature("EC", "SHA256withECDSA");
    }

    private static Pair<PrivateKey, X509Certificate> generateSignature(String sign, String full)
            throws Exception {
        // http://stackoverflow.com/questions/28538785/
        // easy-way-to-generate-a-self-signed-certificate-for-java-security-keystore-using


        KeyPairGenerator generator = null;
        try {
            generator = KeyPairGenerator.getInstance(sign);
        } catch (NoSuchAlgorithmException e) {
            Assume.assumeNoException("Algorithm " + sign + " not supported.", e);
        }

        assertNotNull(generator);
        KeyPair keyPair = generator.generateKeyPair();

        Date notBefore = new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000);
        Date notAfter = new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000);

        X500Name issuer = new X500Name(new X500Principal("cn=Myself").getName());

        SubjectPublicKeyInfo publicKeyInfo;

        if (keyPair.getPublic() instanceof RSAPublicKey) {
            RSAPublicKey rsaPublicKey = (RSAPublicKey) keyPair.getPublic();
            publicKeyInfo = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(
                    new RSAKeyParameters(false, rsaPublicKey.getModulus(),
                            rsaPublicKey.getPublicExponent()));
        } else if (keyPair.getPublic() instanceof ECPublicKey) {
            publicKeyInfo = SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded());
        } else {
            fail();
            publicKeyInfo = null;
        }

        X509v1CertificateBuilder builder = new X509v1CertificateBuilder(issuer, BigInteger.ONE,
                notBefore, notAfter, issuer, publicKeyInfo);

        ContentSigner signer = new JcaContentSignerBuilder(full).setProvider(
                new BouncyCastleProvider()).build(keyPair.getPrivate());
        X509CertificateHolder holder = builder.build(signer);

        JcaX509CertificateConverter converter = new JcaX509CertificateConverter()
                .setProvider(new BouncyCastleProvider());

        return Pair.of(keyPair.getPrivate(), converter.getCertificate(holder));
    }

    @Test
    public void signEmptyJar() throws Exception {
        File zipFile = new File(mTemporaryFolder.getRoot(), "a.zip");
        ZFile zf = new ZFile(zipFile);
        ManifestGenerationExtension manifestExtension = new ManifestGenerationExtension("Me", "Me");
        manifestExtension.register(zf);

        Pair<PrivateKey, X509Certificate> p = generateSignaturePre18();

        SignatureExtension signatureExtension = new SignatureExtension(manifestExtension, 12,
                p.getSecond(), p.getFirst());
        signatureExtension.register();

        zf.close();

        ZFile verifyZFile = new ZFile(zipFile);
        StoredEntry manifestEntry = verifyZFile.get("META-INF/MANIFEST.MF");
        assertNotNull(manifestEntry);

        Manifest manifest = new Manifest(new ByteArrayInputStream(manifestEntry.read()));
        assertEquals(3, manifest.getMainAttributes().size());
        assertEquals("1.0", manifest.getMainAttributes().getValue("Manifest-Version"));
        assertEquals("Me", manifest.getMainAttributes().getValue("Created-By"));
        assertEquals("Me", manifest.getMainAttributes().getValue("Built-By"));
    }

    @Test
    public void signJarWithPrexistingSimpleTextFilePre18() throws Exception {
        File zipFile = new File(mTemporaryFolder.getRoot(), "a.zip");
        ZFile zf1 = new ZFile(zipFile);
        zf1.add("directory/file", new ByteArrayInputStream("useless text".getBytes(
                Charsets.US_ASCII)));
        zf1.close();

        Pair<PrivateKey, X509Certificate> p = generateSignaturePre18();

        ZFile zf2 = new ZFile(zipFile);
        ManifestGenerationExtension me = new ManifestGenerationExtension("Merry", "Christmas");
        me.register(zf2);
        new SignatureExtension(me, 10, p.getSecond(), p.getFirst()).register();
        zf2.close();

        ZFile zf3 = new ZFile(zipFile);
        StoredEntry manifestEntry = zf3.get("META-INF/MANIFEST.MF");
        assertNotNull(manifestEntry);

        Manifest manifest = new Manifest(new ByteArrayInputStream(manifestEntry.read()));
        assertEquals(3, manifest.getMainAttributes().size());
        assertEquals("1.0", manifest.getMainAttributes().getValue("Manifest-Version"));
        assertEquals("Merry", manifest.getMainAttributes().getValue("Built-By"));
        assertEquals("Christmas", manifest.getMainAttributes().getValue("Created-By"));

        Attributes attrs = manifest.getAttributes("directory/file");
        assertNotNull(attrs);
        assertEquals(1, attrs.size());
        assertEquals("OOQgIEXBissIvva3ydRoaXk29Rk=", attrs.getValue("SHA1-Digest"));

        StoredEntry signatureEntry = zf3.get("META-INF/CERT.SF");
        assertNotNull(signatureEntry);

        Manifest signature = new Manifest(new ByteArrayInputStream(signatureEntry.read()));
        assertEquals(3, signature.getMainAttributes().size());
        assertEquals("1.0", signature.getMainAttributes().getValue("Signature-Version"));
        assertEquals("1.0 (Android)", signature.getMainAttributes().getValue("Created-By"));

        byte[] manifestTextBytes = manifestEntry.read();
        byte[] manifestSha1Bytes = Hashing.sha1().hashBytes(manifestTextBytes).asBytes();
        String manifestSha1 = new String(Base64.encodeBase64(manifestSha1Bytes), Charsets.US_ASCII);

        assertEquals(manifestSha1, signature.getMainAttributes().getValue("SHA1-Digest-Manifest"));

        Attributes signAttrs = signature.getAttributes("directory/file");
        assertNotNull(signAttrs);
        assertEquals(1, signAttrs.size());
        assertEquals("OOQgIEXBissIvva3ydRoaXk29Rk=", signAttrs.getValue("SHA1-Digest"));

        StoredEntry rsaEntry = zf3.get("META-INF/CERT.RSA");
        assertNotNull(rsaEntry);
    }

    @Test
    public void signJarWithPrexistingSimpleTextFilePos18() throws Exception {
        File zipFile = new File(mTemporaryFolder.getRoot(), "a.zip");
        ZFile zf1 = new ZFile(zipFile);
        zf1.add("directory/file", new ByteArrayInputStream("useless text".getBytes(
                Charsets.US_ASCII)));
        zf1.close();

        Pair<PrivateKey, X509Certificate> p = generateSignaturePos18();

        ZFile zf2 = new ZFile(zipFile);
        ManifestGenerationExtension me = new ManifestGenerationExtension("Merry", "Christmas");
        me.register(zf2);
        new SignatureExtension(me, 20, p.getSecond(), p.getFirst()).register();
        zf2.close();

        ZFile zf3 = new ZFile(zipFile);
        StoredEntry manifestEntry = zf3.get("META-INF/MANIFEST.MF");
        assertNotNull(manifestEntry);

        Manifest manifest = new Manifest(new ByteArrayInputStream(manifestEntry.read()));
        assertEquals(3, manifest.getMainAttributes().size());
        assertEquals("1.0", manifest.getMainAttributes().getValue("Manifest-Version"));
        assertEquals("Merry", manifest.getMainAttributes().getValue("Built-By"));
        assertEquals("Christmas", manifest.getMainAttributes().getValue("Created-By"));

        Attributes attrs = manifest.getAttributes("directory/file");
        assertNotNull(attrs);
        assertEquals(1, attrs.size());
        assertEquals("QjupZsopQM/01O6+sWHqH64ilMmoBEtljg9VEqN6aI4=",
                attrs.getValue("SHA-256-Digest"));

        StoredEntry signatureEntry = zf3.get("META-INF/CERT.SF");
        assertNotNull(signatureEntry);

        Manifest signature = new Manifest(new ByteArrayInputStream(signatureEntry.read()));
        assertEquals(3, signature.getMainAttributes().size());
        assertEquals("1.0", signature.getMainAttributes().getValue("Signature-Version"));
        assertEquals("1.0 (Android)", signature.getMainAttributes().getValue("Created-By"));

        byte[] manifestTextBytes = manifestEntry.read();
        byte[] manifestSha256Bytes = Hashing.sha256().hashBytes(manifestTextBytes).asBytes();
        String manifestSha256 = new String(Base64.encodeBase64(manifestSha256Bytes), Charsets.US_ASCII);

        assertEquals(manifestSha256, signature.getMainAttributes().getValue(
                "SHA-256-Digest-Manifest"));

        Attributes signAttrs = signature.getAttributes("directory/file");
        assertNotNull(signAttrs);
        assertEquals(1, signAttrs.size());
        assertEquals("QjupZsopQM/01O6+sWHqH64ilMmoBEtljg9VEqN6aI4=",
                signAttrs.getValue("SHA-256-Digest"));

        StoredEntry ecdsaEntry = zf3.get("META-INF/CERT.EC");
        assertNotNull(ecdsaEntry);
    }
}
