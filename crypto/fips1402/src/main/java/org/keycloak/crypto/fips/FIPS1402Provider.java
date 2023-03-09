package org.keycloak.crypto.fips;

import java.net.Socket;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.spec.ECField;
import java.security.spec.ECFieldF2m;
import java.security.spec.ECFieldFp;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.EllipticCurve;
import java.security.Security;
import java.security.Signature;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKeyFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import org.bouncycastle.asn1.x9.ECNamedCurveTable;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.fips.FipsRSA;
import org.bouncycastle.crypto.fips.FipsSHS;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.bouncycastle.jsse.util.CustomSSLSocketFactory;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.util.IPAddress;
import org.jboss.logging.Logger;
import org.keycloak.common.crypto.CryptoProvider;
import org.keycloak.common.crypto.ECDSACryptoProvider;
import org.keycloak.common.crypto.CryptoConstants;
import org.keycloak.common.crypto.CertificateUtilsProvider;
import org.keycloak.common.crypto.PemUtilsProvider;
import org.keycloak.common.crypto.UserIdentityExtractorProvider;
import org.keycloak.common.util.BouncyIntegration;
import org.keycloak.common.util.KeystoreUtil.KeystoreFormat;
import org.keycloak.common.util.Resteasy;
import org.keycloak.crypto.JavaAlgorithm;
import org.keycloak.models.Constants;
import org.keycloak.models.KeycloakSession;


/**
 * Integration based on FIPS 140-2
 *
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class FIPS1402Provider implements CryptoProvider {

    private static final Logger log = Logger.getLogger(FIPS1402Provider.class);

    private final BouncyCastleFipsProvider bcFipsProvider;
    private final Map<String, Object> providers = new ConcurrentHashMap<>();

    public FIPS1402Provider() {
        // Case when BCFIPS provider already registered in Java security file
        BouncyCastleFipsProvider existingBcFipsProvider = (BouncyCastleFipsProvider) Security.getProvider(CryptoConstants.BCFIPS_PROVIDER_ID);
        this.bcFipsProvider = existingBcFipsProvider == null ? new BouncyCastleFipsProvider() : existingBcFipsProvider;

        providers.put(CryptoConstants.A128KW, new FIPSAesKeyWrapAlgorithmProvider());
        providers.put(CryptoConstants.RSA1_5, new FIPSRsaKeyEncryptionJWEAlgorithmProvider(FipsRSA.WRAP_PKCS1v1_5));
        providers.put(CryptoConstants.RSA_OAEP, new FIPSRsaKeyEncryptionJWEAlgorithmProvider(FipsRSA.WRAP_OAEP));
        providers.put(CryptoConstants.RSA_OAEP_256, new FIPSRsaKeyEncryptionJWEAlgorithmProvider(FipsRSA.WRAP_OAEP.withDigest(FipsSHS.Algorithm.SHA256)));

        Security.insertProviderAt(new KeycloakFipsSecurityProvider(bcFipsProvider), 1);
        if (existingBcFipsProvider == null) {
            checkSecureRandom(() -> Security.insertProviderAt(this.bcFipsProvider, 2));
            Provider bcJsseProvider = new BouncyCastleJsseProvider("fips:BCFIPS");
            Security.insertProviderAt(bcJsseProvider, 3);
            // force the key and trust manager factories if default values not present in BCJSSE
            modifyKeyTrustManagerSecurityProperties(bcJsseProvider);
            log.debugf("Inserted security providers: %s", Arrays.asList(this.bcFipsProvider.getName(),bcJsseProvider.getName()));
        } else {
            log.debugf("Security provider %s already loaded", existingBcFipsProvider.getName());
        }
    }


    @Override
    public Provider getBouncyCastleProvider() {
        return bcFipsProvider;
    }

    @Override
    public <T> T getAlgorithmProvider(Class<T> clazz, String algorithm) {
        Object o = providers.get(algorithm);
        if (o == null) {
            throw new IllegalArgumentException("Not found provider of algorithm: " + algorithm);
        }
        return clazz.cast(o);
    }

    @Override
    public CertificateUtilsProvider getCertificateUtils() {
        return new BCFIPSCertificateUtilsProvider();
    }

    @Override
    public PemUtilsProvider getPemUtils() {
        return new BCFIPSPemUtilsProvider();
    }

    /* Create EC Params using BC FipS APIs.
     *
     * @see org.keycloak.common.crypto.CryptoProvider#createECParams(java.lang.String)
     */
    @Override
    public ECParameterSpec createECParams(String curveName) {
        X9ECParameters params = ECNamedCurveTable.getByName(curveName);
        ECField field ;
        ECCurve ecCurve = params.getCurve();
        if (ecCurve instanceof ECCurve.F2m) {
            ECCurve.F2m f2m = (ECCurve.F2m) ecCurve;
            field = new ECFieldF2m(f2m.getM(), new int[] { f2m.getK1(), f2m.getK2(), f2m.getK3()});
        }
        else
        if (ecCurve instanceof ECCurve.Fp) {
            ECCurve.Fp fp = (ECCurve.Fp) ecCurve;
            field = new ECFieldFp(fp.getQ());
        }
        else
            throw new RuntimeException("Unsupported curve");


        EllipticCurve c = new EllipticCurve(field,
                ecCurve.getA().toBigInteger(),
                ecCurve.getB().toBigInteger(),
                params.getSeed());
        ECPoint point = new ECPoint( params.getG().getXCoord().toBigInteger(), params.getG().getYCoord().toBigInteger());
        return new ECParameterSpec( c,point, params.getN(), params.getH().intValue());
    }

    @Override
    public UserIdentityExtractorProvider getIdentityExtractorProvider() {
        return new BCFIPSUserIdentityExtractorProvider();
    }

    @Override
    public ECDSACryptoProvider getEcdsaCryptoProvider() {
        return new BCFIPSECDSACryptoProvider();
    }


    @Override
    public <T> T getOCSPProver(Class<T> clazz) {
        return clazz.cast(new BCFIPSOCSPProvider());
    }


    @Override
    public KeyPairGenerator getKeyPairGen(String algorithm) throws NoSuchAlgorithmException, NoSuchProviderException {
        return KeyPairGenerator.getInstance(algorithm, BouncyIntegration.PROVIDER);
    }

    @Override
    public KeyFactory getKeyFactory(String algorithm) throws NoSuchAlgorithmException, NoSuchProviderException {
        return KeyFactory.getInstance(algorithm , BouncyIntegration.PROVIDER);
    }

    @Override
    public Cipher getAesCbcCipher() throws NoSuchAlgorithmException, NoSuchProviderException, NoSuchPaddingException {
        return Cipher.getInstance("AES/CBC/PKCS7Padding", BouncyIntegration.PROVIDER);
    }

    @Override
    public Cipher getAesGcmCipher() throws NoSuchAlgorithmException, NoSuchProviderException, NoSuchPaddingException {
        return Cipher.getInstance("AES/GCM/NoPadding", BouncyIntegration.PROVIDER);
    }
    
    @Override
    public SecretKeyFactory getSecretKeyFact(String keyAlgorithm) throws NoSuchAlgorithmException, NoSuchProviderException {
        return SecretKeyFactory.getInstance(keyAlgorithm, BouncyIntegration.PROVIDER);
    }
    
    @Override
    public KeyStore getKeyStore(KeystoreFormat format) throws KeyStoreException, NoSuchProviderException {
        return KeyStore.getInstance(format.toString(), BouncyIntegration.PROVIDER);
    }

    @Override
    public CertificateFactory getX509CertFactory() throws CertificateException, NoSuchProviderException {
        return CertificateFactory.getInstance("X.509", BouncyIntegration.PROVIDER);
    }

    @Override
    public CertStore getCertStore(CollectionCertStoreParameters certStoreParams) throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {

        return CertStore.getInstance("Collection", certStoreParams, BouncyIntegration.PROVIDER);

    }

    @Override
    public CertPathBuilder getCertPathBuilder() throws NoSuchAlgorithmException, NoSuchProviderException {
        return CertPathBuilder.getInstance("PKIX", BouncyIntegration.PROVIDER);
    }
    
    @Override
    public Signature getSignature(String sigAlgName) throws NoSuchAlgorithmException, NoSuchProviderException {
        return Signature.getInstance(JavaAlgorithm.getJavaAlgorithm(sigAlgName), BouncyIntegration.PROVIDER);
            
    }

    @Override
    public SSLSocketFactory wrapFactoryForTruststore(SSLSocketFactory delegate) {
        KeycloakSession session = Resteasy.getProvider().getContextData(KeycloakSession.class);
        if (session == null) {
            log.tracef("Not found keycloakSession in the resteasy context when trying to retrieve hostname attribute from it");
            return delegate;
        }
        String hostname = session.getAttribute(Constants.SSL_SERVER_HOST_ATTR, String.class);
        log.tracef("Found hostname '%s' to be used by SSLSocketFactory", hostname);
        if (hostname == null) return delegate;

        // See https://downloads.bouncycastle.org/fips-java/BC-FJA-(D)TLSUserGuide-1.0.9.pdf - Section 3.5.2 (Endpoint identification)
        return new CustomSSLSocketFactory(delegate) {

            @Override
            protected Socket configureSocket(Socket s) {
                if (s instanceof SSLSocket) {
                    SSLSocket ssl = (SSLSocket)s;
                    SNIHostName sniHostName = getSNIHostName(hostname);
                    if (sniHostName != null) {
                        SSLParameters sslParameters = new SSLParameters();
                        sslParameters.setServerNames(Collections.singletonList(sniHostName));
                        ssl.setSSLParameters(sslParameters);
                    }
                }
                return s;
            }

            private SNIHostName getSNIHostName(String host) {
                if (!IPAddress.isValid(host)) {
                    try {
                        return new SNIHostName(host);
                    } catch (RuntimeException e) {
                        log.warnf(e, "Not possible to create SNIHostName from the host '%s'", host);
                    }
                }
                return null;
            }

        };
    }

    // BCFIPS require "SecureRandom.getInstanceStrong" to be available. But it may not be available on RHEL 8 on OpenJDK 17 due the https://bugzilla.redhat.com/show_bug.cgi?id=2155060
    private void checkSecureRandom(Runnable insertBcFipsProvider) {
        try {
            SecureRandom sr = SecureRandom.getInstanceStrong();
            log.debugf("Strong secure random available. Algorithm: %s, Provider: %s", sr.getAlgorithm(), sr.getProvider());
            insertBcFipsProvider.run();
        } catch (NoSuchAlgorithmException nsae) {

            // Fallback to regular SecureRandom
            SecureRandom secRandom = new SecureRandom();
            String origStrongAlgs = Security.getProperty("securerandom.strongAlgorithms");
            String usedAlg = secRandom.getAlgorithm() + ":" + secRandom.getProvider().getName();
            log.debugf("Strong secure random not available. Tried algorithms: %s. Using algorithm as a fallback for strong secure random: %s", origStrongAlgs, usedAlg);

            String strongAlgs = origStrongAlgs == null ? usedAlg : usedAlg + "," + origStrongAlgs;
            Security.setProperty("securerandom.strongAlgorithms", strongAlgs);

            try {
                // Need to insert BCFIPS provider to security providers with "strong algorithm" available
                insertBcFipsProvider.run();
                SecureRandom.getInstance("DEFAULT", "BCFIPS");
                log.debugf("Initialized BCFIPS secured random");
            } catch (NoSuchAlgorithmException | NoSuchProviderException nsaee) {
                throw new IllegalStateException("Not possible to initiate BCFIPS secure random", nsaee);
            } finally {
                Security.setProperty("securerandom.strongAlgorithms", origStrongAlgs != null ? origStrongAlgs : "");
            }
        }
    }

    /**
     * BCJSSE manages X.509, X509 and PKIX for KeyManagerFactory and
     * TrustManagerFactory (names or aliases) while JSSE manages SunX509,
     * NewSunX509 and PKIX for KeyManagerFactory and SunX509, PKIX, SunPKIX,
     * X509 and X.509 for the TrustManagerFactory. As BCJSSE is used when
     * fips enabled, the default implementations are changed to the ones
     * provided by BC if selected ones are not present in the BCJSSE.
     *
     * @param bcJsseProvider The BCJSSE provider
     */
    private static void modifyKeyTrustManagerSecurityProperties(Provider bcJsseProvider) {
        boolean setKey = bcJsseProvider.getService(KeyManagerFactory.class.getSimpleName(), KeyManagerFactory.getDefaultAlgorithm()) == null;
        boolean setTrust = bcJsseProvider.getService(TrustManagerFactory.class.getSimpleName(), TrustManagerFactory.getDefaultAlgorithm()) == null;
        if (!setKey && !setTrust) {
            return;
        }
        Set<Provider.Service> services = bcJsseProvider.getServices();
        if (services != null) {
            for (Provider.Service service : services) {
                if (setKey && KeyManagerFactory.class.getSimpleName().equals(service.getType())) {
                    Security.setProperty("ssl.KeyManagerFactory.algorithm", service.getAlgorithm());
                    setKey = false;
                    if (!setTrust) {
                        return;
                    }
                } else if (setTrust && TrustManagerFactory.class.getSimpleName().equals(service.getType())) {
                    Security.setProperty("ssl.TrustManagerFactory.algorithm", service.getAlgorithm());
                    setTrust = false;
                    if (!setKey) {
                        return;
                    }
                }
            }
        }
        throw new IllegalStateException("Provider " + bcJsseProvider.getName()
                + " does not provide KeyManagerFactory or TrustManagerFactory algorithms for TLS");
    }
}
