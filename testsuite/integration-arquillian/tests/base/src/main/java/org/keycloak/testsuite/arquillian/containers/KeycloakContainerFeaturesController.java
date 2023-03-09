package org.keycloak.testsuite.arquillian.containers;

import org.jboss.arquillian.container.spi.event.StartContainer;
import org.jboss.arquillian.container.spi.event.StopContainer;
import org.jboss.arquillian.core.api.Event;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.test.spi.event.suite.After;
import org.jboss.arquillian.test.spi.event.suite.AfterClass;
import org.jboss.arquillian.test.spi.event.suite.Before;
import org.jboss.arquillian.test.spi.event.suite.BeforeClass;
import org.keycloak.common.Profile;
import org.keycloak.testsuite.ProfileAssume;
import org.keycloak.testsuite.arquillian.SuiteContext;
import org.keycloak.testsuite.arquillian.TestContext;
import org.keycloak.testsuite.arquillian.annotation.DisableFeature;
import org.keycloak.testsuite.arquillian.annotation.DisableFeatures;
import org.keycloak.testsuite.arquillian.annotation.EnableFeature;
import org.keycloak.testsuite.arquillian.annotation.EnableFeatures;
import org.keycloak.testsuite.arquillian.annotation.SetDefaultProvider;
import org.keycloak.testsuite.client.KeycloakTestingClient;
import org.keycloak.testsuite.util.SpiProvidersSwitchingUtils;

import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author mhajas
 */
public class KeycloakContainerFeaturesController {

    @Inject
    private Instance<TestContext> testContextInstance;
    @Inject
    private Instance<SuiteContext> suiteContextInstance;
    @Inject
    private Event<StartContainer> startContainerEvent;
    @Inject
    private Event<StopContainer> stopContainerEvent;

    public enum FeatureAction {
        ENABLE(KeycloakTestingClient::enableFeature),
        DISABLE(KeycloakTestingClient::disableFeature);

        private BiConsumer<KeycloakTestingClient, Profile.Feature> featureConsumer;

        FeatureAction(BiConsumer<KeycloakTestingClient, Profile.Feature> featureConsumer) {
            this.featureConsumer = featureConsumer;
        }

        public void accept(KeycloakTestingClient testingClient, Profile.Feature feature) {
            featureConsumer.accept(testingClient, feature);
        }
    }

    public enum State {
        BEFORE,
        AFTER
    }

    private class UpdateFeature {
        private Profile.Feature feature;
        private boolean skipRestart;
        private FeatureAction action;
        private final AnnotatedElement annotatedElement;

        public UpdateFeature(Profile.Feature feature, boolean skipRestart, FeatureAction action, AnnotatedElement annotatedElement) {
            this.feature = feature;
            this.skipRestart = skipRestart;
            this.action = action;
            this.annotatedElement = annotatedElement;
        }

        private void assertPerformed() {
            assertThat("An annotation requested to " + action.name() +
                            " feature " + feature.getKey() + ", however after performing this operation " +
                            "the feature is not in desired state" ,
                    ProfileAssume.isFeatureEnabled(feature),
                    is(action == FeatureAction.ENABLE));
        }

        public void performAction() {
            if ((action == FeatureAction.ENABLE && !ProfileAssume.isFeatureEnabled(feature))
                    || (action == FeatureAction.DISABLE && ProfileAssume.isFeatureEnabled(feature))) {
                action.accept(testContextInstance.get().getTestingClient(), feature);
                SetDefaultProvider setDefaultProvider = annotatedElement.getAnnotation(SetDefaultProvider.class);
                if (setDefaultProvider != null) {
                    try {
                        if (action == FeatureAction.ENABLE) {
                            SpiProvidersSwitchingUtils.addProviderDefaultValue(suiteContextInstance.get(), setDefaultProvider);
                        } else {
                            SpiProvidersSwitchingUtils.removeProvider(suiteContextInstance.get(), setDefaultProvider);
                        }
                    } catch (Exception cause) {
                        throw new RuntimeException("Failed to (un)set default provider", cause);
                    }
                }
            }
        }

        public Profile.Feature getFeature() {
            return feature;
        }

        public boolean isSkipRestart() {
            return skipRestart;
        }

        public FeatureAction getAction() {
            return action;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UpdateFeature that = (UpdateFeature) o;
            return feature == that.feature;
        }

        @Override
        public int hashCode() {
            return Objects.hash(feature);
        }
    }

    public void restartAuthServer() {
        stopContainerEvent.fire(new StopContainer(suiteContextInstance.get().getAuthServerInfo().getArquillianContainer()));
        startContainerEvent.fire(new StartContainer(suiteContextInstance.get().getAuthServerInfo().getArquillianContainer()));
    }

    private void updateFeatures(Set<UpdateFeature> updateFeatures) throws Exception {
        updateFeatures = updateFeatures.stream()
                .collect(Collectors.toSet());

        updateFeatures.forEach(UpdateFeature::performAction);

        if (updateFeatures.stream().anyMatch(updateFeature -> !updateFeature.skipRestart)) {
            restartAuthServer();
            testContextInstance.get().reconnectAdminClient();
        }

        updateFeatures.forEach(UpdateFeature::assertPerformed);
    }

    private void checkAnnotatedElementForFeatureAnnotations(AnnotatedElement annotatedElement, State state) throws Exception {
        Set<UpdateFeature> updateFeatureSet = new HashSet<>();

        updateFeatureSet.addAll(getUpdateFeaturesSet(annotatedElement, state));

        // we can't rely on @Inherited annotations as it stops "searching" when it finds the first occurrence of given
        // annotation, i.e. annotation from the most specific test class
        if (annotatedElement instanceof Class) {
            Class<?> clazz = ((Class<?>) annotatedElement).getSuperclass();
            while (clazz != null) {
                // duplicates (i.e. annotations from less specific test classes) won't be added
                updateFeatureSet.addAll(getUpdateFeaturesSet(clazz, state));
                clazz = clazz.getSuperclass();
            }
        }

        if (!updateFeatureSet.isEmpty()) {
            updateFeatures(updateFeatureSet);
        }
    }

    private Set<UpdateFeature> getUpdateFeaturesSet(AnnotatedElement annotatedElement, State state) {
        Set<UpdateFeature> ret = new HashSet<>();

        ret.addAll(Arrays.stream(annotatedElement.getAnnotationsByType(EnableFeature.class))
                .map(annotation -> new UpdateFeature(annotation.value(), annotation.skipRestart(),
                        state == State.BEFORE ? FeatureAction.ENABLE : FeatureAction.DISABLE, annotatedElement))
                .collect(Collectors.toSet()));

        ret.addAll(Arrays.stream(annotatedElement.getAnnotationsByType(DisableFeature.class))
                .map(annotation -> new UpdateFeature(annotation.value(), annotation.skipRestart(),
                        state == State.BEFORE ? FeatureAction.DISABLE : FeatureAction.ENABLE, annotatedElement))
                .collect(Collectors.toSet()));

        return ret;
    }

    private boolean isEnableFeature(AnnotatedElement annotatedElement) {
        return (annotatedElement.isAnnotationPresent(EnableFeatures.class) || annotatedElement.isAnnotationPresent(EnableFeature.class));
    }

    private boolean isDisableFeature(AnnotatedElement annotatedElement) {
        return (annotatedElement.isAnnotationPresent(DisableFeatures.class) || annotatedElement.isAnnotationPresent(DisableFeature.class));
    }

    private boolean shouldExecuteAsLast(AnnotatedElement annotatedElement) {
        if (isEnableFeature(annotatedElement)) {
            return Arrays.stream(annotatedElement.getAnnotationsByType(EnableFeature.class))
                    .anyMatch(EnableFeature::executeAsLast);
        }

        if (isDisableFeature(annotatedElement)) {
            return Arrays.stream(annotatedElement.getAnnotationsByType(DisableFeature.class))
                    .anyMatch(DisableFeature::executeAsLast);
        }

        return false;
    }
    
    public void handleEnableFeaturesAnnotationBeforeClass(@Observes(precedence = 1) BeforeClass event) throws Exception {
        checkAnnotatedElementForFeatureAnnotations(event.getTestClass().getJavaClass(), State.BEFORE);
    }

    public void handleEnableFeaturesAnnotationBeforeTest(@Observes(precedence = 1) Before event) throws Exception {
        if (!shouldExecuteAsLast(event.getTestMethod())) {
            checkAnnotatedElementForFeatureAnnotations(event.getTestMethod(), State.BEFORE);
        }
    }

    // KEYCLOAK-13572 Precedence is too low in order to ensure the feature change will be executed as last.
    // If some fail occurs in @Before method, the feature doesn't change its state.
    public void handleChangeStateFeaturePriorityBeforeTest(@Observes(precedence = -100) Before event) throws Exception {
        if (shouldExecuteAsLast(event.getTestMethod())) {
            checkAnnotatedElementForFeatureAnnotations(event.getTestMethod(), State.BEFORE);
        }
    }

    public void handleEnableFeaturesAnnotationAfterTest(@Observes(precedence = 2) After event) throws Exception {
        checkAnnotatedElementForFeatureAnnotations(event.getTestMethod(), State.AFTER);
    }

    public void handleEnableFeaturesAnnotationAfterClass(@Observes(precedence = 2) AfterClass event) throws Exception {
        checkAnnotatedElementForFeatureAnnotations(event.getTestClass().getJavaClass(), State.AFTER);
    }

}
