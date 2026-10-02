package com.aiyu.rewire.domain

import com.aiyu.rewire.domain.update.NoteBlock
import com.aiyu.rewire.domain.update.Release
import com.aiyu.rewire.domain.update.ReleaseAsset
import com.aiyu.rewire.domain.update.ReleaseNotes
import com.aiyu.rewire.domain.update.Releases
import com.aiyu.rewire.domain.update.UpdateChannel
import com.aiyu.rewire.domain.update.Version
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateReleasesTest {
    private fun v(s: String) = Version.parse(s)!!
    private fun release(tag: String, vararg assets: String) =
        Release(v(tag), tag, "", "", assets.map { ReleaseAsset(it, "https://x/$it", 1) })

    @Test fun versionsOrderAcrossStages() {
        assertTrue(v("1.0.1-alpha.1") < v("1.0.1-alpha.2"))
        assertTrue(v("1.0.1-alpha.9") < v("1.0.1-beta.1"))
        assertTrue(v("1.0.1-beta.9") < v("1.0.1"))        // stable beats any pre-release of the same numbers
        assertTrue(v("1.0.1") < v("1.0.2-alpha.1"))
        assertEquals(v("1.0.1"), Version.parse("v1.0.1"))
    }

    @Test fun unknownTagsAreSkippedNotGuessed() {
        assertNull(Version.parse("nightly"))
        assertNull(Version.parse("1.0"))
        assertNull(Version.parse("1.0.1-rc.1"))
    }

    @Test fun channelsAreCumulative() {
        val all = listOf(release("1.1.0-alpha.1"), release("1.1.0-beta.1"), release("1.0.5"))
        assertEquals(v("1.0.5"), Releases.pick(all, v("1.0.1"), UpdateChannel.STABLE)!!.version)
        assertEquals(v("1.1.0-beta.1"), Releases.pick(all, v("1.0.1"), UpdateChannel.BETA)!!.version) // betas + stable, not alphas
        assertEquals(v("1.1.0-beta.1"), Releases.pick(all, v("1.0.1"), UpdateChannel.ALPHA)!!.version)
        assertEquals(v("1.1.0-alpha.1"), Releases.pick(listOf(release("1.1.0-alpha.1")), v("1.0.1"), UpdateChannel.ALPHA)!!.version)
        assertNull(Releases.pick(listOf(release("1.1.0-alpha.1")), v("1.0.1"), UpdateChannel.BETA))
    }

    @Test fun newerIsByVersionNeverByPublishOrder() {
        // A stable cut after an alpha is not an upgrade for somebody already on that alpha's newer numbers.
        val list = listOf(release("1.0.5"), release("1.1.0-alpha.2"))
        assertNull(Releases.pick(list, v("1.1.0-alpha.2"), UpdateChannel.ALPHA))
        assertNull(Releases.pick(list, v("1.0.5"), UpdateChannel.STABLE))
    }

    @Test fun defaultChannelFollowsTheBuild() {
        assertEquals(UpdateChannel.ALPHA, UpdateChannel.forVersion("1.0.1-alpha.1"))
        assertEquals(UpdateChannel.BETA, UpdateChannel.forVersion("1.0.1-beta.2"))
        assertEquals(UpdateChannel.STABLE, UpdateChannel.forVersion("1.0.1"))
        assertEquals(UpdateChannel.STABLE, UpdateChannel.forVersion("garbage"))
    }

    @Test fun apkForPicksTheApkNotTheBundle() {
        val r = release("1.0.2", "Rewire-1.0.2.aab", "Rewire-1.0.2.apk", "other.apk")
        assertEquals("Rewire-1.0.2.apk", Releases.apkFor(r)!!.name)
        assertNull(Releases.apkFor(release("1.0.2", "Rewire-1.0.2.aab")))
    }

    @Test fun apkForKeepsEachBuildOnItsOwnFlavor() {
        val r = release("1.0.2", "Rewire-1.0.2.aab", "Rewire-1.0.2.apk", "Rewire-Lite-1.0.2.apk")
        assertEquals("Rewire-1.0.2.apk", Releases.apkFor(r, lite = false)!!.name)
        assertEquals("Rewire-Lite-1.0.2.apk", Releases.apkFor(r, lite = true)!!.name)
        assertNull(Releases.apkFor(release("1.0.2", "Rewire-1.0.2.apk"), lite = true))
        assertNull(Releases.apkFor(release("1.0.2", "Rewire-Lite-1.0.2.apk"), lite = false))
    }

    @Test fun parsesGithubJsonSkippingDraftsAndUnknownTags() {
        val body = """
            [
              {"tag_name":"v1.0.2","name":"v1.0.2","body":"### Features\n* a","draft":false,"published_at":"2026-10-02T00:00:00Z",
               "assets":[{"name":"Rewire-1.0.2.apk","browser_download_url":"https://github.com/x.apk","size":10,"digest":"sha256:ABC","download_count":3}],
               "unknown_field":{"a":1}},
              {"tag_name":"v1.0.3","draft":true,"assets":[]},
              {"tag_name":"nightly","assets":[]}
            ]
        """.trimIndent()
        val list = Releases.parse(body)
        assertEquals(1, list.size)
        assertEquals("abc", list[0].assets[0].sha256)
        assertEquals("1.0.2", list[0].name) // GitHub says v1.0.2; the app shows versions without the v
    }

    @Test fun notesParseHeadingsBulletsAndDropNoise() {
        val blocks = ReleaseNotes.parse("### Features\n\n* **Add** a thing\n- another\nplain line\n```sh\nrm -rf /\n```\n| a | b |\n| --- | --- |")
        assertEquals(
            listOf(NoteBlock.Heading("Features"), NoteBlock.Bullet("**Add** a thing"), NoteBlock.Bullet("another"), NoteBlock.Paragraph("plain line"), NoteBlock.Paragraph("| a | b |")),
            blocks,
        )
    }
}
