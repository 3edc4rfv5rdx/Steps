package xx.steps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Turning steps into a distance at the configured step length. */
class DistanceTest {

    @Test
    fun `distance is steps times the step length`() {
        // 10 000 steps at 70 cm is 7 km.
        assertEquals(7_000.0, distanceMeters(10_000, 70), 0.001)
        assertEquals(0.0, distanceMeters(0, 70), 0.001)
    }

    @Test
    fun `metres below a kilometre, kilometres above it`() {
        assertFalse(isKilometres(distanceMeters(1_000, 70)))
        assertTrue(isKilometres(distanceMeters(1_500, 70)))

        // The boundary itself reads as kilometres, not as "1000 m".
        assertTrue(isKilometres(1_000.0))
    }

    @Test
    fun `a step length outside human range is clamped`() {
        assertEquals(MIN_STEP_LENGTH_CM, clampStepLength(0))
        assertEquals(MIN_STEP_LENGTH_CM, clampStepLength(-40))
        assertEquals(MAX_STEP_LENGTH_CM, clampStepLength(500))
        assertEquals(70, clampStepLength(70))
    }

    @Test
    fun `a full day of walking does not overflow`() {
        // Int arithmetic would wrap here; the calculation is deliberately in doubles.
        val meters = distanceMeters(Int.MAX_VALUE, MAX_STEP_LENGTH_CM)

        assertTrue(meters > 2_000_000_000.0)
    }
}
