/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2011, 2012, 2013, 2014, 2016 Synacor, Inc.
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software Foundation,
 * version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 * ***** END LICENSE BLOCK *****
 */

package com.zimbra.soap.mail.type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.zimbra.common.gql.GqlConstants;
import com.zimbra.common.soap.MailConstants;

import io.leangen.graphql.annotations.GraphQLIgnore;
import io.leangen.graphql.annotations.GraphQLInputField;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.annotations.types.GraphQLType;

@XmlAccessorType(XmlAccessType.NONE)
@GraphQLType(name=GqlConstants.CLASS_ATTACHMENTS_INFO, description="Attachments Information")
public class AttachmentsInfo {

    /**
     * @zm-api-field-tag attach-upload-id
     * @zm-api-field-description Attachment upload ID
     */
    @XmlAttribute(name=MailConstants.A_ATTACHMENT_ID /* aid */, required=false)
    private String attachmentId;

    /**
     * @zm-api-field-tag attach-mimepart
     * @zm-api-field-description MimePart Attachment details
     */
    @XmlElement(name=MailConstants.E_MIMEPART /* mp */, type=MimePartAttachSpec.class, required=false)
    private final List<MimePartAttachSpec> mimeParts = new ArrayList<>();
    
    /**
     * @zm-api-field-tag attach-msg
     * @zm-api-field-description Message Attachment details
     */
    @XmlElement(name=MailConstants.E_MSG /* m */, type=MsgAttachSpec.class, required=false)
    private final List<MsgAttachSpec> msgs = new ArrayList<>();
    
    /**
     * @zm-api-field-tag attach-contact
     * @zm-api-field-description Contact Attachment details
     */
    @XmlElement(name=MailConstants.E_CONTACT /* cn */, type=ContactAttachSpec.class, required=false)
    private final List<ContactAttachSpec> contacts = new ArrayList<>();
    
    /**
     * @zm-api-field-tag attach-doc
     * @zm-api-field-description Doc Attachment details
     */
    @XmlElement(name=MailConstants.E_DOC /* doc */, type=DocAttachSpec.class, required=false)
    private final List<DocAttachSpec> docs = new ArrayList<>();
    
    /**
     * @zm-api-field-description Other elements
     */
    @XmlAnyElement
    @GraphQLIgnore
    private final List<org.w3c.dom.Element> extraElements = Lists.newArrayList();

    public AttachmentsInfo() {
    }

    @GraphQLInputField(name=GqlConstants.ATTACHMENT_ID, description="Attachment upload ID")
    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    // --MIME_PARTS
    @GraphQLInputField(name = GqlConstants.MIME_PARTS, description = "MimeParts Attachment details")
    public void setMimeParts(Iterable<MimePartAttachSpec> mimeParts) {
        this.mimeParts.clear();
        if (mimeParts != null) {
            Iterables.addAll(this.mimeParts, mimeParts);
        }
    }

    @GraphQLIgnore
    public AttachmentsInfo addMimePart(MimePartAttachSpec mimeParts) {
        this.mimeParts.add(mimeParts);
        return this;
    }

    @GraphQLQuery(name = GqlConstants.MIME_PARTS, description = "MimeParts Attachment details")
    public List<MimePartAttachSpec> getMimeParts() {
        return Collections.unmodifiableList(mimeParts);
    }

    // --E_MSG
    @GraphQLInputField(name=GqlConstants.MSGS, description="Message Attachment details")
    public void setMsgs(Iterable<MsgAttachSpec> msgs) {
        this.msgs.clear();
        if (msgs != null) {
            Iterables.addAll(this.msgs, msgs);
        }
    }

    @GraphQLIgnore
    public AttachmentsInfo addMsg(MsgAttachSpec msgs) {
        this.msgs.add(msgs);
        return this;
    }

    @GraphQLQuery(name = GqlConstants.MSGS, description = "Message Attachment details")
    public List<MsgAttachSpec> getMsgs() {
        return Collections.unmodifiableList(msgs);
    }

    // --E_CONTACT
    @GraphQLInputField(name=GqlConstants.CONTACTS, description="Contact Attachment details")
    public void setContacts(Iterable<ContactAttachSpec> contacts) {
        this.contacts.clear();
        if (contacts != null) {
            Iterables.addAll(this.contacts, contacts);
        }
    }

    @GraphQLIgnore
    public AttachmentsInfo addContact(ContactAttachSpec contacts) {
        this.contacts.add(contacts);
        return this;
    }

    @GraphQLQuery(name = GqlConstants.CONTACTS, description = "Contact Attachment details")
    public List<ContactAttachSpec> getContacts() {
        return Collections.unmodifiableList(contacts);
    }

    // --E_DOC
    @GraphQLInputField(name=GqlConstants.DOCS, description="Doc Attachment details")
    public void setDocs(Iterable<DocAttachSpec> docs) {
        this.docs.clear();
        if (docs != null) {
            Iterables.addAll(this.docs, docs);
        }
    }

    @GraphQLIgnore
    public AttachmentsInfo addDoc(DocAttachSpec docs) {
        this.docs.add(docs);
        return this;
    }

    @GraphQLQuery(name = GqlConstants.DOCS, description = "Doc Attachment details")
    public List<DocAttachSpec> getDocs() {
        return Collections.unmodifiableList(docs);
    }

    @GraphQLIgnore
    public void setExtraElements(Iterable <org.w3c.dom.Element> extraElements) {
        this.extraElements.clear();
        if (extraElements != null) {
            Iterables.addAll(this.extraElements,extraElements);
        }
    }

    @GraphQLIgnore
    public AttachmentsInfo addExtraElement(org.w3c.dom.Element extraElement) {
        this.extraElements.add(extraElement);
        return this;
    }

    @GraphQLQuery(name=GqlConstants.ATTACHMENT_ID, description="Attachment upload ID")
    public String getAttachmentId() { return attachmentId; }

    @GraphQLQuery(name = GqlConstants.ATTACHMENTS, description = "Attachment details")
    public List<AttachSpec> getAttachments() {
        List<AttachSpec> attachments = new ArrayList<>();
        Iterables.addAll(attachments, this.mimeParts);
        Iterables.addAll(attachments, this.msgs);
        Iterables.addAll(attachments, this.contacts);
        Iterables.addAll(attachments, this.docs);
        return Collections.unmodifiableList(attachments);
    }

    
    @GraphQLIgnore
    public List<org.w3c.dom.Element> getExtraElements() {
        return Collections.unmodifiableList(extraElements);
    }

    @GraphQLIgnore
    public MoreObjects.ToStringHelper addToStringInfo(MoreObjects.ToStringHelper helper) {
        return helper
            .add("attachmentId", attachmentId)
            .add("mimeParts", mimeParts)
            .add("msgs", msgs)
            .add("contacts", contacts)
            .add("docs", docs)
            .add("mimeParts", mimeParts)
            .add("extraElements", extraElements);
    }

    @Override
    public String toString() {
        return addToStringInfo(MoreObjects.toStringHelper(this)).toString();
    }
}
