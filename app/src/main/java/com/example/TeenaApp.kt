package com.example

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration

class TeenaApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                container.pdfEngine.onTrimMemory(level)
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() {
                container.pdfEngine.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
            }
        })
    }
}
