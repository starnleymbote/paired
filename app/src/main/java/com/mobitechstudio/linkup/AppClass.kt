package com.mobitechstudio.linkup

import android.content.Context
import androidx.multidex.MultiDexApplication
import com.facebook.ads.AudienceNetworkAds
import com.vanniktech.emoji.EmojiManager
import com.vanniktech.emoji.google.GoogleEmojiProvider
import net.gotev.uploadservice.UploadService

/**
 * Created by Bedasius Budida on 12/11/18.
 *
 * Main Application Class, used in AndroidManifest.xml file
 */

class AppClass : MultiDexApplication() {
    init {
        instance = this
    }

    override fun onCreate() {
        super.onCreate()
        UploadService.NAMESPACE = BuildConfig.APPLICATION_ID
        EmojiManager.install(GoogleEmojiProvider()) //set emoji provider
        AudienceNetworkAds.initialize(this)


    }

    companion object {
        private lateinit var instance: AppClass

        val context: Context
            get() = instance
    }


}
