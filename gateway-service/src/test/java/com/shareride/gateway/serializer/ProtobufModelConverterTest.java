package com.shareride.gateway.serializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.google.protobuf.Message;
import com.shareride.common.grpc.auth.v1.LoginResponse;
import com.shareride.common.grpc.auth.v1.VerifyEmailResponse;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.converter.ModelConverterContextImpl;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Collections;
import java.util.Iterator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProtobufModelConverter Unit Tests")
class ProtobufModelConverterTest {

    private ProtobufModelConverter converter;
    private ModelConverterContext context;

    @BeforeEach
    void setUp() {
        converter = new ProtobufModelConverter();
        context = new ModelConverterContextImpl(Collections.emptyList());
    }

    @Test
    @DisplayName("Should convert VerifyEmailResponse to clean schema without circular references")
    void testVerifyEmailResponseSchema() {
        AnnotatedType annotatedType = new AnnotatedType(VerifyEmailResponse.class);
        Iterator<ModelConverter> chain = Collections.emptyIterator();

        Schema<?> schema = converter.resolve(annotatedType, context, chain);

        assertThat(schema).isNotNull();
        Schema<?> definedSchema = context.getDefinedModels().get("VerifyEmailResponse");
        assertThat(definedSchema).isNotNull();
        assertThat(definedSchema.getType()).isEqualTo("object");

        // Verify only actual Protobuf business fields exist
        assertThat(definedSchema.getProperties()).containsKeys("success", "message", "accountId");

        // Verify all circular and internal Protobuf properties are absent
        assertThat(definedSchema.getProperties())
                .doesNotContainKeys(
                        "defaultInstanceForType",
                        "descriptorForType",
                        "unknownFields",
                        "allFields",
                        "parserForType",
                        "initializationErrorString",
                        "allFieldsRaw",
                        "serializedSize",
                        "memoizedSerializedSize");
    }

    @Test
    @DisplayName("Should convert LoginResponse and resolve nested AccountProto reference")
    void testLoginResponseWithNestedAccountProto() {
        AnnotatedType annotatedType = new AnnotatedType(LoginResponse.class);
        Iterator<ModelConverter> chain = Collections.emptyIterator();

        Schema<?> schema = converter.resolve(annotatedType, context, chain);

        assertThat(schema).isNotNull();
        Schema<?> loginSchema = context.getDefinedModels().get("LoginResponse");
        assertThat(loginSchema).isNotNull();
        assertThat(loginSchema.getProperties())
                .containsKeys(
                        "accessToken",
                        "refreshToken",
                        "tokenType",
                        "expiresIn",
                        "sessionId",
                        "account");

        // Verify nested AccountProto is defined
        Schema<?> accountProp = (Schema<?>) loginSchema.getProperties().get("account");
        assertThat(accountProp.get$ref()).isEqualTo("#/components/schemas/AccountProto");

        Schema<?> accountSchema = context.getDefinedModels().get("AccountProto");
        assertThat(accountSchema).isNotNull();
        assertThat(accountSchema.getProperties())
                .containsKeys(
                        "accountId",
                        "email",
                        "phoneNumber",
                        "roleCode",
                        "status",
                        "roles",
                        "permissions");
    }

    @Test
    @DisplayName("Should return safe ObjectSchema for com.google.protobuf internal classes")
    void testProtobufInternalClass() {
        AnnotatedType annotatedType = new AnnotatedType(Message.class);
        Iterator<ModelConverter> chain = Collections.emptyIterator();

        Schema<?> schema = converter.resolve(annotatedType, context, chain);

        assertThat(schema).isNotNull();
        assertThat(schema.getType()).isEqualTo("object");
    }

    @Test
    @DisplayName("Should delegate non-protobuf classes to converter chain")
    void testNonProtobufClassDelegation() {
        AnnotatedType annotatedType = new AnnotatedType(String.class);
        ModelConverter nextConverter = mock(ModelConverter.class);
        Iterator<ModelConverter> chain = Collections.singletonList(nextConverter).iterator();

        converter.resolve(annotatedType, context, chain);
        // The chain iterator was advanced
        assertThat(chain.hasNext()).isFalse();
    }
}
