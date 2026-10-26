package com.woody.pebbledesk

import android.Manifest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 책 파일 표지: 파일을 적을 때 뒤에서 꺼내 두고([BookShelf.setFilePath]), 못 했으면 그릴 때 꺼낸다. 안드로이드 10 이라 책 파일 읽기는 저장소 읽기 권한 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class FileCoverTest {
    @get:Rule val tmp = TemporaryFolder()

    private val app get() = RuntimeEnvironment.getApplication()

    /** 1×1 PNG */
    private val png: ByteArray =
        java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=")

    @Before fun setUp() {
        BookShelf.clearCaches()
        Db.get(app).execSQL("INSERT INTO shelf VALUES (0, 'b-1', '만화', '', NULL, 0, -1, -1, '', '')")
    }

    @After fun tearDown() {
        BookShelf.clearCaches()
        Db.closeForTest()
        Storage.of(app).getDatabasePath("pebble.db").parentFile?.deleteRecursively()
        File(Storage.of(app).filesDir, "books").deleteRecursively()
    }

    private fun cbz() = File(tmp.root, "c.cbz").also { f ->
        ZipOutputStream(f.outputStream()).use { z -> z.putNextEntry(ZipEntry("p1.png")); z.write(png); z.closeEntry() }
    }

    /** 뒤에서 꺼내기가 끝나기를 기다린다(최대 5초) */
    private fun awaitCover(): Boolean {
        repeat(50) { if (BookShelf.hasCover(app, "b-1")) return true; Thread.sleep(100) }
        return false
    }

    @Test fun `파일을 적으면 그리기 전에 뒤에서 표지를 꺼내 둔다`() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        BookShelf.setFilePath(app, "b-1", cbz().path)
        assertTrue(awaitCover())
    }

    @Test fun `책 파일 읽기가 꺼져 있을 때 적은 파일도 허용한 뒤 그릴 때 꺼낸다`() {
        // 권한이 없으면 뒤에서 꺼내지 않는다(그 자리에서 끝난다)
        BookShelf.setFilePath(app, "b-1", cbz().path)
        assertFalse(BookShelf.hasCover(app, "b-1"))
        shadowOf(app).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        BookShelf.cover(app, BookShelf.list(app).single(), 100)
        assertTrue(BookShelf.hasCover(app, "b-1"))
    }
}
