package com.romulus.mobile.data.downloads

import java.util.concurrent.ConcurrentHashMap

enum class QueueTaskCommand {
    PAUSE,
    CANCEL
}

class QueueCommandBus {
    private val commandsByTaskId = ConcurrentHashMap<String, QueueTaskCommand>()

    fun issue(taskId: String, command: QueueTaskCommand) {
        commandsByTaskId[taskId] = command
    }

    fun read(taskId: String): QueueTaskCommand? {
        return commandsByTaskId[taskId]
    }

    fun clear(taskId: String) {
        commandsByTaskId.remove(taskId)
    }
}
