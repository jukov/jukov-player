import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class ValidateReleaseSigningTask : DefaultTask() {
    @get:Input
    abstract val signingConfigured: Property<Boolean>

    @TaskAction
    fun validateSigning() {
        if (!signingConfigured.get()) {
            throw GradleException(
                "Release signing is not configured. Copy keystore.properties.example to " +
                    "keystore.properties and fill in all values, or set the JUKOV_RELEASE_* environment variables."
            )
        }
    }
}
