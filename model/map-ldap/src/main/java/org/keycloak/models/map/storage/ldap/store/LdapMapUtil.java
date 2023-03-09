/*
 * Copyright 2022. Red Hat, Inc. and/or its affiliates
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

package org.keycloak.models.map.storage.ldap.store;

import org.jboss.logging.Logger;
import org.keycloak.common.util.UriUtils;
import org.keycloak.models.Constants;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ModelException;
import org.keycloak.models.map.storage.ldap.config.LdapMapConfig;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * <p>Utility class for working with LDAP.</p>
 *
 * @author Pedro Igor
 */
public class LdapMapUtil {

    private static final Logger logger = Logger.getLogger(LdapMapUtil.class);

    /**
     * <p>Formats the given date.</p>
     *
     * @param date The Date to format.
     *
     * @return A String representing the formatted date.
     */
    public static String formatDate(Date date) {
        if (date == null) {
            throw new IllegalArgumentException("You must provide a date.");
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss'.0Z'");

        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        return dateFormat.format(date);
    }

    /**
     * <p>
     * Parses dates/time stamps stored in LDAP. Some possible values:
     * </p>
     * <ul>
     *     <li>20020228150820</li>
     *     <li>20030228150820Z</li>
     *     <li>20050228150820.12</li>
     *     <li>20060711011740.0Z</li>
     * </ul>
     *
     * @param date The date string to parse from.
     *
     * @return the Date.
     */
    public static Date parseDate(String date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");

        try {
            if (date.endsWith("Z")) {
                dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            } else {
                dateFormat.setTimeZone(TimeZone.getDefault());
            }

            return dateFormat.parse(date);
        } catch (Exception e) {
            throw new ModelException("Error converting ldap date.", e);
        }
    }



    /**
     * <p>Creates a byte-based {@link String} representation of a raw byte array representing the value of the
     * <code>objectGUID</code> attribute retrieved from Active Directory.</p>
     *
     * <p>The returned string is useful to perform queries on AD based on the <code>objectGUID</code> value. Eg.:</p>
     *
     * <p>
     * String filter = "(&(objectClass=*)(objectGUID" + EQUAL + convertObjectGUIDToByteString(objectGUID) + "))";
     * </p>
     *
     * @param objectGUID A raw byte array representing the value of the <code>objectGUID</code> attribute retrieved from
     * Active Directory.
     *
     * @return A byte-based String representation in the form of \[0]\[1]\[2]\[3]\[4]\[5]\[6]\[7]\[8]\[9]\[10]\[11]\[12]\[13]\[14]\[15]
     */
    public static String convertObjectGUIDToByteString(byte[] objectGUID) {
        StringBuilder result = new StringBuilder();

        for (byte b : objectGUID) {
            String transformed = prefixZeros((int) b & 0xFF);
            result.append("\\");
            result.append(transformed);
        }

        return result.toString();
    }

    /**
     * see http://support.novell.com/docs/Tids/Solutions/10096551.html
     *
     * @param guid A GUID in the form of a dashed String as the result of (@see LDAPUtil#convertToDashedString)
     *
     * @return A String representation in the form of \[0][1]\[2][3]\[4][5]\[6][7]\[8][9]\[10][11]\[12][13]\[14][15]
     */
    public static String convertGUIDToEdirectoryHexString(String guid) {
        String withoutDash = guid.replace("-", "");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < withoutDash.length(); i++) {
            result.append("\\");
            result.append(withoutDash.charAt(i));
            result.append(withoutDash.charAt(++i));
        }

        return result.toString().toUpperCase();
    }

    /**
     * <p>Encode a string representing the display value of the <code>objectGUID</code> attribute retrieved from Active
     * Directory.</p>
     *
     * @param displayString A string representing the decoded value in the form of [3][2][1][0]-[5][4]-[7][6]-[8][9]-[10][11][12][13][14][15].
     *
     * @return A raw byte array representing the value of the <code>objectGUID</code> attribute retrieved from
     * Active Directory.
     */
    public static byte[] encodeObjectGUID(String displayString) {
        byte [] objectGUID = new byte[16];
        // [3][2][1][0]
        objectGUID[0] = (byte) ((Character.digit(displayString.charAt(6), 16) << 4)
                + Character.digit(displayString.charAt(7), 16));
        objectGUID[1] = (byte) ((Character.digit(displayString.charAt(4), 16) << 4)
                + Character.digit(displayString.charAt(5), 16));
        objectGUID[2] = (byte) ((Character.digit(displayString.charAt(2), 16) << 4)
                + Character.digit(displayString.charAt(3), 16));
        objectGUID[3] = (byte) ((Character.digit(displayString.charAt(0), 16) << 4)
                + Character.digit(displayString.charAt(1), 16));
        // [5][4]
        objectGUID[4] = (byte) ((Character.digit(displayString.charAt(11), 16) << 4)
                + Character.digit(displayString.charAt(12), 16));
        objectGUID[5] = (byte) ((Character.digit(displayString.charAt(9), 16) << 4)
                + Character.digit(displayString.charAt(10), 16));
        // [7][6]
        objectGUID[6] = (byte) ((Character.digit(displayString.charAt(16), 16) << 4)
                + Character.digit(displayString.charAt(17), 16));
        objectGUID[7] = (byte) ((Character.digit(displayString.charAt(14), 16) << 4)
                + Character.digit(displayString.charAt(15), 16));
        // [8][9]
        objectGUID[8] = (byte) ((Character.digit(displayString.charAt(19), 16) << 4)
                + Character.digit(displayString.charAt(20), 16));
        objectGUID[9] = (byte) ((Character.digit(displayString.charAt(21), 16) << 4)
                + Character.digit(displayString.charAt(22), 16));
        // [10][11][12][13][14][15]
        objectGUID[10] = (byte) ((Character.digit(displayString.charAt(24), 16) << 4)
                + Character.digit(displayString.charAt(25), 16));
        objectGUID[11] = (byte) ((Character.digit(displayString.charAt(26), 16) << 4)
                + Character.digit(displayString.charAt(27), 16));
        objectGUID[12] = (byte) ((Character.digit(displayString.charAt(28), 16) << 4)
                + Character.digit(displayString.charAt(29), 16));
        objectGUID[13] = (byte) ((Character.digit(displayString.charAt(30), 16) << 4)
                + Character.digit(displayString.charAt(31), 16));
        objectGUID[14] = (byte) ((Character.digit(displayString.charAt(32), 16) << 4)
                + Character.digit(displayString.charAt(33), 16));
        objectGUID[15] = (byte) ((Character.digit(displayString.charAt(34), 16) << 4)
                + Character.digit(displayString.charAt(35), 16));
        return objectGUID;
    }

    /**
     * <p>Decode a raw byte array representing the value of the <code>objectGUID</code> attribute retrieved from Active
     * Directory.</p>
     *
     * <p>The returned string is useful to directly bind an entry. Eg.:</p>
     *
     * <p>
     * String bindingString = decodeObjectGUID(objectGUID);
     * <br/>
     * Attributes attributes = ctx.getAttributes(bindingString);
     * </p>
     *
     * @param objectGUID A raw byte array representing the value of the <code>objectGUID</code> attribute retrieved from
     * Active Directory.
     *
     * @return A string representing the decoded value in the form of [3][2][1][0]-[5][4]-[7][6]-[8][9]-[10][11][12][13][14][15].
     */
    public static String decodeObjectGUID(byte[] objectGUID) {
        return convertToDashedString(objectGUID);
    }

    /**
     * <p>Decode a raw byte array representing the value of the <code>guid</code> attribute retrieved from Novell
     * eDirectory.</p>
     *
     * @param guid A raw byte array representing the value of the <code>guid</code> attribute retrieved from
     * Novell eDirectory.
     *
     * @return A string representing the decoded value in the form of [0][1][2][3]-[4][5]-[6][7]-[8][9]-[10][11][12][13][14][15].
     */
    public static String decodeGuid(byte[] guid) {
        byte[] withBigEndian = new byte[] { guid[3], guid[2], guid[1], guid[0],
            guid[5], guid[4],
            guid[7], guid[6],
            guid[8], guid[9], guid[10], guid[11], guid[12], guid[13], guid[14], guid[15]
        };
        return convertToDashedString(withBigEndian);
    }

    private static String convertToDashedString(byte[] objectGUID) {
        return prefixZeros((int) objectGUID[3] & 0xFF) +
                prefixZeros((int) objectGUID[2] & 0xFF) +
                prefixZeros((int) objectGUID[1] & 0xFF) +
                prefixZeros((int) objectGUID[0] & 0xFF) +
                "-" +
                prefixZeros((int) objectGUID[5] & 0xFF) +
                prefixZeros((int) objectGUID[4] & 0xFF) +
                "-" +
                prefixZeros((int) objectGUID[7] & 0xFF) +
                prefixZeros((int) objectGUID[6] & 0xFF) +
                "-" +
                prefixZeros((int) objectGUID[8] & 0xFF) +
                prefixZeros((int) objectGUID[9] & 0xFF) +
                "-" +
                prefixZeros((int) objectGUID[10] & 0xFF) +
                prefixZeros((int) objectGUID[11] & 0xFF) +
                prefixZeros((int) objectGUID[12] & 0xFF) +
                prefixZeros((int) objectGUID[13] & 0xFF) +
                prefixZeros((int) objectGUID[14] & 0xFF) +
                prefixZeros((int) objectGUID[15] & 0xFF);
    }

    private static String prefixZeros(int value) {
        if (value <= 0xF) {
            return "0" + Integer.toHexString(value);
        } else {
            return Integer.toHexString(value);
        }
    }

    public static void setLDAPHostnameToKeycloakSession(KeycloakSession session, LdapMapConfig ldapConfig) {
        String hostname = UriUtils.getHost(ldapConfig.getConnectionUrl());
        session.setAttribute(Constants.SSL_SERVER_HOST_ATTR, hostname);
        logger.tracef("Setting LDAP server hostname '%s' as KeycloakSession attribute", hostname);
    }


}
