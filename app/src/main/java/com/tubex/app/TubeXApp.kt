package com.tubex.app

import android.app.Application
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class TubeXApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val p = Prefs(this)
        L.apply(p.lang)
        NewPipe.init(
            MyDownloader.create(),
            if (L.en) Localization("en", "US") else Localization("bn", "BD"),
            ContentCountry("BD")
        )
        Notif.ensureChannels(this)
        NotifyScheduler.apply(this, p.notifyEnabled, p.notifyHours, replace = false)
    }
}
