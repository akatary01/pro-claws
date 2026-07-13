package com.vendistri.operations.features.tasks

internal object SharedTaskNotes {
    fun normalizedValue(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        return trimmed.ifBlank { null }
    }

    fun seed(tasks: List<VendiTask>): String {
        val seen = linkedSetOf<String>()
        tasks.forEach { task ->
            task.notes
                ?.lineSequence()
                ?.map(String::trim)
                ?.filter(String::isNotBlank)
                ?.forEach { seen.add(it) }
        }
        return seen.joinToString("\n")
    }
}
