package com.fingertipfuture.hub.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.fingertipfuture.hub.floating.FloatingSearchSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class ExtendedDeviceResult(
    val text: String,
    val data: JSONObject = JSONObject(),
    val ok: Boolean = true,
    val error: String = "",
)

internal object ExtendedDeviceContext {
    private fun formatDeviceTime(timestamp: Long): String =
        SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(timestamp))

    fun readClipboard(context: Context, approximate: Boolean): ExtendedDeviceResult {
        val manager = context.getSystemService(ClipboardManager::class.java)
            ?: return failure("系统不支持读取剪贴板")
        val clip = manager.primaryClip
            ?: return success("剪贴板中没有内容", JSONObject().put("entries", 0))
        val values = buildList {
            for (index in 0 until clip.itemCount) {
                val text = clip.getItemAt(index).coerceToText(context)?.toString().orEmpty()
                if (text.isNotBlank()) add(text)
            }
        }
        val combined = values.joinToString("\n").take(12_000)
        val data = JSONObject()
            .put("itemCount", values.size)
            .put("characterCount", combined.length)
        if (!approximate) data.put("text", combined)
        return success(
            if (approximate) {
                "剪贴板摘要：共 ${values.size} 项，${combined.length} 个字符。大致权限开启时不会返回正文。"
            } else {
                "剪贴板内容：\n$combined"
            },
            data,
        )
    }

    @SuppressLint("MissingPermission")
    suspend fun readBluetooth(
        context: Context,
        approximate: Boolean,
    ): ExtendedDeviceResult = withContext(Dispatchers.Main.immediate) {
        if (!PermissionAccess.hasBluetoothPermissions(context)) {
            return@withContext failure("系统还没有授予蓝牙/附近设备权限")
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return@withContext failure("这台设备不支持蓝牙")
        if (!adapter.isEnabled) return@withContext failure("蓝牙当前未开启")

        data class Device(val name: String, val address: String, val paired: Boolean)

        val devices = linkedMapOf<String, Device>()
        runCatching {
            adapter.bondedDevices.orEmpty().forEach { item ->
                devices[item.address] = Device(
                    name = item.name.orEmpty().ifBlank { "未命名设备" },
                    address = item.address,
                    paired = true,
                )
            }
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner != null) {
            suspendCancellableCoroutine { continuation ->
                val callback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult?) {
                        val item = result?.device ?: return
                        val name = runCatching { item.name }.getOrNull().orEmpty()
                            .ifBlank { result.scanRecord?.deviceName.orEmpty() }
                            .ifBlank { "未命名设备" }
                        devices[item.address] = Device(name, item.address, paired = false)
                    }
                }
                val stopper = Runnable {
                    runCatching { scanner.stopScan(callback) }
                    if (continuation.isActive) continuation.resume(Unit)
                }
                runCatching { scanner.startScan(callback) }
                    .onFailure {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                Handler(Looper.getMainLooper()).postDelayed(stopper, 2_500)
                continuation.invokeOnCancellation {
                    runCatching { scanner.stopScan(callback) }
                }
            }
        }

        val list = devices.values.sortedWith(compareByDescending<Device> { it.paired }.thenBy { it.name })
        val array = JSONArray().apply {
            list.forEach { item ->
                put(JSONObject().apply {
                    put("name", if (approximate) maskName(item.name) else item.name)
                    put("address", if (approximate) "已隐藏" else item.address)
                    put("paired", item.paired)
                })
            }
        }
        val data = JSONObject()
            .put("count", list.size)
            .put("pairedCount", list.count { it.paired })
            .put("devices", array)
        val text = if (approximate) {
            "附近设备摘要：共发现 ${list.size} 个蓝牙设备，其中 ${list.count { it.paired }} 个已配对。"
        } else {
            "附近蓝牙设备：\n" + list.joinToString("\n") {
                "- ${it.name}；${it.address}；${if (it.paired) "已配对" else "附近设备"}"
            }
        }
        success(text, data)
    }

    fun readNotifications(context: Context, approximate: Boolean): ExtendedDeviceResult {
        if (!PermissionAccess.hasNotificationListenerAccess(context)) {
            return failure("系统还没有授予通知使用权")
        }
        val items = NotificationSnapshotStore.load(context).sortedByDescending { it.postedAt }
        val array = JSONArray().apply {
            items.forEach { item ->
                put(JSONObject().apply {
                    put("packageName", item.packageName)
                    put("title", if (approximate) "" else item.title)
                    put("text", if (approximate) "" else item.text)
                    put("postedAt", item.postedAt)
                })
            }
        }
        val data = JSONObject()
            .put("count", items.size)
            .put("items", array)
        val text = if (items.isEmpty()) {
            "当前没有可读的通知。"
        } else if (approximate) {
            "通知摘要：当前共有 ${items.size} 条通知，来自 ${items.map { it.packageName }.distinct().size} 个应用。"
        } else {
            "当前通知：\n" + items.joinToString("\n") {
                "- ${formatDeviceTime(it.postedAt)} ${it.packageName}：${it.title}，${it.text}"
            }
        }
        return success(text, data)
    }

    @SuppressLint("MissingPermission")
    fun readWifi(context: Context, approximate: Boolean): ExtendedDeviceResult {
        if (!PermissionAccess.hasWifiDetails(context)) {
            return failure("系统还没有授予 Wi-Fi、附近设备和位置权限")
        }
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
            ?: return failure("系统不支持 Wi-Fi")
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity?.activeNetwork
        val caps = network?.let(connectivity::getNetworkCapabilities)
        val networkTypes = buildList {
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) add("Wi-Fi")
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) add("蜂窝网络")
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true) add("有线网络")
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) add("VPN")
        }
        val info = runCatching { wifi.connectionInfo }.getOrNull()
        val scans = runCatching { wifi.scanResults }.getOrDefault(emptyList())
        val data = JSONObject().apply {
            put("networkTypes", JSONArray(networkTypes))
            put("wifiEnabled", wifi.isWifiEnabled)
            put("ssid", if (approximate) "" else info?.ssid.orEmpty())
            put("bssid", if (approximate) "" else info?.bssid.orEmpty())
            put("linkSpeedMbps", info?.linkSpeed ?: -1)
            put("frequencyMhz", info?.frequency ?: -1)
            put("nearbyCount", scans.size)
        }
        val text = if (approximate) {
            "网络摘要：当前网络 ${networkTypes.ifEmpty { listOf("未知") }.joinToString("、")}，" +
                "Wi-Fi ${if (wifi.isWifiEnabled) "已开启" else "未开启"}，附近扫描到 ${scans.size} 个热点。"
        } else {
            buildString {
                append("网络类型：").append(networkTypes.ifEmpty { listOf("未知") }.joinToString("、"))
                append("\nWi-Fi：").append(if (wifi.isWifiEnabled) "已开启" else "未开启")
                append("\nSSID：").append(info?.ssid.orEmpty())
                append("\nBSSID：").append(info?.bssid.orEmpty())
                append("\n连接速率：").append(info?.linkSpeed ?: -1).append(" Mbps")
                append("\n频段：").append(info?.frequency ?: -1).append(" MHz")
                if (scans.isNotEmpty()) {
                    append("\n附近热点：\n")
                    append(scans.joinToString("\n") {
                        "- ${it.SSID}；${it.BSSID}；${it.level} dBm；${it.frequency} MHz"
                    })
                }
            }
        }
        return success(text, data)
    }

    fun overlayStatus(context: Context): ExtendedDeviceResult {
        val allowed = Settings.canDrawOverlays(context)
        val running = FloatingSearchSession.isRunning
        val data = JSONObject().apply {
            put("overlayAllowed", allowed)
            put("screenSessionRunning", running)
        }
        return success(
            "悬浮窗权限：${if (allowed) "已开启" else "未开启"}；" +
                "屏幕捕获会话：${if (running) "运行中" else "未运行"}。",
            data,
        )
    }

    fun listMedia(context: Context, approximate: Boolean): ExtendedDeviceResult {
        if (!PermissionAccess.hasMediaAccess(context)) {
            return failure("系统还没有授予完整媒体读取权限")
        }
        data class MediaItem(
            val kind: String,
            val name: String,
            val size: Long,
            val addedAt: Long,
            val uri: String,
        )

        val items = mutableListOf<MediaItem>()
        fun query(uri: Uri, kind: String) {
            runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(
                        MediaStore.MediaColumns.DISPLAY_NAME,
                        MediaStore.MediaColumns.SIZE,
                        MediaStore.MediaColumns.DATE_ADDED,
                        MediaStore.MediaColumns._ID,
                    ),
                    null,
                    null,
                    MediaStore.MediaColumns.DATE_ADDED + " DESC",
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(3)
                        items += MediaItem(
                            kind = kind,
                            name = cursor.getString(0).orEmpty(),
                            size = cursor.getLong(1),
                            addedAt = cursor.getLong(2) * 1_000,
                            uri = Uri.withAppendedPath(uri, id.toString()).toString(),
                        )
                    }
                }
            }
        }
        query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "图片")
        query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "视频")
        query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "音频")

        val data = JSONObject().apply {
            put("count", items.size)
            put("items", JSONArray().apply {
                items.forEach { item ->
                    put(JSONObject().apply {
                        put("kind", item.kind)
                        put("name", if (approximate) maskName(item.name) else item.name)
                        put("size", item.size)
                        put("addedAt", item.addedAt)
                        if (!approximate) put("uri", item.uri)
                    })
                }
            })
        }
        val text = if (approximate) {
            val counts = items.groupingBy { it.kind }.eachCount()
            "媒体摘要：共 ${items.size} 项；" +
                listOf("图片", "视频", "音频").joinToString("、") { "$it ${counts[it] ?: 0}" }
        } else {
            "媒体文件：\n" + items.joinToString("\n") {
                "- ${it.kind}；${it.name}；${it.size} B；${formatDeviceTime(it.addedAt)}"
            }
        }
        return success(text, data)
    }

    fun writeCalendar(
        context: Context,
        title: String,
        beginText: String,
        endText: String,
        description: String,
    ): ExtendedDeviceResult {
        if (!PermissionAccess.has(context, Manifest.permission.WRITE_CALENDAR)) {
            return failure("系统还没有授予写入日历权限")
        }
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).apply { isLenient = false }
        val begin = runCatching { format.parse(beginText) }.getOrNull()
            ?: return failure("开始时间格式应为 yyyy-MM-dd HH:mm")
        val end = endText.takeIf { it.isNotBlank() }
            ?.let { runCatching { format.parse(it) }.getOrNull() }
            ?: Date(begin.time + 60 * 60_000L)
        if (end.time <= begin.time) return failure("结束时间必须晚于开始时间")
        val calendarId = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.VISIBLE}=1",
            null,
            "${CalendarContract.Calendars.IS_PRIMARY} DESC",
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
            ?: return failure("没有找到可写入的本地日历")
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, begin.time)
            put(CalendarContract.Events.DTEND, end.time)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: return failure("日历写入失败")
        return success(
            "已创建日历事项「$title」：${format.format(begin)} 至 ${format.format(end)}",
            JSONObject().put("uri", uri.toString()).put("title", title)
                .put("begin", begin.time).put("end", end.time),
        )
    }

    fun writeContact(
        context: Context,
        name: String,
        phone: String,
    ): ExtendedDeviceResult {
        if (!PermissionAccess.has(context, Manifest.permission.WRITE_CONTACTS)) {
            return failure("系统还没有授予写入联系人权限")
        }
        if (name.isBlank()) return failure("联系人姓名不能为空")
        val rawUri = context.contentResolver.insert(
            ContactsContract.RawContacts.CONTENT_URI,
            ContentValues().apply {
                put(ContactsContract.RawContacts.ACCOUNT_TYPE, null as String?)
                put(ContactsContract.RawContacts.ACCOUNT_NAME, null as String?)
            },
        ) ?: return failure("创建联系人失败")
        val rawId = rawUri.lastPathSegment?.toLongOrNull()
            ?: return failure("创建联系人后没有返回 id")
        val nameInserted = context.contentResolver.insert(
            ContactsContract.Data.CONTENT_URI,
            ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
            },
        ) != null
        var phoneInserted = phone.isBlank()
        if (phone.isNotBlank()) {
            phoneInserted = context.contentResolver.insert(
                ContactsContract.Data.CONTENT_URI,
                ContentValues().apply {
                    put(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                    put(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                    )
                    put(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                    put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                },
            ) != null
        }
        val nameVerified = context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME),
            "${ContactsContract.Data.RAW_CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
            arrayOf(
                rawId.toString(),
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
            ),
            null,
        )?.use { cursor ->
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(0) == name) {
                    found = true
                    break
                }
            }
            found
        } ?: false
        val phoneVerified = phone.isBlank() || context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.Data.RAW_CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
            arrayOf(
                rawId.toString(),
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
            ),
            null,
        )?.use { cursor ->
            var found = false
            while (cursor.moveToNext()) {
                if (phoneDigits(cursor.getString(0)) == phoneDigits(phone)) {
                    found = true
                    break
                }
            }
            found
        } == true
        if (!nameInserted || !phoneInserted || !nameVerified || !phoneVerified) {
            context.contentResolver.delete(rawUri, null, null)
            return failure("联系人写入后验证失败，已撤销未完成的联系人")
        }
        return success(
            "已创建联系人「$name」${if (phone.isBlank()) "" else "，号码 $phone"}",
            JSONObject().put("rawContactId", rawId).put("name", name).put("phone", phone),
        )
    }

    fun composeSms(context: Context, number: String, text: String): ExtendedDeviceResult {
        val uri = Uri.parse("smsto:${number.trim()}")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            success(
                "已打开系统短信编辑页，短信尚未发送，需要用户在系统中确认。",
                JSONObject().put("number", number).put("opened", true),
            )
        }.getOrElse { failure("打不开系统短信编辑页：${it.message.orEmpty()}") }
    }

    private fun maskName(value: String): String {
        if (value.length <= 2) return "**"
        return value.first() + "**" + value.last()
    }

    private fun maskPhone(value: String): String {
        if (value.length <= 4) return "****"
        return value.take(3) + "****" + value.takeLast(2)
    }

    private fun phoneDigits(value: String?): String =
        value.orEmpty().filter(Char::isDigit)

    private fun success(text: String, data: JSONObject = JSONObject()) =
        ExtendedDeviceResult(text = text, data = data)

    private fun failure(message: String) =
        ExtendedDeviceResult(text = "", data = JSONObject(), ok = false, error = message)
}
