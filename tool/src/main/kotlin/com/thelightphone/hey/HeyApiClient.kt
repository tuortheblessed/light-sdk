package com.thelightphone.hey

import com.thelightphone.hey.auth.AuthPreferences
import com.thelightphone.hey.auth.CatalogHabit
import com.thelightphone.hey.auth.HabitCatalogStore
import com.thelightphone.hey.auth.HeyCredentials
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

internal class HeyApiClient(
    private val authPreferences: AuthPreferences,
    private val habitCatalogStore: HabitCatalogStore,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(json)
        }
    }

    private val refreshMutex = Mutex()
    private var cachedCalendarId: Long? = null
    private var cachedIdentity: IdentityPrefs? = null

    suspend fun hasCredentials(): Boolean = authPreferences.load().isSignedIn

    suspend fun ping(): Result<Unit> = runCatching {
        authorizedGet("/calendars.json")
        Unit
    }

    /** Account-local "today" from HEY identity timezone (not device TZ). */
    suspend fun today(): LocalDate = LocalDate.now(identityPrefs().timeZone)

    fun clearSessionCaches() {
        cachedCalendarId = null
        cachedIdentity = null
        calendarDotsGeneration += 1
    }

    @Volatile
    var calendarDotsGeneration: Int = 0
        private set

    fun invalidateCalendarDots() {
        calendarDotsGeneration += 1
    }

    suspend fun loadDayOverview(day: LocalDate? = null): DayOverview {
        val resolvedDay = day ?: today()
        val dayIso = resolvedDay.format(ISO_DATE)
        val calendarId = personalCalendarId()
        // HEY omits Calendar::Habit on many multi-day ranges; fetch today alone for habits.
        // Short lookback refreshes catalog from completion parents; catalog itself persists.
        val todayRecordings = getRecordings(calendarId, dayIso, dayIso)
        val lookbackDays = habitLookbackDays()
        val lookbackRecordings = getRecordings(
            calendarId,
            resolvedDay.minusDays(lookbackDays).format(ISO_DATE),
            resolvedDay.format(ISO_DATE),
        )
        val habits = resolveHabitsForDay(
            calendarId = calendarId,
            dayIso = dayIso,
            todayRecordings = todayRecordings,
            lookbackRecordings = lookbackRecordings,
        )
        val todos = listWeekTodos(calendarId)
        val journal = getJournal(dayIso)
        val stickies = listStickies()
        return DayOverview(
            dayLabel = formatDayLabel(resolvedDay),
            dayIso = dayIso,
            habits = habits,
            todos = todos,
            journal = journal,
            stickies = stickies,
        )
    }

    suspend fun listHabits(day: LocalDate? = null): List<HabitItem> {
        val resolvedDay = day ?: today()
        val dayIso = resolvedDay.format(ISO_DATE)
        val calendarId = personalCalendarId()
        val todayRecordings = getRecordings(calendarId, dayIso, dayIso)
        val lookbackDays = habitLookbackDays()
        val lookbackRecordings = getRecordings(
            calendarId,
            resolvedDay.minusDays(lookbackDays).format(ISO_DATE),
            resolvedDay.format(ISO_DATE),
        )
        return resolveHabitsForDay(
            calendarId = calendarId,
            dayIso = dayIso,
            todayRecordings = todayRecordings,
            lookbackRecordings = lookbackRecordings,
        )
    }

    private suspend fun habitLookbackDays(): Long =
        if (habitCatalogStore.load().size >= MIN_HABIT_CATALOG_BEFORE_SKIP_BOOTSTRAP) {
            HABIT_LOOKBACK_SHORT_DAYS
        } else {
            HABIT_LOOKBACK_DAYS
        }

    suspend fun listWeekTodos(): List<TodoItem> = listWeekTodos(personalCalendarId())

    private suspend fun listWeekTodos(calendarId: Long): List<TodoItem> {
        val today = today()
        val identity = identityPrefs()
        // HEY "Sometime this week" rolls incomplete todos into the current week bucket.
        // recordings.json under-counts and drops completed; HTML has the full week list.
        val weekStart = weekStartFor(today, identity.firstWeekDay)
        val weekEnd = weekStart.plusDays(6)
        val fromJson = parseOpenTodos(
            getRecordings(calendarId, weekStart.format(ISO_DATE), weekEnd.format(ISO_DATE)),
        ).associateBy { it.id }.toMutableMap()

        val htmlTodos = parseSometimeThisWeekTodos(
            html = authorizedGetHtml("/calendar").body,
            weekStart = weekStart,
            zone = identity.timeZone,
        )
        val byId = linkedMapOf<Long, TodoItem>()
        // Prefer HTML for presence/completion/title; keep JSON-only extras via union.
        for (htmlTodo in htmlTodos) {
            val jsonTodo = fromJson.remove(htmlTodo.id)
            byId[htmlTodo.id] = jsonTodo?.copy(
                title = htmlTodo.title.ifBlank { jsonTodo.title },
                completed = htmlTodo.completed || jsonTodo.completed,
            ) ?: htmlTodo
        }
        for (jsonTodo in fromJson.values) {
            byId.putIfAbsent(jsonTodo.id, jsonTodo)
        }
        return byId.values.sortedWith(weekTodoOrder)
    }

    suspend fun firstWeekDay(): DayOfWeek = identityPrefs().firstWeekDay

    private suspend fun identityPrefs(): IdentityPrefs {
        cachedIdentity?.let { return it }
        val response = authorizedGet("/identity.json")
        val root = json.parseToJsonElement(response.body).jsonObject
        val heyDay = root["first_week_day"]?.jsonPrimitive?.intOrNull ?: 1
        val tzName = root.string("time_zone")
            ?: root.string("time_zone_name")
            ?: "UTC"
        val zone = runCatching { ZoneId.of(tzName) }.getOrElse { ZoneId.systemDefault() }
        return IdentityPrefs(
            firstWeekDay = heyWeekdayToDayOfWeek(heyDay),
            timeZone = zone,
        ).also { cachedIdentity = it }
    }

    suspend fun completeHabit(habitId: Long, day: LocalDate? = null) {
        val dayIso = (day ?: today()).format(ISO_DATE)
        authorizedPost("/calendar/days/$dayIso/habits/$habitId/completions.json")
        invalidateCalendarDots()
    }

    suspend fun uncompleteHabit(habitId: Long, day: LocalDate? = null) {
        val dayIso = (day ?: today()).format(ISO_DATE)
        authorizedDelete("/calendar/days/$dayIso/habits/$habitId/completions.json")
        invalidateCalendarDots()
    }

    suspend fun addTodo(title: String, startsAt: LocalDate? = null) {
        val resolvedStart = startsAt ?: today()
        val body = buildJsonObject {
            putJsonObject("calendar_todo") {
                put("title", title.trim())
                put("starts_at", resolvedStart.format(ISO_DATE))
            }
        }
        authorizedPostJson("/calendar/todos.json", body)
    }

    suspend fun completeTodo(todoId: Long) {
        authorizedPost("/calendar/todos/$todoId/completions.json")
    }

    suspend fun uncompleteTodo(todoId: Long) {
        authorizedDelete("/calendar/todos/$todoId/completions.json")
    }

    suspend fun getJournal(dayIso: String): JournalEntry? {
        // hey-cli: JSON often 204 even when content exists; fall back to edit HTML.
        val response = authorizedGet("/calendar/days/$dayIso/journal_entry")
        if (response.status.isSuccess() && response.body.isNotBlank()) {
            val root = json.parseToJsonElement(response.body)
            val content = extractJournalContent(root)
            if (content.isNotBlank()) {
                return JournalEntry(day = dayIso, content = content)
            }
        }
        val edit = authorizedGetHtml("/calendar/days/$dayIso/journal_entry/edit")
        if (edit.status.isSuccess() && edit.body.isNotBlank()) {
            val scraped = extractTrixJournalContent(edit.body)
            return JournalEntry(day = dayIso, content = scraped)
        }
        if (
            response.status == HttpStatusCode.NoContent ||
            response.status.isSuccess() ||
            edit.status == HttpStatusCode.NoContent ||
            edit.status.isSuccess()
        ) {
            return JournalEntry(day = dayIso, content = "")
        }
        throw IllegalStateException("HEY HTTP ${response.status.value} journal get")
    }

    suspend fun updateJournal(dayIso: String, content: String) {
        val body = buildJsonObject {
            putJsonObject("calendar_journal_entry") {
                put("content", content)
            }
        }
        // hey-cli PATCHes without .json and treats 302 redirect as success.
        val response = authorizedPatchJsonAllowFail("/calendar/days/$dayIso/journal_entry", body)
        requireMutationSuccess(response, "/calendar/days/$dayIso/journal_entry")
        invalidateCalendarDots()
    }

    suspend fun listStickies(): List<StickyNote> {
        // Cover Art stickies (freestanding notes on Imbox cover) — not email-tied notes.
        val response = authorizedGet("/stickies.json")
        if (!response.status.isSuccess() || response.body.isBlank()) {
            if (response.status == HttpStatusCode.NoContent) return emptyList()
            throw IllegalStateException("HEY HTTP ${response.status.value} stickies")
        }
        val root = json.parseToJsonElement(response.body)
        return parseCoverStickies(root)
    }

    suspend fun listJournalDays(): Set<LocalDate> {
        // Wide recordings ranges silently drop older journal entries — fetch month chunks.
        val calendarId = personalCalendarId()
        val today = today()
        val days = linkedSetOf<LocalDate>()
        var month = YearMonth.from(today.minusYears(2))
        val endMonth = YearMonth.from(today)
        while (!month.isAfter(endMonth)) {
            days.addAll(listJournalDaysInMonth(calendarId, month))
            month = month.plusMonths(1)
        }
        return days
    }

    suspend fun listJournalDaysInMonth(month: YearMonth): Set<LocalDate> =
        listJournalDaysInMonth(personalCalendarId(), month)

    private suspend fun listJournalDaysInMonth(
        calendarId: Long,
        month: YearMonth,
    ): Set<LocalDate> {
        val recordings = getRecordings(
            calendarId,
            month.atDay(1).format(ISO_DATE),
            month.atEndOfMonth().format(ISO_DATE),
        )
        val days = linkedSetOf<LocalDate>()
        fun consider(entry: JsonObject) {
            val starts = entry.string("starts_at") ?: entry.string("starts_on") ?: return
            runCatching { LocalDate.parse(starts.take(10)) }.getOrNull()?.let { days.add(it) }
        }
        recordingsForType(recordings, "Calendar::JournalEntry").forEach(::consider)
        recordings.values.forEach { value ->
            val arr = value as? JsonArray ?: return@forEach
            arr.forEach { el ->
                val obj = el as? JsonObject ?: return@forEach
                val unwrapped = unwrapRecordingObj(obj)
                if (unwrapped.string("type") == "CalendarJournalEntry" ||
                    unwrapped.string("type") == "Calendar::JournalEntry"
                ) {
                    consider(unwrapped)
                }
            }
        }
        return days
    }

    suspend fun listHabitCompletionDays(): Set<LocalDate> {
        // Wide ranges under-count; walk month by month like journals.
        val calendarId = personalCalendarId()
        val today = today()
        val days = linkedSetOf<LocalDate>()
        var month = YearMonth.from(today.minusYears(1))
        val endMonth = YearMonth.from(today)
        while (!month.isAfter(endMonth)) {
            days.addAll(listHabitCompletionDaysInMonth(calendarId, month))
            month = month.plusMonths(1)
        }
        return days
    }

    suspend fun listHabitCompletionDaysInMonth(month: YearMonth): Set<LocalDate> =
        listHabitCompletionDaysInMonth(personalCalendarId(), month)

    private suspend fun listHabitCompletionDaysInMonth(
        calendarId: Long,
        month: YearMonth,
    ): Set<LocalDate> {
        val recordings = getRecordings(
            calendarId,
            month.atDay(1).format(ISO_DATE),
            month.atEndOfMonth().format(ISO_DATE),
        )
        val days = linkedSetOf<LocalDate>()
        fun consider(completion: JsonObject) {
            val unwrapped = unwrapRecordingObj(completion)
            val starts = unwrapped.string("starts_at")
                ?: unwrapped.string("starts_on")
                ?: return
            runCatching { LocalDate.parse(starts.take(10)) }.getOrNull()?.let { days.add(it) }
        }
        recordingsForType(recordings, "Calendar::Habit::Completion").forEach(::consider)
        recordings.values.forEach { value ->
            val arr = value as? JsonArray ?: return@forEach
            arr.forEach { el ->
                val obj = el as? JsonObject ?: return@forEach
                val unwrapped = unwrapRecordingObj(obj)
                if (unwrapped.string("type") == "CalendarHabitCompletion" ||
                    unwrapped.string("type") == "Calendar::Habit::Completion"
                ) {
                    consider(unwrapped)
                }
            }
        }
        return days
    }

    suspend fun createSticky(content: String) {
        val trimmed = content.trim()
        require(trimmed.isNotEmpty()) { "sticky is empty" }

        val candidates = listOf(
            "/stickies.json" to buildJsonObject {
                putJsonObject("sticky") { put("body", trimmed) }
            },
            "/stickies.json" to buildJsonObject {
                putJsonObject("sticky") { put("content", trimmed) }
            },
        )

        var lastError: Exception? = null
        for ((candidatePath, body) in candidates) {
            try {
                val response = authorizedPostJsonAllowFail(candidatePath, body)
                if (response.status.isSuccess() || response.status == HttpStatusCode.NoContent) {
                    return
                }
                lastError = IllegalStateException("HTTP ${response.status.value} $candidatePath")
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw StickyWriteUnavailableException().apply {
            if (lastError != null) initCause(lastError)
        }
    }

    suspend fun updateTodoTitle(todoId: Long, title: String) {
        val trimmed = title.trim()
        require(trimmed.isNotEmpty()) { "todo title is empty" }
        val body = buildJsonObject {
            putJsonObject("calendar_todo") {
                put("title", trimmed)
            }
        }
        val paths = listOf(
            "/calendar/todos/$todoId.json",
            "/calendar/todos/$todoId",
        )
        for (path in paths) {
            val response = authorizedPatchJsonAllowFail(path, body)
            if (response.status.isSuccess() || response.status == HttpStatusCode.NoContent) {
                return
            }
        }
        throw TodoEditUnavailableException()
    }

    suspend fun updateSticky(noteId: Long, content: String, postingId: Long?) {
        val trimmed = content.trim()
        require(trimmed.isNotEmpty()) { "sticky is empty" }
        val stickyBody = buildJsonObject {
            putJsonObject("sticky") { put("body", trimmed) }
        }
        val noteBody = buildJsonObject {
            putJsonObject("note") { put("content", trimmed) }
        }
        val candidates = buildList {
            add("/stickies/$noteId.json" to stickyBody)
            add("/stickies/$noteId" to stickyBody)
            if (postingId != null) {
                add("/postings/$postingId/note.json" to noteBody)
                add("/postings/$postingId/notes.json" to noteBody)
            }
            add("/notes/$noteId.json" to noteBody)
        }
        for ((path, body) in candidates) {
            val response = authorizedPatchJsonAllowFail(path, body)
            if (response.status.isSuccess() || response.status == HttpStatusCode.NoContent) {
                return
            }
        }
        throw StickyWriteUnavailableException()
    }

    fun close() {
        client.close()
    }

    private suspend fun resolveHabitsForDay(
        calendarId: Long,
        dayIso: String,
        todayRecordings: JsonObject,
        lookbackRecordings: JsonObject,
    ): List<HabitItem> {
        // Catalog is persisted in DataStore — habits do NOT expire after lookback days.
        // Lookback is only used to discover/enrich definitions (HEY often omits
        // Calendar::Habit on multi-day ranges, but completions nest full parents).
        val catalog = habitCatalogStore.load()
        habitCatalogStore.merge(catalog, extractHabits(lookbackRecordings), preferIncoming = false)
        habitCatalogStore.merge(catalog, extractHabits(todayRecordings), preferIncoming = true)

        // Bootstrap sibling habits from known start dates when catalog is still thin.
        if (catalog.size < MIN_HABIT_CATALOG_BEFORE_SKIP_BOOTSTRAP) {
            val bootstrapDates = linkedSetOf<String>()
            catalog.values.forEach { habit ->
                habit.startsOn?.take(10)?.let { if (it != dayIso) bootstrapDates.add(it) }
            }
            extractHabitStartDates(lookbackRecordings).forEach { bootstrapDates.add(it) }
            extractHabitStartDates(todayRecordings).forEach { bootstrapDates.add(it) }
            for (date in bootstrapDates.take(MAX_HABIT_BOOTSTRAP_DAYS)) {
                val dayPayload = getRecordings(calendarId, date, date)
                habitCatalogStore.merge(catalog, extractHabits(dayPayload), preferIncoming = false)
            }
        }

        habitCatalogStore.save(catalog)
        val done = habitCompletionMap(todayRecordings, dayIso) +
            habitCompletionMap(lookbackRecordings, dayIso)
        return habitsForToday(
            catalog = catalog,
            todayHabits = extractHabits(todayRecordings),
            done = done,
            dayIso = dayIso,
        )
    }

    private suspend fun personalCalendarId(): Long {
        cachedCalendarId?.let { return it }
        val response = authorizedGet("/calendars.json")
        val root = json.parseToJsonElement(response.body)
        val id = findPersonalCalendarId(root)
            ?: throw IllegalStateException("personal calendar not found")
        cachedCalendarId = id
        return id
    }

    private suspend fun getRecordings(
        calendarId: Long,
        startsOn: String,
        endsOn: String,
    ): JsonObject {
        val path = "/calendars/$calendarId/recordings.json?starts_on=$startsOn&ends_on=$endsOn"
        val response = authorizedGet(path)
        val root = json.parseToJsonElement(response.body)
        return unwrapRecordings(root)
    }

    private data class HttpTextResponse(val status: HttpStatusCode, val body: String)

    private suspend fun authorizedGet(path: String): HttpTextResponse {
        val absolute = if (path.startsWith("http")) path else baseUrl + path
        return authorizedRequest { creds ->
            val response = client.get(absolute) {
                bearerAuth(creds.accessToken)
                header("Accept", "application/json")
            }
            HttpTextResponse(response.status, response.bodyAsText())
        }
    }

    private suspend fun authorizedGetHtml(path: String): HttpTextResponse {
        val absolute = if (path.startsWith("http")) path else baseUrl + path
        return authorizedRequest { creds ->
            val response = client.get(absolute) {
                bearerAuth(creds.accessToken)
                header("Accept", "text/html, application/xhtml+xml, */*")
            }
            HttpTextResponse(response.status, response.bodyAsText())
        }
    }

    private suspend fun authorizedPost(path: String): HttpTextResponse {
        return authorizedRequest { creds ->
            val response = client.post(baseUrl + path) {
                bearerAuth(creds.accessToken)
                header("Accept", "*/*")
            }
            HttpTextResponse(response.status, response.bodyAsText())
        }.also { requireSuccess(it, path) }
    }

    private suspend fun authorizedDelete(path: String): HttpTextResponse {
        return authorizedRequest { creds ->
            val response = client.delete(baseUrl + path) {
                bearerAuth(creds.accessToken)
                header("Accept", "*/*")
            }
            HttpTextResponse(response.status, response.bodyAsText())
        }.also { requireSuccess(it, path) }
    }

    private suspend fun authorizedPostJson(path: String, body: JsonObject): HttpTextResponse {
        return authorizedPostJsonAllowFail(path, body).also { requireSuccess(it, path) }
    }

    private suspend fun authorizedPostJsonAllowFail(path: String, body: JsonObject): HttpTextResponse {
        return authorizedRequest { creds ->
            val response = client.post(baseUrl + path) {
                bearerAuth(creds.accessToken)
                header("Accept", "*/*")
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            HttpTextResponse(response.status, response.bodyAsText())
        }
    }

    private suspend fun authorizedPatchJson(path: String, body: JsonObject): HttpTextResponse {
        return authorizedPatchJsonAllowFail(path, body).also { requireSuccess(it, path) }
    }

    private suspend fun authorizedPatchJsonAllowFail(path: String, body: JsonObject): HttpTextResponse {
        return authorizedRequest { creds ->
            val response = client.patch(baseUrl + path) {
                bearerAuth(creds.accessToken)
                header("Accept", "*/*")
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            HttpTextResponse(response.status, response.bodyAsText())
        }
    }

    private suspend fun authorizedRequest(
        block: suspend (HeyCredentials) -> HttpTextResponse,
    ): HttpTextResponse {
        var creds = authPreferences.load()
        if (!creds.isSignedIn) throw AuthRequiredException()

        var response = block(creds)
        if (response.status != HttpStatusCode.Unauthorized) {
            return response
        }

        refreshMutex.withLock {
            creds = authPreferences.load()
            if (!creds.isSignedIn) throw AuthRequiredException()
            refreshAccessToken(creds)
            creds = authPreferences.load()
        }
        response = block(creds)
        if (response.status == HttpStatusCode.Unauthorized) {
            authPreferences.clear()
            clearSessionCaches()
            throw AuthRequiredException()
        }
        return response
    }

    private suspend fun refreshAccessToken(creds: HeyCredentials) {
        if (creds.refreshToken.isBlank()) {
            authPreferences.clear()
            clearSessionCaches()
            throw AuthRequiredException()
        }
        val endpoint = creds.tokenEndpoint.ifBlank { HeyCredentials.DEFAULT_TOKEN_ENDPOINT }
        val response = client.post(endpoint) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                "grant_type=refresh_token&refresh_token=" +
                    java.net.URLEncoder.encode(creds.refreshToken, Charsets.UTF_8.name()),
            )
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            authPreferences.clear()
            clearSessionCaches()
            throw AuthRequiredException()
        }
        val root = json.parseToJsonElement(body).jsonObject
        val access = root["access_token"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (access.isBlank()) {
            authPreferences.clear()
            clearSessionCaches()
            throw AuthRequiredException()
        }
        val refresh = root["refresh_token"]?.jsonPrimitive?.contentOrNull ?: creds.refreshToken
        authPreferences.save(
            HeyCredentials(
                accessToken = access,
                refreshToken = refresh,
                tokenEndpoint = HeyCredentials.DEFAULT_TOKEN_ENDPOINT,
            ),
        )
    }

    private fun requireSuccess(response: HttpTextResponse, path: String) {
        if (!response.status.isSuccess() && response.status != HttpStatusCode.NoContent) {
            throw IllegalStateException(
                "HEY HTTP ${response.status.value} $path: ${response.body.take(200)}",
            )
        }
    }

    private fun requireMutationSuccess(response: HttpTextResponse, path: String) {
        if (
            response.status.isSuccess() ||
            response.status == HttpStatusCode.NoContent ||
            response.status == HttpStatusCode.Found ||
            response.status == HttpStatusCode.MovedPermanently ||
            response.status == HttpStatusCode.SeeOther
        ) {
            return
        }
        throw IllegalStateException(
            "HEY HTTP ${response.status.value} $path: ${response.body.take(200)}",
        )
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://app.hey.com"
        private const val HABIT_LOOKBACK_DAYS = 90L
        private const val HABIT_LOOKBACK_SHORT_DAYS = 14L
        private const val MAX_HABIT_BOOTSTRAP_DAYS = 5
        private const val MIN_HABIT_CATALOG_BEFORE_SKIP_BOOTSTRAP = 4
        private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        fun formatDayLabel(day: LocalDate): String {
            val dow = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US).uppercase(Locale.US)
            val mon = day.month.getDisplayName(TextStyle.SHORT, Locale.US).uppercase(Locale.US)
            return "$dow ${day.dayOfMonth} $mon"
        }
    }
}

private fun unwrapRecordings(root: JsonElement): JsonObject {
    val obj = root as? JsonObject ?: return JsonObject(emptyMap())
    val data = obj["data"]
    if (data is JsonObject && obj["Calendar::Habit"] == null) {
        return data
    }
    return obj
}

private fun unwrapRecordingObj(obj: JsonObject): JsonObject =
    (obj["recording"] as? JsonObject) ?: obj

private fun recordingsForType(payload: JsonObject, typeName: String): List<JsonObject> {
    val arr = payload[typeName] as? JsonArray ?: return emptyList()
    return arr.mapNotNull { el ->
        (el as? JsonObject)?.let { unwrapRecordingObj(it) }
    }
}

private fun extractHabits(payload: JsonObject): List<CatalogHabit> {
    val seen = linkedMapOf<Long, CatalogHabit>()
    fun add(obj: JsonObject) {
        val unwrapped = unwrapRecordingObj(obj)
        val id = unwrapped.longId() ?: return
        val stopped = unwrapped.string("stopped_at").orEmpty()
        if (stopped.isNotBlank() && !isZeroDate(stopped)) return
        val title = unwrapped.string("title").orEmpty()
        val days = parseDays(unwrapped["days"])
        val icon = unwrapped.string("icon").orEmpty().ifBlank {
            iconSlugFromUrl(unwrapped.string("icon_url"))
        }
        val iconUrl = unwrapped.string("icon_url").orEmpty()
        val startsOn = unwrapped.string("starts_at")?.take(10)
            ?: unwrapped.string("starts_on")?.take(10)
        val incoming = CatalogHabit(
            id = id,
            title = title,
            icon = icon,
            iconUrl = iconUrl,
            days = days,
            stoppedAt = stopped,
            startsOn = startsOn,
        )
        val current = seen[id]
        if (current == null || habitDetailScore(incoming) >= habitDetailScore(current)) {
            seen[id] = incoming
        }
    }
    recordingsForType(payload, "Calendar::Habit").forEach(::add)
    // Completions on multi-day ranges nest the full habit under parent.
    recordingsForType(payload, "Calendar::Habit::Completion").forEach { completion ->
        val parent = completion["parent"] as? JsonObject
        if (parent != null) add(parent)
    }
    payload.values.forEach { value ->
        val arr = value as? JsonArray ?: return@forEach
        arr.forEach { el ->
            val obj = el as? JsonObject ?: return@forEach
            val unwrapped = unwrapRecordingObj(obj)
            if (unwrapped.string("type") == "CalendarHabit" ||
                unwrapped.string("type") == "Calendar::Habit"
            ) {
                add(unwrapped)
            }
            if (unwrapped.string("type") == "CalendarHabitCompletion" ||
                unwrapped.string("type") == "Calendar::Habit::Completion"
            ) {
                (unwrapped["parent"] as? JsonObject)?.let(::add)
            }
        }
    }
    return seen.values.toList()
}

private fun extractHabitStartDates(payload: JsonObject): List<String> {
    return extractHabits(payload).mapNotNull { it.startsOn }.distinct()
}

private fun parseDays(element: JsonElement?): List<Int> {
    val arr = element as? JsonArray ?: return emptyList()
    return arr.mapNotNull { el ->
        when (el) {
            is JsonPrimitive -> el.longOrNull?.toInt()
                ?: el.contentOrNull?.toIntOrNull()
            else -> null
        }
    }
}

private fun habitDetailScore(habit: CatalogHabit): Int {
    var score = 0
    if (habit.title.isNotBlank()) score += 2
    if (habit.icon.isNotBlank() || habit.iconUrl.isNotBlank()) score += 2
    if (habit.days.isNotEmpty()) score += 3
    return score
}

private fun iconSlugFromUrl(url: String?): String {
    if (url.isNullOrBlank()) return ""
    val file = url.substringAfterLast('/').substringBefore('?')
    return file.replace(Regex("-[a-f0-9]+\\.svg$", RegexOption.IGNORE_CASE), "")
        .removeSuffix(".svg")
        .removeSuffix(".png")
}

private fun habitCompletionMap(payload: JsonObject, dayIso: String): Set<Long> {
    val done = mutableSetOf<Long>()
    fun consider(completion: JsonObject) {
        val unwrapped = unwrapRecordingObj(completion)
        val starts = unwrapped.string("starts_at")
            ?: unwrapped.string("starts_on")
            ?: return
        if (!starts.startsWith(dayIso)) return
        val parentId = unwrapped.long("parent_id")
            ?: (unwrapped["parent"] as? JsonObject)?.longId()
        if (parentId != null) done.add(parentId)
    }
    recordingsForType(payload, "Calendar::Habit::Completion").forEach(::consider)
    payload.values.forEach { value ->
        val arr = value as? JsonArray ?: return@forEach
        arr.forEach { el ->
            val obj = el as? JsonObject ?: return@forEach
            val unwrapped = unwrapRecordingObj(obj)
            if (unwrapped.string("type") == "CalendarHabitCompletion" ||
                unwrapped.string("type") == "Calendar::Habit::Completion"
            ) {
                consider(unwrapped)
            }
        }
    }
    return done
}

private fun habitsForToday(
    catalog: Map<Long, CatalogHabit>,
    todayHabits: List<CatalogHabit>,
    done: Set<Long>,
    dayIso: String,
): List<HabitItem> {
    val byId = linkedMapOf<Long, CatalogHabit>()

    fun include(habit: CatalogHabit) {
        if (habit.stoppedAt.isNotBlank() && !isZeroDate(habit.stoppedAt)) return
        if (!habitStartedBy(habit.startsOn, dayIso)) return
        if (!habitScheduledForDate(habit.days, dayIso)) return
        byId[habit.id] = habit
    }

    for (habit in todayHabits) {
        include(enrichHabit(habit, catalog[habit.id]))
    }

    for (id in done) {
        if (byId.containsKey(id)) continue
        val habit = catalog[id] ?: continue
        // Completions can exist on off-days; still show them if completed that day.
        if (habit.stoppedAt.isNotBlank() && !isZeroDate(habit.stoppedAt)) continue
        if (!habitStartedBy(habit.startsOn, dayIso)) continue
        byId[id] = habit
    }

    for (habit in catalog.values) {
        if (byId.containsKey(habit.id)) continue
        include(habit)
    }

    return byId.values
        .map { habit ->
            HabitItem(
                id = habit.id,
                title = habit.title,
                doneToday = done.contains(habit.id),
                icon = habit.icon.ifBlank { iconSlugFromUrl(habit.iconUrl) },
                iconUrl = habit.iconUrl,
            )
        }
        .sortedBy { it.title.lowercase(Locale.US) }
}

private fun enrichHabit(habit: CatalogHabit, cached: CatalogHabit?): CatalogHabit {
    if (cached == null) return habit
    return habit.copy(
        title = habit.title.ifBlank { cached.title },
        icon = habit.icon.ifBlank { cached.icon },
        iconUrl = habit.iconUrl.ifBlank { cached.iconUrl },
        days = habit.days.ifEmpty { cached.days },
        startsOn = habit.startsOn ?: cached.startsOn,
        stoppedAt = habit.stoppedAt.ifBlank { cached.stoppedAt },
    )
}

/**
 * HEY habit `days` uses JS getDay() indices: Sun=0 … Sat=6.
 * Matching multiple weekday encodings (Mon=0 vs Mon=1) falsely includes adjacent days.
 */
private fun habitScheduledForDate(days: List<Int>, dayIso: String): Boolean {
    if (days.isEmpty()) return true
    val date = LocalDate.parse(dayIso)
    val jsDow = date.dayOfWeek.value % 7 // Mon=1…Sat=6, Sun=0
    return days.contains(jsDow)
}

private fun habitStartedBy(startsOn: String?, dayIso: String): Boolean {
    val start = startsOn?.take(10).orEmpty()
    if (start.isBlank() || isZeroDate(start)) return true
    return dayIso >= start
}

private fun parseSometimeThisWeekTodos(
    html: String,
    weekStart: LocalDate,
    zone: ZoneId,
): List<TodoItem> {
    if (html.isBlank()) return emptyList()
    val epoch = weekStart.atStartOfDay(zone).toEpochSecond()
    val marker = """id="todo-items-$epoch""""
    val start = html.indexOf(marker)
    if (start < 0) return emptyList()
    val next = html.indexOf("""id="todo-items-""", start + marker.length)
    val section = if (next >= 0) {
        html.substring(start, next)
    } else {
        html.substring(start, minOf(html.length, start + 80_000))
    }

    val results = mutableListOf<TodoItem>()
    val todoBlocks = Regex(
        """id="calendar_recording_(\d+)"([\s\S]*?)(?=id="calendar_recording_|\z)""",
        RegexOption.IGNORE_CASE,
    )
    for (match in todoBlocks.findAll(section)) {
        val id = match.groupValues[1].toLongOrNull() ?: continue
        val block = match.groupValues[2]
        val completed = block.contains("todo__checkbox--checked", ignoreCase = true) ||
            Regex("""aria-checked="true"""", RegexOption.IGNORE_CASE).containsMatchIn(block) ||
            block.contains("todo--completed", ignoreCase = true)
        val title = Regex(
            """class="[^"]*todo__text[^"]*"[^>]*>([\s\S]*?)</a>""",
            RegexOption.IGNORE_CASE,
        ).find(block)?.groupValues?.get(1)
            ?.let { stripHtml(decodeHtmlEntities(it)).trim() }
            .orEmpty()
            .ifBlank {
                Regex(
                    """todo__checkbox[^>]*aria-label="([^"]+)"""",
                    RegexOption.IGNORE_CASE,
                ).find(block)?.groupValues?.get(1)
                    ?.let { decodeHtmlEntities(it).trim() }
                    .orEmpty()
            }
        if (title.isBlank() || title.equals("Complete", ignoreCase = true)) continue
        results.add(
            TodoItem(
                id = id,
                title = title,
                startsAt = weekStart.toString(),
                completed = completed,
            ),
        )
    }
    return results.distinctBy { it.id }
}

private data class IdentityPrefs(
    val firstWeekDay: DayOfWeek,
    val timeZone: ZoneId,
)

/** Incomplete first, then oldest-first within each group. */
private val weekTodoOrder = compareBy<TodoItem> { it.completed }
    .thenBy { todoOldestKey(it) }
    .thenBy { it.id }

private fun todoOldestKey(todo: TodoItem): String =
    todo.createdAt.orEmpty().ifBlank { todo.startsAt.orEmpty() }.ifBlank { "9999" }

private fun parseOpenTodos(payload: JsonObject): List<TodoItem> {
    val todos = mutableListOf<TodoItem>()
    fun add(obj: JsonObject) {
        val unwrapped = unwrapRecordingObj(obj)
        val id = unwrapped.longId() ?: return
        val completedAt = unwrapped.string("completed_at")
        val completed = !isZeroDate(completedAt)
        todos.add(
            TodoItem(
                id = id,
                title = unwrapped.string("title").orEmpty(),
                startsAt = unwrapped.string("starts_at") ?: unwrapped.string("starts_on"),
                completed = completed,
                createdAt = unwrapped.string("created_at"),
            ),
        )
    }
    recordingsForType(payload, "Calendar::Todo").forEach(::add)
    if (todos.isEmpty()) {
        payload.values.forEach { value ->
            val arr = value as? JsonArray ?: return@forEach
            arr.forEach { el ->
                val obj = el as? JsonObject ?: return@forEach
                val unwrapped = unwrapRecordingObj(obj)
                if (unwrapped.string("type") == "CalendarTodo" ||
                    unwrapped.string("type") == "Calendar::Todo"
                ) {
                    add(unwrapped)
                }
            }
        }
    }
    return todos.distinctBy { it.id }
}

private fun isZeroDate(value: String?): Boolean {
    if (value.isNullOrBlank()) return true
    return value.startsWith("0001-01-01")
}

private fun findPersonalCalendarId(root: JsonElement): Long? {
    val calendars = unwrapCalendars(root)
    calendars.firstOrNull { it.boolean("personal") == true }?.longId()?.let { return it }
    calendars.firstOrNull {
        it.string("name")?.equals("personal", ignoreCase = true) == true
    }?.longId()?.let { return it }
    return calendars.firstOrNull()?.longId()
}

private fun unwrapCalendars(root: JsonElement): List<JsonObject> {
    when (root) {
        is JsonArray -> {
            return root.mapNotNull { el ->
                when (el) {
                    is JsonObject -> (el["calendar"] as? JsonObject) ?: el
                    else -> null
                }
            }
        }
        is JsonObject -> {
            val list = when {
                root["calendars"] is JsonArray -> root["calendars"]!!.jsonArray
                root["data"] is JsonArray -> root["data"]!!.jsonArray
                root["data"] is JsonObject &&
                    (root["data"] as JsonObject)["calendars"] is JsonArray ->
                    (root["data"] as JsonObject)["calendars"]!!.jsonArray
                else -> return emptyList()
            }
            return list.mapNotNull { el ->
                when (el) {
                    is JsonObject -> (el["calendar"] as? JsonObject) ?: el
                    else -> null
                }
            }
        }
        else -> return emptyList()
    }
}

private fun extractJournalContent(root: JsonElement): String {
    val obj = when (root) {
        is JsonObject -> root
        else -> return root.toString().trim().removeSurrounding("\"")
    }
    obj.string("content")?.let { return stripHtml(it) }
    (obj["calendar_journal_entry"] as? JsonObject)?.string("content")?.let { return stripHtml(it) }
    (obj["journal_entry"] as? JsonObject)?.string("content")?.let { return stripHtml(it) }
    (obj["recording"] as? JsonObject)?.let { return extractJournalContent(it) }
    (obj["data"] as? JsonObject)?.let { return extractJournalContent(it) }
    return ""
}

/** Match hey-sdk: pull Trix hidden input value from the journal edit page. */
private fun extractTrixJournalContent(html: String): String {
    val inputRegex = Regex(
        """<input\b[^>]*\bid\s*=\s*["']([^"']*journal[^"']*trix_input[^"']*)["'][^>]*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    val match = inputRegex.find(html) ?: run {
        // attribute order may put value before id
        val alt = Regex(
            """<input\b[^>]*\bvalue\s*=\s*["']([^"']*)["'][^>]*\bid\s*=\s*["'][^"']*journal[^"']*trix_input[^"']*["'][^>]*>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(html)
        val encoded = alt?.groupValues?.getOrNull(1).orEmpty()
        return stripHtml(decodeHtmlEntities(encoded)).trim()
    }
    val tag = match.value
    val valueMatch = Regex(
        """\bvalue\s*=\s*["']([^"']*)["']""",
        RegexOption.IGNORE_CASE,
    ).find(tag)
    val encoded = valueMatch?.groupValues?.getOrNull(1).orEmpty()
    return stripHtml(decodeHtmlEntities(encoded)).trim()
}

private fun decodeHtmlEntities(value: String): String {
    if (value.isEmpty()) return value
    return value
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&nbsp;", " ")
        .replace(Regex("&#(\\d+);")) { m ->
            m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value
        }
        .replace(Regex("&#x([0-9a-fA-F]+);")) { m ->
            m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: m.value
        }
}

private fun parseCoverStickies(root: JsonElement): List<StickyNote> {
    val items: JsonArray = when (root) {
        is JsonArray -> root
        is JsonObject -> (root["stickies"] as? JsonArray) ?: return emptyList()
        else -> return emptyList()
    }
    return items.mapNotNull { el ->
        val obj = el as? JsonObject ?: return@mapNotNull null
        val sticky = (obj["sticky"] as? JsonObject) ?: obj
        val id = sticky.longId() ?: return@mapNotNull null
        if (id <= 0L) return@mapNotNull null
        val raw = sticky.string("body")
            ?: sticky.string("content")
            ?: sticky.string("text")
            ?: ""
        val content = stripHtml(raw).trim()
        if (content.isEmpty()) return@mapNotNull null
        StickyNote(noteId = id, content = content, postingId = null)
    }
}

private fun heyWeekdayToDayOfWeek(heyDay: Int): DayOfWeek = when (heyDay) {
    0 -> DayOfWeek.SUNDAY
    1 -> DayOfWeek.MONDAY
    2 -> DayOfWeek.TUESDAY
    3 -> DayOfWeek.WEDNESDAY
    4 -> DayOfWeek.THURSDAY
    5 -> DayOfWeek.FRIDAY
    6 -> DayOfWeek.SATURDAY
    else -> DayOfWeek.MONDAY
}

/** Inclusive start of the week containing [day], given HEY/identity first weekday. */
private fun weekStartFor(day: LocalDate, first: DayOfWeek): LocalDate {
    val diff = (day.dayOfWeek.value - first.value + 7) % 7
    return day.minusDays(diff.toLong())
}

private fun stripHtml(value: String): String {
    if (!value.contains('<')) return value
    return value
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</p>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.long(key: String): Long? =
    (this[key] as? JsonPrimitive)?.longOrNull

private fun JsonObject.longId(): Long? = long("id")

private fun JsonObject.boolean(key: String): Boolean? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    return primitive.contentOrNull?.toBooleanStrictOrNull()
}
