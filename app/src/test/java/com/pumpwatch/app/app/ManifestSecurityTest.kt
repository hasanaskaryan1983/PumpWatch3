package com.pumpwatch.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 🚀 Sprint 14 (Commit 7B): رگرسیون امنیت Manifest
 *
 * این تست فایل واقعی AndroidManifest.xml را parse می‌کند و چک می‌کند:
 *  - allowBackup=false (بمب نشت داده)
 *  - POST_NOTIFICATIONS اعلام شده (بمب Commit 3)
 *  - dataExtractionRules وجود دارد
 *  - networkSecurityConfig وجود دارد
 *  - usesCleartextTraffic=false
 *  - taskAffinity="" (جلوگیری از task hijacking)
 *
 * این تست روی JVM اجرا می‌شود و هیچ dependency اندرویدی ندارد.
 */
class ManifestSecurityTest {

    private fun loadManifest(): Element {
        // تلاش برای پیدا کردن Manifest در مسیرهای مختلف (CI vs local)
        val candidates = listOf(
            File("app/src/main/AndroidManifest.xml"),
            File("../app/src/main/AndroidManifest.xml"),
            File("src/main/AndroidManifest.xml")
        )
        val manifestFile = candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException(
                "AndroidManifest.xml not found in any of: ${candidates.map { it.absolutePath }}"
            )
        val dbf = DocumentBuilderFactory.newInstance()
        // امن‌سازی parser در برابر XXE
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false)
        val doc = dbf.newDocumentBuilder().parse(manifestFile)
        return doc.documentElement
    }

    private fun findApplication(manifest: Element): Element {
        val apps = manifest.getElementsByTagName("application")
        assertTrue("manifest must have <application>", apps.length == 1)
        return apps.item(0) as Element
    }

    private fun hasPermission(manifest: Element, name: String): Boolean {
        val perms = manifest.getElementsByTagName("uses-permission")
        for (i in 0 until perms.length) {
            val p = perms.item(i) as Element
            if (p.getAttribute("android:name") == name) return true
        }
        return false
    }

    // ---------- تست‌های حیاتی ----------

    @Test
    fun backup_must_be_disabled() {
        val app = findApplication(loadManifest())
        assertEquals(
            "allowBackup must be false (wallet addresses must not leak to Google Drive)",
            "false", app.getAttribute("android:allowBackup")
        )
    }

    @Test
    fun post_notifications_permission_must_be_declared() {
        // بمب Commit 3: بدون این اعلام، runtime check روی اندروید ۱۳+ همیشه DENIED است
        assertTrue(
            "POST_NOTIFICATIONS must be declared in manifest (Commit 3 runtime check depends on it)",
            hasPermission(loadManifest(), "android.permission.POST_NOTIFICATIONS")
        )
    }

    @Test
    fun data_extraction_rules_must_be_present() {
        val app = findApplication(loadManifest())
        val rules = app.getAttribute("android:dataExtractionRules")
        assertTrue(
            "dataExtractionRules must reference an xml resource (Android 12+ requirement)",
            rules.startsWith("@xml/")
        )
    }

    @Test
    fun full_backup_content_must_be_present() {
        val app = findApplication(loadManifest())
        val rules = app.getAttribute("android:fullBackupContent")
        assertTrue(
            "fullBackupContent must reference an xml resource (legacy backup)",
            rules.startsWith("@xml/")
        )
    }

    @Test
    fun network_security_config_must_be_present() {
        val app = findApplication(loadManifest())
        val cfg = app.getAttribute("android:networkSecurityConfig")
        assertTrue(
            "networkSecurityConfig must reference an xml resource (HTTPS-only)",
            cfg.startsWith("@xml/")
        )
    }

    @Test
    fun cleartext_traffic_must_be_forbidden() {
        val app = findApplication(loadManifest())
        assertEquals(
            "usesCleartextTraffic must be false (MITM downgrade prevention)",
            "false", app.getAttribute("android:usesCleartextTraffic")
        )
    }

    @Test
    fun task_affinity_must_be_empty() {
        val app = findApplication(loadManifest())
        // taskAffinity="" جلوی نشت داده بین task های اپ و سایر اپ‌ها را می‌گیرد
        assertEquals(
            "taskAffinity must be empty string (task hijacking prevention)",
            "", app.getAttribute("android:taskAffinity")
        )
    }

    @Test
    fun manifest_permission_set_is_minimal() {
        // فقط INTERNET و POST_NOTIFICATIONS — هیچ دسترسی اضافی
        val manifest = loadManifest()
        val perms = manifest.getElementsByTagName("uses-permission")
        val declared = (0 until perms.length).map {
            (perms.item(it) as Element).getAttribute("android:name")
        }.toSet()
        assertEquals(
            "only INTERNET and POST_NOTIFICATIONS are allowed",
            setOf(
                "android.permission.INTERNET",
                "android.permission.POST_NOTIFICATIONS"
            ),
            declared
        )
    }

    @Test
    fun main_activity_must_not_be_exported_beyond_launcher() {
        // MainActivity فقط باید از launcher قابل‌اجرا باشد
        val manifest = loadManifest()
        val activities = manifest.getElementsByTagName("activity")
        for (i in 0 until activities.length) {
            val a = activities.item(i) as Element
            if (a.getAttribute("android:name") == ".MainActivity") {
                val intents = a.getElementsByTagName("intent-filter")
                assertTrue("MainActivity must have intent-filter", intents.length == 1)
                val filter = intents.item(0) as Element
                val actions = filter.getElementsByTagName("action")
                assertEquals("only MAIN action allowed", 1, actions.length)
                assertEquals(
                    "android.intent.action.MAIN",
                    (actions.item(0) as Element).getAttribute("android:name")
                )
                return
            }
        }
        throw IllegalStateException("MainActivity not found in manifest")
    }
}
