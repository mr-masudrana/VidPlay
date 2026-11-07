# TubeX

NewPipe Extractor ভিত্তিক YouTube-স্টাইল ভিডিও প্লেয়ার (Kotlin + Jetpack Compose + Media3 + Room)।

## GitHub Actions দিয়ে বিল্ড
1. নতুন GitHub রিপোজিটরি বানিয়ে এই ফোল্ডারের সব ফাইল push করুন (ব্রাঞ্চ `main`)।
2. রিপোজিটরির **Actions** ট্যাবে "Build APK" ওয়ার্কফ্লো নিজে চলবে (বা **Run workflow** চাপুন)।
3. বিল্ড শেষ হলে রানের পেজের নিচে **Artifacts → tubex-debug-apk** থেকে APK নামান।

## লোকালি বিল্ড
Android Studio-তে প্রজেক্ট খুলুন, অথবা Gradle 8.9 + JDK 17 দিয়ে `gradle assembleDebug`।

## লাইসেন্স
GPL-3.0 (`LICENSE`) — NewPipe Extractor GPL-3 হওয়ায় এই অ্যাপও GPL-3। গোপনীয়তা: `privacy.md`।

## ভিডিও না চললে
YouTube ঘন ঘন বদলায়। `app/build.gradle.kts`-এ NewPipeExtractor-এর ভার্সন সর্বশেষ রিলিজে (github.com/TeamNewPipe/NewPipeExtractor/releases) আপডেট করুন।

## কোডের কাঠামো (`app/src/main/java/com/tubex/app/`)

```
com.tubex.app
├── TubeXApp.kt, MainActivity.kt     অ্যাপ এন্ট্রি পয়েন্ট
├── util/            Lang (বাংলা/ইংরেজি স্ট্রিং), TextUtil, Format (সংখ্যা/সময়/URL ফরম্যাট)
├── data/
│   ├── model/       ডোমেইন মডেল (VideoItem, ChannelData, CommentItem …)
│   ├── local/       Room: AppDb, AppDao, entity/ + Prefs (SharedPreferences)
│   ├── remote/      YtRepository (NewPipe Extractor), Extras (SponsorBlock/DeArrow), MyDownloader
│   ├── cache/       AvatarCache, SubsCache
│   ├── backup/      ProfileBackup, NewPipeImport
│   ├── recommend/   Recommender, Blocks
│   └── search/      SearchFilter
├── viewmodel/       MainViewModel, UiModels
├── ui/
│   ├── theme/       রং, Palette, YtTheme
│   ├── components/  VideoCard, Avatars, Skeleton (পুনঃব্যবহারযোগ্য কম্পোনেন্ট)
│   ├── navigation/  PredictiveBack
│   ├── main/        YtApp, MainScaffold, BackPreviews
│   ├── library/     LibraryScreen, SubsTab, HistoryFavorites, PlaylistsSection, DownloadsSection, LibraryComponents
│   ├── settings/    SettingsScreen, SettingsPage, AboutSection, SettingsComponents
│   └── home/ · channel/ · playlist/ · splash/   স্ক্রিন
├── player/
│   ├── PlayerHolder.kt
│   ├── ui/          PlayerScreen, PlayerSheets, PlayerDetails, CommentsSheet, DescriptionSheet, PlayerMorph, MiniPlayer
│   ├── gesture/     Gestures
│   └── service/     PlaybackService, CastOptionsProvider
├── download/        DownloadEngine, DownloadService, Downloads, Muxer, MediaExport
├── notification/    নতুন ভিডিওর নোটিফিকেশন + ওয়ার্কার
└── update/          Updater, UpdateDialog
```

**নির্ভরতার নিয়ম:** `ui` → `viewmodel` → `data`/`download`/`player`। `data` কখনও `ui` বা `viewmodel` ইম্পোর্ট করবে না।
