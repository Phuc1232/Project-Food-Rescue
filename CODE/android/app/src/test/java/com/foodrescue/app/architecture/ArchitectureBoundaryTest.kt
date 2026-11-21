package com.foodrescue.app.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guard the package boundaries documented in CODE/readme.md. */
class ArchitectureBoundaryTest {
    private val root = listOf(File("src/main/java/com/foodrescue/app"), File("app/src/main/java/com/foodrescue/app"))
        .first { it.isDirectory }
    private val sources = root.walkTopDown().filter { it.extension == "kt" }.toList()

    @Test fun `packages match their source folders`() {
        val violations = sources.mapNotNull { file ->
            val expected = "com.foodrescue.app." + requireNotNull(file.parentFile).relativeTo(root).invariantSeparatorsPath.replace('/', '.')
            val actual = Regex("(?m)^package ([\\w.]+)").find(file.readText())?.groupValues?.get(1)
            if (actual == expected.trimEnd('.')) null else file.name
        }
        assertTrue("Wrong source package: $violations", violations.isEmpty())
    }

    @Test fun `layers and features respect dependency direction`() {
        val violations = mutableListOf<String>()
        sources.forEach { file ->
            val relative = file.relativeTo(root).invariantSeparatorsPath
            val feature = relative.split('/').takeIf { it.first() == "feature" }?.get(1)
            val text = file.readText()
            val imports = (Regex("(?m)^import ([\\w.*]+)").findAll(text).map { it.groupValues[1] } +
                Regex("\\bcom\\.foodrescue\\.app\\.[\\w.*]+").findAll(text).map { it.value }).distinct()
            imports.forEach { imported ->
                val forbidden = when {
                    relative.startsWith("core/") -> imported.matches(Regex("com\\.foodrescue\\.app\\.(app|dashboard|feature|di)\\..*"))
                    "/domain/" in relative -> imported.startsWith("android.") || imported.startsWith("androidx.") || ".presentation." in imported || ".data." in imported
                    "/data/" in relative -> ".presentation." in imported || imported.startsWith("com.foodrescue.app.dashboard.")
                    "/presentation/" in relative -> ".data." in imported || imported.startsWith("com.foodrescue.app.di.") ||
                        (imported.startsWith("com.foodrescue.app.feature.") && !imported.startsWith("com.foodrescue.app.feature.$feature.") &&
                            ".presentation." in imported && ".presentation.component." !in imported)
                    else -> false
                }
                if (forbidden) violations += "$relative imports $imported"
            }
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    @Test fun `discovery presentation contains neither catalog fixtures nor domain declarations`() {
        val forbidden = Regex("\\b(val catalogBoxes|data class CatalogBox|enum class BoxCategory|data class PriceRange|fun filterCatalog)\\b")
        val violations = sources.filter { "/feature/discovery/presentation/" in it.invariantSeparatorsPath }
            .filter { forbidden.containsMatchIn(it.readText()) }.map { it.name }
        assertTrue("Data/domain logic in presentation: $violations", violations.isEmpty())
    }
}
