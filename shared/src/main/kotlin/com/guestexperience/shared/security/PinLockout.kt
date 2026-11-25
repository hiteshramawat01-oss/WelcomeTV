package com.guestexperience.shared.security

import kotlinx.serialization.Serializable

@Serializable
data class LockoutState(
    val failures: Int = 0,
    val lockedUntilMs: Long = 0L,
)

/**
 * Wrong-PIN policy: [freeAttempts] tries, then a [baseLockMs] wait that doubles with every further
 * failure, capped at [maxLockMs]. Pure and clock-free: callers pass `now`, which makes it testable.
 */
class PinLockoutPolicy(
    val freeAttempts: Int = 5,
    val baseLockMs: Long = 30_000L,
    val maxLockMs: Long = 15 * 60_000L,
) {
    fun isLocked(state: LockoutState, nowMs: Long): Boolean = nowMs < state.lockedUntilMs

    fun remainingLockMs(state: LockoutState, nowMs: Long): Long = (state.lockedUntilMs - nowMs).coerceAtLeast(0L)

    fun attemptsBeforeLock(state: LockoutState): Int = (freeAttempts - state.failures).coerceAtLeast(0)

    fun onFailure(state: LockoutState, nowMs: Long): LockoutState {
        val failures = state.failures + 1
        if (failures < freeAttempts) return LockoutState(failures, 0L)
        val doublings = (failures - freeAttempts).coerceAtMost(20)
        val lock = (baseLockMs shl doublings).coerceAtMost(maxLockMs)
        return LockoutState(failures, nowMs + lock)
    }

    fun onSuccess(): LockoutState = LockoutState()
}
