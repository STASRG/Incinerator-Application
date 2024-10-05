package com.stasrg.incinerator.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.stasrg.incinerator.screen.checkNetworkConnection

// ConnectivityReceiver to listen for network changes
class ConnectivityReceiver(private val onNetworkChange: (Boolean) -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val isConnected = checkNetworkConnection(context)
        onNetworkChange(isConnected)
    }
}
