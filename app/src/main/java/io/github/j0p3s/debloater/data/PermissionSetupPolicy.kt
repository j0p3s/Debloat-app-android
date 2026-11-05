package io.github.j0p3s.debloater.data

object PermissionSetupPolicy {
    private val component = Regex("[A-Za-z][A-Za-z0-9_.]*/[A-Za-z0-9_.$]+")
    /** Preserve all existing services; reject shell metacharacters and malformed settings. */
    fun addService(current: String, service: String): String {
        require(component.matches(service))
        val existing = if(current.trim() in setOf("", "null")) emptyList() else current.trim().split(':').filter { it.isNotBlank() }
        require(existing.all { component.matches(it) }) { "Invalid accessibility settings; use manual setup" }
        fun expanded(value: String): String {
            val (pkg, cls) = value.split('/')
            return "$pkg/" + if(cls.startsWith('.')) pkg + cls else cls
        }
        return (if(existing.any { expanded(it) == expanded(service) }) existing else existing + service).distinct().joinToString(":")
    }
}
