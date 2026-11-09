package com.dengao.noticetodo.data.local

import com.dengao.noticetodo.domain.model.Priority
import com.dengao.noticetodo.domain.model.Task
import com.dengao.noticetodo.domain.model.TaskSource

fun TaskEntity.toDomain(): Task = Task(
    id = id,
    title = title,
    startTime = startTime,
    ddl = ddl,
    courseTag = courseTag,
    priority = Priority.of(priority),
    source = TaskSource.of(source),
    rawText = rawText,
    isDone = isDone,
    createdAt = createdAt,
    completedAt = completedAt,
    notificationKey = notificationKey
)

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title,
    startTime = startTime,
    ddl = ddl,
    courseTag = courseTag,
    priority = priority.value,
    source = source.name,
    rawText = rawText,
    isDone = isDone,
    createdAt = createdAt,
    completedAt = completedAt,
    notificationKey = notificationKey
)
