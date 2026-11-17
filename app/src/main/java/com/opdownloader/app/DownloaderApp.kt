package com.opdownloader.app

import android.app.Application
import com.opdownloader.app.data.Store
import com.opdownloader.app.engine.AppContextHolder
import com.opdownloader.app.engine.DownloadManager

class DownloaderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContextHolder.context = this
        Store.init(this)
        DownloadManager.init(this)
    }
}
