package com.fingertipfuture.hub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class BackupRunnerTest {

    @Test
    fun `backup stamps group chat and files from the same backup`() {
        val names = listOf(
            "chat-20261019-120000.json",
            "files-20261019-120000.zip",
            "chat-20261019-121000.json",
            "files-20261019-121000.zip",
            "chat-old.json",
        )
        assertEquals(
            listOf("20261019-120000", "20261019-121000"),
            BackupRunner.backupStamps(names),
        )
    }

    @Test
    fun acceptsFilesInsideAllowedDirectories() {
        val root = Files.createTempDirectory("hub-backup-test").toFile()
        val target = BackupRunner.resolveZipTarget(root, "attachments/image.jpg")
        assertEquals("image.jpg", target?.name)
        assertEquals("attachments", target?.parentFile?.name)
    }

    @Test
    fun acceptsVoiceDirectory() {
        val root = Files.createTempDirectory("hub-backup-test").toFile()
        val target = BackupRunner.resolveZipTarget(root, "voice/voice-1.m4a")
        assertEquals("voice-1.m4a", target?.name)
    }

    @Test
    fun rejectsPathTraversal() {
        val root = Files.createTempDirectory("hub-backup-test").toFile()
        assertNull(BackupRunner.resolveZipTarget(root, "attachments/../../shared_prefs/hub.xml"))
    }

    @Test
    fun rejectsAbsoluteAndUnknownDirectories() {
        val root = Files.createTempDirectory("hub-backup-test").toFile()
        assertNull(BackupRunner.resolveZipTarget(root, "/data/local/tmp/x"))
        assertNull(BackupRunner.resolveZipTarget(root, "databases/hub.db"))
    }

    @Test
    fun commitsStagedFilesOnlyAfterFullExtraction() {
        val root = Files.createTempDirectory("hub-restore-root").toFile()
        val staging = Files.createTempDirectory("hub-restore-staging").toFile()
        val backup = Files.createTempDirectory("hub-restore-backup").toFile()
        try {
            File(root, "attachments").mkdirs()
            File(root, "attachments/a.txt").writeText("old")
            File(staging, "attachments").mkdirs()
            File(staging, "attachments/a.txt").writeText("new")
            File(staging, "attachments/b.txt").writeText("new-b")

            assertEquals(2, BackupRunner.commitStagedFiles(root, staging, backup))
            assertEquals("new", File(root, "attachments/a.txt").readText())
            assertEquals("new-b", File(root, "attachments/b.txt").readText())
        } finally {
            root.deleteRecursively()
            staging.deleteRecursively()
            backup.deleteRecursively()
        }
    }
}
