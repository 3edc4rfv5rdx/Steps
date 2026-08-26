package xx.steps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import xx.steps.steps.StepAccess
import xx.steps.steps.stepAccessOf

/**
 * Whether the app can count, and if not, why. One rule: the background worker used to answer the
 * same question in the opposite order and journal the wrong reason for it.
 */
class StepAccessRuleTest {

    @Test
    fun `without the permission the answer is the permission, not the missing sensor`() {
        // Android hides the counter from an app that has not been allowed activity data, so a phone
        // that has one looks exactly like a phone that has none until the permission is granted.
        assertEquals(
            StepAccess.PERMISSION_MISSING,
            stepAccessOf(demo = false, permitted = false, sensorPresent = { false }),
        )
    }

    @Test
    fun `the sensor is not even looked for without the permission`() {
        var lookedFor = false

        stepAccessOf(demo = false, permitted = false, sensorPresent = { lookedFor = true; true })

        assertFalse(lookedFor)
    }

    @Test
    fun `a phone that has been allowed and has no counter says so`() {
        assertEquals(
            StepAccess.SENSOR_MISSING,
            stepAccessOf(demo = false, permitted = true, sensorPresent = { false }),
        )
    }

    @Test
    fun `permission and sensor together are ready`() {
        assertEquals(
            StepAccess.READY,
            stepAccessOf(demo = false, permitted = true, sensorPresent = { true }),
        )
    }

    @Test
    fun `the demo needs neither the hardware nor the permission`() {
        assertEquals(
            StepAccess.READY,
            stepAccessOf(demo = true, permitted = false, sensorPresent = { false }),
        )
    }
}
