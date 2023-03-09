package org.keycloak.testsuite.util;

import org.jboss.arquillian.container.spi.Container;
import org.jboss.logging.Logger;
import org.keycloak.testsuite.arquillian.ContainerInfo;
import org.keycloak.testsuite.arquillian.SuiteContext;
import org.keycloak.testsuite.arquillian.annotation.SetDefaultProvider;
import org.keycloak.testsuite.arquillian.containers.AbstractQuarkusDeployableContainer;
import org.keycloak.utils.StringUtil;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class SpiProvidersSwitchingUtils {

    private static final String KEYCLOAKX_ARG_SPI_PREFIX = "--spi-";
    private static final Map<String, String> originalSettingsBackup = new ConcurrentHashMap<>();
    protected static final Logger log = Logger.getLogger(SpiProvidersSwitchingUtils.class);

    private enum SpiSwitcher {
        UNDERTOW {
            @Override
            public Optional<String> getCurrentDefaultProvider(Container container, String spiName,
                    SetDefaultProvider annotation) {
                return Optional.ofNullable(System.getProperty(getProviderPropertyName(spiName)));
            }

            @Override
            public void setDefaultProvider(Container container, String spiName, String providerId) {
                System.setProperty(getProviderPropertyName(spiName), providerId);
            }

            @Override
            public void removeProviderConfig(Container container, String spiName) {
                System.clearProperty(getProviderPropertyName(spiName));
            }

            private String getProviderPropertyName(String spiName) {
                return "keycloak." + spiName + ".provider";
            }
        },
        QUARKUS {
            @Override
            public void setDefaultProvider(Container container, String spiName, String providerId) {
                getQuarkusContainer(container).setAdditionalBuildArgs(Collections
                        .singletonList(KEYCLOAKX_ARG_SPI_PREFIX + toDashCase(spiName) + "-provider=" + providerId));
            }

            @Override
            public void removeProviderConfig(Container container, String spiName) {
                getQuarkusContainer(container).setAdditionalBuildArgs(Collections.emptyList());
            }

            private AbstractQuarkusDeployableContainer getQuarkusContainer(Container container) {
                return (AbstractQuarkusDeployableContainer) container.getDeployableContainer();
            }

            /**
             * Parses the non-standard SPI-Name format to the standardized format
             * we use in the Keycloak.X Configuration
             *
             * @param s possibly non-standard spi name
             * @return standardized spi name in dash-case. e.g. userProfile -> user-profile
             */
            private String toDashCase(String s) {
                StringBuilder sb = new StringBuilder(s.length());
                boolean l = false;

                for (int i = 0; i < s.length(); i++) {
                    char c = s.charAt(i);
                    if (l && Character.isUpperCase(c)) {
                        sb.append('-');
                        c = Character.toLowerCase(c);
                        l = false;
                    } else {
                        l = Character.isLowerCase(c);
                    }
                    sb.append(c);
                }
                return sb.toString();
            }
        };

        public Optional<String> getCurrentDefaultProvider(Container container, String spiName,
                SetDefaultProvider annotation) {
            String defaultProvider = annotation.defaultProvider();
            if (StringUtil.isNotBlank(defaultProvider)) {
                return Optional.of(defaultProvider);
            }
            return Optional.empty();
        }

        public abstract void setDefaultProvider(Container container, String spiName, String providerId);

        public void updateDefaultProvider(Container container, String spiName, String providerId) {
            setDefaultProvider(container, spiName, providerId);
        }

        public void unsetDefaultProvider(Container container, String spiName) {
            removeProviderConfig(container, spiName);
        }

        public abstract void removeProviderConfig(Container container, String spiName);

        public static SpiSwitcher getSpiSwitcherFor(ContainerInfo containerInfo) {
            if (containerInfo.isUndertow()) {
                return SpiSwitcher.UNDERTOW;
            }
            return SpiSwitcher.QUARKUS;
        }
    }

    private SpiProvidersSwitchingUtils() {
    }

    public static void addProviderDefaultValue(SuiteContext suiteContext, SetDefaultProvider annotation) {
        ContainerInfo authServerInfo = suiteContext.getAuthServerInfo();
        SpiSwitcher spiSwitcher = SpiSwitcher.getSpiSwitcherFor(authServerInfo);
        String spi = annotation.spi();
        Container container = authServerInfo.getArquillianContainer();

        log.infof("Setting default provider for %s to %s", spi, annotation.providerId());

        if (annotation.onlyUpdateDefault()) {
            spiSwitcher.getCurrentDefaultProvider(container, spi, annotation).ifPresent(v -> originalSettingsBackup.put(spi, v));
            spiSwitcher.updateDefaultProvider(container, spi, annotation.providerId());
        } else {
            spiSwitcher.setDefaultProvider(container, spi, annotation.providerId());
        }
    }

    public static void resetProvider(SuiteContext suiteContext, SetDefaultProvider annotation) {
        ContainerInfo authServerInfo = suiteContext.getAuthServerInfo();
        SpiSwitcher spiSwitcher = SpiSwitcher.getSpiSwitcherFor(authServerInfo);
        String spi = annotation.spi();
        Container container = authServerInfo.getArquillianContainer();

        if (annotation.onlyUpdateDefault()) {
            String originalValue = originalSettingsBackup.get(spi);

            log.infof("Resetting default provider for %s to %s", spi,
                    originalValue == null ? "<null>" : originalValue);

            if (originalValue != null) {
                spiSwitcher.updateDefaultProvider(container, spi, originalValue);
            } else {
                spiSwitcher.unsetDefaultProvider(container, spi);
            }
        } else {
            log.infof("Removing default provider for %s to %s", spi);
            spiSwitcher.removeProviderConfig(container, spi);
        }
    }

    public static void removeProvider(SuiteContext suiteContext, SetDefaultProvider annotation) {
        ContainerInfo authServerInfo = suiteContext.getAuthServerInfo();
        SpiSwitcher spiSwitcher = SpiSwitcher.getSpiSwitcherFor(authServerInfo);
        String spi = annotation.spi();
        Container container = authServerInfo.getArquillianContainer();

        log.infof("Removing default provider setting for %s", spi);
        
        if (annotation.onlyUpdateDefault()) {
            spiSwitcher.unsetDefaultProvider(container, spi);
        } else {
            spiSwitcher.removeProviderConfig(container, spi);
        }
    }
}
