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

import org.jboss.logging.Logger;
import org.jboss.resteasy.annotations.cache.NoCache;
import javax.ws.rs.NotFoundException;
import org.keycloak.common.ClientConnection;
import org.keycloak.component.ComponentFactory;
import org.keycloak.component.ComponentModel;
import org.keycloak.component.ComponentValidationException;
import org.keycloak.component.SubComponentFactory;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.models.utils.ModelToRepresentation;
import org.keycloak.models.utils.RepresentationToModel;
import org.keycloak.models.utils.StripSecretsUtils;
import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.representations.idm.ComponentRepresentation;
import org.keycloak.representations.idm.ComponentTypeRepresentation;
import org.keycloak.representations.idm.ConfigPropertyRepresentation;
import org.keycloak.services.ErrorResponse;
import org.keycloak.services.resources.admin.permissions.AdminPermissionEvaluator;
import org.keycloak.utils.LockObjectsForModification;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * @resource Component
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class ComponentResource {
    protected static final Logger logger = Logger.getLogger(ComponentResource.class);

    protected final RealmModel realm;

    private final AdminPermissionEvaluator auth;

    private final AdminEventBuilder adminEvent;

    protected final ClientConnection clientConnection;

    protected final KeycloakSession session;

    protected final HttpHeaders headers;

    public ComponentResource(KeycloakSession session, AdminPermissionEvaluator auth, AdminEventBuilder adminEvent) {
        this.session = session;
        this.auth = auth;
        this.realm = session.getContext().getRealm();
        this.adminEvent = adminEvent.resource(ResourceType.COMPONENT);
        this.clientConnection = session.getContext().getConnection();
        this.headers = session.getContext().getRequestHeaders();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @NoCache
    public Stream<ComponentRepresentation> getComponents(@QueryParam("parent") String parent,
                                                       @QueryParam("type") String type,
                                                       @QueryParam("name") String name) {
        auth.realm().requireViewRealm();
        Stream<ComponentModel> components;
        if (parent == null && type == null) {
            components = realm.getComponentsStream();

        } else if (type == null) {
            components = realm.getComponentsStream(parent);
        } else if (parent == null) {
            components = realm.getComponentsStream(realm.getId(), type);
        } else {
            components = realm.getComponentsStream(parent, type);
        }

        return components
                .filter(component -> Objects.isNull(name) || Objects.equals(component.getName(), name))
                .map(component -> {
                    try {
                        return ModelToRepresentation.toRepresentation(session, component, false);
                    } catch (Exception e) {
                        logger.error("Failed to get component list for component model" + component.getName() + "of realm " + realm.getName());
                        return ModelToRepresentation.toRepresentationWithoutConfig(component);
                    }
                });
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(ComponentRepresentation rep) {
        auth.realm().requireManageRealm();
        return KeycloakModelUtils.runJobInRetriableTransaction(session.getKeycloakSessionFactory(), kcSession -> {
            RealmModel realmModel = LockObjectsForModification.lockRealmsForModification(kcSession, () -> kcSession.realms().getRealm(realm.getId()));
            try {
                ComponentModel model = RepresentationToModel.toModel(kcSession, rep);
                if (model.getParentId() == null) model.setParentId(realmModel.getId());

                model = realmModel.addComponentModel(model);

                adminEvent.operation(OperationType.CREATE).resourcePath(kcSession.getContext().getUri(), model.getId()).representation(StripSecretsUtils.strip(kcSession, rep)).success();
                return Response.created(kcSession.getContext().getUri().getAbsolutePathBuilder().path(model.getId()).build()).build();
            } catch (ComponentValidationException e) {
                return localizedErrorResponse(e);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(e);
            }
        }, 10, 100);
    }

    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @NoCache
    public ComponentRepresentation getComponent(@PathParam("id") String id) {
        auth.realm().requireViewRealm();
        ComponentModel model = realm.getComponent(id);
        if (model == null) {
            throw new NotFoundException("Could not find component");
        }
        ComponentRepresentation rep = ModelToRepresentation.toRepresentation(session, model, false);
        return rep;
    }

    @PUT
    @Path("{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateComponent(@PathParam("id") String id, ComponentRepresentation rep) {
        auth.realm().requireManageRealm();
        return KeycloakModelUtils.runJobInRetriableTransaction(session.getKeycloakSessionFactory(), kcSession -> {
            RealmModel realmModel = LockObjectsForModification.lockRealmsForModification(kcSession, () -> kcSession.realms().getRealm(realm.getId()));
            try {
                ComponentModel model = realmModel.getComponent(id);
                if (model == null) {
                    throw new NotFoundException("Could not find component");
                }
                RepresentationToModel.updateComponent(kcSession, rep, model, false);
                adminEvent.operation(OperationType.UPDATE).resourcePath(kcSession.getContext().getUri()).representation(StripSecretsUtils.strip(kcSession, rep)).success();
                realmModel.updateComponent(model);
                return Response.noContent().build();
            } catch (ComponentValidationException e) {
                return localizedErrorResponse(e);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException();
            }
        }, 10, 100);
    }
    @DELETE
    @Path("{id}")
    public void removeComponent(@PathParam("id") String id) {
        auth.realm().requireManageRealm();
        KeycloakModelUtils.runJobInRetriableTransaction(session.getKeycloakSessionFactory(), kcSession -> {
            RealmModel realmModel = LockObjectsForModification.lockRealmsForModification(kcSession, () -> kcSession.realms().getRealm(realm.getId()));

            ComponentModel model = realmModel.getComponent(id);
            if (model == null) {
                throw new NotFoundException("Could not find component");
            }
            adminEvent.operation(OperationType.DELETE).resourcePath(kcSession.getContext().getUri()).success();
            realmModel.removeComponent(model);
            return null;
        }, 10 , 100);
    }

    private Response localizedErrorResponse(ComponentValidationException cve) {
        Properties messages = AdminRoot.getMessages(session, realm, auth.adminAuth().getToken().getLocale(), "admin-messages", "messages");

        Object[] localizedParameters = cve.getParameters()==null ? null : Arrays.asList(cve.getParameters()).stream().map((Object parameter) -> {

            if (parameter instanceof String) {
                String paramStr = (String) parameter;
                return messages.getProperty(paramStr, paramStr);
            } else {
                return parameter;
            }

        }).toArray();

        String message = MessageFormat.format(messages.getProperty(cve.getMessage(), cve.getMessage()), localizedParameters);
        return ErrorResponse.error(message, Response.Status.BAD_REQUEST);
    }

    /**
     * List of subcomponent types that are available to configure for a particular parent component.
     *
     * @param parentId
     * @param subtype
     * @return
     */
    @GET
    @Path("{id}/sub-component-types")
    @Produces(MediaType.APPLICATION_JSON)
    @NoCache
    public Stream<ComponentTypeRepresentation> getSubcomponentConfig(@PathParam("id") String parentId, @QueryParam("type") String subtype) {
        auth.realm().requireViewRealm();
        ComponentModel parent = realm.getComponent(parentId);
        if (parent == null) {
            throw new NotFoundException("Could not find parent component");
        }
        if (subtype == null) {
            throw new BadRequestException("must specify a subtype");
        }
        Class<? extends Provider> providerClass;
        try {
            providerClass = (Class<? extends Provider>)Class.forName(subtype);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        return session.getKeycloakSessionFactory().getProviderFactoriesStream(providerClass)
            .filter(ComponentFactory.class::isInstance)
            .map(factory -> toComponentTypeRepresentation(factory, parent));
    }

    private ComponentTypeRepresentation toComponentTypeRepresentation(ProviderFactory factory, ComponentModel parent) {
        ComponentTypeRepresentation rep = new ComponentTypeRepresentation();
        rep.setId(factory.getId());

        ComponentFactory componentFactory = (ComponentFactory)factory;

        rep.setHelpText(componentFactory.getHelpText());
        List<ProviderConfigProperty> props;
        Map<String, Object> metadata;
        if (factory instanceof SubComponentFactory) {
            props = ((SubComponentFactory)factory).getConfigProperties(realm, parent);
            metadata = ((SubComponentFactory)factory).getTypeMetadata(realm, parent);

        } else {
            props = componentFactory.getConfigProperties();
            metadata = componentFactory.getTypeMetadata();
        }

        List<ConfigPropertyRepresentation> propReps =  ModelToRepresentation.toRepresentation(props);
        rep.setProperties(propReps);
        rep.setMetadata(metadata);
        return rep;
    }
}
