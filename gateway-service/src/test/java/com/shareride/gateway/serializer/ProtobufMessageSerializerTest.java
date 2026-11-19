package com.shareride.gateway.serializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.shareride.common.grpc.auth.v1.VerifyEmailResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;

@DisplayName("ProtobufMessageSerializer Unit Tests")
class ProtobufMessageSerializerTest {

    @Test
    @DisplayName("Should serialize Protobuf message to valid JSON string")
    void testSerializeProtobufMessage() throws Exception {
        ProtobufMessageSerializer serializer = new ProtobufMessageSerializer();
        VerifyEmailResponse message =
                VerifyEmailResponse.newBuilder()
                        .setSuccess(true)
                        .setMessage("Account verified")
                        .setAccountId("01a1f4f9-7475-70c6-adc2-fbf06028ce53")
                        .build();

        JsonGenerator generator = mock(JsonGenerator.class);
        SerializationContext context = mock(SerializationContext.class);

        serializer.serialize(message, generator, context);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(generator).writeRawValue(captor.capture());

        assertThat(captor.getValue()).contains("\"success\": true");
        assertThat(captor.getValue()).contains("\"message\": \"Account verified\"");
        assertThat(captor.getValue())
                .contains("\"accountId\": \"01a1f4f9-7475-70c6-adc2-fbf06028ce53\"");
    }

    @Test
    @DisplayName("Should throw DatabindException when Protobuf serialization fails")
    void testSerializeInvalidAnyMessage_ThrowsDatabindException() {
        ProtobufMessageSerializer serializer = new ProtobufMessageSerializer();
        com.google.protobuf.Any invalidAny =
                com.google.protobuf.Any.newBuilder()
                        .setTypeUrl("type.googleapis.com/unknown.InvalidType")
                        .setValue(com.google.protobuf.ByteString.copyFromUtf8("invalid payload"))
                        .build();

        JsonGenerator generator = mock(JsonGenerator.class);
        SerializationContext context = mock(SerializationContext.class);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> serializer.serialize(invalidAny, generator, context))
                .isInstanceOf(tools.jackson.databind.DatabindException.class)
                .hasMessageContaining("Failed to serialize Protobuf message to JSON");
    }
}
