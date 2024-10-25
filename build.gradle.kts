plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.0" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    id ("com.onesignal.androidsdk.onesignal-gradle-plugin") version "0.14.0" apply false
}

buildscript {
    repositories {
        mavenCentral() // Tambahkan ini jika belum ada
    }
}