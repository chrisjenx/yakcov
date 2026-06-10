package com.chrisjenx.yakcov

import com.chrisjenx.yakcov.ValidationResult.Outcome
import kotlinx.serialization.json.Json
import yakcov.library.generated.resources.Res
import yakcov.library.generated.resources.ruleRequired
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ValidationResultTest {

    @Test
    fun messageOrNull_regular_returnsEagerString() {
        assertEquals("Bad value", RegularValidationResult.error("Bad value").messageOrNull())
    }

    @Test
    fun messageOrNull_resource_returnsNull() {
        // Resource-backed results need composition; the eager accessor returns null.
        assertNull(ResourceValidationResult.error(Res.string.ruleRequired).messageOrNull())
    }

    @Test
    fun outcome_isSerializable_roundTrips() {
        val json = Json.encodeToString(Outcome.serializer(), Outcome.WARNING)
        val restored = Json.decodeFromString(Outcome.serializer(), json)
        assertEquals(Outcome.WARNING, restored)
    }
}
