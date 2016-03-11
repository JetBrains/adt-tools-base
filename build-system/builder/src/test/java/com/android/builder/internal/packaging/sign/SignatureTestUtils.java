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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.utils.Pair;

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

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;

import javax.security.auth.x500.X500Principal;

/**
 * Utilities to use signatures in tests.
 */
public class SignatureTestUtils {

    /**
     * Generates a private key / certificate for pre-18 systems.
     *
     * @return the pair with the private key and certificate
     * @throws Exception failed to generate the signature data
     */
    @NonNull
    public static Pair<PrivateKey, X509Certificate> generateSignaturePre18() throws Exception {
        return generateSignature("RSA", "SHA1withRSA");
    }

    /**
     * Generates a private key / certificate for post-18 systems.
     *
     * @return the pair with the private key and certificate
     * @throws Exception failed to generate the signature data
     */
    @NonNull
    public static Pair<PrivateKey, X509Certificate> generateSignaturePos18() throws Exception {
        return generateSignature("EC", "SHA256withECDSA");
    }

    /**
     * Generates a private key / certificate.
     *
     * @param sign the asymmetric cypher, <em>e.g.</em>, {@code RSA}
     * @param full the full signature algorithm name, <em>e.g.</em>, {@code SHA1withRSA}
     * @return the pair with the private key and certificate
     * @throws Exception failed to generate the signature data
     */
    @NonNull
    public static Pair<PrivateKey, X509Certificate> generateSignature(@NonNull String sign,
            @NonNull String full)
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

}
