package org.rhythmeta.maimaid

import android.app.Application
import org.rhythmeta.maimaid.core.AppContainer

class MaimaidApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
