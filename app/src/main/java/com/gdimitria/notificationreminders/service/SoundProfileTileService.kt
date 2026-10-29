package com.gdimitria.notificationreminders.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.gdimitria.notificationreminders.R
import com.gdimitria.notificationreminders.data.Store
import androidx.core.graphics.drawable.IconCompat
import com.gdimitria.notificationreminders.engine.ProfileStatusNotifier
import com.gdimitria.notificationreminders.engine.SoundLock
import com.gdimitria.notificationreminders.engine.SoundProfileApplier
import com.gdimitria.notificationreminders.ui.soundProfileName

/** Quick settings tile showing the active sound profile; a tap switches to the next one. */
class SoundProfileTileService : TileService() {

    override fun onStartListening() = refresh()

    override fun onClick() {
        if (SoundLock.isLocked(this)) {
            Toast.makeText(this, R.string.sound_locked_toast, Toast.LENGTH_LONG).show()
            return
        }
        val next = Store.get(this).soundProfiles.value.next() ?: return
        val problems = SoundProfileApplier.apply(this, next)
        if (problems.isNotEmpty()) {
            Toast.makeText(this, R.string.sound_profile_partly_applied, Toast.LENGTH_LONG).show()
        }
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val sound = Store.get(this).soundProfiles.value
        val active = sound.profile(sound.activeId)
        tile.label = active?.let { soundProfileName(it) } ?: getString(R.string.sound_profiles)
        tile.icon = (active?.let { ProfileStatusNotifier.icon(this, it) }
            ?: IconCompat.createWithResource(this, R.drawable.ic_sound_profile)).toIcon(this)
        tile.state = if (active != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
