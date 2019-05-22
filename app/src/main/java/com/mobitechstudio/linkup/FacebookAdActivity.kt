package com.mobitechstudio.linkup

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import com.facebook.ads.*
import kotlinx.android.synthetic.main.activity_facebook_ad.*
import kotlinx.android.synthetic.main.native_ad_layout.view.*

//this activity handles showing facebook native ads as popups
class FacebookAdActivity : AppCompatActivity() {
    lateinit var tinyDB: TinyDB
    //facebook native ads
    private val TAG = ViewUserProfileActivity::class.java.simpleName
    private var nativeAd: NativeAd? = null
    private var closeAd = false
    private lateinit var nativeAdLayout: NativeAdLayout
    private lateinit var adView: LinearLayout
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_facebook_ad)
        tinyDB = TinyDB(this)
        loadNativeAd()
        closeAdBtn.setOnClickListener {
            finish()
        }
        btnRechargeNotification.setOnClickListener {
            tinyDB.putString("topUpType", "Normal")
            goToStorePayments()
        }
    }
    private fun showCloseBtn(){
        object: CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished:Long) {

            }
            override fun onFinish() {
                closeAd = true
                closeAdBtn.visibility = View.VISIBLE
                tvCloseBtnHolder.visibility = View.GONE
            }
        }.start()
    }
    private fun loadNativeAd() {
        // Instantiate a NativeAd object.
        // NOTE: the placement ID will eventually identify this as your App, you can ignore it for
        // now, while you are testing and replace it later when you have signed up.
        // While you are using this temporary code you will only get test ads and if you release
        // your code like this to the Google Play your users will not receive ads (you will get a no fill error).
        nativeAd = NativeAd(this, Constants.facebookNativeId)
        nativeAd!!.setAdListener(object: NativeAdListener {
            override fun onMediaDownloaded(ad: Ad) {
                // Native ad finished downloading all assets
                Log.e(TAG, "Native ad finished downloading all assets.")
            }
            override fun onError(ad: Ad, adError: AdError) {
                // Native ad failed to load
                finish()
                Log.e(TAG, "Native ad failed to load: " + adError.errorMessage)
            }
            override fun onAdLoaded(ad: Ad) {
                // Native ad is loaded and ready to be displayed
                Log.d(TAG, "Native ad is loaded and ready to be displayed!")
                // Race condition, load() called again before last ad was displayed
                if (nativeAd == null || nativeAd != ad) {
                    return;
                }
                tvCloseBtnHolder.visibility = View.VISIBLE
                btnsLayoutAd.visibility = View.VISIBLE
                showCloseBtn()
                // Inflate Native Ad into Container
                inflateAd(nativeAd!!)
            }
            override fun onAdClicked(ad: Ad) {
                // Native ad clicked
                Log.d(TAG, "Native ad clicked!")
            }
            override fun onLoggingImpression(ad: Ad) {
                // Native ad impression
                Log.d(TAG, "Native ad impression logged!")
            }
        })
        // Request an ad
        nativeAd!!.loadAd()
    }

    private fun inflateAd(nativeAd:NativeAd) {
        nativeAd.unregisterView()
        // Add the Ad view into the ad container.
        nativeAdLayout = findViewById(R.id.native_ad_container)
        val inflater = LayoutInflater.from(this@FacebookAdActivity)
        // Inflate the Ad view. The layout referenced should be the one you created in the last step.
        adView = inflater.inflate(R.layout.native_ad_layout, nativeAdLayout, false) as LinearLayout
        nativeAdLayout.addView(adView)
        // Add the AdOptionsView
        val adChoicesContainer = adView.ad_choices_container
        val adOptionsView = AdOptionsView(this@FacebookAdActivity, nativeAd, nativeAdLayout)
        adChoicesContainer.removeAllViews()
        adChoicesContainer.addView(adOptionsView, 0)

        // Create native UI using the ad metadata.
        val nativeAdIcon = adView.native_ad_icon
        val nativeAdTitle = adView.native_ad_title
        val nativeAdMedia = adView.native_ad_media
        val nativeAdSocialContext = adView.native_ad_social_context
        val nativeAdBody = adView.native_ad_body
        val sponsoredLabel = adView.native_ad_sponsored_label
        val nativeAdCallToAction = adView.native_ad_call_to_action
        // Set the Text.
        nativeAdTitle.text = nativeAd.advertiserName
        nativeAdBody.text = nativeAd.adBodyText
        nativeAdSocialContext.text = nativeAd.adSocialContext
        nativeAdCallToAction.visibility = if (nativeAd.hasCallToAction()) View.VISIBLE else View.INVISIBLE
        nativeAdCallToAction.text = nativeAd.adCallToAction
        sponsoredLabel.text = nativeAd.sponsoredTranslation
        // Create a list of clickable views
        val clickableViews = ArrayList<View>()
        clickableViews.add(nativeAdTitle)
        clickableViews.add(nativeAdCallToAction)
        // Register the Title and CTA button to listen for clicks.
        nativeAd.registerViewForInteraction(
            adView,
            nativeAdMedia,
            nativeAdIcon,
            clickableViews)
    }

    //go to payment payment according to where the app was downloaded, if from playstore 'com.android.vending' take user to GPay Activity otherwise to Amazon pay
    private fun goToStorePayments(){
        val pkgManager = this.packageManager
        val installerPackageName = pkgManager.getInstallerPackageName(this.packageName)
        if ("com.android.vending" == installerPackageName)
        {
            val intent = Intent(this@FacebookAdActivity, GPayActivity::class.java)
            startActivity(intent)
        }
        else
        {
            //downloaded from Amazon App Store
            val intent = Intent(this@FacebookAdActivity, AmazonPayActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onBackPressed() {
        if(closeAd){
            finish()
        }
        else{
            toastMsgShort("Wait For Closing Button")
        }

    }
    private fun toastMsgShort(msg: String) {
        Toast.makeText(this@FacebookAdActivity, msg, Toast.LENGTH_SHORT).show()
    }
}
