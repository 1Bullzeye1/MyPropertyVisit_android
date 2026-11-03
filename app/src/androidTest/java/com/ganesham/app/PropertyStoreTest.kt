package com.ganesham.app

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID

class PropertyStoreTest {
    @Test fun propertySurvivesReopeningAndCanBeEditedAndDeleted() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "property-test-${UUID.randomUUID()}.db"
        var store = PropertyStore(context, databaseName)
        try {
            val property = Property(UUID.randomUUID().toString(), "Test flat", "Flat", "Test address")
            store.save(property, false)
            store.close()
            store = PropertyStore(context, databaseName)
            assertEquals(listOf(property), store.all())
            val edited = property.copy(name = "Edited land", type = "Land", location = "New address")
            store.save(edited, true)
            assertEquals(listOf(edited), store.all())
            store.delete(property.id)
            assertTrue(store.all().isEmpty())
        } finally { store.close(); context.deleteDatabase(databaseName) }
    }
}
