package org.keycloak.services.x509;

import org.jboss.logging.Logger;
import org.keycloak.http.HttpRequest;
import org.keycloak.common.util.PemException;
import org.keycloak.common.util.PemUtils;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;

/**
 * The NGINX Trusted Provider verify extract end user X.509 certificate sent during TLS mutual authentication,
 * verifies it against provided CA the and forwarded in an HTTP header along with a new header ssl-client-verify: SUCCESS.
 *
 * NGINX configuration must have :
 * <code>
 * server {
 *    ...
 *    ssl_client_certificate                  path-to-trusted-ca.crt;
 *    ssl_verify_client                       on|optional;
 *    ssl_verify_depth                        2;
 *    ...
 *    location / {
 *    ...
 *      proxy_set_header ssl-client-cert        $ssl_client_escaped_cert;
 *    ...
 *  }
 * </code>
 *
 * Note that $ssl_client_cert is deprecated, use only $ssl_client_escaped_cert with this implementation
 *
 * @author <a href="mailto:youssef.elhouti@tailosoft.com">Youssef El Houti</a>
 * @version $Revision: 1 $
 * @since 01/09/2022
 */

public class NginxProxyTrustedClientCertificateLookup extends AbstractClientCertificateFromHttpHeadersLookup {

    private static final Logger log = Logger.getLogger(NginxProxyTrustedClientCertificateLookup.class);

    public NginxProxyTrustedClientCertificateLookup(String sslCientCertHttpHeader,
                                                String sslCertChainHttpHeaderPrefix,
                                                int certificateChainLength) {
        super(sslCientCertHttpHeader, sslCertChainHttpHeaderPrefix, certificateChainLength);
    }

    @Override
    protected X509Certificate getCertificateFromHttpHeader(HttpRequest request, String httpHeader) throws GeneralSecurityException {
        X509Certificate certificate = super.getCertificateFromHttpHeader(request, httpHeader);
        if (certificate == null) {
            return null;
        }
        String validCertificateResult = getHeaderValue(request, "ssl-client-verify");
        if ("SUCCESS".equals(validCertificateResult)) {
            return certificate;
        } else {
            log.warn("nginx could not verify the certificate: ssl-client-verify: " + validCertificateResult);
            return null;
        }
    }

    @Override
    protected X509Certificate decodeCertificateFromPem(String pem) throws PemException {

        if (pem == null) {
            log.warn("End user TLS Certificate is NULL! ");
            return null;
        }
        try {
            pem = java.net.URLDecoder.decode(pem, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.error("Cannot URL decode the end user TLS Certificate : " + pem,e);
        }

        return PemUtils.decodeCertificate(pem);
    }

}
