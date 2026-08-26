package xx.steps

import org.junit.Assert.assertEquals
import org.junit.Test
import xx.steps.steps.PermissionAsk
import xx.steps.steps.nextPermissionAsk

/**
 * The order the app puts its questions in once counting has been allowed. One at a time, and the
 * battery exemption first: it is a system screen the user leaves the app for, and a permission
 * dialog launched while it is coming up is dismissed unread.
 */
class PermissionAskTest {

    @Test
    fun `the exemption is asked for before the notification`() {
        assertEquals(
            PermissionAsk.BATTERY_EXEMPTION,
            nextPermissionAsk(exempt = false, canPostNotifications = false),
        )
        // Even with nothing else left to ask, the exemption still goes first.
        assertEquals(
            PermissionAsk.BATTERY_EXEMPTION,
            nextPermissionAsk(exempt = false, canPostNotifications = true),
        )
    }

    @Test
    fun `the notification is asked for once the exemption is settled`() {
        assertEquals(
            PermissionAsk.NOTIFICATIONS,
            nextPermissionAsk(exempt = true, canPostNotifications = false),
        )
    }

    @Test
    fun `nothing is asked when both are settled`() {
        assertEquals(
            PermissionAsk.NOTHING,
            nextPermissionAsk(exempt = true, canPostNotifications = true),
        )
    }
}
