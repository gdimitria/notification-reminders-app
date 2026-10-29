package com.gdimitria.notificationreminders.engine

import android.content.Context
import android.util.Log
import com.gdimitria.notificationreminders.data.Store
import com.gdimitria.notificationreminders.model.WifiRule

/** Applies, locks and restores sound profiles when the phone joins or leaves a Wi-Fi network with a rule. */
object WifiRules {

    private const val TAG = "WifiRules"

    fun onNetworkChanged(context: Context, ssid: String?) {
        val store = Store.get(context)
        val (leave, enter) = store.soundProfiles.value.wifiTransition(ssid)

        if (leave != null) {
            val sound = store.soundProfiles.value
            if (sound.lock?.byWifiRuleId == leave.id) SoundLock.unlock(context)
            // Back to the profile that was selected before connecting, even if it was changed by hand meanwhile.
            sound.profile(sound.wifiPreviousProfileId)?.let { SoundProfileApplier.apply(context, it) }
            store.updateSoundProfiles { it.copy(wifiActiveRuleId = null, wifiPreviousProfileId = null) }
        }

        if (enter != null) {
            val before = store.soundProfiles.value
            val profile = before.profile(enter.profileId)
            if (profile == null) {
                Log.w(TAG, "Wi-Fi rule ${enter.ssid} points to a deleted profile")
            } else {
                store.updateSoundProfiles { it.copy(wifiActiveRuleId = enter.id, wifiPreviousProfileId = before.activeId) }
                val problems = SoundProfileApplier.apply(context, profile)
                // A lock the user set by hand stays theirs; otherwise the rule locks until the network is left.
                if (enter.lock && SoundProfileApplier.Problem.LOCKED !in problems && before.lock == null) {
                    SoundLock.lock(context, wifiRuleId = enter.id)
                }
            }
        }
        if (leave != null || enter != null) ProfileStatusNotifier.update(context)
    }

    /** A rule was edited: changes to the rule of the network the phone is on take effect at once. */
    fun onRuleChanged(context: Context, old: WifiRule, new: WifiRule) {
        val sound = Store.get(context).soundProfiles.value
        val ssid = WifiWatcher.ssid.value
        if (sound.wifiActiveRuleId != new.id || !new.enabled || new.ssid != ssid) {
            onNetworkChanged(context, ssid)
            return
        }
        if (old.profileId != new.profileId) {
            sound.profile(new.profileId)?.let { SoundProfileApplier.apply(context, it) }
        }
        if (old.lock != new.lock) {
            val lock = Store.get(context).soundProfiles.value.lock
            when {
                new.lock && lock == null -> SoundLock.lock(context, wifiRuleId = new.id)
                !new.lock && lock?.byWifiRuleId == new.id -> SoundLock.unlock(context)
            }
        }
        ProfileStatusNotifier.update(context)
    }
}
