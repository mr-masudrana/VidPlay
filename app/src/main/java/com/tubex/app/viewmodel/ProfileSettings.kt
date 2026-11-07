package com.tubex.app.viewmodel

import com.tubex.app.data.local.Prefs

import org.json.JSONObject

/**
 * প্রোফাইল ব্যাকআপের সেটিংস অংশ: বর্তমান সেটিংস → JSON এবং JSON → সেটিংস প্রয়োগ।
 * (ডেটা অংশ — ইতিহাস, ফেভারিট ইত্যাদি — ProfileBackup-এ।) JSON-এর কী-গুলো আগের মতোই, তাই পুরনো ব্যাকআপও চলবে।
 */
object ProfileSettings {
    fun toJson(vm: MainViewModel, prefs: Prefs): JSONObject =
        JSONObject().put("theme", vm.themeIndex).put("sponsorblock", vm.sponsorOn).put("ryd", vm.rydOn)
            .put("captionSize", vm.captionSizeIndex).put("captionLang", prefs.captionLang)
            .put("qualityWifi", vm.qualityWifi).put("qualityMobile", vm.qualityMobile).put("skipSilence", vm.skipSilence)
            .put("captionColor", vm.captionColorIndex).put("captionBg", vm.captionBgIndex).put("lang", vm.langMode)
            .put("autoplay", vm.autoplay).put("banglaAudio", vm.banglaAudio)
            .put("notifyEnabled", vm.notifyOn).put("notifyHours", vm.notifyHours)
            .put("dearrowTitles", prefs.dearrowTitles).put("dearrowThumbs", prefs.dearrowThumbs)
            .put("floatMini", vm.floatingMini)
            .put("themeAuto", vm.themeAuto).put("accentColor", vm.accentColor).put("dataSaver", vm.dataSaver)
            .put("backgroundPlay", vm.backgroundPlay).put("pipPlay", vm.pipOn).put("wifiOnlyDownload", vm.wifiOnlyDl)
            .put("parallelDownloads", vm.parallelDl).put("saveSearchHistory", vm.saveSearchHist)
            .put("saveWatchHistory", vm.saveWatchHist)

    /** JSON-এ যে কী নেই, সেটির বর্তমান মান অপরিবর্তিত থাকে */
    fun apply(vm: MainViewModel, prefs: Prefs, it: JSONObject) {
        vm.chooseTheme(it.optInt("theme", vm.themeIndex))
        vm.toggleSponsor(it.optBoolean("sponsorblock", vm.sponsorOn))
        vm.toggleRyd(it.optBoolean("ryd", vm.rydOn))
        vm.chooseCaptionSize(it.optInt("captionSize", vm.captionSizeIndex))
        vm.chooseDefaultQuality(true, it.optInt("qualityWifi", vm.qualityWifi))
        vm.chooseDefaultQuality(false, it.optInt("qualityMobile", vm.qualityMobile))
        vm.toggleSkipSilence(it.optBoolean("skipSilence", vm.skipSilence))
        prefs.captionLang = it.optString("captionLang", "")
        vm.chooseCaptionColor(it.optInt("captionColor", vm.captionColorIndex))
        vm.chooseCaptionBg(it.optInt("captionBg", vm.captionBgIndex))
        vm.chooseLang(it.optInt("lang", vm.langMode))
        if (it.optBoolean("autoplay", vm.autoplay) != vm.autoplay) vm.toggleAutoplay()
        vm.applyBanglaAudio(it.optBoolean("banglaAudio", vm.banglaAudio))
        vm.toggleNotify(it.optBoolean("notifyEnabled", vm.notifyOn))
        vm.chooseNotifyHours(it.optInt("notifyHours", vm.notifyHours))
        vm.toggleDearrowTitles(it.optBoolean("dearrowTitles", prefs.dearrowTitles))
        vm.toggleDearrowThumbs(it.optBoolean("dearrowThumbs", prefs.dearrowThumbs))
        vm.toggleFloatingMini(it.optBoolean("floatMini", vm.floatingMini))
        vm.applyThemeAuto(it.optBoolean("themeAuto", vm.themeAuto))
        vm.chooseAccent(it.optInt("accentColor", vm.accentColor))
        vm.applyDataSaver(it.optBoolean("dataSaver", vm.dataSaver))
        vm.applyBackgroundPlay(it.optBoolean("backgroundPlay", vm.backgroundPlay))
        vm.setPip(it.optBoolean("pipPlay", vm.pipOn))
        vm.setWifiOnlyDownload(it.optBoolean("wifiOnlyDownload", vm.wifiOnlyDl))
        vm.chooseParallelDownloads(it.optInt("parallelDownloads", vm.parallelDl))
        vm.setSaveSearchHistory(it.optBoolean("saveSearchHistory", vm.saveSearchHist))
        vm.setSaveWatchHistory(it.optBoolean("saveWatchHistory", vm.saveWatchHist))
    }
}
