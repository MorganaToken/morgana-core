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

package org.keycloak.saml;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.keycloak.dom.saml.v2.metadata.EndpointType;
import org.keycloak.dom.saml.v2.metadata.EntityDescriptorType;
import org.keycloak.dom.saml.v2.metadata.IndexedEndpointType;
import org.keycloak.dom.saml.v2.metadata.KeyDescriptorType;
import org.keycloak.dom.saml.v2.metadata.KeyTypes;
import org.keycloak.dom.saml.v2.metadata.SPSSODescriptorType;
import org.keycloak.dom.xmlsec.w3.xmlenc.EncryptionMethodType;
import org.keycloak.saml.processing.core.saml.v2.common.IDGenerator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.keycloak.saml.common.constants.JBossSAMLURIConstants.XMLDSIG_NSURI;
import static org.keycloak.saml.common.constants.JBossSAMLURIConstants.PROTOCOL_NSURI;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class SPMetadataDescriptor {

    public static EntityDescriptorType buildSPDescriptor(URI loginBinding, URI logoutBinding, URI assertionEndpoint, URI logoutEndpoint,
                                                         boolean wantAuthnRequestsSigned, boolean wantAssertionsSigned, boolean wantAssertionsEncrypted,
                                                         String entityId, String nameIDPolicyFormat, List<KeyDescriptorType> signingCerts, List<KeyDescriptorType> encryptionCerts)
    {
        EntityDescriptorType entityDescriptor = new EntityDescriptorType(entityId);
        entityDescriptor.setID(IDGenerator.create("ID_"));

        SPSSODescriptorType spSSODescriptor = new SPSSODescriptorType(Arrays.asList(PROTOCOL_NSURI.get()));
        spSSODescriptor.setAuthnRequestsSigned(wantAuthnRequestsSigned);
        spSSODescriptor.setWantAssertionsSigned(wantAssertionsSigned);
        spSSODescriptor.addNameIDFormat(nameIDPolicyFormat);
        spSSODescriptor.addSingleLogoutService(new EndpointType(logoutBinding, logoutEndpoint));

        if (wantAuthnRequestsSigned && signingCerts != null) {
            for (KeyDescriptorType key: signingCerts) {
                spSSODescriptor.addKeyDescriptor(key);
            }
        }

        if (wantAssertionsEncrypted && encryptionCerts != null) {
            for (KeyDescriptorType key: encryptionCerts) {
                spSSODescriptor.addKeyDescriptor(key);
            }
        }

        IndexedEndpointType assertionConsumerEndpoint = new IndexedEndpointType(loginBinding, assertionEndpoint);
        assertionConsumerEndpoint.setIsDefault(true);
        assertionConsumerEndpoint.setIndex(1);
        spSSODescriptor.addAssertionConsumerService(assertionConsumerEndpoint);

        entityDescriptor.addChoiceType(new EntityDescriptorType.EDTChoiceType(Arrays.asList(new EntityDescriptorType.EDTDescriptorChoiceType(spSSODescriptor))));

        return entityDescriptor;
    }

    public static KeyDescriptorType buildKeyDescriptorType(Element keyInfo, KeyTypes use, String algorithm) {
        KeyDescriptorType keyDescriptor = new KeyDescriptorType();
        keyDescriptor.setUse(use);
        keyDescriptor.setKeyInfo(keyInfo);

        if (algorithm != null) {
            EncryptionMethodType encMethod = new EncryptionMethodType(algorithm);
            keyDescriptor.addEncryptionMethod(encMethod);
        }

        return keyDescriptor;
    }

    public static Element buildKeyInfoElement(String keyName, String pemEncodedCertificate)
        throws javax.xml.parsers.ParserConfigurationException
    {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.newDocument();

        Element keyInfo = doc.createElementNS(XMLDSIG_NSURI.get(), "ds:KeyInfo");

        if (keyName != null) {
            Element keyNameElement = doc.createElementNS(XMLDSIG_NSURI.get(), "ds:KeyName");
            keyNameElement.setTextContent(keyName);
            keyInfo.appendChild(keyNameElement);
        }

        Element x509Data = doc.createElementNS(XMLDSIG_NSURI.get(), "ds:X509Data");

        Element x509Certificate = doc.createElementNS(XMLDSIG_NSURI.get(), "ds:X509Certificate");
        x509Certificate.setTextContent(pemEncodedCertificate);
      
        x509Data.appendChild(x509Certificate);

        keyInfo.appendChild(x509Data);

        return keyInfo;
    }
}
