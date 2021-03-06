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
package org.apache.james.mailbox.cassandra.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.util.OptionalConverter;
import org.assertj.core.data.MapEntry;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class AttachmentLoaderTest {

    private AttachmentMapper attachmentMapper;
    private AttachmentLoader testee;

    @Before
    public void setup() {
        attachmentMapper = mock(AttachmentMapper.class);
        testee = new AttachmentLoader(attachmentMapper);
    }

    @Test
    public void getAttachmentsShouldWorkWithDuplicatedIds() {
        AttachmentId attachmentId = AttachmentId.from("1");
        Set<AttachmentId> attachmentIds = ImmutableSet.of(attachmentId);

        Attachment attachment = Attachment.builder()
            .attachmentId(attachmentId)
            .bytes("attachment".getBytes())
            .type("type")
            .build();
        when(attachmentMapper.getAttachments(attachmentIds))
            .thenReturn(ImmutableList.of(attachment));

        Optional<String> name1 = Optional.of("name1");
        Optional<String> name2 = Optional.of("name2");
        Optional<Cid> cid = Optional.empty();
        boolean isInlined = false;
        CassandraMessageDAO.MessageAttachmentRepresentation attachmentRepresentation1 = new CassandraMessageDAO.MessageAttachmentRepresentation(attachmentId, name1, cid, isInlined);
        CassandraMessageDAO.MessageAttachmentRepresentation attachmentRepresentation2 = new CassandraMessageDAO.MessageAttachmentRepresentation(attachmentId, name2, cid, isInlined);

        Collection<MessageAttachment> attachments = testee.getAttachments(Sets.newHashSet(attachmentRepresentation1, attachmentRepresentation2));

        assertThat(attachments).hasSize(2)
            .containsOnly(new MessageAttachment(attachment, OptionalConverter.toGuava(name1), OptionalConverter.toGuava(cid), isInlined),
                new MessageAttachment(attachment, OptionalConverter.toGuava(name2), OptionalConverter.toGuava(cid), isInlined));
    }

    @Test
    public void getAttachmentsShouldReturnMultipleAttachmentWhenSeveralAttachmentsRepresentation() {
        AttachmentId attachmentId1 = AttachmentId.from("1");
        AttachmentId attachmentId2 = AttachmentId.from("2");
        Set<AttachmentId> attachmentIds = ImmutableSet.of(attachmentId1, attachmentId2);

        Attachment attachment1 = Attachment.builder()
            .attachmentId(attachmentId1)
            .bytes("attachment1".getBytes())
            .type("type")
            .build();
        Attachment attachment2 = Attachment.builder()
            .attachmentId(attachmentId2)
            .bytes("attachment2".getBytes())
            .type("type")
            .build();
        when(attachmentMapper.getAttachments(attachmentIds))
            .thenReturn(ImmutableList.of(attachment1, attachment2));

        Optional<String> name1 = Optional.of("name1");
        Optional<String> name2 = Optional.of("name2");
        Optional<Cid> cid = Optional.empty();
        boolean isInlined = false;
        CassandraMessageDAO.MessageAttachmentRepresentation attachmentRepresentation1 = new CassandraMessageDAO.MessageAttachmentRepresentation(attachmentId1, name1, cid, isInlined);
        CassandraMessageDAO.MessageAttachmentRepresentation attachmentRepresentation2 = new CassandraMessageDAO.MessageAttachmentRepresentation(attachmentId2, name2, cid, isInlined);

        Collection<MessageAttachment> attachments = testee.getAttachments(Sets.newHashSet(attachmentRepresentation1, attachmentRepresentation2));

        assertThat(attachments).hasSize(2)
            .containsOnly(new MessageAttachment(attachment1, OptionalConverter.toGuava(name1), OptionalConverter.toGuava(cid), isInlined),
                new MessageAttachment(attachment2, OptionalConverter.toGuava(name2), OptionalConverter.toGuava(cid), isInlined));
    }

    @Test
    public void getAttachmentsShouldReturnEmptyByDefault() {
        AttachmentId attachmentId = AttachmentId.from("1");
        Set<AttachmentId> attachmentIds = ImmutableSet.of(attachmentId);

        Attachment attachment = Attachment.builder()
            .attachmentId(attachmentId)
            .bytes("attachment".getBytes())
            .type("type")
            .build();
        when(attachmentMapper.getAttachments(attachmentIds))
            .thenReturn(ImmutableList.of(attachment));

        Collection<MessageAttachment> attachments = testee.getAttachments(Sets.newHashSet());

        assertThat(attachments).isEmpty();
    }

    @Test
    public void attachmentsByIdShouldReturnMapWhenExist() {
        AttachmentId attachmentId = AttachmentId.from("1");
        AttachmentId attachmentId2 = AttachmentId.from("2");
        Set<AttachmentId> attachmentIds = ImmutableSet.of(attachmentId, attachmentId2);

        Attachment attachment = Attachment.builder()
                .attachmentId(attachmentId)
                .bytes("attachment".getBytes())
                .type("type")
                .build();
        Attachment attachment2 = Attachment.builder()
                .attachmentId(attachmentId2)
                .bytes("attachment2".getBytes())
                .type("type")
                .build();
        when(attachmentMapper.getAttachments(attachmentIds))
            .thenReturn(ImmutableList.of(attachment, attachment2));

        Map<AttachmentId, Attachment> attachmentsById = testee.attachmentsById(attachmentIds);

        assertThat(attachmentsById).hasSize(2)
                .containsOnly(MapEntry.entry(attachmentId, attachment), MapEntry.entry(attachmentId2, attachment2));
    }

    @Test
    public void attachmentsByIdShouldReturnEmptyMapWhenAttachmentsDontExists() {
        AttachmentId attachmentId = AttachmentId.from("1");
        AttachmentId attachmentId2 = AttachmentId.from("2");
        Set<AttachmentId> attachmentIds = ImmutableSet.of(attachmentId, attachmentId2);

        when(attachmentMapper.getAttachments(attachmentIds))
                .thenReturn(ImmutableList.of());

        Map<AttachmentId, Attachment> attachmentsById = testee.attachmentsById(attachmentIds);

        assertThat(attachmentsById).hasSize(0);
    }

}
