package com.fingertipfuture.hub.data

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Telephony
import android.net.Uri
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 把用户明确授权的设备信息整理成一段上下文，交给模型。
 *
 * 只在助理对应的权限开关打开、并且这条消息确实在问相关东西时才读；
 * 不是每一条消息都把短信/联系人塞进提示词。读取全部在 IO 线程。
 */
internal object DeviceContext {

    private const val MAX_CHARS = 20_000
    private val timeFormat = SimpleDateFormat("M月d日 HH:mm", Locale.CHINA)

    suspend fun build(context: Context, assistant: HubAssistant, userText: String): DeviceContextResult =
        withContext(Dispatchers.IO) {
            val text = userText.lowercase(Locale.CHINA)
            val parts = mutableListOf<String>()
            val traces = mutableListOf<DeviceAccessTrace>()
            var searchLocation = ""
            var locationSummary = ""

            if (assistant.permLocation &&
                wants(
                    text,
                    "天气", "气温", "温度", "下雨", "降雨", "附近", "周边", "本地", "当地",
                    "位置", "在哪", "导航", "路线", "weather", "temperature", "nearby",
                )
            ) {
                val location = runCatching { AttachmentStore.currentLocation(context) }.getOrNull()
                if (location != null) {
                    locationSummary = location.positionText()
                    parts += "当前位置：" + locationSummary
                    searchLocation = location.label.ifBlank {
                        "%.5f,%.5f".format(Locale.US, location.lat, location.lon)
                    }
                } else if (!AttachmentStore.hasLocationPermission(context)) {
                    locationSummary = "系统还没有授予定位权限"
                    parts += "位置：助手开关已开，但系统还没有授予定位权限。"
                } else {
                    locationSummary = "暂时无法获取当前坐标"
                    parts += "位置：暂时无法获取当前坐标。"
                }
            }
            if (assistant.permScreenTime &&
                wants(text, "屏幕时间", "使用时间", "用了多久", "手机用时", "screen time", "screen")
            ) {
                recordTrace("screen_time", "读取屏幕时间", screenTime(context), parts, traces)
            }
            if (assistant.permCalendar &&
                wants(
                    text,
                    "日历", "日程", "行程", "会议", "我的安排", "有什么安排",
                    "明天安排", "今天安排", "日程安排", "calendar", "schedule",
                )
            ) {
                recordTrace("calendar", "读取日历", calendar(context), parts, traces)
            }
            if (assistant.permInstalledApps &&
                wants(
                    text,
                    "已安装", "装了什么", "装过什么", "应用列表", "软件列表",
                    "哪些应用", "哪些软件", "病毒软件", "查病毒", "杀毒",
                    "有没有病毒", "手机病毒", "可疑软件", "流氓软件",
                    "installed app", "app list", "package list",
                )
            ) {
                recordTrace("apps", "读取已安装应用", installedApps(context), parts, traces)
            }
            if (assistant.permContacts &&
                wants(text, "联系人", "通讯录", "电话号码", "打给", "contact", "contacts")
            ) {
                recordTrace("contacts", "读取联系人", contacts(context), parts, traces)
            }
            if (assistant.permSms && wants(text, "短信", "sms")) {
                recordTrace("sms", "读取短信", sms(context), parts, traces)
            }
            if (assistant.permMms && wants(text, "彩信", "mms")) {
                recordTrace("mms", "读取彩信", mms(context), parts, traces)
            }
            if (assistant.permActivity &&
                wants(text, "步数", "走了多少步", "步行", "运动数据", "activity", "steps")
            ) {
                recordTrace("activity", "读取今日步数", stepCount(context), parts, traces)
            }

            if (parts.isEmpty()) return@withContext DeviceContextResult("", "", "", emptyList())
            DeviceContextResult(
                text = ("【设备上下文（用户已授权，仅本次回答使用）】\n" + parts.joinToString("\n"))
                    .take(MAX_CHARS),
                searchLocation = searchLocation,
                locationSummary = locationSummary,
                accessTraces = traces,
            )
        }

    private fun recordTrace(
        kind: String,
        title: String,
        value: String?,
        parts: MutableList<String>,
        traces: MutableList<DeviceAccessTrace>,
    ) {
        if (value.isNullOrBlank()) return
        parts += value
        val failed = value.contains("没有授予") ||
            value.contains("系统不支持") ||
            value.contains("暂时无法") ||
            value.contains("没有读到")
        traces += DeviceAccessTrace(
            kind = kind,
            title = title,
            detail = value,
            ok = !failed,
        )
    }

    private fun wants(text: String, vararg keywords: String): Boolean =
        keywords.any { text.contains(it) }

    private fun has(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun screenTime(context: Context): String? {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return "屏幕时间：系统不支持读取。"
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        }
        if (mode != AppOpsManager.MODE_ALLOWED) {
            return "屏幕时间：助手开关已开，但系统还没有授予「使用情况访问」权限。"
        }
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val stats = manager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            start.timeInMillis,
            System.currentTimeMillis(),
        ).orEmpty().filter { it.totalTimeInForeground > 0 }
        if (stats.isEmpty()) return "屏幕时间：今天还没有可读的使用记录。"
        val totalMinutes = stats.sumOf { it.totalTimeInForeground } / 60_000
        val top = stats.sortedByDescending { it.totalTimeInForeground }.take(5).map {
            appLabel(context, it.packageName) + " " + (it.totalTimeInForeground / 60_000) + " 分钟"
        }
        return "屏幕时间（今天）：约 " + totalMinutes + " 分钟；" + top.joinToString("、")
    }

    private fun appLabel(context: Context, packageName: String): String =
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)

    private fun calendar(context: Context): String? {
        if (!has(context, Manifest.permission.READ_CALENDAR)) {
            return "日历：助手开关已开，但系统还没有授予日历读取权限。"
        }
        val begin = System.currentTimeMillis()
        val end = begin + 7L * 24 * 60 * 60 * 1000
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(begin.toString())
            .appendPath(end.toString())
            .build()
        val rows = mutableListOf<String>()
        context.contentResolver.query(
            uri,
            arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN),
            null,
            null,
            CalendarContract.Instances.BEGIN + " ASC",
        )?.use { cursor ->
            while (cursor.moveToNext() && rows.size < 12) {
                val title = cursor.getString(0).orEmpty().ifBlank { "未命名日程" }
                val at = cursor.getLong(1)
                rows += "- " + timeFormat.format(Date(at)) + " " + title
            }
        }
        return if (rows.isEmpty()) "日历：未来 7 天没有读到日程。" else "日历（未来 7 天）：\n" + rows.joinToString("\n")
    }

    private fun installedApps(context: Context): String? {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val apps = pm.getInstalledApplications(0)
            .filter { it.packageName != context.packageName }
        if (apps.isEmpty()) return null
        val userApps = apps.filter {
            (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0
        }
        val systemApps = apps.filter {
            (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        }
        val userNameList = userApps
            .map { pm.getApplicationLabel(it).toString() }
            .distinct()
            .sorted()
            .joinToString("、")
        val systemNameList = systemApps
            .map { pm.getApplicationLabel(it).toString() }
            .distinct()
            .sorted()
            .joinToString("、")
        return buildString {
            append("已安装应用：共 ").append(apps.size).append(" 个")
            append("（用户应用 ").append(userApps.size).append(" 个，系统应用 ")
                .append(systemApps.size).append(" 个）。")
            append("\n用户应用：").append(userNameList)
            if (systemNameList.isNotBlank()) {
                append("\n系统应用：").append(systemNameList)
            }
        }
    }

    private fun contacts(context: Context): String? {
        if (!has(context, Manifest.permission.READ_CONTACTS)) {
            return "联系人：助手开关已开，但系统还没有授予联系人读取权限。"
        }
        val rows = mutableListOf<String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC",
        )?.use { cursor ->
            while (cursor.moveToNext() && rows.size < 40) {
                val name = cursor.getString(0).orEmpty().ifBlank { "未命名" }
                val number = cursor.getString(1).orEmpty()
                if (number.isNotBlank()) rows += name + " " + number
            }
        }
        return if (rows.isEmpty()) "联系人：没有读到联系人。" else "联系人：\n" + rows.joinToString("\n") { "- $it" }
    }

    private fun sms(context: Context): String? {
        if (!has(context, Manifest.permission.READ_SMS)) {
            return "短信：助手开关已开，但系统还没有授予短信读取权限。"
        }
        val rows = mutableListOf<String>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            null,
            null,
            Telephony.Sms.DATE + " DESC",
        )?.use { cursor ->
            while (cursor.moveToNext() && rows.size < 8) {
                val address = cursor.getString(0).orEmpty().ifBlank { "未知号码" }
                val body = cursor.getString(1).orEmpty().replace('\n', ' ').take(160)
                val at = cursor.getLong(2)
                rows += "- " + timeFormat.format(Date(at)) + " " + address + "：" + body
            }
        }
        return if (rows.isEmpty()) "短信：没有读到短信。" else "最近短信：\n" + rows.joinToString("\n")
    }

    private fun mms(context: Context): String? {
        if (!has(context, Manifest.permission.READ_SMS)) {
            return "彩信：助手开关已开，但系统还没有授予短信读取权限。"
        }
        val rows = mutableListOf<String>()
        context.contentResolver.query(
            Uri.parse("content://mms/inbox"),
            arrayOf("_id", "date", "sub"),
            null,
            null,
            "date DESC",
        )?.use { cursor ->
            while (cursor.moveToNext() && rows.size < 5) {
                val date = cursor.getLong(1)
                val subject = cursor.getString(2).orEmpty().ifBlank { "（无主题）" }
                rows += "- " + timeFormat.format(Date(date * 1000)) + " " + subject
            }
        }
        return if (rows.isEmpty()) "彩信：没有读到彩信。" else "最近彩信：\n" + rows.joinToString("\n")
    }

    /**
     * 今日步数：TYPE_STEP_COUNTER 给的是“开机以来”，这里按自然日存一个基线，
     * 每次跨日时把当天的第一次读数当基线。读不到传感器就如实说明。
     */
    private fun stepCount(context: Context): String? {
        if (!has(context, Manifest.permission.ACTIVITY_RECOGNITION) && Build.VERSION.SDK_INT >= 29) {
            return "步数：助手开关已开，但系统还没有授予「身体活动」权限。"
        }
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return "步数：系统不支持读取传感器。"
        val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            ?: return "步数：这台设备没有计步传感器。"
        val latch = CountDownLatch(1)
        var raw = -1f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                raw = event.values.firstOrNull() ?: -1f
                latch.countDown()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val registered = runCatching {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)
        }.getOrDefault(false)
        if (!registered) return "步数：暂时无法启用计步传感器。"
        runCatching { latch.await(1500, TimeUnit.MILLISECONDS) }
        manager.unregisterListener(listener)
        if (raw < 0f) return "步数：暂时没有读到计步数据。"

        val total = raw.toLong()
        val prefs = context.getSharedPreferences("hub_device_context", Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(Date())
        val savedDay = prefs.getString("step_day", "").orEmpty()
        val baseline = if (savedDay == today) {
            prefs.getLong("step_baseline", total)
        } else {
            prefs.edit()
                .putString("step_day", today)
                .putLong("step_baseline", total)
                .apply()
            total
        }
        val todaySteps = (total - baseline).coerceAtLeast(0L)
        return "今日步数：约 $todaySteps 步（系统计步器）。"
    }
}

internal data class DeviceContextResult(
    val text: String,
    /** 可拼进网页搜索关键词的当前位置（地名优先，没有地名就用经纬度）。 */
    val searchLocation: String,
    /** 给思考框显示的位置结果；空 = 这条消息没有请求位置。 */
    val locationSummary: String,
    /** 这一轮真正读取过的其它系统权限，用于思考时间线。 */
    val accessTraces: List<DeviceAccessTrace>,
)

internal data class DeviceAccessTrace(
    val kind: String,
    val title: String,
    val detail: String,
    val ok: Boolean,
)
