package com.guestexperience.shared.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinSecurityTest {
    @Test
    fun `hash verifies the right pin only`() {
        val stored = PinHasher.hash("4829", iterations = 1_000)
        assertTrue(PinHasher.verify("4829", stored))
        assertFalse(PinHasher.verify("4828", stored))
        assertFalse(PinHasher.verify("48290", stored))
        assertFalse(stored.hashB64.contains("4829"))
    }

    @Test
    fun `same pin gets a different salt and hash each time`() {
        val a = PinHasher.hash("123456", iterations = 1_000)
        val b = PinHasher.hash("123456", iterations = 1_000)
        assertNotEquals(a.saltB64, b.saltB64)
        assertNotEquals(a.hashB64, b.hashB64)
        assertTrue(PinHasher.verify("123456", a) && PinHasher.verify("123456", b))
    }

    @Test
    fun `default iteration count is used and stored`() {
        val stored = PinHasher.hash("1234")
        assertEquals(PinHasher.DEFAULT_ITERATIONS, stored.iterations)
        assertTrue(PinHasher.verify("1234", stored))
    }

    @Test
    fun `lockout after five failures then doubling up to the cap`() {
        val policy = PinLockoutPolicy(freeAttempts = 5, baseLockMs = 30_000, maxLockMs = 15 * 60_000)
        var state = LockoutState()
        var now = 1_000_000L
        repeat(4) {
            state = policy.onFailure(state, now)
            assertFalse(policy.isLocked(state, now))
        }
        assertEquals(1, policy.attemptsBeforeLock(state))

        state = policy.onFailure(state, now) // 5th
        assertTrue(policy.isLocked(state, now))
        assertEquals(30_000, policy.remainingLockMs(state, now))
        assertFalse(policy.isLocked(state, now + 30_000))

        now += 30_000
        state = policy.onFailure(state, now) // 6th → 60 s
        assertEquals(60_000, policy.remainingLockMs(state, now))

        repeat(10) { now += 10_000_000; state = policy.onFailure(state, now) }
        assertEquals(15 * 60_000L, policy.remainingLockMs(state, now)) // capped

        assertEquals(LockoutState(), policy.onSuccess())
    }
}
