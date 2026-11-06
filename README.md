# TubeX

NewPipe Extractor ভিত্তিক YouTube-স্টাইল ভিডিও প্লেয়ার (Kotlin + Jetpack Compose + Media3 + Room)।

## GitHub Actions দিয়ে বিল্ড
1. নতুন GitHub রিপোজিটরি বানিয়ে এই ফোল্ডারের সব ফাইল push করুন (ব্রাঞ্চ `main`)।
2. রিপোজিটরির **Actions** ট্যাবে "Build APK" ওয়ার্কফ্লো নিজে চলবে (বা **Run workflow** চাপুন)।
3. বিল্ড শেষ হলে রানের পেজের নিচে **Artifacts → tubex-debug-apk** থেকে APK নামান।

## লোকালি বিল্ড
Android Studio-তে প্রজেক্ট খুলুন, অথবা Gradle 8.9 + JDK 17 দিয়ে `gradle assembleDebug`।

## ভিডিও না চললে
YouTube ঘন ঘন বদলায়। `app/build.gradle.kts`-এ NewPipeExtractor-এর ভার্সন সর্বশেষ রিলিজে (github.com/TeamNewPipe/NewPipeExtractor/releases) আপডেট করুন।

## কোডের কাঠামো (`app/src/main/java/com/tubex/app/`)
| প্যাকেজ | কী আছে |
|---|---|
| `core` | `MainActivity`, `TubeXApp`, `MainViewModel`, `Prefs`, `Lang`, আপডেটার, নতুন ভিডিওর নোটিফিকেশন |
| `player` | প্লেয়ার UI, জেসচার, মিনি/ভাসমান প্লেয়ার, `PlaybackService`, Cast |
| `ui` | হোম, চ্যানেল, লাইব্রেরি, প্লেলিস্ট, সেটিংস |
| `data` | Room ডাটাবেস, `YtRepository`, রিকমেন্ডার, ক্যাশ, ব্যাকআপ/ইম্পোর্ট |
| `download` | ডাউনলোড ইঞ্জিন ও সার্ভিস, মাক্সার, মিডিয়া এক্সপোর্ট |
