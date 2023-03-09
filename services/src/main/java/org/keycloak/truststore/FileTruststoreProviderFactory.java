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

package org.keycloak.truststore;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.common.util.KeystoreUtil;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.security.auth.x500.X500Principal;

/**
 * @author <a href="mailto:mstrukel@redhat.com">Marko Strukelj</a>
 */
public class FileTruststoreProviderFactory implements TruststoreProviderFactory {

    private static final Logger log = Logger.getLogger(FileTruststoreProviderFactory.class);

    private TruststoreProvider provider;

    @Override
    public TruststoreProvider create(KeycloakSession session) {
        return provider;
    }

    @Override
    public void init(Config.Scope config) {

        String storepath = config.get("file");
        String pass = config.get("password");
        String policy = config.get("hostname-verification-policy");
        String configuredType = config.get("type");

        // if "truststore" . "file" is not configured then it is disabled
        if (storepath == null && pass == null && policy == null) {
            return;
        }

        HostnameVerificationPolicy verificationPolicy = null;
        KeyStore truststore = null;

        if (storepath == null) {
            throw new RuntimeException("Attribute 'file' missing in 'truststore':'file' configuration");
        }
        if (pass == null) {
            throw new RuntimeException("Attribute 'password' missing in 'truststore':'file' configuration");
        }

        String type = KeystoreUtil.getKeystoreType(configuredType, storepath, KeyStore.getDefaultType());
        try {
            truststore = loadStore(storepath, type, pass == null ? null :pass.toCharArray());
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize TruststoreProviderFactory: " + new File(storepath).getAbsolutePath() + ", truststore type: " + type, e);
        }
        if (policy == null) {
            verificationPolicy = HostnameVerificationPolicy.WILDCARD;
        } else {
            try {
                verificationPolicy = HostnameVerificationPolicy.valueOf(policy);
            } catch (Exception e) {
                throw new RuntimeException("Invalid value for 'hostname-verification-policy': " + policy + " (must be one of: ANY, WILDCARD, STRICT)");
            }
        }

        TruststoreCertificatesLoader certsLoader = new TruststoreCertificatesLoader(truststore);
        provider = new FileTruststoreProvider(truststore, verificationPolicy, Collections.unmodifiableMap(certsLoader.trustedRootCerts)
                , Collections.unmodifiableMap(certsLoader.intermediateCerts));
        TruststoreProviderSingleton.set(provider);
        log.debugf("File truststore provider initialized: %s, Truststore type: %s",  new File(storepath).getAbsolutePath(), type);
    }

    private KeyStore loadStore(String path, String type, char[] password) throws Exception {
        KeyStore ks = KeyStore.getInstance(type);
        InputStream is = new FileInputStream(path);
        try {
            ks.load(is, password);
            return ks;
        } finally {
            try {
                is.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return "file";
    }

    @Override
    public List<ProviderConfigProperty> getConfigMetadata() {
        return ProviderConfigurationBuilder.create()
                .property()
                .name("file")
                .type("string")
                .helpText("The file path of the trust store from where the certificates are going to be read from to validate TLS connections.")
                .add()
                .property()
                .name("password")
                .type("string")
                .helpText("The trust store password.")
                .add()
                .property()
                .name("hostname-verification-policy")
                .type("string")
                .helpText("The hostname verification policy.")
                .options(Arrays.stream(HostnameVerificationPolicy.values()).map(HostnameVerificationPolicy::name).map(String::toLowerCase).toArray(String[]::new))
                .defaultValue(HostnameVerificationPolicy.WILDCARD.name().toLowerCase())
                .add()
                .property()
                .name("type")
                .type("string")
                .helpText("Type of the truststore. If not provided, the type would be detected based on the truststore file extension or platform default type.")
                .add()
                .build();
    }

    private static class TruststoreCertificatesLoader {

        private Map<X500Principal, X509Certificate> trustedRootCerts = new HashMap<>();
        private Map<X500Principal, X509Certificate> intermediateCerts = new HashMap<>();


        public TruststoreCertificatesLoader(KeyStore truststore) {
            readTruststore(truststore);
        }

        /**
         * Get all certificates from Keycloak Truststore, and classify them in two lists : root CAs and intermediates CAs
         */
        private void readTruststore(KeyStore truststore) {

            //Reading truststore aliases & certificates
            Enumeration enumeration;

            try {

                enumeration = truststore.aliases();
                log.trace("Checking " + truststore.size() + " entries from the truststore.");
                while(enumeration.hasMoreElements()) {
                    String alias = (String)enumeration.nextElement();
                    readTruststoreEntry(truststore, alias);
                }
            } catch (KeyStoreException e) {
                log.error("Error while reading Keycloak truststore "+e.getMessage(),e);
            }
        }

        private void readTruststoreEntry(KeyStore truststore, String alias) {
            try {
                Certificate certificate = truststore.getCertificate(alias);

                if (certificate instanceof X509Certificate) {
                    X509Certificate cax509cert = (X509Certificate) certificate;
                    if (isSelfSigned(cax509cert)) {
                        X500Principal principal = cax509cert.getSubjectX500Principal();
                        trustedRootCerts.put(principal, cax509cert);
                        log.debug("Trusted root CA found in trustore : alias : " + alias + " | Subject DN : " + principal);
                    } else {
                        X500Principal principal = cax509cert.getSubjectX500Principal();
                        intermediateCerts.put(principal, cax509cert);
                        log.debug("Intermediate CA found in trustore : alias : " + alias + " | Subject DN : " + principal);
                    }
                } else
                    log.info("Skipping certificate with alias [" + alias + "] from truststore, because it's not an X509Certificate");
            } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | NoSuchProviderException e) {
                log.warnf("Error while reading Keycloak truststore entry [%s]. Exception message: %s", alias, e.getMessage(), e);
            }
        }

        /**
         * Checks whether given X.509 certificate is self-signed.
         */
        private boolean isSelfSigned(X509Certificate cert)
                throws CertificateException, NoSuchAlgorithmException,
                NoSuchProviderException {
            try {
                // Try to verify certificate signature with its own public key
                PublicKey key = cert.getPublicKey();
                cert.verify(key);
                log.trace("certificate " + cert.getSubjectDN() + " detected as root CA");
                return true;
            } catch (SignatureException sigEx) {
                // Invalid signature --> not self-signed
                log.trace("certificate " + cert.getSubjectDN() + " detected as intermediate CA");
            } catch (InvalidKeyException keyEx) {
                // Invalid key --> not self-signed
                log.trace("certificate " + cert.getSubjectDN() + " detected as intermediate CA");
            }
            return false;
        }
    }
}
