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

    @Test
    fun interruptedRestoreCanRollbackNewAndReplacedFiles() {
        val root = Files.createTempDirectory("hub-rollback-root").toFile()
        val staging = Files.createTempDirectory("hub-rollback-staging").toFile()
        val backup = Files.createTempDirectory("hub-rollback-backup").toFile()
        try {
            File(root, "attachments").mkdirs()
            File(root, "attachments/a.txt").writeText("old")
            File(staging, "attachments").mkdirs()
            File(staging, "attachments/a.txt").writeText("new")
            File(staging, "attachments/b.txt").writeText("new-b")

            val commit = BackupRunner.installStagedFiles(root, staging, backup)
            assertEquals("new", File(root, "attachments/a.txt").readText())
            assertEquals("new-b", File(root, "attachments/b.txt").readText())

            BackupRunner.rollbackStagedFiles(commit)
            assertEquals("old", File(root, "attachments/a.txt").readText())
            assertNull(File(root, "attachments/b.txt").takeIf(File::exists))
        } finally {
            root.deleteRecursively()
            staging.deleteRecursively()
            backup.deleteRecursively()
        }
    }

    @Test
    fun installedFilesCanBeRemappedBeforeChatImport() {
        val root = Files.createTempDirectory("hub-remap-root").toFile()
        val staging = Files.createTempDirectory("hub-remap-staging").toFile()
        val backup = Files.createTempDirectory("hub-remap-backup").toFile()
        try {
            File(staging, "attachments").mkdirs()
            File(staging, "attachments/photo.jpg").writeText("image")
            val commit = BackupRunner.installStagedFiles(root, staging, backup)

            val remapped = AttachmentPathPolicy.remapImported(
                root,
                "/old-device/files/attachments/photo.jpg",
            )
            assertEquals(File(root, "attachments/photo.jpg").absolutePath, remapped)

            BackupRunner.finishStagedFiles(commit)
        } finally {
            root.deleteRecursively()
            staging.deleteRecursively()
            backup.deleteRecursively()
        }
    }
}
