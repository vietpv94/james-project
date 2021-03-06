/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mailbox.store.mail.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.junit.After;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.xenei.junit.contract.Contract;
import org.xenei.junit.contract.ContractTest;
import org.xenei.junit.contract.IProducer;

import com.google.common.collect.ImmutableList;

@Contract(MapperProvider.class)
public class MessageWithAttachmentMapperTest<T extends MapperProvider> {

    private static final int LIMIT = 10;
    private static final int BODY_START = 16;
    public static final int UID_VALIDITY = 42;
    public static final String USER_FLAG = "userFlag";

    private IProducer<T> producer;
    private MapperProvider mapperProvider;
    private MessageMapper messageMapper;
    private AttachmentMapper attachmentMapper;

    private SimpleMailbox attachmentsMailbox;
    
    private SimpleMailboxMessage messageWithoutAttachment;
    private SimpleMailboxMessage message7With1Attachment;
    private SimpleMailboxMessage message8With2Attachments;

    @Rule
    public ExpectedException expected = ExpectedException.none();

    @Contract.Inject
    public final void setProducer(IProducer<T> producer) throws MailboxException {
        this.producer = producer;
        this.mapperProvider = producer.newInstance();
        this.mapperProvider.ensureMapperPrepared();

        Assume.assumeFalse(mapperProvider.getNotImplemented().contains(MapperProvider.Capabilities.MESSAGE));
        Assume.assumeFalse(mapperProvider.getNotImplemented().contains(MapperProvider.Capabilities.ATTACHMENT));

        this.messageMapper = mapperProvider.createMessageMapper();
        this.attachmentMapper = mapperProvider.createAttachmentMapper();

        attachmentsMailbox = createMailbox( new MailboxPath("#private", "benwa", "Attachments"));

        Attachment attachment = Attachment.builder()
                .attachmentId(AttachmentId.from("123"))
                .bytes("attachment".getBytes())
                .type("content")
                .build();
        attachmentMapper.storeAttachment(attachment);
        Attachment attachment2 = Attachment.builder()
                .attachmentId(AttachmentId.from("456"))
                .bytes("attachment2".getBytes())
                .type("content")
                .build();
        attachmentMapper.storeAttachment(attachment2);
        message7With1Attachment = createMessage(attachmentsMailbox, mapperProvider.generateMessageId(), "Subject: Test7 \n\nBody7\n.\n", BODY_START, new PropertyBuilder(), 
                ImmutableList.of(MessageAttachment.builder()
                        .attachment(attachment)
                        .cid(Cid.from("cid"))
                        .isInline(true)
                        .build()));
        message8With2Attachments = createMessage(attachmentsMailbox, mapperProvider.generateMessageId(), "Subject: Test8 \n\nBody8\n.\n", BODY_START, new PropertyBuilder(),
                ImmutableList.of(
                        MessageAttachment.builder()
                            .attachment(attachment)
                            .cid(Cid.from("cid"))
                            .isInline(true)
                            .build(),
                        MessageAttachment.builder()
                            .attachment(attachment2)
                            .cid(Cid.from("cid2"))
                            .isInline(false)
                            .build()));
        messageWithoutAttachment = createMessage(attachmentsMailbox, mapperProvider.generateMessageId(), "Subject: Test1 \n\nBody1\n.\n", BODY_START, new PropertyBuilder());

    }

    @After
    public void tearDown() throws MailboxException {
        producer.cleanUp();
    }

    @ContractTest
    public void messagesRetrievedUsingFetchTypeFullShouldHaveAttachmentsLoadedWhenOneAttachment() throws MailboxException, IOException{
        saveMessages();
        MessageMapper.FetchType fetchType = MessageMapper.FetchType.Full;
        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper.findInMailbox(attachmentsMailbox, MessageRange.one(message7With1Attachment.getUid()), fetchType, LIMIT);
        assertThat(retrievedMessageIterator.next().getAttachments()).isEqualTo(message7With1Attachment.getAttachments());
    }

    @ContractTest
    public void messagesRetrievedUsingFetchTypeFullShouldHaveAttachmentsLoadedWhenTwoAttachments() throws MailboxException, IOException{
        saveMessages();
        MessageMapper.FetchType fetchType = MessageMapper.FetchType.Full;
        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper.findInMailbox(attachmentsMailbox, MessageRange.one(message8With2Attachments.getUid()), fetchType, LIMIT);
        assertThat(retrievedMessageIterator.next().getAttachments()).isEqualTo(message8With2Attachments.getAttachments());
    }

    @ContractTest
    public void messagesRetrievedUsingFetchTypeBodyShouldHaveAttachmentsLoadedWhenOneAttachment() throws MailboxException, IOException{
        saveMessages();
        MessageMapper.FetchType fetchType = MessageMapper.FetchType.Body;
        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper.findInMailbox(attachmentsMailbox, MessageRange.one(message7With1Attachment.getUid()), fetchType, LIMIT);
        assertThat(retrievedMessageIterator.next().getAttachments()).isEqualTo(message7With1Attachment.getAttachments());
    }

    @ContractTest
    public void messagesRetrievedUsingFetchTypeHeadersShouldHaveAttachmentsEmptyWhenOneAttachment() throws MailboxException, IOException{
        Assume.assumeTrue(mapperProvider.supportPartialAttachmentFetch());
        saveMessages();
        MessageMapper.FetchType fetchType = MessageMapper.FetchType.Headers;
        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper.findInMailbox(attachmentsMailbox, MessageRange.one(message7With1Attachment.getUid()), fetchType, LIMIT);
        assertThat(retrievedMessageIterator.next().getAttachments()).isEmpty();
    }

    @ContractTest
    public void messagesRetrievedUsingFetchTypeMetadataShouldHaveAttachmentsEmptyWhenOneAttachment() throws MailboxException, IOException{
        Assume.assumeTrue(mapperProvider.supportPartialAttachmentFetch());
        saveMessages();
        MessageMapper.FetchType fetchType = MessageMapper.FetchType.Metadata;
        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper.findInMailbox(attachmentsMailbox, MessageRange.one(message7With1Attachment.getUid()), fetchType, LIMIT);
        assertThat(retrievedMessageIterator.next().getAttachments()).isEmpty();
    }

    @ContractTest
    public void messagesRetrievedUsingFetchTypeFullShouldHaveAttachmentsEmptyWhenNoAttachment() throws MailboxException, IOException{
        saveMessages();
        MessageMapper.FetchType fetchType = MessageMapper.FetchType.Full;
        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper.findInMailbox(attachmentsMailbox, MessageRange.one(messageWithoutAttachment.getUid()), fetchType, LIMIT);
        assertThat(retrievedMessageIterator.next().getAttachments()).isEmpty();
    }
    
    private SimpleMailbox createMailbox(MailboxPath mailboxPath) {
        SimpleMailbox mailbox = new SimpleMailbox(mailboxPath, UID_VALIDITY);
        MailboxId id = mapperProvider.generateId();
        mailbox.setMailboxId(id);
        return mailbox;
    }
    
    private void saveMessages() throws MailboxException {
        messageMapper.add(attachmentsMailbox, messageWithoutAttachment);
        messageWithoutAttachment.setModSeq(messageMapper.getHighestModSeq(attachmentsMailbox));
        messageMapper.add(attachmentsMailbox, message7With1Attachment);
        message7With1Attachment.setModSeq(messageMapper.getHighestModSeq(attachmentsMailbox));
        messageMapper.add(attachmentsMailbox, message8With2Attachments);
        message8With2Attachments.setModSeq(messageMapper.getHighestModSeq(attachmentsMailbox));
    }

    private SimpleMailboxMessage createMessage(Mailbox mailbox, MessageId messageId, String content, int bodyStart, PropertyBuilder propertyBuilder, List<MessageAttachment> attachments) {
        return new SimpleMailboxMessage(messageId, new Date(), content.length(), bodyStart, new SharedByteArrayInputStream(content.getBytes()), new Flags(), propertyBuilder, mailbox.getMailboxId(), attachments);
    }

    private SimpleMailboxMessage createMessage(Mailbox mailbox, MessageId messageId, String content, int bodyStart, PropertyBuilder propertyBuilder) {
        return new SimpleMailboxMessage(messageId, new Date(), content.length(), bodyStart, new SharedByteArrayInputStream(content.getBytes()), new Flags(), propertyBuilder, mailbox.getMailboxId());
    }
}
