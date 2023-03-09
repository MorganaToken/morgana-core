package org.keycloak.config;

import java.io.File;
import org.keycloak.common.crypto.FipsMode;

public class HttpOptions {

    public static final Option<Boolean> HTTP_ENABLED = new OptionBuilder<>("http-enabled", Boolean.class)
            .category(OptionCategory.HTTP)
            .description("Enables the HTTP listener.")
            .defaultValue(Boolean.FALSE)
            .build();

    public static final Option HTTP_HOST = new OptionBuilder<>("http-host", String.class)
            .category(OptionCategory.HTTP)
            .description("The used HTTP Host.")
            .defaultValue("0.0.0.0")
            .build();

    public static final Option HTTP_RELATIVE_PATH = new OptionBuilder<>("http-relative-path", String.class)
            .category(OptionCategory.HTTP)
            .description("Set the path relative to '/' for serving resources. The path must start with a '/'.")
            .defaultValue("/")
            .buildTime(true)
            .build();

    public static final Option<Integer> HTTP_PORT = new OptionBuilder<>("http-port", Integer.class)
            .category(OptionCategory.HTTP)
            .description("The used HTTP port.")
            .defaultValue(8080)
            .build();

    public static final Option<Integer> HTTPS_PORT = new OptionBuilder<>("https-port", Integer.class)
            .category(OptionCategory.HTTP)
            .description("The used HTTPS port.")
            .defaultValue(8443)
            .build();

    public enum ClientAuth {
        none,
        request,
        required
    }

    public static final Option HTTPS_CLIENT_AUTH = new OptionBuilder<>("https-client-auth", ClientAuth.class)
            .category(OptionCategory.HTTP)
            .description("Configures the server to require/request client authentication.")
            .defaultValue(ClientAuth.none)
            .build();

    public static final Option HTTPS_CIPHER_SUITES = new OptionBuilder<>("https-cipher-suites", String.class)
            .category(OptionCategory.HTTP)
            .description("The cipher suites to use. If none is given, a reasonable default is selected.")
            .build();

    public static final Option HTTPS_PROTOCOLS = new OptionBuilder<>("https-protocols", String.class)
            .category(OptionCategory.HTTP)
            .description("The list of protocols to explicitly enable.")
            .defaultValue("TLSv1.3")
            .build();

    public static final Option HTTPS_CERTIFICATE_FILE = new OptionBuilder<>("https-certificate-file", File.class)
            .category(OptionCategory.HTTP)
            .description("The file path to a server certificate or certificate chain in PEM format.")
            .build();

    public static final Option HTTPS_CERTIFICATE_KEY_FILE = new OptionBuilder<>("https-certificate-key-file", File.class)
            .category(OptionCategory.HTTP)
            .description("The file path to a private key in PEM format.")
            .build();

    public static final Option HTTPS_KEY_STORE_FILE = new OptionBuilder<>("https-key-store-file", File.class)
            .category(OptionCategory.HTTP)
            .description("The key store which holds the certificate information instead of specifying separate files.")
            .build();

    public static final Option HTTPS_KEY_STORE_PASSWORD = new OptionBuilder<>("https-key-store-password", String.class)
            .category(OptionCategory.HTTP)
            .description("The password of the key store file.")
            .defaultValue("password")
            .build();

    public static final Option<String> HTTPS_KEY_STORE_TYPE = new OptionBuilder<>("https-key-store-type", String.class)
            .category(OptionCategory.HTTP)
            .description("The type of the key store file. " +
                    "If not given, the type is automatically detected based on the file name. " +
                    "If '" + SecurityOptions.FIPS_MODE.getKey() + "' is set to '" + FipsMode.STRICT + "' and no value is set, it defaults to 'BCFKS'.")
            .build();

    public static final Option HTTPS_TRUST_STORE_FILE = new OptionBuilder<>("https-trust-store-file", File.class)
            .category(OptionCategory.HTTP)
            .description("The trust store which holds the certificate information of the certificates to trust.")
            .build();

    public static final Option HTTPS_TRUST_STORE_PASSWORD = new OptionBuilder<>("https-trust-store-password", String.class)
            .category(OptionCategory.HTTP)
            .description("The password of the trust store file.")
            .build();

    public static final Option<String> HTTPS_TRUST_STORE_TYPE = new OptionBuilder<>("https-trust-store-type", String.class)
            .category(OptionCategory.HTTP)
            .description("The type of the trust store file. " +
                    "If not given, the type is automatically detected based on the file name. " +
                    "If '" + SecurityOptions.FIPS_MODE.getKey() + "' is set to '" + FipsMode.STRICT + "' and no value is set, it defaults to 'BCFKS'.")
            .build();

    public static final Option<Boolean> HTTP_SERVER_ENABLED = new OptionBuilder<>("http-server-enabled", Boolean.class)
            .category(OptionCategory.HTTP)
            .hidden()
            .description("Enables or disables the HTTP/s and Socket serving.")
            .defaultValue(Boolean.TRUE)
            .build();
}
