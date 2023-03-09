package org.keycloak.services.error;

import com.fasterxml.jackson.core.JsonParseException;
import org.jboss.logging.Logger;
import org.jboss.resteasy.spi.Failure;
import org.keycloak.Config;
import org.keycloak.forms.login.freemarker.model.UrlBean;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakTransaction;
import org.keycloak.models.ModelDuplicateException;
import org.keycloak.models.RealmModel;
import org.keycloak.representations.idm.OAuth2ErrorRepresentation;
import org.keycloak.services.managers.RealmManager;
import org.keycloak.services.messages.Messages;
import org.keycloak.theme.Theme;
import org.keycloak.theme.beans.LocaleBean;
import org.keycloak.theme.beans.MessageBean;
import org.keycloak.theme.beans.MessageFormatterMethod;
import org.keycloak.theme.beans.MessageType;
import org.keycloak.theme.freemarker.FreeMarkerProvider;
import org.keycloak.utils.MediaType;
import org.keycloak.utils.MediaTypeMatcher;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KeycloakErrorHandler implements ExceptionMapper<Throwable> {

    private static final Logger logger = Logger.getLogger(KeycloakErrorHandler.class);

    private static final Pattern realmNamePattern = Pattern.compile(".*/realms/([^/]+).*");

    public static final String UNCAUGHT_SERVER_ERROR_TEXT = "Uncaught server error";
    public static final String ERROR_RESPONSE_TEXT = "Error response {0}";

    @Context
    KeycloakSession session;

    @Override
    public Response toResponse(Throwable throwable) {
        return getResponse(session, throwable);
    }

    public static Response getResponse(KeycloakSession session, Throwable throwable) {
        KeycloakTransaction tx = session.getTransactionManager();
        tx.setRollbackOnly();

        int statusCode = getStatusCode(throwable);

        if (statusCode >= 500 && statusCode <= 599) {
            logger.error(UNCAUGHT_SERVER_ERROR_TEXT, throwable);
        } else {
            logger.debugv(throwable, ERROR_RESPONSE_TEXT, statusCode);
        }

        HttpHeaders headers = session.getContext().getRequestHeaders();

        if (!MediaTypeMatcher.isHtmlRequest(headers)) {
            OAuth2ErrorRepresentation error = new OAuth2ErrorRepresentation();

            error.setError(getErrorCode(throwable));
            
            return Response.status(statusCode)
                    .header(HttpHeaders.CONTENT_TYPE, javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE.toString())
                    .entity(error)
                    .build();
        }

        try {
            RealmModel realm = resolveRealm(session);

            Theme theme = session.theme().getTheme(Theme.Type.LOGIN);

            Locale locale = session.getContext().resolveLocale(null);

            FreeMarkerProvider freeMarker = session.getProvider(FreeMarkerProvider.class);
            Map<String, Object> attributes = initAttributes(session, realm, theme, locale, statusCode);

            String templateName = "error.ftl";

            String content = freeMarker.processTemplate(attributes, templateName, theme);
            return Response.status(statusCode).type(MediaType.TEXT_HTML_UTF_8_TYPE).entity(content).build();
        } catch (Throwable t) {
            logger.error("Failed to create error page", t);
            return Response.serverError().build();
        }
    }

    private static int getStatusCode(Throwable throwable) {
        int status = Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
        if (throwable instanceof WebApplicationException) {
            WebApplicationException ex = (WebApplicationException) throwable;
            status = ex.getResponse().getStatus();
        }
        if (throwable instanceof Failure) {
            Failure f = (Failure) throwable;
            status = f.getErrorCode();
        }
        if (throwable instanceof JsonParseException) {
            status = Response.Status.BAD_REQUEST.getStatusCode();
        }
        
        if (throwable instanceof ModelDuplicateException) {
            status = Response.Status.CONFLICT.getStatusCode();
        }
        
        return status;
    }

    private static String getErrorCode(Throwable throwable) {
        if (throwable instanceof WebApplicationException && throwable.getMessage() != null) {
            return throwable.getMessage();
        }

        return "unknown_error";
    }

    private static RealmModel resolveRealm(KeycloakSession session) {
        String path = session.getContext().getUri().getPath();
        Matcher m = realmNamePattern.matcher(path);
        String realmName;
        if(m.matches()) {
            realmName = m.group(1);
        } else {
            realmName = Config.getAdminRealm();
        }

        RealmManager realmManager = new RealmManager(session);
        RealmModel realm = realmManager.getRealmByName(realmName);
        if (realm == null) {
            realm = realmManager.getRealmByName(Config.getAdminRealm());
        }

        session.getContext().setRealm(realm);

        return realm;
    }

    private static Map<String, Object> initAttributes(KeycloakSession session, RealmModel realm, Theme theme, Locale locale, int statusCode) throws IOException {
        Map<String, Object> attributes = new HashMap<>();
        Properties messagesBundle = theme.getMessages(locale);

        attributes.put("statusCode", statusCode);

        attributes.put("realm", realm);
        attributes.put("url", new UrlBean(realm, theme, session.getContext().getUri().getBaseUri(), null));
        attributes.put("locale", new LocaleBean(realm, locale, session.getContext().getUri().getRequestUriBuilder(), messagesBundle));


        String errorKey = statusCode == 404 ? Messages.PAGE_NOT_FOUND : Messages.INTERNAL_SERVER_ERROR;
        String errorMessage = messagesBundle.getProperty(errorKey);

        attributes.put("message", new MessageBean(errorMessage, MessageType.ERROR));

        try {
            attributes.put("msg", new MessageFormatterMethod(locale, theme.getMessages(locale)));
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            attributes.put("properties", theme.getProperties());
        } catch (IOException e) {
            e.printStackTrace();
        }

        return attributes;
    }

}
