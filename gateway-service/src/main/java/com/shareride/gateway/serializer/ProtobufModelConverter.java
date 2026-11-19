package com.shareride.gateway.serializer;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Iterator;
import lombok.extern.slf4j.Slf4j;

/**
 * Custom OpenAPI {@link ModelConverter} for Protobuf {@link Message} instances.
 *
 * <p>SpringDoc's default reflection-based {@code ModelResolver} inspects all getters on Protobuf
 * generated classes, including internal getters such as {@code getDescriptorForType()}, {@code
 * getDefaultInstanceForType()}, {@code getUnknownFields()}, and {@code getAllFields()}. These
 * internal getters create cyclic object graphs (e.g. {@code Descriptor -> DescriptorProto ->
 * Descriptor} and {@code Message -> defaultInstanceForType -> Message}) that freeze Swagger UI in
 * the browser with an infinite recursion error ("Page Unresponsive").
 *
 * <p>This converter intercepts Protobuf {@link Message} classes and generates a clean OpenAPI 3
 * schema matching the exact JSON structure serialized by Protobuf {@code JsonFormat}, completely
 * eliminating internal Protobuf metadata and circular references.
 */
@Slf4j
public class ProtobufModelConverter implements ModelConverter {

    private static final String SCHEMA_REF_PREFIX = "#/components/schemas/";

    @Override
    public Schema<?> resolve(
            AnnotatedType annotatedType,
            ModelConverterContext context,
            Iterator<ModelConverter> chain) {
        if (annotatedType == null) {
            return null;
        }

        Type type = annotatedType.getType();
        Class<?> rawClass = getRawClass(type);

        if (rawClass == null) {
            return chain.hasNext() ? chain.next().resolve(annotatedType, context, chain) : null;
        }

        // Prevent deep reflection on Protobuf internal classes
        if (rawClass.getName().startsWith("com.google.protobuf.")) {
            return new ObjectSchema();
        }

        // Intercept concrete generated Protobuf messages
        if (Message.class.isAssignableFrom(rawClass)) {
            return resolveProtobufMessage(rawClass, annotatedType, context);
        }

        return chain.hasNext() ? chain.next().resolve(annotatedType, context, chain) : null;
    }

    private Schema<?> resolveProtobufMessage(
            Class<?> rawClass, AnnotatedType annotatedType, ModelConverterContext context) {
        String schemaName = rawClass.getSimpleName();

        var definedModels = context.getDefinedModels();
        if (definedModels == null || !definedModels.containsKey(schemaName)) {
            try {
                Method getDescriptorMethod = rawClass.getMethod("getDescriptor");
                Descriptor descriptor = (Descriptor) getDescriptorMethod.invoke(null);
                defineProtobufModel(schemaName, descriptor, context);
            } catch (Exception e) {
                log.warn(
                        "Failed to introspect Protobuf descriptor for class {}: {}",
                        rawClass.getName(),
                        e.getMessage());
                Schema<Object> fallbackSchema = new ObjectSchema();
                fallbackSchema.setName(schemaName);
                context.defineModel(schemaName, fallbackSchema);
            }
        }

        if (annotatedType.isResolveAsRef()
                || annotatedType.getParent() != null
                || annotatedType.getPropertyName() != null) {
            return new Schema<>().$ref(SCHEMA_REF_PREFIX + schemaName);
        } else {
            return context.getDefinedModels() != null
                    ? context.getDefinedModels().get(schemaName)
                    : null;
        }
    }

    private void defineProtobufModel(
            String schemaName, Descriptor descriptor, ModelConverterContext context) {
        Schema<Object> model = new ObjectSchema();
        model.setName(schemaName);
        // Register early in context to prevent recursion if messages reference each other
        context.defineModel(schemaName, model);

        for (FieldDescriptor fd : descriptor.getFields()) {
            model.addProperty(fd.getJsonName(), toFieldSchema(fd, context));
        }
    }

    private Schema<?> toFieldSchema(FieldDescriptor fd, ModelConverterContext context) {
        if (fd.isMapField()) {
            MapSchema mapSchema = new MapSchema();
            Descriptor mapEntryDesc = fd.getMessageType();
            FieldDescriptor valueFd = mapEntryDesc.findFieldByName("value");
            if (valueFd != null) {
                mapSchema.setAdditionalProperties(toSingleFieldSchema(valueFd, context));
            } else {
                mapSchema.setAdditionalProperties(new ObjectSchema());
            }
            return mapSchema;
        }

        if (fd.isRepeated()) {
            ArraySchema arraySchema = new ArraySchema();
            arraySchema.setItems(toSingleFieldSchema(fd, context));
            return arraySchema;
        }

        return toSingleFieldSchema(fd, context);
    }

    private Schema<?> toSingleFieldSchema(FieldDescriptor fd, ModelConverterContext context) {
        return switch (fd.getJavaType()) {
            case BOOLEAN -> new BooleanSchema();
            case INT -> new IntegerSchema();
            case LONG -> new IntegerSchema().format("int64");
            case FLOAT -> new NumberSchema().format("float");
            case DOUBLE -> new NumberSchema().format("double");
            case STRING -> new StringSchema();
            case BYTE_STRING -> new StringSchema().format("byte");
            case ENUM -> {
                StringSchema enumSchema = new StringSchema();
                for (Descriptors.EnumValueDescriptor evd : fd.getEnumType().getValues()) {
                    enumSchema.addEnumItemObject(evd.getName());
                }
                yield enumSchema;
            }
            case MESSAGE -> resolveNestedMessageSchema(fd.getMessageType(), context);
        };
    }

    private Schema<?> resolveNestedMessageSchema(Descriptor desc, ModelConverterContext context) {
        String fullName = desc.getFullName();
        if ("google.protobuf.Timestamp".equals(fullName)) {
            return new StringSchema().format("date-time").example("2026-11-19T00:00:00Z");
        }
        if ("google.protobuf.StringValue".equals(fullName)) {
            return new StringSchema();
        }
        if ("google.protobuf.Int32Value".equals(fullName)) {
            return new IntegerSchema();
        }
        if ("google.protobuf.Int64Value".equals(fullName)) {
            return new IntegerSchema().format("int64");
        }
        if ("google.protobuf.BoolValue".equals(fullName)) {
            return new BooleanSchema();
        }
        if ("google.protobuf.Empty".equals(fullName)) {
            return new ObjectSchema();
        }

        String nestedName = desc.getName();
        var definedModels = context.getDefinedModels();
        if (definedModels == null || !definedModels.containsKey(nestedName)) {
            defineProtobufModel(nestedName, desc, context);
        }
        return new Schema<>().$ref(SCHEMA_REF_PREFIX + nestedName);
    }

    private Class<?> getRawClass(Type type) {
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> clazz) {
            return clazz;
        }
        try {
            Method m = type.getClass().getMethod("getRawClass");
            Object res = m.invoke(type);
            if (res instanceof Class<?> c) {
                return c;
            }
        } catch (Exception ignored) {
            // Ignored
        }
        return null;
    }
}
