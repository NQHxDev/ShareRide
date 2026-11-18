package com.shareride.gateway.serializer;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Jackson 3 serializer for Protobuf {@link Message} instances in Gateway WebFlux responses.
 *
 * <p>Uses Protobuf {@link JsonFormat} printer to output clean JSON matching REST DTO format.
 */
public class ProtobufMessageSerializer extends ValueSerializer<Message> {

    @Override
    public void serialize(Message value, JsonGenerator gen, SerializationContext ctxt)
            throws JacksonException {
        try {
            String json = JsonFormat.printer().alwaysPrintFieldsWithNoPresence().print(value);
            gen.writeRawValue(json);
        } catch (InvalidProtocolBufferException e) {
            throw DatabindException.from(gen, "Failed to serialize Protobuf message to JSON", e);
        }
    }
}
