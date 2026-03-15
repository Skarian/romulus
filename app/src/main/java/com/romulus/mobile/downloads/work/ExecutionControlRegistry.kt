@file:Suppress("ClassSignature")

package com.romulus.mobile.downloads.work

import com.romulus.mobile.downloads.attempts.ControlHandle
import com.romulus.mobile.downloads.queue.ControlSignal
import com.romulus.mobile.downloads.queue.TaskId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

internal class ExecutionControlRegistry {
    private val controls = mutableMapOf<TaskId, MutableStateFlow<ControlSignal>>()

    fun register(taskId: TaskId): ControlHandle {
        val state = synchronized(controls) {
            controls.getOrPut(taskId) { MutableStateFlow(ControlSignal.NONE) }
        }
        return RegisteredControlHandle(state)
    }

    fun unregister(taskId: TaskId) {
        synchronized(controls) {
            controls.remove(taskId)
        }
    }

    fun signal(taskId: TaskId, signal: ControlSignal): Result<Unit> {
        val state = synchronized(controls) {
            controls[taskId]
        } ?: return Result.failure(IllegalStateException("Task ${taskId.value} is not running"))
        state.update { current -> current.escalateTo(signal) }
        return Result.success(Unit)
    }

    fun signalIfRegistered(taskId: TaskId, signal: ControlSignal): Result<Unit> {
        val state = synchronized(controls) {
            controls[taskId]
        } ?: return Result.success(Unit)
        state.update { current -> current.escalateTo(signal) }
        return Result.success(Unit)
    }
}

private class RegisteredControlHandle(private val state: MutableStateFlow<ControlSignal>) :
    ControlHandle {
    override suspend fun awaitSignal(): ControlSignal =
        state.first { signal -> signal != ControlSignal.NONE }

    override fun current(): ControlSignal = state.value
}

private fun ControlSignal.escalateTo(signal: ControlSignal): ControlSignal = when {
    this == ControlSignal.CANCEL || signal == ControlSignal.CANCEL -> ControlSignal.CANCEL
    this == ControlSignal.PAUSE || signal == ControlSignal.PAUSE -> ControlSignal.PAUSE
    else -> ControlSignal.NONE
}
