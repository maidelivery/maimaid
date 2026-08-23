package org.rhythmeta.maimaid.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class WidgetUpdateCoordinator(context: Context) {
    private val applicationContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val requests = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (request in requests) {
                try {
                    MaimaidWidget().updateAll(applicationContext)
                    Log.d(TAG, "Widget update requested successfully")
                } catch (throwable: Throwable) {
                    Log.e(TAG, "Widget update failed", throwable)
                }
            }
        }
    }

    fun requestUpdate() {
        requests.trySend(Unit)
    }

    private companion object {
        const val TAG = "MaimaidWidget"
    }
}
