package com.argenox.bluenoxandroid

import android.os.Handler
import android.os.Looper

enum class BlueNoxMockAction {
    CONNECTED,
    DISCONNECTED,
    MTU_CHANGED,
    BOND_STATE,
    FAILURE,
}

data class BlueNoxMockStep(
    val delayMs: Long,
    val action: BlueNoxMockAction,
    val mtu: Int? = null,
    val bondState: BlueNoxDeviceCallbacks.BlueNoxBondState? = null,
    val failureReason: BlueNoxDeviceCallbacks.BlueNoxFailureReason? = null,
    val detail: String = "",
)

interface BlueNoxMockBleListener {
    fun onConnected()
    fun onDisconnected()
    fun onMtuChanged(mtu: Int)
    fun onBondState(state: BlueNoxDeviceCallbacks.BlueNoxBondState, detail: String)
    fun onFailure(reason: BlueNoxDeviceCallbacks.BlueNoxFailureReason, detail: String)
}

/**
 * Runs deterministic BLE scenarios for integration tests and UI rehearsals.
 */
class BlueNoxMockBleHarness(
    private val listener: BlueNoxMockBleListener,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val pending = mutableListOf<Runnable>()

    fun cancel() {
        pending.forEach(handler::removeCallbacks)
        pending.clear()
    }

    fun runScenario(steps: List<BlueNoxMockStep>) {
        cancel()
        var accumulatedDelay = 0L
        steps.forEach { step ->
            accumulatedDelay += step.delayMs.coerceAtLeast(0L)
            val task = Runnable {
                when (step.action) {
                    BlueNoxMockAction.CONNECTED -> listener.onConnected()
                    BlueNoxMockAction.DISCONNECTED -> listener.onDisconnected()
                    BlueNoxMockAction.MTU_CHANGED -> listener.onMtuChanged(step.mtu ?: 247)
                    BlueNoxMockAction.BOND_STATE -> {
                        listener.onBondState(
                            state = step.bondState ?: BlueNoxDeviceCallbacks.BlueNoxBondState.BONDED,
                            detail = step.detail,
                        )
                    }

                    BlueNoxMockAction.FAILURE -> {
                        listener.onFailure(
                            reason = step.failureReason ?: BlueNoxDeviceCallbacks.BlueNoxFailureReason.OPERATION_START_FAILED,
                            detail = step.detail.ifBlank { "Mocked failure" },
                        )
                    }
                }
            }
            pending += task
            handler.postDelayed(task, accumulatedDelay)
        }
    }
}

