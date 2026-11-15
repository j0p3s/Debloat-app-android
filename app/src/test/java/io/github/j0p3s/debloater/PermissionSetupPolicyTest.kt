package io.github.j0p3s.debloater
import io.github.j0p3s.debloater.data.PermissionSetupPolicy
import org.junit.Assert.*
import org.junit.Test
class PermissionSetupPolicyTest {
    private val service = "io.github.j0p3s.debloater/io.github.j0p3s.debloater.cache.SettingsCacheService"
    @Test fun preservesOtherServicesAndDoesNotDuplicateOwnEntry() {
        val other = "com.example.reader/.TalkBack"
        assertEquals("$other:$service", PermissionSetupPolicy.addService(other, service))
        assertEquals("$other:$service", PermissionSetupPolicy.addService("$other:$service", service))
        val short = "io.github.j0p3s.debloater/.cache.SettingsCacheService"
        assertEquals(short, PermissionSetupPolicy.addService(short, service))
        assertEquals(service, PermissionSetupPolicy.addService("null", service))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsShellMetacharactersWithoutChangingServices() {
        PermissionSetupPolicy.addService("com.other/.Reader'; touch /tmp/file", service)
    }
}
