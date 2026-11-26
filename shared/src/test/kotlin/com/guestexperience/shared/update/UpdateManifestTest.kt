package com.guestexperience.shared.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestTest {
    private val hash = "a".repeat(64)
    private val url = "${UpdateSource.DOWNLOAD_PREFIX}build-142/GuestExperienceHost-1.2.142.apk"

    @Test
    fun `parses the workflow's manifest and ignores unknown fields`() {
        val text = """
            {"schema":1,"commit":"abc",
             "host":{"versionCode":142,"versionName":"1.2.142","apk":"$url","sha256":"$hash","size":123},
             "tv":{"versionCode":142,"versionName":"1.2.142","apk":"$url","sha256":"$hash"}}
        """.trimIndent()
        val m = UpdateManifest.parse(text)!!
        assertEquals(142L, m.forApp(UpdateChannel.HOST)!!.versionCode)
        assertEquals("1.2.142", m.forApp(UpdateChannel.TV)!!.versionName)
    }

    @Test
    fun `missing app and broken text`() {
        assertNull(UpdateManifest.parse("""{"tv":null}""")!!.forApp(UpdateChannel.HOST))
        assertNull(UpdateManifest.parse("not json"))
        assertNull(UpdateManifest.parse("""{"host":{"versionCode":"x"}}"""))
    }

    @Test
    fun `only a higher version code is an update`() {
        val r = AppRelease(142, "1.2.142", url, hash)
        assertTrue(r.isNewerThan(141))
        assertFalse(r.isNewerThan(142))
        assertFalse(r.isNewerThan(200))
    }

    @Test
    fun `downloads must come from this repository's releases over https`() {
        assertTrue(AppRelease(1, "1", url, hash).isTrusted())
        assertFalse(AppRelease(1, "1", "http://github.com/hiteshramawat01-oss/WelcomeTV/releases/download/x/a.apk", hash).isTrusted())
        assertFalse(AppRelease(1, "1", "https://github.com/someone-else/WelcomeTV/releases/download/x/a.apk", hash).isTrusted())
        assertFalse(AppRelease(1, "1", "${UpdateSource.DOWNLOAD_PREFIX}../../evil/a.apk", hash).isTrusted())
        assertFalse(AppRelease(1, "1", url.removeSuffix(".apk") + ".zip", hash).isTrusted())
        assertFalse(AppRelease(1, "1", url, "A".repeat(64)).isTrusted())
        assertFalse(AppRelease(1, "1", url, "abc").isTrusted())
    }

    @Test
    fun `sha256 of known input`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.hex("abc".byteInputStream()),
        )
    }
}
