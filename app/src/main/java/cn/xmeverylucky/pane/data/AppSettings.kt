package cn.xmeverylucky.pane.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.ZoneId

class AppSettings(context: Context) {

    data class ZoneState(
        val home: String?,
        val current: String?,
        val recents: List<String>,
    ) {
        val isConfigured: Boolean get() = home != null

        val isAway: Boolean get() = current != null && current != home

        val activeId: String? get() = current ?: home
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _zone = MutableStateFlow(read())
    val zone: StateFlow<ZoneState> = _zone.asStateFlow()

    private val _privacyAccepted = MutableStateFlow(prefs.getBoolean(KEY_PRIVACY_ACCEPTED, false))
    val privacyAccepted: StateFlow<Boolean> = _privacyAccepted.asStateFlow()

    private val _onboardingCompleted = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false))
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    private val _reliabilityGuideDismissed =
        MutableStateFlow(prefs.getBoolean(KEY_RELIABILITY_GUIDE_DISMISSED, false))
    val reliabilityGuideDismissed: StateFlow<Boolean> = _reliabilityGuideDismissed.asStateFlow()

    fun alarmZone(): ZoneId = parse(_zone.value.activeId) ?: ZoneId.systemDefault()

    fun homeZone(): ZoneId? = parse(_zone.value.home)

    fun acceptPrivacy() {
        prefs.edit().putBoolean(KEY_PRIVACY_ACCEPTED, true).apply()
        _privacyAccepted.value = true
    }

    fun revokePrivacy() {
        prefs.edit().putBoolean(KEY_PRIVACY_ACCEPTED, false).apply()
        _privacyAccepted.value = false
    }

    fun completeOnboarding() {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
        _onboardingCompleted.value = true
    }

    fun dismissReliabilityGuide() {
        prefs.edit().putBoolean(KEY_RELIABILITY_GUIDE_DISMISSED, true).apply()
        _reliabilityGuideDismissed.value = true
    }

    fun hasPromptedNotification(): Boolean = prefs.getBoolean(KEY_NOTIFICATION_PROMPTED, false)

    fun markNotificationPrompted() {
        prefs.edit().putBoolean(KEY_NOTIFICATION_PROMPTED, true).apply()
    }

    fun setHome(id: String) = write(_zone.value.copy(home = id, current = null).withRecent(id))

    fun switchTo(id: String) {
        val s = _zone.value
        write(s.copy(current = if (id == s.home) null else id).withRecent(id))
    }

    fun switchBackHome() = write(_zone.value.copy(current = null))

    private fun ZoneState.withRecent(id: String) =
        copy(recents = (listOf(id) + recents.filterNot { it == id }).take(MAX_RECENTS))

    private fun write(state: ZoneState) {
        prefs.edit()
            .putString(KEY_HOME, state.home)
            .putString(KEY_CURRENT, state.current)
            .putString(KEY_RECENTS, state.recents.joinToString(","))
            .apply()
        _zone.value = state
    }

    private fun read() = ZoneState(
        home = prefs.getString(KEY_HOME, null)?.takeIf { parse(it) != null },
        current = prefs.getString(KEY_CURRENT, null)?.takeIf { parse(it) != null },
        recents = prefs.getString(KEY_RECENTS, "").orEmpty().split(',').filter { it.isNotBlank() && parse(it) != null },
    )

    private fun parse(id: String?): ZoneId? = id?.let { runCatching { ZoneId.of(it) }.getOrNull() }

    companion object {
        const val PREFS_NAME = "settings"
        private const val KEY_HOME = "home_zone"
        private const val KEY_CURRENT = "current_zone"
        private const val KEY_RECENTS = "recent_zones"
        private const val KEY_PRIVACY_ACCEPTED = "privacy_accepted"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_NOTIFICATION_PROMPTED = "notification_prompted"
        private const val KEY_RELIABILITY_GUIDE_DISMISSED = "reliability_guide_dismissed"
        private const val MAX_RECENTS = 5
    }
}
