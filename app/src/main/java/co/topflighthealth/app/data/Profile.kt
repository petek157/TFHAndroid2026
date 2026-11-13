package co.topflighthealth.app.data

import co.topflighthealth.app.domain.MacroHistoryEntry
import co.topflighthealth.app.domain.MacroInput
import co.topflighthealth.app.domain.PlanPolicy
import co.topflighthealth.app.api.ApiFactory
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The user record (the iOS `User` struct), read from the raw JSON the server
 * sends. Keeping [raw] means saving never drops fields this app doesn't model.
 */
class Profile(val raw: JsonObject) {
    private fun str(key: String) = (raw[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun num(key: String) = (raw[key] as? JsonPrimitive)?.doubleOrNull
    private fun int(key: String) = num(key)?.toInt()

    val id: String get() = str("_id").orEmpty()
    val email: String get() = str("email").orEmpty()
    val firstname: String? get() = str("firstname")
    val lastname: String? get() = str("lastname")
    val phone: String? get() = str("phone")
    val role: String get() = str("role") ?: "client"
    val sex: String? get() = str("sex")
    val feet: Int? get() = int("feet")
    val inches: Int? get() = int("inches")
    val currentWeight: Double? get() = num("currentWeight")
    val goalWeight: Double? get() = num("goalWeight")
    val objective: String? get() = str("objective")
    val activityLevel: String? get() = str("activityLevel")
    val balanceOfCalories: String? get() = str("balanceOfCalories")
    val proteinMultiplier: Double? get() = num("proteinMultiplier")
    val carbMultiplier: Double? get() = num("carbMultiplier")
    val wristCircumference: Double? get() = num("wristCircumference")
    val waterGoal: Double? get() = num("waterGoal")
    val useTargetWeightForGoal: Boolean
        get() = (raw["useTargetWeightForGoal"] as? JsonPrimitive)?.booleanOrNull ?: false

    val birthdayInstant: Instant? get() = str("birthday")?.let { runCatching { Instant.parse(it) }.getOrNull() }
    /** The birthday as a calendar day on this phone, as iOS shows it. */
    val birthday: LocalDate? get() = birthdayInstant?.atZone(ZoneId.systemDefault())?.toLocalDate()
    val createdDay: String
        get() = str("_created_at")?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?.atZone(ZoneId.systemDefault())?.toLocalDate()?.toString() ?: "1970-01-01"

    val macroHistory: List<MacroHistoryEntry>
        get() = (raw["macroHistory"] as? JsonArray)?.let {
            runCatching { ApiFactory.json.decodeFromJsonElement(ListSerializer(MacroHistoryEntry.serializer()), it) }.getOrNull()
        }.orEmpty()

    /** Coach/user goal, else 0.033 L per kg of current weight, else 80 oz (iOS User.effectiveWaterGoal). */
    val effectiveWaterGoal: Double
        get() {
            waterGoal?.takeIf { it > 0 }?.let { return it }
            currentWeight?.takeIf { it > 0 }?.let { return it * 0.453592 * 0.033 * 33.814 }
            return 80.0
        }

    val displayName: String
        get() = listOfNotNull(firstname, lastname).joinToString(" ").ifBlank { email }

    val macroInput: MacroInput
        get() = MacroInput(birthday, sex, feet, inches, currentWeight, goalWeight, activityLevel, objective,
            proteinMultiplier, carbMultiplier, balanceOfCalories)

    val policy: PlanPolicy
        get() = PlanPolicy(objective, activityLevel, goalWeight, balanceOfCalories, proteinMultiplier, carbMultiplier)

    /** A copy with [changes] applied on top, as the server would after a save. */
    fun with(changes: Map<String, JsonElement>) = Profile(JsonObject(raw + changes))

    companion object {
        val POLICY_KEYS = setOf("objective", "activityLevel", "goalWeight", "balanceOfCalories", "proteinMultiplier", "carbMultiplier")

        fun from(element: JsonElement): Profile? = (element as? JsonObject)?.let(::Profile)

        fun value(v: String?): JsonElement = v?.let(::JsonPrimitive) ?: JsonNull
        fun value(v: Number?): JsonElement = v?.let(::JsonPrimitive) ?: JsonNull
        fun value(v: Instant?): JsonElement = v?.let { JsonPrimitive(it.toString()) } ?: JsonNull

        /** A local calendar day as the instant of its midnight on this phone (how iOS stores dates). */
        fun localMidnight(day: LocalDate): Instant = day.atStartOfDay(ZoneId.systemDefault()).toInstant()
    }
}

