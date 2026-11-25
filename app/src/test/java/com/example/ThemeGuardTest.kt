package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Plain JVM guard tests preventing hardcoded colors, UI string literals,
 * and brand/launcher/window background drift from creeping back in.
 */
class ThemeGuardTest {

    private fun mainDir(): File {
        return when {
            File("src/main").isDirectory -> File("src/main")
            File("app/src/main").isDirectory -> File("app/src/main")
            else -> error("Cannot locate src/main directory from ${File(".").absolutePath}")
        }
    }

    private fun normalizePath(file: File): String = file.path.replace(File.separatorChar, '/')

    @Test
    fun noColorLiteralsOutsideTheme() {
        val main = mainDir()
        val colorRegex = Regex(
            """Color\s*\(\s*0[xX]|Color\.parseColor|Color\.(Red|Blue|Green|Yellow|Cyan|Magenta|Gray|LightGray|DarkGray|Black|White)\b|#[0-9A-Fa-f]{6,8}"""
        )
        val allowedXmlPaths = setOf(
            "res/values/colors.xml",
            "res/values/ic_launcher_colors.xml"
        )
        val violations = mutableListOf<String>()

        main.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
            .forEach { file ->
                val rel = normalizePath(file.relativeTo(main))
                val isExcluded = rel.startsWith("java/com/example/ui/theme/") || rel in allowedXmlPaths
                if (isExcluded) return@forEach

                file.readLines().forEachIndexed { idx, line ->
                    if (colorRegex.containsMatchIn(line)) {
                        violations += "$rel:${idx + 1}: ${line.trim()}"
                    }
                }
            }

        assertTrue(
            "Found hardcoded color literals outside ui/theme/:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun noStringLiteralsInUi() {
        val main = mainDir()
        val uiLiteralRegex = Regex(
            """\bText\s*\(\s*"[^"]|contentDescription\s*=\s*"[^"]|\btitle\s*=\s*"[^"]|\bplaceholder\s*=\s*"[^"]"""
        )
        val enumLabelRegex = Regex("""val\s+(label|shortLabel)\s*:\s*String""")
        val violations = mutableListOf<String>()

        val uiAndVmDirs = listOf(
            File(main, "java/com/example/ui"),
            File(main, "java/com/example/data/model")
        )
        uiAndVmDirs.filter { it.isDirectory }.forEach { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val rel = normalizePath(file.relativeTo(main))
                    if (rel.startsWith("java/com/example/ui/theme/")) return@forEach

                    file.readLines().forEachIndexed { idx, line ->
                        if (uiLiteralRegex.containsMatchIn(line)) {
                            violations += "$rel:${idx + 1}: ${line.trim()}"
                        }
                        if (rel == "java/com/example/data/model/Enums.kt" && enumLabelRegex.containsMatchIn(line)) {
                            violations += "$rel:${idx + 1}: ${line.trim()}"
                        }
                    }
                }
        }

        assertTrue(
            "Found hardcoded UI or enum string literals:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun launcherColorMatchesBrand() {
        val main = mainDir()
        val brandText = File(main, "java/com/example/ui/theme/Brand.kt").readText()
        val launcherText = File(main, "res/values/ic_launcher_colors.xml").readText()

        val brandAccentHex = Regex("""val\s+accentLight\s*=\s*Color\(\s*0[xX]([0-9A-Fa-f]{8})\s*\)""")
            .find(brandText)?.groupValues?.get(1)?.uppercase()
            ?: error("Could not find Brand.accentLight in Brand.kt")
        val brandRgb = brandAccentHex.takeLast(6)

        val launcherBgHex = Regex("""<color\s+name="ic_launcher_background">\s*#([0-9A-Fa-f]{6,8})\s*</color>""")
            .find(launcherText)?.groupValues?.get(1)?.uppercase()
            ?: error("Could not find ic_launcher_background in ic_launcher_colors.xml")
        val launcherRgb = launcherBgHex.takeLast(6)

        assertEquals("ic_launcher_background must match Brand.accentLight", brandRgb, launcherRgb)
    }

    @Test
    fun windowBackgroundMatchesTheme() {
        val main = mainDir()
        val colorKt = File(main, "java/com/example/ui/theme/Color.kt").readText()
        val colorsXml = File(main, "res/values/colors.xml").readText()

        val windowLight = Regex("""<color\s+name="window_bg_light">\s*#([0-9A-Fa-f]{8})\s*</color>""")
            .find(colorsXml)?.groupValues?.get(1)?.uppercase()
            ?: error("Could not find window_bg_light in colors.xml")
        val windowDark = Regex("""<color\s+name="window_bg_dark">\s*#([0-9A-Fa-f]{8})\s*</color>""")
            .find(colorsXml)?.groupValues?.get(1)?.uppercase()
            ?: error("Could not find window_bg_dark in colors.xml")

        val lightSchemeBlock = colorKt.substringAfter("internal fun lightScheme").substringBefore("internal fun darkScheme")
        val darkSchemeBlock = colorKt.substringAfter("internal fun darkScheme").substringBefore("@Immutable")

        val lightBg = if (Regex("""background\s*=\s*Color\.White""").containsMatchIn(lightSchemeBlock)) {
            "FFFFFFFF"
        } else {
            Regex("""background\s*=\s*Color\(\s*0[xX]([0-9A-Fa-f]{8})\s*\)""")
                .find(lightSchemeBlock)?.groupValues?.get(1)?.uppercase()
                ?: error("Could not parse lightScheme background in Color.kt")
        }
        val darkBg = Regex("""background\s*=\s*Color\(\s*0[xX]([0-9A-Fa-f]{8})\s*\)""")
            .find(darkSchemeBlock)?.groupValues?.get(1)?.uppercase()
            ?: error("Could not parse darkScheme background in Color.kt")

        assertEquals("window_bg_light must match lightScheme background", lightBg, windowLight)
        assertEquals("window_bg_dark must match darkScheme background", darkBg, windowDark)
    }

    @Test
    fun appNameMatchesBrand() {
        val main = mainDir()
        val brandText = File(main, "java/com/example/ui/theme/Brand.kt").readText()
        val stringsXml = File(main, "res/values/strings.xml").readText()

        val strong = Regex("""const\s+val\s+NAME_STRONG\s*=\s*"([^"]+)"""")
            .find(brandText)?.groupValues?.get(1)
            ?: error("Could not find NAME_STRONG in Brand.kt")
        val light = Regex("""const\s+val\s+NAME_LIGHT\s*=\s*"([^"]+)"""")
            .find(brandText)?.groupValues?.get(1)
            ?: error("Could not find NAME_LIGHT in Brand.kt")
        val expectedName = "$strong$light"

        val actualAppName = Regex("""<string\s+name="app_name">([^<]+)</string>""")
            .find(stringsXml)?.groupValues?.get(1)
            ?: error("Could not find app_name in strings.xml")

        assertEquals("app_name in strings.xml must match Brand.NAME", expectedName, actualAppName)
    }
}
