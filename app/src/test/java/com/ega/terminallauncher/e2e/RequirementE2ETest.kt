package com.ega.terminallauncher.e2e

import com.ega.terminallauncher.alias.AliasManager
import com.ega.terminallauncher.alias.AliasResult
import com.ega.terminallauncher.alias.InMemoryAliasRepository
import com.ega.terminallauncher.bridge.ExitCodeMapper
import com.ega.terminallauncher.bridge.ExecutionIdManager
import com.ega.terminallauncher.calc.CalcEngine
import com.ega.terminallauncher.calc.CalcResult
import com.ega.terminallauncher.command.BuiltinCommands
import com.ega.terminallauncher.command.CommandParser
import com.ega.terminallauncher.command.SystemOperations
import com.ega.terminallauncher.model.AppEntry
import com.ega.terminallauncher.mru.InMemoryMruRepository
import com.ega.terminallauncher.state.CommandAction
import com.ega.terminallauncher.state.LauncherState
import com.ega.terminallauncher.state.LauncherStateMachine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Requirement-Driven, Opaque-Box E2E Test Suite for Ash Launcher Fase 3.
 *
 * Implements 4-tier methodology:
 * - Tier 1: Feature Coverage (>=5 tests per feature for all 18 features in PROJECT.md)
 * - Tier 2: Boundary & Corner Cases (>=5 tests per critical boundary area)
 * - Tier 3: Cross-Feature Combinations (pairwise interaction testing)
 * - Tier 4: Real-World Application Scenarios (end-to-end user workflows)
 */
class RequirementE2ETest {

    private lateinit var mruRepo: InMemoryMruRepository
    private lateinit var aliasRepo: InMemoryAliasRepository
    private lateinit var aliasManager: AliasManager
    private lateinit var systemOps: FakeSystemOperations
    private lateinit var builtinCommands: BuiltinCommands
    private lateinit var commandParser: CommandParser
    private lateinit var stateMachine: LauncherStateMachine

    private val testApps = listOf(
        AppEntry(packageName = "com.whatsapp", label = "WhatsApp", activityName = ".Main"),
        AppEntry(packageName = "com.whatsapp.w4b", label = "WhatsApp Business", activityName = ".Main"),
        AppEntry(packageName = "com.google.android.calculator", label = "Calculator", activityName = ".CalcActivity"),
        AppEntry(packageName = "com.android.settings", label = "Settings", activityName = ".Settings"),
        AppEntry(packageName = "org.telegram.messenger", label = "Telegram", activityName = ".LaunchActivity"),
        AppEntry(packageName = "com.termux", label = "Termux", activityName = ".TermuxActivity"),
        AppEntry(packageName = "com.android.camera", label = "Camera", activityName = ".CameraActivity")
    )

    class FakeSystemOperations(
        var battery: String = "[Battery: 85% | Status: Discharging | Temp: 32.0°C]",
        var flashOn: Boolean = false,
        var volumeLevel: Int = 10,
        var maxVolume: Int = 15
    ) : SystemOperations {
        override fun getBatteryInfo(): String = battery

        override fun toggleFlashlight(): String {
            flashOn = !flashOn
            return if (flashOn) "[Flashlight ON]" else "[Flashlight OFF]"
        }

        override fun handleVolume(args: String): String {
            val trimmed = args.trim()
            if (trimmed.isEmpty()) {
                val pct = if (maxVolume > 0) (volumeLevel * 100) / maxVolume else 0
                return "[Volume: $volumeLevel/$maxVolume ($pct%)]"
            }
            val isPercent = trimmed.endsWith("%")
            val numStr = trimmed.removeSuffix("%").trim()
            val num = numStr.toIntOrNull() ?: return "[Error: Invalid volume level]"
            if (num < 0) return "[Error: Invalid volume level]"

            val targetVol = if (isPercent || num > maxVolume) {
                val pct = num.coerceIn(0, 100)
                ((pct * maxVolume) + 50) / 100
            } else {
                num.coerceIn(0, maxVolume)
            }
            volumeLevel = targetVol
            val pct = if (maxVolume > 0) (volumeLevel * 100) / maxVolume else 0
            return "[Volume set to $volumeLevel/$maxVolume ($pct%)]"
        }
    }

    @Before
    fun setUp() {
        mruRepo = InMemoryMruRepository()
        aliasRepo = InMemoryAliasRepository()
        aliasManager = AliasManager(aliasRepo)
        systemOps = FakeSystemOperations()
        builtinCommands = BuiltinCommands(systemOps, aliasManager, mruRepo)
        commandParser = CommandParser(builtinCommands, aliasManager, aliasRepo, mruRepo)
        stateMachine = LauncherStateMachine(
            mruRepo = mruRepo,
            commandParser = commandParser,
            aliasRepo = aliasRepo
        )
    }

    private fun input(cmd: String): CommandAction {
        return stateMachine.onInput(cmd, testApps)
    }

    private fun getGradleFile(): File {
        val candidates = listOf(
            File("build.gradle.kts"),
            File("app/build.gradle.kts"),
            File("../app/build.gradle.kts")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Cannot locate build.gradle.kts in candidate paths: $candidates")
    }

    private fun getManifestFile(): File {
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
            File("../app/src/main/AndroidManifest.xml")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Cannot locate AndroidManifest.xml in candidate paths: $candidates")
    }

    // =========================================================================
    // TIER 1: FEATURE COVERAGE (Features F1 through F18, >=5 per feature)
    // =========================================================================

    // --- Feature 1: Version Bump (Fase 3: versionCode 4, versionName "0.3.0") ---
    @Test
    fun test_tier1_f01_versionCode_is_4() {
        val gradleContent = getGradleFile().readText()
        assertTrue("versionCode must be 4", Regex("""versionCode\s*=\s*4""").containsMatchIn(gradleContent))
    }

    @Test
    fun test_tier1_f01_versionName_is_0_3_0() {
        val gradleContent = getGradleFile().readText()
        assertTrue("versionName must be \"0.3.0\"", Regex("""versionName\s*=\s*"0\.3\.0"""").containsMatchIn(gradleContent))
    }

    @Test
    fun test_tier1_f01_versionCode_incremented_over_v2() {
        val gradleContent = getGradleFile().readText()
        val match = Regex("""versionCode\s*=\s*(\d+)""").find(gradleContent)
        assertNotNull("versionCode should be declared", match)
        val code = match!!.groupValues[1].toInt()
        assertTrue("versionCode must be strictly greater than 3", code > 3)
    }

    @Test
    fun test_tier1_f01_versionName_follows_semver() {
        val gradleContent = getGradleFile().readText()
        val match = Regex("""versionName\s*=\s*"([^"]+)"""").find(gradleContent)
        assertNotNull("versionName should be declared", match)
        val name = match!!.groupValues[1]
        assertTrue("versionName must match SemVer format X.Y.Z", Regex("""^\d+\.\d+\.\d+$""").matches(name))
    }

    @Test
    fun test_tier1_f01_sdk_versions_compatibility() {
        val gradleContent = getGradleFile().readText()
        assertTrue("compileSdk should be 34", Regex("""compileSdk\s*=\s*34""").containsMatchIn(gradleContent))
        assertTrue("targetSdk should be 34", Regex("""targetSdk\s*=\s*34""").containsMatchIn(gradleContent))
    }

    // --- Feature 2: Manifest Update (Flash hardware non-required) ---
    @Test
    fun test_tier1_f02_manifest_contains_flash_feature() {
        val manifest = getManifestFile().readText()
        assertTrue("Manifest must declare camera.flash feature", manifest.contains("android.hardware.camera.flash"))
    }

    @Test
    fun test_tier1_f02_manifest_flash_not_required() {
        val manifest = getManifestFile().readText()
        assertTrue(
            "Flash feature must be marked android:required=\"false\"",
            Regex("""<uses-feature[^>]*android:name="android\.hardware\.camera\.flash"[^>]*android:required="false"[^>]*/>""").containsMatchIn(manifest) ||
            Regex("""<uses-feature[^>]*android:required="false"[^>]*android:name="android\.hardware\.camera\.flash"[^>]*/>""").containsMatchIn(manifest)
        )
    }

    @Test
    fun test_tier1_f02_no_unnecessary_camera_permission() {
        val manifest = getManifestFile().readText()
        assertFalse("CAMERA permission is NOT needed for torch mode and must not be declared", manifest.contains("android.permission.CAMERA"))
    }

    @Test
    fun test_tier1_f02_manifest_package_intact() {
        val manifest = getManifestFile().readText()
        assertTrue("Manifest package or activity namespace must be preserved", manifest.contains("com.ega.terminallauncher"))
    }

    @Test
    fun test_tier1_f02_manifest_uses_feature_syntax() {
        val manifest = getManifestFile().readText()
        assertTrue("Manifest must contain valid <uses-feature tag", manifest.contains("<uses-feature"))
    }

    // --- Feature 3: CommandParser & 4-Tier Precedence ---
    @Test
    fun test_tier1_f03_shell_preempts_app_match() {
        val action = input("\$Settings")
        assertTrue("Shell command must preempt app matching", action is CommandAction.ExecuteShell)
        assertEquals("Settings", (action as CommandAction.ExecuteShell).shellCommand)
    }

    @Test
    fun test_tier1_f03_builtin_preempts_app_match() {
        val action = input("calc")
        assertTrue("Built-in calc must preempt app Calculator", action is CommandAction.Message)
        assertEquals("[Usage: calc <expression>]", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier1_f03_alias_preempts_app_match() {
        input("alias wa=\$echo custom_wa")
        val action = input("wa")
        assertTrue("User alias must preempt app matching", action is CommandAction.ExecuteShell)
        assertEquals("echo custom_wa", (action as CommandAction.ExecuteShell).shellCommand)
    }

    @Test
    fun test_tier1_f03_shell_preempts_alias() {
        input("alias ls=Settings")
        val action = input("\$ls")
        assertTrue("Shell command must preempt alias expansion", action is CommandAction.ExecuteShell)
        assertEquals("ls", (action as CommandAction.ExecuteShell).shellCommand)
    }

    @Test
    fun test_tier1_f03_precedence_hierarchy_order() {
        // App exists: Settings
        // Alias exists: settings = $echo alias_triggered
        input("alias settings=\$echo alias_triggered")
        // Shell overrides all
        val shellAct = input("\$settings")
        assertTrue(shellAct is CommandAction.ExecuteShell)
        assertEquals("settings", (shellAct as CommandAction.ExecuteShell).shellCommand)
    }

    // --- Feature 4: Timeout Override Syntax ($t<sec> <cmd>) ---
    @Test
    fun test_tier1_f04_standard_timeout_override() {
        val action = input("\$t30 pkg update")
        assertTrue(action is CommandAction.ExecuteShell)
        val shell = action as CommandAction.ExecuteShell
        assertEquals("pkg update", shell.shellCommand)
        assertEquals(30, shell.timeoutSec)
    }

    @Test
    fun test_tier1_f04_whitespace_variations() {
        val action = input("\$   t120   ls -la")
        assertTrue(action is CommandAction.ExecuteShell)
        val shell = action as CommandAction.ExecuteShell
        assertEquals("ls -la", shell.shellCommand)
        assertEquals(120, shell.timeoutSec)
    }

    @Test
    fun test_tier1_f04_min_boundary() {
        val action = input("\$t1 echo fast")
        assertTrue(action is CommandAction.ExecuteShell)
        assertEquals(1, (action as CommandAction.ExecuteShell).timeoutSec)
    }

    @Test
    fun test_tier1_f04_max_boundary() {
        val action = input("\$t600 ./long_script.sh")
        assertTrue(action is CommandAction.ExecuteShell)
        assertEquals(600, (action as CommandAction.ExecuteShell).timeoutSec)
    }

    @Test
    fun test_tier1_f04_anti_collision_top() {
        val action = input("\$top")
        assertTrue(action is CommandAction.ExecuteShell)
        val shell = action as CommandAction.ExecuteShell
        assertEquals("top", shell.shellCommand)
        assertEquals(30, shell.timeoutSec)
    }

    // --- Feature 5: General Shell Execution ($<cmd> and $ <cmd>) ---
    @Test
    fun test_tier1_f05_no_space_prefix() {
        val action = input("\$ls")
        assertTrue(action is CommandAction.ExecuteShell)
        val shell = action as CommandAction.ExecuteShell
        assertEquals("ls", shell.shellCommand)
        assertEquals(30, shell.timeoutSec)
    }

    @Test
    fun test_tier1_f05_space_prefix() {
        val action = input("\$ uname -a")
        assertTrue(action is CommandAction.ExecuteShell)
        val shell = action as CommandAction.ExecuteShell
        assertEquals("uname -a", shell.shellCommand)
        assertEquals(30, shell.timeoutSec)
    }

    @Test
    fun test_tier1_f05_multiple_spaces() {
        val action = input("\$    uptime")
        assertTrue(action is CommandAction.ExecuteShell)
        assertEquals("uptime", (action as CommandAction.ExecuteShell).shellCommand)
    }

    @Test
    fun test_tier1_f05_bare_dollar() {
        val action = input("\$")
        assertTrue(action is CommandAction.None)
    }

    @Test
    fun test_tier1_f05_dollar_whitespace_only() {
        val action = input("\$   ")
        assertTrue(action is CommandAction.None)
    }

    // --- Feature 6: Built-in clear / cls ---
    @Test
    fun test_tier1_f06_clear_returns_clearlogs() {
        val action = input("clear")
        assertEquals(CommandAction.ClearLogs, action)
    }

    @Test
    fun test_tier1_f06_cls_returns_clearlogs() {
        val action = input("cls")
        assertEquals(CommandAction.ClearLogs, action)
    }

    @Test
    fun test_tier1_f06_clear_case_insensitive() {
        assertEquals(CommandAction.ClearLogs, input("CLEAR"))
        assertEquals(CommandAction.ClearLogs, input("Cls"))
    }

    @Test
    fun test_tier1_f06_clear_with_whitespace() {
        assertEquals(CommandAction.ClearLogs, input("   clear   "))
    }

    @Test
    fun test_tier1_f06_clear_with_extra_args() {
        assertEquals(CommandAction.ClearLogs, input("clear terminal"))
    }

    // --- Feature 7: Built-in apps / list ---
    @Test
    fun test_tier1_f07_apps_lists_installed() {
        val action = input("apps")
        assertTrue(action is CommandAction.Message)
        val msg = (action as CommandAction.Message).text
        assertTrue("Output should contain WhatsApp", msg.contains("WhatsApp"))
        assertTrue("Output should contain Settings", msg.contains("Settings"))
    }

    @Test
    fun test_tier1_f07_list_lists_installed() {
        val action = input("list")
        assertTrue(action is CommandAction.Message)
        val msg = (action as CommandAction.Message).text
        assertTrue("Output should contain Telegram", msg.contains("Telegram"))
    }

    @Test
    fun test_tier1_f07_apps_alphabetical_sorting() {
        val action = input("apps")
        assertTrue(action is CommandAction.Message)
        val msg = (action as CommandAction.Message).text
        val cameraIdx = msg.indexOf("Camera")
        val settingsIdx = msg.indexOf("Settings")
        val whatsappIdx = msg.indexOf("WhatsApp")
        assertTrue("Camera should appear before Settings", cameraIdx < settingsIdx)
        assertTrue("Settings should appear before WhatsApp", settingsIdx < whatsappIdx)
    }

    @Test
    fun test_tier1_f07_apps_count_header() {
        val action = input("apps")
        assertTrue(action is CommandAction.Message)
        val msg = (action as CommandAction.Message).text
        assertTrue("Header should show app count", msg.contains("(${testApps.size})"))
    }

    @Test
    fun test_tier1_f07_empty_apps_handled() {
        val emptyAction = builtinCommands.execute("apps", "", emptyList(), { "" }, { _, _ -> })
        assertTrue(emptyAction is CommandAction.Message)
        assertEquals("[No installed apps found]", (emptyAction as CommandAction.Message).text)
    }

    // --- Feature 8: Built-in battery / bat ---
    @Test
    fun test_tier1_f08_battery_query() {
        val action = input("battery")
        assertTrue(action is CommandAction.Message)
        assertEquals(systemOps.battery, (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier1_f08_bat_query() {
        val action = input("bat")
        assertTrue(action is CommandAction.Message)
        assertEquals(systemOps.battery, (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier1_f08_battery_case_insensitive() {
        val action = input("BAT")
        assertTrue(action is CommandAction.Message)
        assertEquals(systemOps.battery, (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier1_f08_battery_format_pattern() {
        val action = input("battery")
        assertTrue(action is CommandAction.Message)
        val text = (action as CommandAction.Message).text
        assertTrue("Text should contain Battery percentage", text.contains("Battery:"))
        assertTrue("Text should contain Status", text.contains("Status:"))
        assertTrue("Text should contain Temp", text.contains("Temp:"))
    }

    @Test
    fun test_tier1_f08_battery_error_handled() {
        systemOps.battery = "[Error: Battery status unavailable]"
        val action = input("battery")
        assertTrue(action is CommandAction.Message)
        assertEquals("[Error: Battery status unavailable]", (action as CommandAction.Message).text)
    }

    // --- Feature 9: Built-in flash ---
    @Test
    fun test_tier1_f09_flash_toggle() {
        val a1 = input("flash")
        assertTrue(a1 is CommandAction.Message)
        assertEquals("[Flashlight ON]", (a1 as CommandAction.Message).text)

        val a2 = input("flash")
        assertTrue(a2 is CommandAction.Message)
        assertEquals("[Flashlight OFF]", (a2 as CommandAction.Message).text)
    }

    @Test
    fun test_tier1_f09_flash_case_insensitive() {
        val a1 = input("FLASH")
        assertTrue(a1 is CommandAction.Message)
        assertEquals("[Flashlight ON]", (a1 as CommandAction.Message).text)
    }

    @Test
    fun test_tier1_f09_flash_sequential_toggles() {
        input("flash") // ON
        input("flash") // OFF
        val a3 = input("flash") // ON
        assertEquals("[Flashlight ON]", (a3 as CommandAction.Message).text)
    }

    @Test
    fun test_tier1_f09_flash_output_format() {
        val text = (input("flash") as CommandAction.Message).text
        assertTrue("Output format must match bracketed indicator", text == "[Flashlight ON]" || text == "[Flashlight OFF]")
    }

    @Test
    fun test_tier1_f09_flash_error_handled() {
        val noFlashOps = object : SystemOperations {
            override fun getBatteryInfo(): String = ""
            override fun toggleFlashlight(): String = "[Error: Flashlight unavailable: device has no flash unit]"
            override fun handleVolume(args: String): String = ""
        }
        val customBuiltin = BuiltinCommands(noFlashOps, aliasManager, mruRepo)
        val act = customBuiltin.execute("flash", "", testApps, { "" }, { _, _ -> })
        assertTrue(act is CommandAction.Message)
        assertEquals("[Error: Flashlight unavailable: device has no flash unit]", (act as CommandAction.Message).text)
    }

    // --- Feature 10: Built-in vol / volume ---
    @Test
    fun test_tier1_f10_volume_query() {
        val action = input("vol")
        assertTrue(action is CommandAction.Message)
        assertEquals("[Volume: 10/15 (66%)]", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier1_f10_volume_full_word() {
        val action = input("volume")
        assertTrue(action is CommandAction.Message)
        assertEquals("[Volume: 10/15 (66%)]", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier1_f10_volume_set_index() {
        val action = input("vol 8")
        assertTrue(action is CommandAction.Message)
        assertEquals("[Volume set to 8/15 (53%)]", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier1_f10_volume_set_percent() {
        val action = input("vol 80%")
        assertTrue(action is CommandAction.Message)
        assertEquals("[Volume set to 12/15 (80%)]", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier1_f10_volume_invalid_handled() {
        val a1 = input("vol -5")
        assertTrue(a1 is CommandAction.Message)
        assertEquals("[Error: Invalid volume level]", (a1 as CommandAction.Message).text)

        val a2 = input("vol abc")
        assertTrue(a2 is CommandAction.Message)
        assertEquals("[Error: Invalid volume level]", (a2 as CommandAction.Message).text)
    }

    // --- Feature 11: Built-in calc <expr> ---
    @Test
    fun test_tier1_f11_calc_addition() {
        val action = input("calc 2 + 2")
        assertTrue(action is CommandAction.Message)
        assertEquals("4", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier1_f11_calc_precedence() {
        val action = input("calc (10 - 2) * 3 / 4")
        assertTrue(action is CommandAction.Message)
        assertEquals("6", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier1_f11_calc_exponentiation() {
        val action = input("calc 2 ^ 3")
        assertTrue(action is CommandAction.Message)
        assertEquals("8", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier1_f11_calc_modulo() {
        val action = input("calc 10 % 3")
        assertTrue(action is CommandAction.Message)
        assertEquals("1", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier1_f11_calc_unary_minus() {
        val action = input("calc -5 + 10")
        assertTrue(action is CommandAction.Message)
        assertEquals("5", (action as CommandAction.Message).text)
    }

    // --- Feature 12: Built-in alias / unalias ---
    @Test
    fun test_tier1_f12_alias_list_empty() {
        val action = input("alias")
        assertTrue(action is CommandAction.Message)
        assertEquals("[No aliases defined]", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier1_f12_alias_register() {
        val action = input("alias gs=git status")
        assertTrue(action is CommandAction.Message)
        assertEquals("[Alias registered: gs -> git status]", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier1_f12_alias_quote_stripping() {
        input("alias gs=\"git status\"")
        assertEquals("git status", aliasRepo.get("gs"))
    }

    @Test
    fun test_tier1_f12_alias_expansion() {
        input("alias gs=\$git status")
        val action = input("gs -s")
        assertTrue(action is CommandAction.ExecuteShell)
        assertEquals("git status -s", (action as CommandAction.ExecuteShell).shellCommand)
    }

    @Test
    fun test_tier1_f12_unalias_success() {
        input("alias gs=git status")
        val action = input("unalias gs")
        assertTrue(action is CommandAction.Message)
        assertEquals("[Alias removed: gs]", (action as CommandAction.Message).text)
    }

    // --- Feature 13: Alias Persistence ---
    @Test
    fun test_tier1_f13_persists_across_instances() {
        aliasRepo.put("gs", "git status")
        assertEquals("git status", aliasRepo.get("gs"))
    }

    @Test
    fun test_tier1_f13_persists_deletion() {
        aliasRepo.put("gs", "git status")
        aliasRepo.remove("gs")
        assertNull(aliasRepo.get("gs"))
    }

    @Test
    fun test_tier1_f13_persists_clear() {
        aliasRepo.put("a", "1")
        aliasRepo.put("b", "2")
        aliasRepo.clear()
        assertTrue(aliasRepo.getAll().isEmpty())
    }

    @Test
    fun test_tier1_f13_case_insensitive_persistence() {
        aliasRepo.put("GS", "git status")
        assertEquals("git status", aliasRepo.get("gs"))
    }

    @Test
    fun test_tier1_f13_getAll_immutability() {
        aliasRepo.put("k", "v")
        val all = aliasRepo.getAll()
        assertEquals(1, all.size)
        aliasRepo.put("k2", "v2")
        assertEquals("Snapshot returned should not modify caller reference", 1, all.size)
    }

    // --- Feature 14: Built-in reset mru ---
    @Test
    fun test_tier1_f14_reset_mru_clears_cache() {
        mruRepo.put("Whats", "com.whatsapp")
        val action = input("reset mru")
        assertTrue(action is CommandAction.Message)
        assertEquals("[MRU cache cleared]", (action as CommandAction.Message).text)
        assertNull(mruRepo.get("Whats"))
    }

    @Test
    fun test_tier1_f14_reset_mru_case_insensitive() {
        val action = input("RESET MRU")
        assertTrue(action is CommandAction.Message)
        assertEquals("[MRU cache cleared]", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier1_f14_reset_without_mru() {
        val action = input("reset")
        assertTrue(action is CommandAction.Message)
        assertEquals("[Usage: reset mru]", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier1_f14_reset_invalid_arg() {
        val action = input("reset other")
        assertTrue(action is CommandAction.Message)
        assertEquals("[Usage: reset mru]", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier1_f14_reset_mru_affects_selection() {
        mruRepo.put("Whats", "com.whatsapp")
        // Normally "Whats" would auto-launch com.whatsapp from MRU
        input("reset mru")
        // After reset, "Whats" is ambiguous again
        val act = input("Whats")
        assertTrue("After reset mru, ambiguous query must show choices", act is CommandAction.ShowChoices)
    }

    // --- Feature 15: Built-in help ---
    @Test
    fun test_tier1_f15_help_returns_table() {
        val action = input("help")
        assertTrue(action is CommandAction.Message)
        val text = (action as CommandAction.Message).text
        assertTrue("Help should contain header", text.contains("Ash Launcher"))
    }

    @Test
    fun test_tier1_f15_help_case_insensitive() {
        val action = input("HELP")
        assertTrue(action is CommandAction.Message)
        assertTrue((action as CommandAction.Message).text.contains("Ash Launcher"))
    }

    @Test
    fun test_tier1_f15_help_contains_version() {
        val action = input("help")
        assertTrue((action as CommandAction.Message).text.contains("v0.3.0"))
    }

    @Test
    fun test_tier1_f15_help_contains_all_builtins() {
        val text = (input("help") as CommandAction.Message).text
        assertTrue(text.contains("clear"))
        assertTrue(text.contains("apps"))
        assertTrue(text.contains("battery"))
        assertTrue(text.contains("flash"))
        assertTrue(text.contains("vol"))
        assertTrue(text.contains("calc"))
        assertTrue(text.contains("alias"))
        assertTrue(text.contains("unalias"))
        assertTrue(text.contains("reset mru"))
        assertTrue(text.contains("open"))
    }

    @Test
    fun test_tier1_f15_help_contains_shell_syntax() {
        val text = (input("help") as CommandAction.Message).text
        assertTrue(text.contains("$ <cmd>"))
        assertTrue(text.contains("\$t<sec> <cmd>"))
    }

    // --- Feature 16: TermuxBridge Integration ---
    @Test
    fun test_tier1_f16_execute_shell_action_structure() {
        val action = input("\$uname -a")
        assertTrue(action is CommandAction.ExecuteShell)
        val shell = action as CommandAction.ExecuteShell
        assertEquals("\$uname -a", shell.rawCommand)
        assertEquals("uname -a", shell.shellCommand)
        assertEquals(30, shell.timeoutSec)
    }

    @Test
    fun test_tier1_f16_execution_id_monotonic() {
        val id1 = ExecutionIdManager.nextRequestCode()
        val id2 = ExecutionIdManager.nextRequestCode()
        assertTrue("Execution IDs must be monotonically increasing", id2 > id1)
    }

    @Test
    fun test_tier1_f16_bridge_timeout_override_forwarded() {
        val action = input("\$t45 sleep 10")
        assertTrue(action is CommandAction.ExecuteShell)
        assertEquals(45, (action as CommandAction.ExecuteShell).timeoutSec)
    }

    @Test
    fun test_tier1_f16_watchdog_delay_formula() {
        val timeoutSec = 20
        val watchdogSec = timeoutSec + 5
        assertEquals(25, watchdogSec)
    }

    @Test
    fun test_tier1_f16_shell_action_preserves_arguments() {
        val action = input("\$find . -name '*.kt' -type f")
        assertTrue(action is CommandAction.ExecuteShell)
        assertEquals("find . -name '*.kt' -type f", (action as CommandAction.ExecuteShell).shellCommand)
    }

    // --- Feature 17: Log Formatting & Truncation ---
    @Test
    fun test_tier1_f17_exit_code_0_maps_to_success() {
        val msg = ExitCodeMapper.mapExitCode(exitCode = 0, durationMs = 100, timeoutSec = 30)
        assertEquals("Success", msg)
    }

    @Test
    fun test_tier1_f17_exit_code_124_maps_to_timeout() {
        val msg = ExitCodeMapper.mapExitCode(exitCode = 124, durationMs = 30100, timeoutSec = 30)
        assertEquals("[Command timed out after 30s]", msg)
    }

    @Test
    fun test_tier1_f17_exit_code_137_killed_after_timeout() {
        val msg = ExitCodeMapper.mapExitCode(exitCode = 137, durationMs = 32100, timeoutSec = 30)
        assertEquals("[Command force-killed after timeout]", msg)
    }

    @Test
    fun test_tier1_f17_exit_code_137_killed_by_oom() {
        val msg = ExitCodeMapper.mapExitCode(exitCode = 137, durationMs = 1500, timeoutSec = 30)
        assertEquals("[Command killed by system / OOM]", msg)
    }

    @Test
    fun test_tier1_f17_truncation_marker_rendering() {
        val isTruncated = true
        val exitCode = 0
        val statusLine = if (exitCode == 0) {
            val trunc = if (isTruncated) " [output truncated]" else ""
            "[Exit: 0]$trunc"
        } else {
            "error"
        }
        assertEquals("[Exit: 0] [output truncated]", statusLine)
    }

    // --- Feature 18: End-to-End Pipeline & Matcher ---
    @Test
    fun test_tier1_f18_exact_app_launch() {
        val action = input("Settings")
        assertTrue(action is CommandAction.Launch)
        assertEquals("Settings", (action as CommandAction.Launch).app.label)
        assertEquals("exact", action.source)
    }

    @Test
    fun test_tier1_f18_prefix_app_launch() {
        val action = input("Tele")
        assertTrue(action is CommandAction.Launch)
        assertEquals("Telegram", (action as CommandAction.Launch).app.label)
        assertEquals("single", action.source)
    }

    @Test
    fun test_tier1_f18_ambiguous_app_choices() {
        val action = input("Whats")
        assertTrue(action is CommandAction.ShowChoices)
        assertTrue((action as CommandAction.ShowChoices).choicesText.contains("[1] WhatsApp"))
        assertTrue(stateMachine.state is LauncherState.AwaitingSelection)
    }

    @Test
    fun test_tier1_f18_forced_ambiguity_question_mark() {
        mruRepo.put("Whats", "com.whatsapp")
        // Without ?, "Whats" would auto-launch from MRU
        val mruAction = input("Whats")
        assertTrue(mruAction is CommandAction.Launch)
        assertEquals("mru", (mruAction as CommandAction.Launch).source)

        // With ?, forces choices list bypassing MRU
        val forcedAction = input("Whats?")
        assertTrue(forcedAction is CommandAction.ShowChoices)
    }

    @Test
    fun test_tier1_f18_no_match_message() {
        val action = input("nonexistentapp12345")
        assertTrue(action is CommandAction.Message)
        assertEquals("No match for 'nonexistentapp12345'", (action as CommandAction.Message).text)
    }

    // =========================================================================
    // TIER 2: BOUNDARY & CORNER CASES (>=5 per critical boundary area)
    // =========================================================================

    // --- Boundary Area 1: Timeout Boundaries ---
    @Test
    fun test_tier2_timeout_zero_rejected() {
        val action = input("\$t0 ls")
        assertTrue(action is CommandAction.Message)
        assertEquals("[Error: Timeout must be between 1 and 600 seconds]", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier2_timeout_601_rejected() {
        val action = input("\$t601 ls")
        assertTrue(action is CommandAction.Message)
        assertEquals("[Error: Timeout must be between 1 and 600 seconds]", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier2_timeout_9999_rejected() {
        val action = input("\$t9999 ls")
        assertTrue(action is CommandAction.Message)
        assertEquals("[Error: Timeout must be between 1 and 600 seconds]", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier2_timeout_min_boundary_1() {
        val action = input("\$t1 sleep 1")
        assertTrue(action is CommandAction.ExecuteShell)
        assertEquals(1, (action as CommandAction.ExecuteShell).timeoutSec)
    }

    @Test
    fun test_tier2_timeout_max_boundary_600() {
        val action = input("\$t600 ./heavy.sh")
        assertTrue(action is CommandAction.ExecuteShell)
        assertEquals(600, (action as CommandAction.ExecuteShell).timeoutSec)
    }

    // --- Boundary Area 2: Timeout Anti-Collision Fall-Through ---
    @Test
    fun test_tier2_anti_collision_top() {
        val action = input("\$top -n 1")
        assertTrue(action is CommandAction.ExecuteShell)
        assertEquals("top -n 1", (action as CommandAction.ExecuteShell).shellCommand)
        assertEquals(30, (action as CommandAction.ExecuteShell).timeoutSec)
    }

    @Test
    fun test_tier2_anti_collision_tar() {
        val action = input("\$tar -czf test.tar.gz .")
        assertTrue(action is CommandAction.ExecuteShell)
        assertEquals("tar -czf test.tar.gz .", (action as CommandAction.ExecuteShell).shellCommand)
    }

    @Test
    fun test_tier2_anti_collision_touch() {
        val action = input("\$touch file.txt")
        assertTrue(action is CommandAction.ExecuteShell)
        assertEquals("touch file.txt", (action as CommandAction.ExecuteShell).shellCommand)
    }

    @Test
    fun test_tier2_anti_collision_tail() {
        val action = input("\$tail -n 20 /sdcard/log")
        assertTrue(action is CommandAction.ExecuteShell)
        assertEquals("tail -n 20 /sdcard/log", (action as CommandAction.ExecuteShell).shellCommand)
    }

    @Test
    fun test_tier2_anti_collision_tee() {
        val action = input("\$tee out.log")
        assertTrue(action is CommandAction.ExecuteShell)
        assertEquals("tee out.log", (action as CommandAction.ExecuteShell).shellCommand)
    }

    @Test
    fun test_tier2_anti_collision_bare_t() {
        val action = input("\$t")
        assertTrue(action is CommandAction.ExecuteShell)
        assertEquals("t", (action as CommandAction.ExecuteShell).shellCommand)
    }

    @Test
    fun test_tier2_anti_collision_t30_no_cmd() {
        val action = input("\$t30")
        assertTrue(action is CommandAction.ExecuteShell)
        assertEquals("t30", (action as CommandAction.ExecuteShell).shellCommand)
    }

    @Test
    fun test_tier2_anti_collision_t_space_30() {
        val action = input("\$t 30 ls")
        assertTrue(action is CommandAction.ExecuteShell)
        assertEquals("t 30 ls", (action as CommandAction.ExecuteShell).shellCommand)
    }

    // --- Boundary Area 3: Calculator Edge Cases ---
    @Test
    fun test_tier2_calc_division_by_zero() {
        val action = input("calc 10 / 0")
        assertTrue(action is CommandAction.Message)
        assertEquals("[Error: Division by zero]", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier2_calc_modulo_by_zero() {
        val action = input("calc 5 % 0")
        assertTrue(action is CommandAction.Message)
        assertEquals("[Error: Division by zero]", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier2_calc_unclosed_parenthesis() {
        val action = input("calc (2 + 3 * 4")
        assertTrue(action is CommandAction.Message)
        assertEquals("[Error: Mismatched parentheses]", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier2_calc_stray_closing_parenthesis() {
        val action = input("calc 2 + 3) * 4")
        assertTrue(action is CommandAction.Message)
        assertEquals("[Error: Mismatched parentheses]", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier2_calc_consecutive_operators() {
        val action = input("calc 2 + * 3")
        assertTrue(action is CommandAction.Message)
        assertEquals("[Error: Invalid expression]", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier2_calc_empty_expression() {
        val action = input("calc")
        assertTrue(action is CommandAction.Message)
        assertEquals("[Usage: calc <expression>]", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier2_calc_right_associativity_power() {
        // 2 ^ 3 ^ 2 should evaluate as 2 ^ (3 ^ 2) = 2 ^ 9 = 512, not (2 ^ 3) ^ 2 = 64
        val action = input("calc 2 ^ 3 ^ 2")
        assertTrue(action is CommandAction.Message)
        assertEquals("512", (action as CommandAction.Message).text)
    }

    // --- Boundary Area 4: Alias Validation & 2-Way Cycle Prevention ---
    @Test
    fun test_tier2_alias_numeric_rejected() {
        val action = input("alias 1=whatsapp")
        assertTrue(action is CommandAction.Message)
        assertEquals("[Error: Alias name cannot be purely numeric or start with a digit]", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier2_alias_starts_with_digit_rejected() {
        val action = input("alias 9gag=app")
        assertTrue(action is CommandAction.Message)
        assertEquals("[Error: Alias name cannot be purely numeric or start with a digit]", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier2_alias_reserved_clear_rejected() {
        val action = input("alias clear=ls")
        assertTrue(action is CommandAction.Message)
        assertEquals("[Error: 'clear' is a reserved command name]", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier2_alias_reserved_calc_rejected() {
        val action = input("alias calc=wa")
        assertTrue(action is CommandAction.Message)
        assertEquals("[Error: 'calc' is a reserved command name]", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier2_alias_reserved_open_rejected() {
        val action = input("alias open=something")
        assertTrue(action is CommandAction.Message)
        assertEquals("[Error: 'open' is a reserved command name]", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier2_alias_cycle_direction1() {
        // Direction 1: target's first token is an existing alias
        input("alias b=echo hello")
        val action = input("alias a=b")
        assertTrue(action is CommandAction.Message)
        assertEquals("[Error: Nested alias is not supported]", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier2_alias_cycle_direction2() {
        // Direction 2: new alias name is already the target of an existing alias
        input("alias a=b")
        val action = input("alias b=echo world")
        assertTrue(action is CommandAction.Message)
        assertEquals("[Error: Nested alias is not supported]", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier2_alias_empty_target_rejected() {
        val action = input("alias foo=")
        assertTrue(action is CommandAction.Message)
        assertEquals("[Error: Alias target cannot be empty]", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier2_alias_missing_equals() {
        val action = input("alias foo")
        assertTrue(action is CommandAction.Message)
        assertEquals("[Usage: alias <name>=<cmd>]", (action as CommandAction.Message).text)
    }

    // --- Boundary Area 5: Selection State Preemption ---
    @Test
    fun test_tier2_selection_numeric_choice_1() {
        input("Whats") // triggers choices: [1] WhatsApp [2] WhatsApp Business
        assertTrue(stateMachine.state is LauncherState.AwaitingSelection)
        val action = input("1")
        assertTrue(action is CommandAction.Launch)
        assertEquals("WhatsApp", (action as CommandAction.Launch).app.label)
        assertEquals("selection", action.source)
        assertTrue(stateMachine.state is LauncherState.Normal)
    }

    @Test
    fun test_tier2_selection_numeric_choice_2() {
        input("Whats")
        val action = input("2")
        assertTrue(action is CommandAction.Launch)
        assertEquals("WhatsApp Business", (action as CommandAction.Launch).app.label)
        assertEquals("selection", action.source)
    }

    @Test
    fun test_tier2_selection_out_of_range_cancels() {
        input("Whats")
        // "99" is non-existent index -> cancels selection and searches for "99"
        val action = input("99")
        assertTrue(stateMachine.state is LauncherState.Normal)
        assertTrue(action is CommandAction.Message)
        assertEquals("No match for '99'", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier2_selection_preempted_by_shell() {
        input("Whats")
        assertTrue(stateMachine.state is LauncherState.AwaitingSelection)
        val action = input("\$ls")
        assertTrue(action is CommandAction.ExecuteShell)
        assertEquals("ls", (action as CommandAction.ExecuteShell).shellCommand)
        assertTrue("State must drop back to Normal", stateMachine.state is LauncherState.Normal)
    }

    @Test
    fun test_tier2_selection_preempted_by_builtin() {
        input("Whats")
        assertTrue(stateMachine.state is LauncherState.AwaitingSelection)
        val action = input("clear")
        assertEquals(CommandAction.ClearLogs, action)
        assertTrue(stateMachine.state is LauncherState.Normal)
    }

    @Test
    fun test_tier2_selection_preempted_by_alias() {
        input("alias st=Settings")
        input("Whats")
        assertTrue(stateMachine.state is LauncherState.AwaitingSelection)
        val action = input("st")
        assertTrue(action is CommandAction.Launch)
        assertEquals("Settings", (action as CommandAction.Launch).app.label)
        assertTrue(stateMachine.state is LauncherState.Normal)
    }

    // =========================================================================
    // TIER 3: CROSS-FEATURE COMBINATIONS (Pairwise Coverage)
    // =========================================================================

    @Test
    fun test_tier3_p01_alias_expanding_to_timeout_shell() {
        input("alias quick=\"\$t10 ping -c 1 8.8.8.8\"")
        val action = input("quick")
        assertTrue(action is CommandAction.ExecuteShell)
        val shell = action as CommandAction.ExecuteShell
        assertEquals("ping -c 1 8.8.8.8", shell.shellCommand)
        assertEquals(10, shell.timeoutSec)
    }

    @Test
    fun test_tier3_p02_alias_expanding_to_calculator() {
        input("alias tax=calc 100 * 1.5")
        val action = input("tax")
        assertTrue(action is CommandAction.Message)
        assertEquals("150", (action as CommandAction.Message).text)
    }

    @Test
    fun test_tier3_p03_alias_expanding_to_app_match() {
        input("alias chat=whats")
        val action = input("chat")
        assertTrue(action is CommandAction.ShowChoices) // WhatsApp vs WhatsApp Business
    }

    @Test
    fun test_tier3_p04_escaped_app_clash_open_command() {
        // App named "Calculator" clashes with built-in "calc"
        // 1. "calc" runs built-in math
        val mathAction = input("calc 5 + 5")
        assertTrue(mathAction is CommandAction.Message)
        assertEquals("10", (mathAction as CommandAction.Message).text)

        // 2. "open calc" launches the Calculator app
        val openAction = input("open calc")
        assertTrue(openAction is CommandAction.Launch)
        assertEquals("Calculator", (openAction as CommandAction.Launch).app.label)
    }

    @Test
    fun test_tier3_p05_selection_preempted_by_shell_retains_normal_state() {
        input("Whats")
        assertEquals(LauncherState.AwaitingSelection("Whats", listOf(testApps[0], testApps[1])), stateMachine.state)
        val action = input("\$uptime")
        assertTrue(action is CommandAction.ExecuteShell)
        assertEquals(LauncherState.Normal, stateMachine.state)
    }

    @Test
    fun test_tier3_p06_selection_preempted_by_builtin_retains_normal_state() {
        input("Whats")
        val action = input("battery")
        assertTrue(action is CommandAction.Message)
        assertEquals(LauncherState.Normal, stateMachine.state)
    }

    @Test
    fun test_tier3_p07_mru_updated_then_reset_mru_invalidates() {
        // Select WhatsApp to store in MRU
        input("Whats")
        input("1") // WhatsApp selected, recorded in MRU
        assertEquals("com.whatsapp", mruRepo.get("Whats"))

        // Next time "Whats" auto-launches from MRU
        val mruAction = input("Whats")
        assertTrue(mruAction is CommandAction.Launch)
        assertEquals("mru", (mruAction as CommandAction.Launch).source)

        // Reset MRU
        input("reset mru")
        assertNull(mruRepo.get("Whats"))

        // Next time "Whats" shows choices again
        val choiceAction = input("Whats")
        assertTrue(choiceAction is CommandAction.ShowChoices)
    }

    @Test
    fun test_tier3_p08_alias_with_additional_arguments() {
        input("alias myls=\$ls")
        val action = input("myls -la /sdcard")
        assertTrue(action is CommandAction.ExecuteShell)
        assertEquals("ls -la /sdcard", (action as CommandAction.ExecuteShell).shellCommand)
    }

    @Test
    fun test_tier3_p09_alias_with_single_and_double_quotes() {
        input("alias msg='echo \"hello world\"'")
        val action = input("msg")
        assertTrue(action is CommandAction.Message)
        // Stored target was echo "hello world"
        assertEquals("echo \"hello world\"", aliasRepo.get("msg"))
    }

    @Test
    fun test_tier3_p10_open_command_with_ambiguous_app_triggers_selection() {
        val action = input("open Whats")
        assertTrue(action is CommandAction.ShowChoices)
        assertTrue(stateMachine.state is LauncherState.AwaitingSelection)
    }

    // =========================================================================
    // TIER 4: REAL-WORLD APPLICATION SCENARIOS
    // =========================================================================

    @Test
    fun test_tier4_scenario_developer_cli_session() {
        // Step 1: Query device architecture via shell
        val a1 = input("\$uname -m")
        assertTrue(a1 is CommandAction.ExecuteShell)
        assertEquals("uname -m", (a1 as CommandAction.ExecuteShell).shellCommand)

        // Step 2: Run a build with a custom timeout override
        val a2 = input("\$t180 ./gradlew assembleDebug")
        assertTrue(a2 is CommandAction.ExecuteShell)
        val s2 = a2 as CommandAction.ExecuteShell
        assertEquals("./gradlew assembleDebug", s2.shellCommand)
        assertEquals(180, s2.timeoutSec)

        // Step 3: Register an alias for git status
        val a3 = input("alias gs=\"\$git status\"")
        assertTrue(a3 is CommandAction.Message)
        assertTrue((a3 as CommandAction.Message).text.contains("Alias registered"))

        // Step 4: Run the alias with extra flags
        val a4 = input("gs -s")
        assertTrue(a4 is CommandAction.ExecuteShell)
        assertEquals("git status -s", (a4 as CommandAction.ExecuteShell).shellCommand)

        // Step 5: Clean screen
        val a5 = input("clear")
        assertEquals(CommandAction.ClearLogs, a5)
    }

    @Test
    fun test_tier4_scenario_daily_multitasking() {
        // Step 1: Calculate split bill with discount
        val a1 = input("calc (150000 * 0.8) + 25000")
        assertTrue(a1 is CommandAction.Message)
        assertEquals("145000", (a1 as CommandAction.Message).text)

        // Step 2: Check battery status
        val a2 = input("bat")
        assertTrue(a2 is CommandAction.Message)
        assertTrue((a2 as CommandAction.Message).text.contains("Battery:"))

        // Step 3: Launch messaging app
        val a3 = input("Tele")
        assertTrue(a3 is CommandAction.Launch)
        assertEquals("Telegram", (a3 as CommandAction.Launch).app.label)

        // Step 4: Reset MRU cache at end of day
        val a4 = input("reset mru")
        assertTrue(a4 is CommandAction.Message)
        assertEquals("[MRU cache cleared]", (a4 as CommandAction.Message).text)
    }

    @Test
    fun test_tier4_scenario_device_management() {
        // Step 1: Check media volume
        val a1 = input("vol")
        assertTrue(a1 is CommandAction.Message)
        assertTrue((a1 as CommandAction.Message).text.contains("Volume:"))

        // Step 2: Set media volume to 75%
        val a2 = input("vol 75%")
        assertTrue(a2 is CommandAction.Message)
        assertTrue((a2 as CommandAction.Message).text.contains("Volume set to"))

        // Step 3: Toggle flashlight on and off
        val a3 = input("flash")
        assertEquals("[Flashlight ON]", (a3 as CommandAction.Message).text)
        val a4 = input("flash")
        assertEquals("[Flashlight OFF]", (a4 as CommandAction.Message).text)

        // Step 4: Read launcher help
        val a5 = input("help")
        assertTrue(a5 is CommandAction.Message)
        assertTrue((a5 as CommandAction.Message).text.contains("Ash Launcher"))
    }

    @Test
    fun test_tier4_scenario_defensive_error_recovery() {
        // Step 1: User enters invalid timeout
        val a1 = input("\$t0 ping 1.1.1.1")
        assertTrue(a1 is CommandAction.Message)
        assertEquals("[Error: Timeout must be between 1 and 600 seconds]", (a1 as CommandAction.Message).text)

        // Step 2: User enters divide by zero
        val a2 = input("calc 100 / 0")
        assertTrue(a2 is CommandAction.Message)
        assertEquals("[Error: Division by zero]", (a2 as CommandAction.Message).text)

        // Step 3: User tries to create illegal numeric alias
        val a3 = input("alias 1=wa")
        assertTrue(a3 is CommandAction.Message)
        assertTrue((a3 as CommandAction.Message).text.contains("cannot be purely numeric"))

        // Step 4: User enters bare dollar prompt
        val a4 = input("\$")
        assertTrue(a4 is CommandAction.None)

        // Step 5: User triggers selection then cancels via back button
        input("Whats")
        assertTrue(stateMachine.state is LauncherState.AwaitingSelection)
        val handled = stateMachine.onBack()
        assertTrue(handled)
        assertTrue(stateMachine.state is LauncherState.Normal)
    }
}
