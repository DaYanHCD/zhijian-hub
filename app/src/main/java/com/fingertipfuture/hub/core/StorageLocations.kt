package com.fingertipfuture.hub.core

import android.os.Environment
import java.io.File

/** App 仍会主动管理的公开存储位置，统一在这里生成，避免统计和备份使用不同路径。 */
object StorageLocations {

    fun persistentRoot(): File {
        val documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        return File(File(documents, "指尖Hub"), ".persistence")
    }
}
