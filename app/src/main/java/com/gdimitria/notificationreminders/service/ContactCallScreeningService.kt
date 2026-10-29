package com.gdimitria.notificationreminders.service

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import com.gdimitria.notificationreminders.engine.ContactExceptions

/**
 * Hears about incoming calls before the phone rings, so a contact exception can switch the sound profile in
 * time. Every call is allowed through unchanged. Active once the app is the phone's Caller ID and spam app.
 */
class ContactCallScreeningService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        val incoming = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            details.callDirection == Call.Details.DIRECTION_INCOMING
        if (incoming) ContactExceptions.onIncomingCall(this, details.handle?.schemeSpecificPart)
        respondToCall(details, CallResponse.Builder().build())
    }
}
