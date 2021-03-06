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
package org.apache.james.jmap.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import javax.mail.internet.MimeMessage;

import org.apache.james.jmap.model.MessageContentExtractor.MessageContent;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.field.Fields;
import org.apache.james.mime4j.message.BasicBodyFactory;
import org.apache.james.mime4j.message.BodyPart;
import org.apache.james.mime4j.message.BodyPartBuilder;
import org.apache.james.mime4j.message.HeaderImpl;
import org.apache.james.mime4j.message.MessageBuilder;
import org.apache.james.mime4j.message.MultipartBuilder;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.util.ByteSequence;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Charsets;

public class MessageContentExtractorTest {
    private static final String BINARY_CONTENT = "binary";
    private static final String TEXT_CONTENT = "text content";
    private static final String HTML_CONTENT = "<b>html</b> content";
    private static final String ATTACHMENT_CONTENT = "attachment content";
    private static final String ANY_VALUE = "anyValue";
    private static final Field CONTENT_ID_FIELD = new Field() {
        @Override
        public String getName() {
            return MessageContentExtractor.CONTENT_ID;
        }

        @Override
        public String getBody() {
            return ANY_VALUE;
        }

        @Override
        public ByteSequence getRaw() {
            return ByteSequence.EMPTY;
        }
    };

    private MessageContentExtractor testee;

    private BodyPart htmlPart;
    private BodyPart textPart;
    private BodyPart textAttachment;

    @Before
    public void setup() throws IOException {
        testee = new MessageContentExtractor();
        textPart = BodyPartBuilder.create().setBody(TEXT_CONTENT, "plain", Charsets.UTF_8).build();
        htmlPart = BodyPartBuilder.create().setBody(HTML_CONTENT, "html", Charsets.UTF_8).build();
        textAttachment = BodyPartBuilder.create()
                .setBody(ATTACHMENT_CONTENT, "plain", Charsets.UTF_8)
                .setContentDisposition("attachment")
                .build();
    }

    @Test
    public void extractShouldReturnEmptyWhenBinaryContentOnly() throws IOException {
        Message message = MessageBuilder.create()
                .setBody(BasicBodyFactory.INSTANCE.binaryBody(BINARY_CONTENT, Charsets.UTF_8))
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).isEmpty();
        assertThat(actual.getHtmlBody()).isEmpty();
    }

    @Test
    public void extractShouldReturnTextOnlyWhenTextOnlyBody() throws IOException {
        Message message = MessageBuilder.create()
                .setBody(TEXT_CONTENT, Charsets.UTF_8)
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).contains(TEXT_CONTENT);
        assertThat(actual.getHtmlBody()).isEmpty();
    }

    @Test
    public void extractShouldReturnHtmlOnlyWhenHtmlOnlyBody() throws IOException {
        Message message = MessageBuilder.create()
                .setBody(HTML_CONTENT, "html", Charsets.UTF_8)
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).isEmpty();
        assertThat(actual.getHtmlBody()).contains(HTML_CONTENT);
    }

    @Test
    public void extractShouldReturnHtmlAndTextWhenMultipartAlternative() throws IOException {
        Multipart multipart = MultipartBuilder.create("alternative")
                .addBodyPart(textPart)
                .addBodyPart(htmlPart)
                .build();
        Message message = MessageBuilder.create()
                .setBody(multipart)
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).contains(TEXT_CONTENT);
        assertThat(actual.getHtmlBody()).contains(HTML_CONTENT);
    }

    @Test
    public void extractShouldReturnHtmlWhenMultipartAlternativeWithoutPlainPart() throws IOException {
        Multipart multipart = MultipartBuilder.create("alternative")
                .addBodyPart(htmlPart)
                .build();
        Message message = MessageBuilder.create()
                .setBody(multipart)
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).isEmpty();
        assertThat(actual.getHtmlBody()).contains(HTML_CONTENT);
    }

    @Test
    public void extractShouldReturnTextWhenMultipartAlternativeWithoutHtmlPart() throws IOException {
        Multipart multipart = MultipartBuilder.create("alternative")
                .addBodyPart(textPart)
                .build();
        Message message = MessageBuilder.create()
                .setBody(multipart)
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).contains(TEXT_CONTENT);
        assertThat(actual.getHtmlBody()).isEmpty();
    }

    @Test
    public void extractShouldReturnFirstNonAttachmentPartWhenMultipartMixed() throws IOException {
        Multipart multipart = MultipartBuilder.create("mixed")
                .addBodyPart(textAttachment)
                .addBodyPart(htmlPart)
                .addBodyPart(textPart)
                .build();
        Message message = MessageBuilder.create()
                .setBody(multipart)
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getHtmlBody()).contains(HTML_CONTENT);
        assertThat(actual.getTextBody()).isEmpty();
    }

    @Test
    public void extractShouldReturnEmptyWhenMultipartMixedAndFirstPartIsATextAttachment() throws IOException {
        Multipart multipart = MultipartBuilder.create("mixed")
                .addBodyPart(textAttachment)
                .build();
        Message message = MessageBuilder.create()
                .setBody(multipart)
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).isEmpty();
        assertThat(actual.getHtmlBody()).isEmpty();
    }

    @Test
    public void extractShouldReturnFirstPartOnlyWhenMultipartMixedAndFirstPartIsHtml() throws IOException {
        Multipart multipart = MultipartBuilder.create("mixed")
                .addBodyPart(htmlPart)
                .addBodyPart(textPart)
                .build();
        Message message = MessageBuilder.create()
                .setBody(multipart)
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).isEmpty();
        assertThat(actual.getHtmlBody()).contains(HTML_CONTENT);
    }

    @Test
    public void extractShouldReturnHtmlAndTextWhenMultipartMixedAndFirstPartIsMultipartAlternative() throws IOException {
        BodyPart multipartAlternative = BodyPartBuilder.create()
            .setBody(MultipartBuilder.create("alternative")
                    .addBodyPart(htmlPart)
                    .addBodyPart(textPart)
                    .build())
            .build();
        Multipart multipartMixed = MultipartBuilder.create("mixed")
                .addBodyPart(multipartAlternative)
                .build();
        Message message = MessageBuilder.create()
                .setBody(multipartMixed)
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).contains(TEXT_CONTENT);
        assertThat(actual.getHtmlBody()).contains(HTML_CONTENT);
    }

    @Test
    public void extractShouldReturnHtmlWhenMultipartRelated() throws IOException {
        Multipart multipart = MultipartBuilder.create("related")
                .addBodyPart(htmlPart)
                .build();
        Message message = MessageBuilder.create()
                .setBody(multipart)
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getTextBody()).isEmpty();
        assertThat(actual.getHtmlBody()).contains(HTML_CONTENT);
    }

    @Test
    public void extractShouldReturnHtmlAndTextWhenMultipartAlternativeAndFirstPartIsMultipartRelated() throws IOException {
        BodyPart multipartRelated = BodyPartBuilder.create()
            .setBody(MultipartBuilder.create("related")
                    .addBodyPart(htmlPart)
                    .build())
            .build();
        Multipart multipartAlternative = MultipartBuilder.create("alternative")
                .addBodyPart(multipartRelated)
                .build();
        Message message = MessageBuilder.create()
                .setBody(multipartAlternative)
                .build();
        MessageContent actual = testee.extract(message);
        assertThat(actual.getHtmlBody()).contains(HTML_CONTENT);
    }

    @Test
    public void extractShouldRetrieveHtmlBodyWithOneInlinedHTMLAttachmentWithoutCid() throws IOException {
        //Given
        BodyPart inlinedHTMLPart = BodyPartBuilder.create()
            .setBody(HTML_CONTENT, "html", Charsets.UTF_8)
            .build();
        HeaderImpl inlinedHeader = new HeaderImpl();
        inlinedHeader.addField(Fields.contentDisposition(MimeMessage.INLINE));
        inlinedHeader.addField(Fields.contentType("text/html; charset=utf-8"));
        inlinedHTMLPart.setHeader(inlinedHeader);
        Multipart multipartAlternative = MultipartBuilder.create("alternative")
            .addBodyPart(inlinedHTMLPart)
            .build();
        Message message = MessageBuilder.create()
            .setBody(multipartAlternative)
            .build();

        //When
        MessageContent actual = testee.extract(message);

        //Then
        assertThat(actual.getHtmlBody()).contains(HTML_CONTENT);
    }

    @Test
    public void extractShouldNotRetrieveHtmlBodyWithOneInlinedHTMLAttachmentWithCid() throws IOException {
        //Given
        BodyPart inlinedHTMLPart = BodyPartBuilder.create()
            .setBody(HTML_CONTENT, "html", Charsets.UTF_8)
            .build();
        HeaderImpl inlinedHeader = new HeaderImpl();
        inlinedHeader.addField(Fields.contentDisposition(MimeMessage.INLINE));
        inlinedHeader.addField(Fields.contentType("text/html; charset=utf-8"));
        inlinedHeader.addField(CONTENT_ID_FIELD);
        inlinedHTMLPart.setHeader(inlinedHeader);
        Multipart multipartAlternative = MultipartBuilder.create("alternative")
            .addBodyPart(inlinedHTMLPart)
            .build();
        Message message = MessageBuilder.create()
            .setBody(multipartAlternative)
            .build();

        //When
        MessageContent actual = testee.extract(message);

        //Then
        assertThat(actual.getHtmlBody()).isEmpty();
    }


    @Test
    public void extractShouldRetrieveTextBodyWithOneInlinedTextAttachmentWithoutCid() throws IOException {
        //Given
        BodyPart inlinedTextPart = BodyPartBuilder.create()
            .setBody(TEXT_CONTENT, "text", Charsets.UTF_8)
            .build();
        HeaderImpl inlinedHeader = new HeaderImpl();
        inlinedHeader.addField(Fields.contentDisposition(MimeMessage.INLINE));
        inlinedHeader.addField(Fields.contentType("text/plain; charset=utf-8"));
        inlinedTextPart.setHeader(inlinedHeader);
        Multipart multipartAlternative = MultipartBuilder.create("alternative")
            .addBodyPart(inlinedTextPart)
            .build();
        Message message = MessageBuilder.create()
            .setBody(multipartAlternative)
            .build();

        //When
        MessageContent actual = testee.extract(message);

        //Then
        assertThat(actual.getTextBody()).contains(TEXT_CONTENT);
    }

    @Test
    public void extractShouldNotRetrieveTextBodyWithOneInlinedTextAttachmentWithCid() throws IOException {
        //Given
        BodyPart inlinedTextPart = BodyPartBuilder.create()
            .setBody(TEXT_CONTENT, "text", Charsets.UTF_8)
            .build();
        HeaderImpl inlinedHeader = new HeaderImpl();
        inlinedHeader.addField(Fields.contentDisposition(MimeMessage.INLINE));
        inlinedHeader.addField(Fields.contentType("text/plain; charset=utf-8"));
        inlinedHeader.addField(CONTENT_ID_FIELD);
        inlinedTextPart.setHeader(inlinedHeader);
        Multipart multipartAlternative = MultipartBuilder.create("alternative")
            .addBodyPart(inlinedTextPart)
            .build();
        Message message = MessageBuilder.create()
            .setBody(multipartAlternative)
            .build();

        //When
        MessageContent actual = testee.extract(message);

        //Then
        assertThat(actual.getTextBody()).isEmpty();
    }
}
