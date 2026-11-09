package com.shareride.common.persistence;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SoftDeletableEntityTest {

    static class SampleSoftDeletable extends SoftDeletableEntity {}

    @Test
    @DisplayName("markDeleted and restore should transition soft delete flags correctly")
    void testSoftDeleteTransitions() {
        SampleSoftDeletable entity = new SampleSoftDeletable();
        assertFalse(entity.isDeleted());
        assertNull(entity.getDeletedAt());
        assertNull(entity.getDeletedBy());

        entity.markDeleted("admin-user-1");
        assertTrue(entity.isDeleted());
        assertNotNull(entity.getDeletedAt());
        assertEquals("admin-user-1", entity.getDeletedBy());

        entity.restore();
        assertFalse(entity.isDeleted());
        assertNull(entity.getDeletedAt());
        assertNull(entity.getDeletedBy());
    }
}
