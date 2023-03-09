/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
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
package org.keycloak.crypto.elytron;

import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Signature;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKeyFactory;
import javax.net.ssl.SSLSocketFactory;

import org.keycloak.common.crypto.CertificateUtilsProvider;
import org.keycloak.common.crypto.CryptoConstants;
import org.keycloak.common.crypto.CryptoProvider;
import org.keycloak.common.crypto.ECDSACryptoProvider;
import org.keycloak.common.crypto.PemUtilsProvider;
import org.keycloak.common.crypto.UserIdentityExtractorProvider;
import org.keycloak.common.util.KeystoreUtil.KeystoreFormat;
import org.keycloak.crypto.JavaAlgorithm;

public class WildFlyElytronProvider implements CryptoProvider {

    private Map<String, Object> providers = new ConcurrentHashMap<>();

    public WildFlyElytronProvider() {
        providers.put(CryptoConstants.A128KW, new AesKeyWrapAlgorithmProvider());
        providers.put(CryptoConstants.RSA1_5, new ElytronRsaKeyEncryptionJWEAlgorithmProvider("RSA/ECB/PKCS1Padding"));
        providers.put(CryptoConstants.RSA_OAEP, new ElytronRsaKeyEncryptionJWEAlgorithmProvider("RSA/ECB/OAEPWithSHA-1AndMGF1Padding"));
        providers.put(CryptoConstants.RSA_OAEP_256, new ElytronRsaKeyEncryption256JWEAlgorithmProvider("RSA/ECB/OAEPWithSHA-256AndMGF1Padding"));
    }

    @Override
    public Provider getBouncyCastleProvider() {
        return null;
    }

    @Override
    public <T> T getAlgorithmProvider(Class<T> clazz, String algorithm) {
        Object o = providers.get(algorithm);
        if (o == null) {
            throw new IllegalArgumentException("Not found provider of algorithm type: " + algorithm);
        }
        return clazz.cast(o);
    }

    @Override
    public CertificateUtilsProvider getCertificateUtils() {
        return new ElytronCertificateUtils();
    }

    @Override
    public PemUtilsProvider getPemUtils() {
        return new ElytronPEMUtilsProvider();
    }

    @Override
    public <T> T getOCSPProver(Class<T> clazz) {
        return clazz.cast(new ElytronOCSPProvider());
    }

    @Override
    public UserIdentityExtractorProvider getIdentityExtractorProvider() {
        return new ElytronUserIdentityExtractorProvider();
    }

    @Override
    public ECDSACryptoProvider getEcdsaCryptoProvider() {
        return new ElytronECDSACryptoProvider();
    }

    @Override
    public ECParameterSpec createECParams(String curveName) {
        AlgorithmParameters params;
        try {
            params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec(curveName));
            return params.getParameterSpec(ECParameterSpec.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate EC parameter spec", e);
        }
    }

    @Override
    public KeyPairGenerator getKeyPairGen(String algorithm) throws NoSuchAlgorithmException {
        return KeyPairGenerator.getInstance(algorithm);
    }

    @Override
    public KeyFactory getKeyFactory(String algorithm) throws NoSuchAlgorithmException {
        if("ECDSA".equals(algorithm)) {
            // ECDSA is not a listed JavaSE KeyFactory algorithm
            // see https://docs.oracle.com/en/java/javase/11/docs/specs/security/standard-names.html#cipher-algorithm-names
            algorithm = "EC";
        }
        return KeyFactory.getInstance(algorithm);
    }

    @Override
    public Cipher getAesCbcCipher() throws NoSuchAlgorithmException, NoSuchPaddingException {
        return Cipher.getInstance("AES/CBC/PKCS5Padding");
    }

    @Override
    public Cipher getAesGcmCipher() throws NoSuchAlgorithmException, NoSuchPaddingException {
        return Cipher.getInstance("AES/GCM/NoPadding");
    }

    @Override
    public SecretKeyFactory getSecretKeyFact(String keyAlgorithm) throws NoSuchAlgorithmException {
        return SecretKeyFactory.getInstance(keyAlgorithm);
    }
    
    @Override
    public KeyStore getKeyStore(KeystoreFormat format) throws KeyStoreException {
            return KeyStore.getInstance(format.toString());
    }

    @Override
    public CertificateFactory getX509CertFactory() throws CertificateException {
        return CertificateFactory.getInstance("X.509");
    }

    @Override
    public CertStore getCertStore(CollectionCertStoreParameters certStoreParams) throws InvalidAlgorithmParameterException, NoSuchAlgorithmException {

        return CertStore.getInstance("Collection", certStoreParams);

    }

    @Override
    public CertPathBuilder getCertPathBuilder() throws NoSuchAlgorithmException {
        return CertPathBuilder.getInstance("PKIX");
    }

    @Override
    public Signature getSignature(String sigAlgName) throws NoSuchAlgorithmException {
        return Signature.getInstance(JavaAlgorithm.getJavaAlgorithm(sigAlgName));
            
    }

    @Override
    public SSLSocketFactory wrapFactoryForTruststore(SSLSocketFactory delegate) {
        return delegate;
    }
}
