# VidPlay

NewPipe Extractor ভিত্তিক YouTube-স্টাইল ভিডিও প্লেয়ার (Kotlin + Jetpack Compose + Media3 + Room)।

## GitHub Actions দিয়ে বিল্ড
1. নতুন GitHub রিপোজিটরি বানিয়ে এই ফোল্ডারের সব ফাইল push করুন (ব্রাঞ্চ `main`)।
2. রিপোজিটরির **Actions** ট্যাবে "Build APK" ওয়ার্কফ্লো নিজে চলবে (বা **Run workflow** চাপুন)।
3. বিল্ড শেষ হলে রানের পেজের নিচে **Artifacts → vidplay-debug-apk** থেকে APK নামান।

## লোকালি বিল্ড
Android Studio-তে প্রজেক্ট খুলুন, অথবা Gradle 8.9 + JDK 17 দিয়ে `gradle assembleDebug`।

## ভিডিও না চললে
YouTube ঘন ঘন বদলায়। `app/build.gradle.kts`-এ NewPipeExtractor-এর ভার্সন সর্বশেষ রিলিজে (github.com/TeamNewPipe/NewPipeExtractor/releases) আপডেট করুন।
