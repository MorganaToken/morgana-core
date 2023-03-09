/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.jose.jwk;

import java.util.Arrays;
import java.util.List;

import org.junit.ClassRule;
import org.junit.Test;
import org.keycloak.common.util.Base64Url;
import org.keycloak.common.util.KeyUtils;
import org.keycloak.common.util.PemUtils;
import org.keycloak.crypto.JavaAlgorithm;
import org.keycloak.crypto.KeyType;
import org.keycloak.common.crypto.CryptoIntegration;
import org.keycloak.rule.CryptoInitRule;
import org.keycloak.util.JsonSerialization;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.keycloak.common.util.CertificateUtils.generateV1SelfSignedCertificate;

/**
 * This is not tested in keycloak-core. The subclasses should be created in the crypto modules to make sure it is tested with corresponding modules (bouncycastle VS bouncycastle-fips)
 *
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */
public abstract class JWKTest {

    @ClassRule
    public static CryptoInitRule cryptoInitRule = new CryptoInitRule();

    @Test
    public void publicRs256() throws Exception {
        KeyPair keyPair = CryptoIntegration.getProvider().getKeyPairGen(KeyType.RSA).generateKeyPair();
        PublicKey publicKey = keyPair.getPublic();
        X509Certificate certificate = generateV1SelfSignedCertificate(keyPair, "Test");

        JWK jwk = JWKBuilder.create().kid(KeyUtils.createKeyId(publicKey)).algorithm("RS256").rsa(publicKey, certificate);

        assertNotNull(jwk.getKeyId());
        assertEquals("RSA", jwk.getKeyType());
        assertEquals("RS256", jwk.getAlgorithm());
        assertEquals("sig", jwk.getPublicKeyUse());

        assertTrue(jwk instanceof RSAPublicJWK);
        assertNotNull(((RSAPublicJWK) jwk).getModulus());
        assertNotNull(((RSAPublicJWK) jwk).getPublicExponent());
        assertNotNull(((RSAPublicJWK) jwk).getX509CertificateChain());
        assertEquals(PemUtils.encodeCertificate(certificate), ((RSAPublicJWK) jwk).getX509CertificateChain()[0]);
        assertNotNull(((RSAPublicJWK) jwk).getSha1x509Thumbprint());
        assertEquals(PemUtils.generateThumbprint(((RSAPublicJWK) jwk).getX509CertificateChain(), "SHA-1"), ((RSAPublicJWK) jwk).getSha1x509Thumbprint());
        assertNotNull(((RSAPublicJWK) jwk).getSha256x509Thumbprint());
        assertEquals(PemUtils.generateThumbprint(((RSAPublicJWK) jwk).getX509CertificateChain(), "SHA-256"), ((RSAPublicJWK) jwk).getSha256x509Thumbprint());

        String jwkJson = JsonSerialization.writeValueAsString(jwk);

        PublicKey publicKeyFromJwk = JWKParser.create().parse(jwkJson).toPublicKey();

        // Parse
        assertArrayEquals(publicKey.getEncoded(), publicKeyFromJwk.getEncoded());

        byte[] data = "Some test string".getBytes(StandardCharsets.UTF_8);
        byte[] sign = sign(data, JavaAlgorithm.RS256, keyPair.getPrivate());
        verify(data, sign, JavaAlgorithm.RS256, publicKeyFromJwk);
    }

    @Test
    public void publicRs256Chain() throws Exception {
        KeyPair keyPair = CryptoIntegration.getProvider().getKeyPairGen(KeyType.RSA).generateKeyPair();
        PublicKey publicKey = keyPair.getPublic();
        List<X509Certificate> certificates = Arrays.asList(generateV1SelfSignedCertificate(keyPair, "Test"), generateV1SelfSignedCertificate(keyPair, "Intermediate"));

        JWK jwk = JWKBuilder.create().kid(KeyUtils.createKeyId(publicKey)).algorithm("RS256").rsa(publicKey, certificates);

        assertNotNull(jwk.getKeyId());
        assertEquals("RSA", jwk.getKeyType());
        assertEquals("RS256", jwk.getAlgorithm());
        assertEquals("sig", jwk.getPublicKeyUse());

        assertTrue(jwk instanceof RSAPublicJWK);
        assertNotNull(((RSAPublicJWK) jwk).getModulus());
        assertNotNull(((RSAPublicJWK) jwk).getPublicExponent());
        assertNotNull(((RSAPublicJWK) jwk).getX509CertificateChain());

        String[] expectedChain = new String[certificates.size()];
        for (int i = 0; i < certificates.size(); i++) {
            expectedChain[i] = PemUtils.encodeCertificate(certificates.get(i));
        }

        assertArrayEquals(expectedChain, ((RSAPublicJWK) jwk).getX509CertificateChain());
        assertNotNull(((RSAPublicJWK) jwk).getSha1x509Thumbprint());
        assertEquals(PemUtils.generateThumbprint(((RSAPublicJWK) jwk).getX509CertificateChain(), "SHA-1"), ((RSAPublicJWK) jwk).getSha1x509Thumbprint());
        assertNotNull(((RSAPublicJWK) jwk).getSha256x509Thumbprint());
        assertEquals(PemUtils.generateThumbprint(((RSAPublicJWK) jwk).getX509CertificateChain(), "SHA-256"), ((RSAPublicJWK) jwk).getSha256x509Thumbprint());

        String jwkJson = JsonSerialization.writeValueAsString(jwk);

        PublicKey publicKeyFromJwk = JWKParser.create().parse(jwkJson).toPublicKey();

        // Parse
        assertArrayEquals(publicKey.getEncoded(), publicKeyFromJwk.getEncoded());

        byte[] data = "Some test string".getBytes(StandardCharsets.UTF_8);
        byte[] sign = sign(data, JavaAlgorithm.RS256, keyPair.getPrivate());
        verify(data, sign, JavaAlgorithm.RS256, publicKeyFromJwk);
    }

    private void testPublicEs256(String algorithm) throws Exception {
        KeyPairGenerator keyGen = CryptoIntegration.getProvider().getKeyPairGen(KeyType.EC);
        SecureRandom randomGen = new SecureRandom();
        ECGenParameterSpec ecSpec = new ECGenParameterSpec(algorithm);
        keyGen.initialize(ecSpec, randomGen);
        KeyPair keyPair = keyGen.generateKeyPair();

        ECPublicKey publicKey = (ECPublicKey) keyPair.getPublic();

        JWK jwk = JWKBuilder.create().kid(KeyUtils.createKeyId(keyPair.getPublic())).algorithm("ES256").ec(publicKey);

        assertEquals("EC", jwk.getKeyType());
        assertEquals("ES256", jwk.getAlgorithm());
        assertEquals("sig", jwk.getPublicKeyUse());

        assertTrue(jwk instanceof ECPublicJWK);

        ECPublicJWK ecJwk = (ECPublicJWK) jwk;

        assertNotNull(ecJwk.getCrv());
        assertNotNull(ecJwk.getX());
        assertNotNull(ecJwk.getY());

        byte[] xBytes = Base64Url.decode(ecJwk.getX());
        byte[] yBytes = Base64Url.decode(ecJwk.getY());

        final int expectedSize = (publicKey.getParams().getCurve().getField().getFieldSize() + 7) / 8;
        assertEquals(expectedSize, xBytes.length);
        assertEquals(expectedSize, yBytes.length);

        String jwkJson = JsonSerialization.writeValueAsString(jwk);

        JWKParser parser = JWKParser.create().parse(jwkJson);
        ECPublicKey publicKeyFromJwk = (ECPublicKey) parser.toPublicKey();
        assertEquals(publicKey.getW(), publicKeyFromJwk.getW());

        byte[] data = "Some test string".getBytes(StandardCharsets.UTF_8);
        byte[] sign = sign(data, JavaAlgorithm.ES256, keyPair.getPrivate());
        verify(data, sign, JavaAlgorithm.ES256, publicKeyFromJwk);
    }

    @Test
    public void publicEs256P256() throws Exception {
        testPublicEs256("secp256r1");
    }

    @Test
    public void publicEs256P521() throws Exception {
        testPublicEs256("secp521r1");
    }

    @Test
    public void publicEs256P384() throws Exception {
        testPublicEs256("secp384r1");
    }

    @Test
    public void parse() {
        String jwkJson = "{" +
                "   \"kty\": \"RSA\"," +
                "   \"alg\": \"RS256\"," +
                "   \"use\": \"sig\"," +
                "   \"kid\": \"3121adaa80ace09f89d80899d4a5dc4ce33d0747\"," +
                "   \"n\": \"soFDjoZ5mQ8XAA7reQAFg90inKAHk0DXMTizo4JuOsgzUbhcplIeZ7ks83hsEjm8mP8lUVaHMPMAHEIp3gu6Xxsg-s73ofx1dtt_Fo7aj8j383MFQGl8-FvixTVobNeGeC0XBBQjN8lEl-lIwOa4ZoERNAShplTej0ntDp7TQm0=\"," +
                "   \"e\": \"AQAB\"" +
                "  }";

        PublicKey key = JWKParser.create().parse(jwkJson).toPublicKey();
        assertEquals("RSA", key.getAlgorithm());
        assertEquals("X.509", key.getFormat());
    }

    private byte[] sign(byte[] data, String javaAlgorithm, PrivateKey key) throws Exception {
        Signature signature = Signature.getInstance(javaAlgorithm);
        signature.initSign(key);
        signature.update(data);
        return signature.sign();
    }

    private boolean verify(byte[] data, byte[] signature, String javaAlgorithm, PublicKey key) throws Exception {
        Signature verifier = Signature.getInstance(javaAlgorithm);
        verifier.initVerify(key);
        verifier.update(data);
        return verifier.verify(signature);
    }


}
