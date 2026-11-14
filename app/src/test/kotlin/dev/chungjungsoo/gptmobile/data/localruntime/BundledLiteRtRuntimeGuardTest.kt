package dev.chungjungsoo.gptmobile.data.localruntime

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the on-device stack to bundled LiteRT-LM.
 * Transitive Play services artifacts are rejected by the Gradle task
 * `assertNoPlayServicesDependencies`, which resolves the runtime classpath.
 */
class BundledLiteRtRuntimeGuardTest {

    @Test
    fun `local runtime source calls LiteRT-LM and not Play services or Interpreter`() {
        val source = stripComments(
            readRepoFile(
                "app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/localruntime/LocalRuntimeImpl.kt"
            )
        )

        assertTrue(source.contains("import com.google.ai.edge.litertlm.Engine"))
        assertTrue(source.contains("import com.google.ai.edge.litertlm.Backend"))
        assertTrue(source.contains("Backend.GPU()"))
        assertTrue(source.contains("Backend.CPU()"))
        assertTrue(source.contains("Backend.NPU("))
        assertFalse(source.contains("com.google.android.gms"))
        assertFalse(source.contains("play-services-litert"))
        assertFalse(source.contains("play-services-tflite"))
        assertFalse(source.contains("CompiledModel"))
        assertFalse(source.contains("Interpreter"))
        assertFalse(source.contains("GpuDelegate"))
        assertFalse(source.contains("NnApiDelegate"))
    }

    @Test
    fun `version catalog pins bundled LiteRT-LM and declares no Play services artifact`() {
        val catalog = stripComments(readRepoFile("gradle/libs.versions.toml"))

        assertTrue(catalog.contains("com.google.ai.edge.litertlm"))
        assertTrue(catalog.contains("litertlm-android"))
        assertFalse(catalog.contains("com.google.android.gms"))
        assertFalse(catalog.contains("play-services-litert"))
        assertFalse(catalog.contains("play-services-tflite"))
        assertFalse(catalog.contains("org.tensorflow"))
    }

    @Test
    fun `app dependencies declare bundled LiteRT-LM and no Play services coordinate`() {
        val script = stripComments(readRepoFile("app/build.gradle.kts"))
        val dependencyLines = script.lineSequence()
            .map { it.trim() }
            .filter { line ->
                line.startsWith("implementation(") ||
                    line.startsWith("api(") ||
                    line.startsWith("compileOnly(") ||
                    line.startsWith("runtimeOnly(")
            }
            .toList()

        assertTrue(dependencyLines.any { it.contains("libs.litertlm") })
        assertFalse(dependencyLines.any { it.contains("com.google.android.gms") })
        assertFalse(dependencyLines.any { it.contains("play-services") })
        assertFalse(dependencyLines.any { it.contains("org.tensorflow") })
    }

    private fun readRepoFile(relativePath: String): String {
        val candidates = listOf(
            File(relativePath),
            File("..", relativePath),
            File("../..", relativePath)
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Could not find $relativePath from ${File(".").absolutePath}")
        return file.readText()
    }

    private fun stripComments(source: String): String {
        val withoutBlocks = BLOCK_COMMENT.replace(source, "")
        return withoutBlocks.lineSequence()
            .map { line -> line.substringBefore("//") }
            .joinToString("\n")
    }

    private companion object {
        val BLOCK_COMMENT = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
    }
}
