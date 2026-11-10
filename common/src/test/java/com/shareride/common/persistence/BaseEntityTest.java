package com.shareride.common.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BaseEntityTest {

    static class SampleEntity extends BaseEntity {}

    @Test
    @DisplayName("PrePersist ensureId should generate UUID if null")
    void testEnsureId() {
        SampleEntity entity = new SampleEntity();
        assertNull(entity.getId());
        assertEquals(0L, entity.getVersion());

        entity.ensureId();
        assertNotNull(entity.getId());
        assertEquals(entity.getId(), entity.getId());
    }

    @Test
    @DisplayName("PrePersist ensureId should retain predefined UUID")
    void testPredefinedId() {
        SampleEntity entity = new SampleEntity();
        UUID customId = UUID.randomUUID();
        entity.setId(customId);

        entity.ensureId();
        assertEquals(customId, entity.getId());
    }

    @Test
    @DisplayName("Equals and hashCode should adhere to JPA contracts")
    void testEqualsAndHashCode() {
        SampleEntity entity1 = new SampleEntity();
        SampleEntity entity2 = new SampleEntity();

        // Null IDs should not be equal
        assertNotEquals(entity1, entity2);

        UUID id = UUID.randomUUID();
        entity1.setId(id);
        entity2.setId(id);

        assertEquals(entity1, entity2);
        assertEquals(entity1.hashCode(), entity2.hashCode());

        entity2.setId(UUID.randomUUID());
        assertNotEquals(entity1, entity2);
    }
}
