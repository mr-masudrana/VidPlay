package com.example.vidplay

import android.app.Application
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class VidPlayApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(MyDownloader.create(), Localization("bn", "BD"), ContentCountry("BD"))
        Notif.ensureChannels(this)
        val p = Prefs(this)
        NotifyScheduler.apply(this, p.notifyEnabled, p.notifyHours, replace = false)
    }
}
