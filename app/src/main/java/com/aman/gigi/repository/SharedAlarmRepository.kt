package com.aman.gigi.repository

import android.content.Context
import com.aman.gigi.BuildConfig
import com.aman.gigi.alarm.AlarmUtils
import com.aman.gigi.db.SharedAlarmDao
import com.aman.gigi.model.Connection
import com.aman.gigi.model.ConnectionRole
import com.aman.gigi.model.SharedAlarmMirror
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Thrown when the server rejects a write because the member's plan limit was hit (HTTP 402). */
class PlanLimitException(message: String) : Exception(message)

@Singleton
class SharedAlarmRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedAlarmDao: SharedAlarmDao
) {
    private val httpBaseUrl = run {
        val wsUri = URI(BuildConfig.SERVER_URL)
        val scheme = if (wsUri.scheme.equals("wss", ignoreCase = true)) "https" else "http"
        URI(
            scheme,
            wsUri.userInfo,
            wsUri.host,
            if (wsUri.port == -1) -1 else wsUri.port,
            null,
            null,
            null
        ).toString().trimEnd('/')
    }

    fun observeAllActive(): Flow<List<SharedAlarmMirror>> = sharedAlarmDao.observeActiveAlarms()

    fun observeActiveForConnection(connectionId: String): Flow<List<SharedAlarmMirror>> {
        return sharedAlarmDao.observeActiveAlarmsForConnection(connectionId)
    }

    suspend fun reconcileWithServer(
        serverAlarms: List<SharedAlarmMirror>,
        activeConnections: List<Connection>,
        selectedCreatorConnectionId: String?,
        authoritative: Boolean = false
    ) {
        if (authoritative) {
            sharedAlarmDao.deleteAll()
        }

        val existing = if (authoritative) emptyList() else sharedAlarmDao.getAllAlarmsOnce()
        val nextIds = serverAlarms.map { it.alarmId }.toSet()
        
        if (!authoritative) {
            existing
                .filter { it.alarmId !in nextIds }
                .forEach {
                    cancelLocalAlarm(it, activeConnections)
                    sharedAlarmDao.deleteById(it.alarmId)
                }
        }

        sharedAlarmDao.upsertAll(serverAlarms)
        refreshSchedules(activeConnections, selectedCreatorConnectionId)
    }

    suspend fun applyRemoteUpsert(
        alarm: SharedAlarmMirror,
        activeConnections: List<Connection>,
        selectedCreatorConnectionId: String?
    ) {
        sharedAlarmDao.upsert(alarm)
        refreshSchedules(activeConnections, selectedCreatorConnectionId)
    }

    suspend fun applyRemoteDelete(
        alarmId: String,
        activeConnections: List<Connection>,
        selectedCreatorConnectionId: String?
    ) {
        val existing = sharedAlarmDao.getAlarmById(alarmId)
        if (existing != null) {
            cancelLocalAlarm(existing, activeConnections)
            sharedAlarmDao.deleteById(alarmId)
        }
        refreshSchedules(activeConnections, selectedCreatorConnectionId)
    }

    suspend fun refreshSchedules(
        activeConnections: List<Connection>,
        selectedCreatorConnectionId: String?
    ) {
        val connectionsById = activeConnections
            .filter { it.isActive && !it.serverArchived }
            .associateBy { it.connectionId }

        sharedAlarmDao.getAllActiveAlarmsOnce().forEach { alarm ->
            val connection = connectionsById[alarm.connectionId]
            if (connection == null) {
                cancelLocalAlarm(alarm, activeConnections)
                return@forEach
            }

            val shouldSchedule = when (connection.role.uppercase()) {
                ConnectionRole.CREATOR.name -> {
                    val effectiveSelected = selectedCreatorConnectionId
                        ?: activeConnections.firstOrNull { it.role.equals(ConnectionRole.CREATOR.name, ignoreCase = true) }?.connectionId
                    alarm.connectionId == effectiveSelected
                }
                else -> true
            }

            if (!shouldSchedule) {
                cancelLocalAlarm(alarm, activeConnections)
                return@forEach
            }

            val reminder = alarm.asReminderStub(connection.partnerName)
            if (alarm.dueAt >= System.currentTimeMillis()) {
                AlarmUtils.scheduleAlarm(context, reminder)
            } else if (!alarm.recurrencePattern.isNullOrBlank()) {
                AlarmUtils.rescheduleAlarm(context, reminder)
            } else {
                AlarmUtils.cancelAlarm(context, reminder)
            }
        }
    }

    suspend fun upsertRemoteAlarm(
        authToken: String,
        alarm: SharedAlarmMirror
    ): SharedAlarmMirror? {
        val response = requestJson(
            path = "/api/shared-alarms/upsert",
            method = "POST",
            body = JSONObject().apply {
                put("sessionToken", authToken)
                put("alarmId", alarm.alarmId)
                put("connectionCode", alarm.connectionId)
                put("title", alarm.title)
                put("note", alarm.note)
                put("dueAt", alarm.dueAt)
                put("recurrencePattern", alarm.recurrencePattern)
                put("customIntervalMillis", alarm.customIntervalMillis)
                put("repeatStartHour", alarm.repeatStartHour)
                put("repeatStartMinute", alarm.repeatStartMinute)
                put("repeatEndHour", alarm.repeatEndHour)
                put("repeatEndMinute", alarm.repeatEndMinute)
            }
        )
        val alarmJson = response.optJSONObject("alarm") ?: return null
        return parseSharedAlarm(alarmJson)
    }

    suspend fun deleteRemoteAlarm(
        authToken: String,
        connectionId: String,
        alarmId: String
    ): Boolean {
        val response = requestJson(
            path = "/api/shared-alarms/delete",
            method = "POST",
            body = JSONObject().apply {
                put("sessionToken", authToken)
                put("connectionCode", connectionId)
                put("alarmId", alarmId)
            }
        )
        return response.optBoolean("ok", false)
    }

    fun parseSharedAlarms(array: JSONArray?): List<SharedAlarmMirror> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                parseSharedAlarm(array.optJSONObject(index))?.let(::add)
            }
        }
    }

    fun parseSharedAlarm(json: JSONObject?): SharedAlarmMirror? {
        if (json == null) return null
        val alarmId = json.optString("alarmId").trim()
        val connectionId = json.optString("connectionCode")
            .ifBlank { json.optString("connectionId") }
            .trim()
            .lowercase()
        if (alarmId.isBlank() || connectionId.isBlank()) return null

        return SharedAlarmMirror(
            alarmId = alarmId,
            connectionId = connectionId,
            title = json.optString("title").ifBlank { "Shared alarm" },
            note = json.optString("note").takeIf { it.isNotBlank() },
            dueAt = json.optLong("dueAt"),
            recurrencePattern = json.optString("recurrencePattern").takeIf { it.isNotBlank() },
            customIntervalMillis = json.optLong("customIntervalMillis").takeIf { it > 0L },
            repeatStartHour = json.optInt("repeatStartHour").takeIf { it >= 0 },
            repeatStartMinute = json.optInt("repeatStartMinute").takeIf { it >= 0 },
            repeatEndHour = json.optInt("repeatEndHour").takeIf { it >= 0 },
            repeatEndMinute = json.optInt("repeatEndMinute").takeIf { it >= 0 },
            ownerMemberId = json.optString("ownerMemberId").takeIf { it.isNotBlank() },
            ownerDisplayName = json.optString("ownerDisplayName").takeIf { it.isNotBlank() },
            isActive = json.optBoolean("isActive", true),
            updatedAt = json.optLong("updatedAt").takeIf { it > 0L } ?: System.currentTimeMillis()
        )
    }

    private fun cancelLocalAlarm(alarm: SharedAlarmMirror, activeConnections: List<Connection>) {
        val partnerName = activeConnections.firstOrNull { it.connectionId == alarm.connectionId }?.partnerName
        AlarmUtils.cancelAlarm(context, alarm.asReminderStub(partnerName))
    }

    private suspend fun requestJson(path: String, method: String, body: JSONObject): JSONObject {
        return withContext(Dispatchers.IO) {
            val url = URL("$httpBaseUrl$path")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15000
                readTimeout = 15000
                doInput = true
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }

            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val payload = runCatching {
                (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()
                    ?.use { JSONObject(it.readText()) }
            }.getOrNull() ?: JSONObject()

            if (connection.responseCode !in 200..299) {
                val message = payload.optString("error").ifBlank { "Request failed with ${connection.responseCode}" }
                if (connection.responseCode == 402 || payload.optString("code") == "PLAN_LIMIT_REACHED") {
                    throw PlanLimitException(message)
                }
                throw IllegalStateException(message)
            }

            payload
        }
    }

    /**
     * Clear all alarms from database and cancel all system schedules.
     */
    suspend fun deleteAllAlarms() {
        val alarms = sharedAlarmDao.getAllAlarmsOnce()
        alarms.forEach { alarm ->
            // Partner name is not needed for cancellation as we only rely on the stable reminder ID
            AlarmUtils.cancelAlarm(context, alarm.asReminderStub(null))
        }
        sharedAlarmDao.deleteAll()
    }
}
