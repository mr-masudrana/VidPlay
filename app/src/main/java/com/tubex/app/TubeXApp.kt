package com.tubex.app

import com.tubex.app.data.local.Prefs
import com.tubex.app.data.remote.MyDownloader
import com.tubex.app.util.L
import com.tubex.app.notification.Notif
import com.tubex.app.notification.NotifyScheduler
import com.tubex.app.update.UpdateScheduler

import android.app.Application
import com.tubex.app.data.potoken.PoTokenProviderImpl
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
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
        // YouTube-এর BotGuard/poToken: ব্যর্থ হলে null ফেরত যায়, তাই আগের আচরণ অক্ষুণ্ণ থাকে
        PoTokenProviderImpl.init(this)
        PoTokenProviderImpl.enabled = p.poToken
        YoutubeStreamExtractor.setPoTokenProvider(PoTokenProviderImpl)
        Notif.ensureChannels(this)
        // সিডিউল ভার্সন 2: ওয়ার্কার ক্লাসের প্যাকেজ বদলেছে, তাই পুরনো সিডিউল একবার নতুন ক্লাসে replace করা হয়
        // (ভার্সন 1 = পুরনো ১২ ঘণ্টা → ২৪ ঘণ্টা মাইগ্রেশন)
        val replaceSchedules = p.updateSchedVersion < 2
        NotifyScheduler.apply(this, p.notifyEnabled, p.notifyHours, replace = replaceSchedules)
        UpdateScheduler.apply(this, p.updateNotify, replace = replaceSchedules)
        p.updateSchedVersion = 2
    }
}
