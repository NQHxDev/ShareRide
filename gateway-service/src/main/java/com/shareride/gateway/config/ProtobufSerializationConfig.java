package com.shareride.gateway.config;

import com.google.protobuf.Message;
import com.shareride.gateway.serializer.ProtobufMessageSerializer;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.module.SimpleModule;

/**
 * Jackson configuration for Protobuf Message serialization in Gateway WebFlux responses.
 *
 * <p>Registers a custom Jackson 3 serializer for {@link Message} using Protobuf's {@link
 * com.google.protobuf.util.JsonFormat} printer, enabling seamless serialization of downstream gRPC
 * responses into JSON for REST clients.
 */
@Configuration
public class ProtobufSerializationConfig {

    @Bean
    public JsonMapperBuilderCustomizer protobufJsonMapperCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule("ProtobufMessageModule");
            module.addSerializer(Message.class, new ProtobufMessageSerializer());
            builder.addModule(module);
        };
    }
}
