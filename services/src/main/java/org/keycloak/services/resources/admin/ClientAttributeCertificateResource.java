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

package org.keycloak.services.resources.admin;

import org.jboss.resteasy.annotations.cache.NoCache;
import javax.ws.rs.NotAcceptableException;
import javax.ws.rs.NotFoundException;

import org.keycloak.common.crypto.CryptoIntegration;
import org.keycloak.common.util.PemUtils;
import org.keycloak.common.util.StreamUtil;
import org.keycloak.common.util.KeystoreUtil.KeystoreFormat;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.http.FormPartValue;
import org.keycloak.jose.jwk.JSONWebKeySet;
import org.keycloak.jose.jwk.JWK;
import org.keycloak.jose.jwk.JWKParser;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeyManager;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.representations.KeyStoreConfig;
import org.keycloak.representations.idm.CertificateRepresentation;
import org.keycloak.services.ErrorResponseException;
import org.keycloak.services.resources.admin.permissions.AdminPermissionEvaluator;
import org.keycloak.services.util.CertificateInfoHelper;
import org.keycloak.util.JWKSUtils;
import org.keycloak.util.JsonSerialization;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @resource Client Attribute Certificate
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class ClientAttributeCertificateResource {

    public static final String CERTIFICATE_PEM = "Certificate PEM";
    public static final String PUBLIC_KEY_PEM = "Public Key PEM";
    public static final String JSON_WEB_KEY_SET = "JSON Web Key Set";

    protected final RealmModel realm;
    private final AdminPermissionEvaluator auth;
    protected final ClientModel client;
    protected final KeycloakSession session;
    protected final AdminEventBuilder adminEvent;
    protected final String attributePrefix;

    public ClientAttributeCertificateResource(AdminPermissionEvaluator auth, ClientModel client, KeycloakSession session, String attributePrefix, AdminEventBuilder adminEvent) {
        this.realm = session.getContext().getRealm();
        this.auth = auth;
        this.client = client;
        this.session = session;
        this.attributePrefix = attributePrefix;
        this.adminEvent = adminEvent.resource(ResourceType.CLIENT);
    }

    /**
     * Get key info
     *
     * @return
     */
    @GET
    @NoCache
    @Produces(MediaType.APPLICATION_JSON)
    public CertificateRepresentation getKeyInfo() {
        auth.clients().requireView(client);

        CertificateRepresentation info = CertificateInfoHelper.getCertificateFromClient(client, attributePrefix);
        return info;
    }

    /**
     * Generate a new certificate with new key pair
     *
     * @return
     */
    @POST
    @NoCache
    @Path("generate")
    @Produces(MediaType.APPLICATION_JSON)
    public CertificateRepresentation generate() {
        auth.clients().requireConfigure(client);

        CertificateRepresentation info = KeycloakModelUtils.generateKeyPairCertificate(client.getClientId());

        CertificateInfoHelper.updateClientModelCertificateInfo(client, info, attributePrefix);

        adminEvent.operation(OperationType.ACTION).resourcePath(session.getContext().getUri()).representation(info).success();

        return info;
    }

    /**
     * Upload certificate and eventually private key
     *
     * @param input
     * @return
     * @throws IOException
     */
    @POST
    @Path("upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public CertificateRepresentation uploadJks() throws IOException {
        auth.clients().requireConfigure(client);

        try {
            CertificateRepresentation info = getCertFromRequest();
            CertificateInfoHelper.updateClientModelCertificateInfo(client, info, attributePrefix);

            adminEvent.operation(OperationType.ACTION).resourcePath(session.getContext().getUri()).representation(info).success();
            return info;
        } catch (IllegalStateException ise) {
            throw new ErrorResponseException("certificate-not-found", "Certificate or key with given alias not found in the keystore", Response.Status.BAD_REQUEST);
        }
    }

    /**
     * Upload only certificate, not private key
     *
     * @param input
     * @return information extracted from uploaded certificate - not necessarily the new state of certificate on the server
     * @throws IOException
     */
    @POST
    @Path("upload-certificate")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public CertificateRepresentation uploadJksCertificate() throws IOException {
        auth.clients().requireConfigure(client);

        try {
            CertificateRepresentation info = getCertFromRequest();
            info.setPrivateKey(null);
            CertificateInfoHelper.updateClientModelCertificateInfo(client, info, attributePrefix);

            adminEvent.operation(OperationType.ACTION).resourcePath(session.getContext().getUri()).representation(info).success();
            return info;
        } catch (IllegalStateException ise) {
            throw new ErrorResponseException("certificate-not-found", "Certificate or key with given alias not found in the keystore", Response.Status.BAD_REQUEST);
        }
    }

    private CertificateRepresentation getCertFromRequest() throws IOException {
        auth.clients().requireManage(client);
        CertificateRepresentation info = new CertificateRepresentation();
        MultivaluedMap<String, FormPartValue> uploadForm = session.getContext().getHttpRequest().getMultiPartFormParameters();
        FormPartValue keystoreFormatPart = uploadForm.getFirst("keystoreFormat");
        if (keystoreFormatPart == null) throw new BadRequestException();
        String keystoreFormat = keystoreFormatPart.asString();
        FormPartValue inputParts = uploadForm.getFirst("file");
        if (keystoreFormat.equals(CERTIFICATE_PEM)) {
            String pem = StreamUtil.readString(inputParts.asInputStream());

            pem = PemUtils.removeBeginEnd(pem);

            // Validate format
            KeycloakModelUtils.getCertificate(pem);

            info.setCertificate(pem);
            return info;
        } else if (keystoreFormat.equals(PUBLIC_KEY_PEM)) {
            String pem = StreamUtil.readString(inputParts.asInputStream());

            // Validate format
            KeycloakModelUtils.getPublicKey(pem);

            info.setPublicKey(pem);
            return info;
        } else if (keystoreFormat.equals(JSON_WEB_KEY_SET)) {
            InputStream stream = inputParts.asInputStream();
            JSONWebKeySet keySet = JsonSerialization.readValue(stream, JSONWebKeySet.class);
            JWK publicKeyJwk = JWKSUtils.getKeyForUse(keySet, JWK.Use.SIG);
            if (publicKeyJwk == null) {
                throw new IllegalStateException("Certificate not found for use sig");
            } else {
                PublicKey publicKey = JWKParser.create(publicKeyJwk).toPublicKey();
                String publicKeyPem = KeycloakModelUtils.getPemFromKey(publicKey);
                info.setPublicKey(publicKeyPem);
                info.setKid(publicKeyJwk.getKeyId());
                return info;
            }
        }


        String keyAlias = uploadForm.getFirst("keyAlias").asString();
        FormPartValue keyPasswordPart = uploadForm.getFirst("keyPassword");
        char[] keyPassword = keyPasswordPart != null ? keyPasswordPart.asString().toCharArray() : null;

        FormPartValue storePasswordPart = uploadForm.getFirst("storePassword");
        char[] storePassword = storePasswordPart != null ? storePasswordPart.asString().toCharArray() : null;
        PrivateKey privateKey = null;
        X509Certificate certificate = null;
        try {
            KeyStore keyStore = CryptoIntegration.getProvider().getKeyStore(KeystoreFormat.valueOf(keystoreFormat));
            keyStore.load(inputParts.asInputStream(), storePassword);
            try {
                privateKey = (PrivateKey)keyStore.getKey(keyAlias, keyPassword);
            } catch (Exception e) {
                // ignore
            }
            certificate = (X509Certificate)keyStore.getCertificate(keyAlias);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (privateKey != null) {
            String privateKeyPem = KeycloakModelUtils.getPemFromKey(privateKey);
            info.setPrivateKey(privateKeyPem);
        }

        if (certificate != null) {
            String certPem = KeycloakModelUtils.getPemFromCertificate(certificate);
            info.setCertificate(certPem);
        }

        return info;
    }

    /**
     * Get a keystore file for the client, containing private key and public certificate
     *
     * @param config Keystore configuration as JSON
     * @return
     */
    @POST
    @NoCache
    @Path("/download")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Consumes(MediaType.APPLICATION_JSON)
    public byte[] getKeystore(final KeyStoreConfig config) {
        auth.clients().requireView(client);

        checkKeystoreFormat(config);

        CertificateRepresentation info = CertificateInfoHelper.getCertificateFromClient(client, attributePrefix);
        String privatePem = info.getPrivateKey();
        String certPem = info.getCertificate();

        if (privatePem == null && certPem == null) {
            throw new NotFoundException("keypair not generated for client");
        }
        if (privatePem != null && config.getKeyPassword() == null) {
            throw new ErrorResponseException("password-missing", "Need to specify a key password for jks download", Response.Status.BAD_REQUEST);
        }
        if (config.getStorePassword() == null) {
            throw new ErrorResponseException("password-missing", "Need to specify a store password for jks download", Response.Status.BAD_REQUEST);
        }

        byte[] rtn = getKeystore(config, privatePem, certPem);
        return rtn;
    }

    /**
     * Generate a new keypair and certificate, and get the private key file
     *
     * Generates a keypair and certificate and serves the private key in a specified keystore format.
     * Only generated public certificate is saved in Keycloak DB - the private key is not.
     *
     * @param config Keystore configuration as JSON
     * @return
     */
    @POST
    @NoCache
    @Path("/generate-and-download")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Consumes(MediaType.APPLICATION_JSON)
    public byte[] generateAndGetKeystore(final KeyStoreConfig config) {
        auth.clients().requireConfigure(client);

        checkKeystoreFormat(config);
        if (config.getKeyPassword() == null) {
            throw new ErrorResponseException("password-missing", "Need to specify a key password for jks generation and download", Response.Status.BAD_REQUEST);
        }
        if (config.getStorePassword() == null) {
            throw new ErrorResponseException("password-missing", "Need to specify a store password for jks generation and download", Response.Status.BAD_REQUEST);
        }

        CertificateRepresentation info = KeycloakModelUtils.generateKeyPairCertificate(client.getClientId());
        byte[] rtn = getKeystore(config, info.getPrivateKey(), info.getCertificate());

        info.setPrivateKey(null);

        CertificateInfoHelper.updateClientModelCertificateInfo(client, info, attributePrefix);

        adminEvent.operation(OperationType.ACTION).resourcePath(session.getContext().getUri()).representation(info).success();
        return rtn;
    }

    private byte[] getKeystore(KeyStoreConfig config, String privatePem, String certPem) {
        try {
            String format = config.getFormat();
            KeyStore keyStore = CryptoIntegration.getProvider().getKeyStore(KeystoreFormat.valueOf(format));
            keyStore.load(null, null);
            String keyAlias = config.getKeyAlias();
            if (keyAlias == null) keyAlias = client.getClientId();
            if (privatePem != null) {
                PrivateKey privateKey = PemUtils.decodePrivateKey(privatePem);
                X509Certificate clientCert = PemUtils.decodeCertificate(certPem);


                Certificate[] chain =  {clientCert};

                keyStore.setKeyEntry(keyAlias, privateKey, config.getKeyPassword().trim().toCharArray(), chain);
            } else {
                X509Certificate clientCert = PemUtils.decodeCertificate(certPem);
                keyStore.setCertificateEntry(keyAlias, clientCert);
            }


            if (config.isRealmCertificate() == null || config.isRealmCertificate().booleanValue()) {
                KeyManager keys = session.keys();
                String kid = keys.getActiveRsaKey(realm).getKid();
                Certificate certificate = keys.getRsaCertificate(realm, kid);
                String certificateAlias = config.getRealmAlias();
                if (certificateAlias == null) certificateAlias = realm.getName();
                keyStore.setCertificateEntry(certificateAlias, certificate);

            }
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            keyStore.store(stream, config.getStorePassword().trim().toCharArray());
            stream.flush();
            stream.close();
            byte[] rtn = stream.toByteArray();
            return rtn;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void checkKeystoreFormat(KeyStoreConfig config) throws NotAcceptableException {
        if (config.getFormat() != null) {
            Set<KeystoreFormat> supportedKeystoreFormats = CryptoIntegration.getProvider().getSupportedKeyStoreTypes()
                    .collect(Collectors.toSet());
            try {
                KeystoreFormat format = Enum.valueOf(KeystoreFormat.class, config.getFormat().toUpperCase());
                if (config.getFormat() != null && !supportedKeystoreFormats.contains(format)) {
                    throw new NotAcceptableException("Not supported keystore format. Supported keystore formats: " + supportedKeystoreFormats);
                }
            } catch (IllegalArgumentException iae) {
                throw new NotAcceptableException("Not supported keystore format. Supported keystore formats: " + supportedKeystoreFormats);
            }
        }
    }


}
