package com.mobitechstudio.linkup

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.facebook.ads.*
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.rbrooks.indefinitepagerindicator.IndefinitePagerIndicator
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_view_user_profile.*
import kotlinx.android.synthetic.main.full_image_layout.view.*
import kotlinx.android.synthetic.main.native_ad_layout.view.*
import kotlinx.android.synthetic.main.user_list_image.view.*
import kotlinx.android.synthetic.main.user_profile_layout.*
import kotlinx.android.synthetic.main.user_profile_layout.view.*
import org.json.JSONArray
import java.util.*

class ViewUserProfileActivity : AppCompatActivity() {
    lateinit var tinyDB: TinyDB
    val REQUESTCODE = 111
    //facebook native ads
    private val TAG = ViewUserProfileActivity::class.java.simpleName
    private var nativeAd: NativeAd? = null
    private var adLoaded = false
    private var closeAd = false
    private lateinit var nativeAdLayout: NativeAdLayout
    private lateinit var adView:LinearLayout
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_user_profile)
        tinyDB = TinyDB(this)
        val premiumExpired = tinyDB.getString("premiumExpired")
        if(premiumExpired=="Yes"){
            loadNativeAd()
        }

        viewUserProfile(tinyDB.getInt("currentUserId"))
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
        nativeAd = NativeAd(this, "2126716717397572_2139708702765040")
        nativeAd!!.setAdListener(object: NativeAdListener {
            override fun onMediaDownloaded(ad:Ad) {
                // Native ad finished downloading all assets

                Log.e(TAG, "Native ad finished downloading all assets.")
            }
            override fun onError(ad: Ad, adError: AdError) {
                // Native ad failed to load
                Log.e(TAG, "Native ad failed to load: " + adError.errorMessage)
            }
            override fun onAdLoaded(ad:Ad) {
                // Native ad is loaded and ready to be displayed
                Log.d(TAG, "Native ad is loaded and ready to be displayed!")
                // Race condition, load() called again before last ad was displayed
                if (nativeAd == null || nativeAd != ad) {
                    return;
                }
                tvCloseBtnHolder.visibility = View.VISIBLE
                btnsLayoutAd.visibility = View.VISIBLE
                adLoaded = true
                // Inflate Native Ad into Container
                inflateAd(nativeAd!!)
            }
            override fun onAdClicked(ad:Ad) {
                // Native ad clicked
                Log.d(TAG, "Native ad clicked!")
            }
            override fun onLoggingImpression(ad:Ad) {
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
        val inflater = LayoutInflater.from(this@ViewUserProfileActivity)
        // Inflate the Ad view. The layout referenced should be the one you created in the last step.
        adView = inflater.inflate(R.layout.native_ad_layout, nativeAdLayout, false) as LinearLayout
        nativeAdLayout.addView(adView)
        // Add the AdOptionsView
        val adChoicesContainer = adView.ad_choices_container
        val adOptionsView = AdOptionsView(this@ViewUserProfileActivity, nativeAd, nativeAdLayout)
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


    private fun initProfileAds(mAdView: AdView) {
            //initialize ads
            MobileAds.initialize(this, getString(R.string.appId))
            val adRequest = AdRequest
                .Builder()
                .addTestDevice("28480BE4C8AF9DAA06B7514F14DE6DDA")
                .addTestDevice("99C784B0B9C5ED479BFF3B1557A6309F")
                .build()
            mAdView.loadAd(adRequest)

            mAdView.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    super.onAdLoaded()
                    //toastMsg("AD LOADED")
                }

                override fun onAdFailedToLoad(i: Int) {
                    super.onAdFailedToLoad(i)
                    //toastMsg("FAILED To LOAD AD: $i")
                }

            }
    }
    private fun viewUserProfile(userId: Int){
        val dialog = AlertDialog.Builder(this@ViewUserProfileActivity, android.R.style.Theme_Black)
        val view = layoutInflater.inflate(R.layout.user_profile_layout, null)
        view.profileLayout.minimumHeight = tinyDB.getInt("height")
        initProfileAds(view.adViewProfile)
        //set layout manager for Recylerview
        val height = tinyDB.getInt("height")/2
        view.imagesListView.setHasFixedSize(true)
        view.imagesListView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
            this,
            androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL,
            false
        )
        view.imagesListView.minimumHeight = height
        dialog.setView(view)
        dialog.setCancelable(false)

        val customDialog = dialog.create()
        customDialog.setTitle("Loading profile....")
        customDialog.setIcon(R.mipmap.paired_circle_round)
        customDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        customDialog.window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        customDialog.show()
        view.btnCloseProfile.setOnClickListener {
            if(customDialog.isShowing){
                customDialog.dismiss()
                //show facebook pop up ad on closing user profile if viewing user is not premium and ad was loaded successfully
                val premiumExpired = tinyDB.getString("premiumExpired")
                if(!adLoaded || premiumExpired=="No"){
                    finish()
                }
                else{
                    showCloseBtn()
                }

            }
        }
        //send info to server
        val url = Constants.baseUrl + "index.php/app/userProfile"
        //start sending request
        val queue = Volley.newRequestQueue(this)
        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { response ->

                try {
                    val json = JSONArray(response)
                    val size = json.length()
                    if (size > 0){

                        for(i in 0 until size){
                            val user = json.getJSONObject(i)
                            val id = user.getInt("userId")
                            val age = user.getInt("age")
                            val likesCount = user.getInt("likes_count")
                            val name = user.getString("name")
                            val bio = user.getString("bio")
                            val emailAddress = user.getString("emailAddress")
                            val sex = user.getString("sex")
                            val dobYear = user.getInt("dobYear")
                            val locLat = user.getDouble("locLat")
                            val locLon = user.getDouble("locLon")
                            val locationName = user.getString("locName")
                            val profileImage = user.getString("profileImage")
                            val premiumAccount = user.getString("premiumAccount")
                            var sexOrientation = user.getString("sexOrientation")
                            val maritalStatus = user.getString("maritalStatus")
                            val smoking = user.getString("smoking")
                            val drinking = user.getString("drinking")
                            val education = user.getString("education")
                            val religion = user.getString("religion")
                            val ethnicity = user.getString("ethnicity")
                            val chatUserType = user.getString("chatUserType")
                            val chatSex = user.getString("chatSex")
                            val chatAgeMin = user.getInt("chatAgeMin")
                            val chatAgeMax = user.getInt("chatAgeMax")

                            //load images here
                            fetchUserImages(userId,  view.imagesListView, view.pagesIndicator, premiumAccount)

                            //set values
                            if(sexOrientation=="Heterosexual"){
                                sexOrientation = "Heterosexual(Straight)"
                            }
                            view.tvUserNameProfile.text = name
                            view.tvUserOrientation.text = sexOrientation
                            view.tvUserMarital.text = maritalStatus

                            view.tvSmoke.text = smoking
                            view.tvDrink.text = drinking
                            view.tvEducation.text = education
                            view.tvReligion.text = religion
                            view.tvEthnicity.text = ethnicity
                            //hide view in case value not availbale
                            if(smoking=="NA"){
                                view.tvSmoke.visibility = View.GONE
                                view.tvSmokingTitle.visibility = View.GONE
                            }
                            if(drinking=="NA"){
                                view.tvDrink.visibility = View.GONE
                                view.tvDrinkingTitle.visibility = View.GONE
                            }
                            if(education=="NA"){
                                view.tvEducation.visibility = View.GONE
                                view.tvEducationTitle.visibility = View.GONE
                            }
                            if(religion=="NA"){
                                view.tvReligion.visibility = View.GONE
                                view.tvReligionTitle.visibility = View.GONE
                            }
                            if(ethnicity=="NA"){
                                view.tvEthnicity.visibility = View.GONE
                                view.tvEthnicityTitle.visibility = View.GONE
                            }
                            customDialog.setTitle(name)
                            view.tvUserBio.text = bio
                            if(bio=="NA"){
                                view.tvUserBio.visibility = View.GONE
                                view.tvUserBioTitle.visibility = View.GONE
                            }
                            view.tvUserLocationProfile.text = locationName
                            view.tvUserAgeProfile.text = "$age Years old"
                            view.tvLikesCountProfile.text = "Likes $likesCount"

                            //view user location on map, when world map icon is clicked on user profile view
                            view.btnProfileMap.setOnClickListener {
                                tinyDB.putDouble("toUserLat", locLat)
                                tinyDB.putDouble("toUserLon", locLon)
                                tinyDB.putString("toUserAddress", locationName)
                                tinyDB.putString("toUserName", name)
                                val intent = Intent(this, UserMapActivity::class.java)
                                startActivity(intent)
                            }
                            view.btnVideoCall.setOnClickListener {
                                val premiumExpired = tinyDB.getString("premiumExpired")
                                val accountType = tinyDB.getString("accountType")
                                val mySex       = tinyDB.getString("sex")
                                val myAge           = tinyDB.getInt("myAge")
                                //check balance here
                                val callCredits = tinyDB.getDouble("callCredits", 0.0)
                                //update profile views
                                if(callCredits > 0.0) {
                                    if (tinyDB.getInt("userId") != id) { //restrict calling self account

                                        //check users filters conditions here
                                        if(myAge in chatAgeMin..chatAgeMax) {
                                            if(chatSex=="All" || mySex==chatSex) {
                                                var myUserType = "Free"
                                                if(premiumExpired=="No"){
                                                    myUserType = "Premium"
                                                }
                                                tinyDB.putBoolean("outGoingCall", true)
                                                tinyDB.putInt("toUserId", id)
                                                tinyDB.putString("toUserName", name)
                                                tinyDB.putString("toUserImage", profileImage)
                                                tinyDB.putDouble("toUserLat", locLat)
                                                tinyDB.putDouble("toUserLon", locLon)
                                                tinyDB.putString("toUserAddress", locationName)
                                                if(chatUserType =="All"){
                                                    requestPerms()
                                                }
                                                else if(chatUserType=="Premium" && myUserType=="Free"){
                                                    confirmRechargeChat("PREMIUM USERS", "$name Receives Calls From PREMIUM Users Only. To Be One Of Them Recharge Your Account")
                                                }
                                                else if(chatUserType=="Premium" && myUserType=="Premium"){
                                                    requestPerms()
                                                }

                                            }
                                            else{
                                                toastMsgShort("Calling $name is Restricted")
                                            }
                                        }
                                        else
                                        {
                                            toastMsgShort("Calling $name is Restricted")
                                        }

                                    } else {
                                        toastMsgShort("You Can not call Your Self")
                                    }
                                }
                                else{
                                    tinyDB.putString("topUpType", "Call")
                                    confirmRechargeTopUp("Top Up", "You Have 0 Credits For Live talk(Video Call) Consider Topping Up")
                                }


                            }
                            view.btnLikeUserProfile.setOnClickListener {
                                val myUserId = tinyDB.getInt("userId")
                                val accountType = tinyDB.getString("accountType")
                                val profileImg = tinyDB.getString("profileImage")
                                if ((profileImg != "NA" && accountType=="Normal") || accountType=="VIP")
                                {
                                    if (myUserId != userId) {
                                        likeProfile(userId, view.tvLikesCountProfile, likesCount + 1)
                                    } else {
                                        toastMsg("Liking yourself restricted")
                                    }
                                }
                                else
                                {
                                    confirmProfileImage()
                                }
                            }
                            view.btnChatProfile.setOnClickListener {
                                val premiumExpired = tinyDB.getString("premiumExpired")
                                val accountType = tinyDB.getString("accountType")
                                val profileImg = tinyDB.getString("profileImage")
                                val mySex = tinyDB.getString("sex")
                                val myAge = tinyDB.getInt("myAge")
                                if ((profileImg != "NA" && accountType=="Normal") || (accountType=="VIP" && premiumExpired =="No")) {

                                        val myUserId = tinyDB.getInt("userId")
                                        if (myUserId != userId) {
                                            //check users filters conditions here
                                            if(myAge in chatAgeMin..chatAgeMax) {
                                                if(chatSex=="All" || mySex==chatSex) {
                                                    var myUserType = "Free"
                                                    if(premiumExpired=="No"){
                                                        myUserType = "Premium"
                                                    }
                                                    tinyDB.putInt("toUserId", id)
                                                    tinyDB.putString("toUserName", name)
                                                    tinyDB.putDouble("toUserLat", locLat)
                                                    tinyDB.putDouble("toUserLon", locLon)
                                                    tinyDB.putString("toUserAddress", locationName)
                                                    if(chatUserType =="All"){
                                                        startChat(id, name)
                                                    }
                                                    else if(chatUserType=="Premium" && myUserType=="Free"){
                                                        confirmRechargeChat("PREMIUM USERS", "$name Chats With PREMIUM Users Only. To Be One Of Them Recharge Your Account")
                                                    }
                                                    else if(chatUserType=="Premium" && myUserType=="Premium"){
                                                        startChat(id, name)
                                                    }

                                                }
                                                else{
                                                    toastMsgShort("Chatting with $name is Restricted")
                                                }
                                            }
                                            else
                                            {
                                                toastMsgShort("Chatting with $name is Restricted")
                                            }



                                        } else {
                                            toastMsg("Sending message to yourself is restricted")
                                        }


                                }
                                else{
                                    if(accountType=="Normal") {
                                        confirmProfileImage()
                                    }
                                    else
                                    {
                                        confirmPayVIP()
                                    }
                                }
                            }
                            //update profile views
                            if(tinyDB.getInt("userId") != id) { //restrict update viewing on own profile
                                viewedProfile(id)
                            }

                        }


                    }
                    else
                    {
                        toastMsg("Profile not Found Here")
                    }

                }catch (ex:Exception){
                    toastMsg("Error loading images, try gain")
                }


            },
            Response.ErrorListener { e ->
                toastMsg("Reload again")
            }) {
            override fun getParams(): Map<String, String?> {
                return mapOf(
                    "userId" to userId.toString()
                )
            }
        }


        stringRequest.retryPolicy = DefaultRetryPolicy(
            50000,
            10,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
        queue.add(stringRequest)
        //end request code


    }
    private fun goToStorePayments(){
        val pkgManager = this.packageManager
        val installerPackageName = pkgManager.getInstallerPackageName(this.packageName)
        if ("com.android.vending" == installerPackageName)
        {
            val intent = Intent(this@ViewUserProfileActivity, GPayActivity::class.java)
            startActivity(intent)
        }
        else
        {
            //downloaded from Amazon App Store
            val intent = Intent(this@ViewUserProfileActivity, AmazonPayActivity::class.java)
            startActivity(intent)
        }
    }
    private fun confirmRechargeTopUp(title: String, msg: String){
        val builder = AlertDialog.Builder(this@ViewUserProfileActivity)
        builder.setTitle(title)
        builder.setMessage(msg)
        builder.setCancelable(false)
        builder.setIcon(R.mipmap.paired_circle_round)
        builder.setPositiveButton("Recharge") { _, _ ->

            goToStorePayments()

        }
        builder.setNeutralButton("Close") { dialog, _ ->
            dialog.cancel()
        }
        val alert = builder.create()
        alert.show()
    }
    private fun toastMsg(msg: String){
        Toast.makeText(this@ViewUserProfileActivity, msg, Toast.LENGTH_SHORT).show()
    }

    private fun viewedProfile(toUserId: Int) {
        //send info to server
        toastMsg("Loading..")
        val url = Constants.baseUrl + "index.php/app/updateProfileView"
        //start sending request
        val queue = Volley.newRequestQueue(this)
        val stringRequest = object : StringRequest(Request.Method.POST, url,
            Response.Listener { response ->
                //println("RESULT: $response")
                when {
                    response.contains("SUCCESS") -> {
                        //toastMsg("Viewed successfully")
                    }
                    response.contains("DUBLICATE") -> {
                        //toastMsg("Viewed successfully")
                    }
                    else ->{
                        response.contains("Try Again")
                    }
                }
            },
            Response.ErrorListener { e ->
                //toastMsg("Try again")
            }) {
            override fun getParams(): Map<String, String?> {
                return mapOf(
                    "name" to tinyDB.getString("name"),
                    "toUserId" to toUserId.toString(),
                    "userId" to tinyDB.getInt("userId").toString()
                )
            }
        }
        stringRequest.retryPolicy = DefaultRetryPolicy(
            50000,
            10,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )
        queue.add(stringRequest)
        //end request code
    }

    private fun fetchUserImages(userId: Int, imageListV: androidx.recyclerview.widget.RecyclerView, pagerIndicator: IndefinitePagerIndicator, premiumAccount: String)
    {
        val imagesList = arrayListOf<UserImage>()
        val height = tinyDB.getInt("height")/2
        imageListV.setHasFixedSize(true)
        imageListV.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
            this,
            androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL,
            false
        )
        imageListV.minimumHeight = height

        //send info to server
        val url = Constants.baseUrl + "index.php/app/userImages"
        //start sending request
        val queue = Volley.newRequestQueue(this)
        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { response ->
                // println("IMAGES_RESULT: $response")
                try {
                    val json = JSONArray(response)
                    val size = json.length()
                    if (size > 0){

                        for(i in 0 until size){
                            val user = json.getJSONObject(i)
                            val imgId = user.getInt("imgId")
                            val imgName = user.getString("imgName")


                            imagesList.add(UserImage(
                                imgId,
                                imgName
                            ))
                        }
                        imageListV.adapter = ImageAdapter(imagesList, premiumAccount)
                        pagerIndicator.attachToRecyclerView(imageListV)
                    }
                    else
                    {
                        toastMsg("No Images Found Here")
                    }

                }catch (ex:Exception){
                    println("ERROR_IMAGES: ${ex.message}")
                    toastMsg("Error loading images, try gain")
                }


            },
            Response.ErrorListener { e ->
                toastMsg("Reload again")
            }) {
            override fun getParams(): Map<String, String?> {
                return mapOf(
                    "userId" to userId.toString()
                )
            }
        }


        stringRequest.retryPolicy = DefaultRetryPolicy(
            50000,
            10,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
        queue.add(stringRequest)
        //end request code
    }

    private fun likeProfile(liked_profile_id: Int, tvLikes: TextView, likesCount: Int) {
        val profileImg = tinyDB.getString("profileImage")
        val accountType = tinyDB.getString("accountType")
        val premiumExpired = tinyDB.getString("premiumExpired")
        if ((profileImg != "NA" && accountType=="Normal") || (accountType=="VIP" && premiumExpired =="No")) {
        //send info to server
        toastMsg("Processing..")
        val url = Constants.baseUrl + "index.php/app/likeUser"
        //start sending request
        val queue = Volley.newRequestQueue(this)
        val stringRequest = object : StringRequest(Request.Method.POST, url,
            Response.Listener { response ->
                //println("RESULT: $response")
                when {
                    response.contains("SUCCESS") -> {
                        //toastMsg("Subscribed successfully")
                        toastMsg("Thanks")
                        tvLikes.text = "Likes $likesCount"

                    }
                    response.contains("DUBLICATE") -> toastMsg("Thanks")
                    else -> response.contains("Try Again")
                }
            },
            Response.ErrorListener { e ->
                //toastMsg("Try again")
            }) {
            override fun getParams(): Map<String, String?> {
                return mapOf(
                    "name" to tinyDB.getString("name"),
                    "liked_profile_id" to liked_profile_id.toString(),
                    "userId" to tinyDB.getInt("userId").toString()
                )
            }
        }
        stringRequest.retryPolicy = DefaultRetryPolicy(
            50000,
            10,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )
        queue.add(stringRequest)
        //end request code
        }
        else{
            if(accountType=="Normal") {
                confirmProfileImage()
            }
            else
            {
                confirmPayVIP()
            }
        }
    }
    private fun confirmProfileImage(){
        val builder = AlertDialog.Builder(this@ViewUserProfileActivity)
        builder.setTitle("Profile Image")
        builder.setMessage("You do not have Profile Photo. Your account is not visible to others. Upload Photos now?")
        builder.setCancelable(false)
        builder.setIcon(R.mipmap.paired_circle_round)
        builder.setPositiveButton("Yes") { dialog, which ->
            val intent = Intent(this@ViewUserProfileActivity, EditImagesActivity::class.java)
            startActivity(intent)
        }
        builder.setNegativeButton("No") { dialog, which ->
            dialog.cancel()
        }
        val alert = builder.create()
        alert.show()
    }
    private fun dialogMsg(msg: String){
        val builder = AlertDialog.Builder(this@ViewUserProfileActivity)
        builder.setTitle("Info")
        builder.setMessage(msg)
        builder.setCancelable(false)
        builder.setIcon(R.mipmap.paired_circle_round)
        builder.setNegativeButton("No") { dialog, which ->
            dialog.cancel()
        }
        val alert = builder.create()
        alert.show()
    }


    private fun confirmPayVIP(){
        val builder = AlertDialog.Builder(this@ViewUserProfileActivity)
        builder.setTitle("VIP Account EXPIRED")
        builder.setMessage("Your VIP Account is Expired. Do you want to Recharge Now?")
        builder.setCancelable(false)
        builder.setIcon(R.mipmap.paired_circle_round)
        builder.setPositiveButton("Yes") { dialog, which ->
            goToStorePayments()
        }
        builder.setNegativeButton("No") { dialog, which ->
            dialog.cancel()
        }
        val alert = builder.create()
        alert.show()
    }

    inner class ImageAdapter(private val listImages: ArrayList<UserImage>, private val premiumAccount: String): androidx.recyclerview.widget.RecyclerView.Adapter<ImageAdapter.MessageViewHolder>(){

        inner class MessageViewHolder(val card: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(card)

        //specify the contents for the shown items/habits
        override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
            val image = listImages[position]
            val imgId = image.imgId
            val imgName = image.imgName
            //load image
            val height = tinyDB.getInt("height")/2
            val width = tinyDB.getInt("width")
            val premiumExpired = tinyDB.getString("premiumExpired")
            if(premiumAccount=="Yes"){
                holder.card.btnFree.visibility = View.GONE
                holder.card.btnPremium.visibility = View.VISIBLE
            }
            else{
                holder.card.btnFree.visibility = View.VISIBLE
                holder.card.btnPremium.visibility = View.GONE
            }
            Picasso.get()
                .load(Constants.baseUrl+"images/userimages/"+imgName)
                .placeholder(R.drawable.progress_animation)
                .resize(width, height)
                .centerInside()
                .into(holder.card.imgProfileUser)
            //handle image click listner to zoom image
            holder.card.imgProfileUser.setOnClickListener {
                viewFullImage(imgName)
            }
        }

        //create new view holder for each item
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.user_list_image, parent, false)
            return MessageViewHolder(view)
        }

        override fun getItemCount()= listImages.size

    }

    private fun viewFullImage(imageName: String){
        val dialog = AlertDialog.Builder(this@ViewUserProfileActivity, android.R.style.Theme_Black)
        val view = layoutInflater.inflate(R.layout.full_image_layout, profileLayout)
        dialog.setView(view)
        dialog.setCancelable(false)

        val customDialog = dialog.create()
        //customDialog.setTitle("Loading image....")
        customDialog.setIcon(R.mipmap.paired_circle_round)
        customDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        customDialog.window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        customDialog.show()

        val height = tinyDB.getInt("height")
        val width = tinyDB.getInt("width")
        Picasso.get()
            .load(Constants.baseUrl+"images/userimages/"+imageName)
            .placeholder(R.drawable.progress_animation)
            .resize(width, height)
            .centerInside()
            .into(view.fullImageView)
        view.btnCloseView.setOnClickListener {
            customDialog.dismiss()
        }
    }



    private fun confirmRechargeChat(title: String, msg: String){
        val builder = AlertDialog.Builder(this@ViewUserProfileActivity)
        builder.setTitle(title)
        builder.setMessage(msg)
        builder.setCancelable(false)
        builder.setIcon(R.mipmap.paired_circle_round)
        builder.setPositiveButton("Recharge Now") { _, _ ->
            goToStorePayments()

        }

        builder.setNeutralButton("Close") { dialog, _ ->
            dialog.cancel()
        }
        val alert = builder.create()
        alert.show()
    }



    private fun toastMsgShort(msg: String) {
        Toast.makeText(this@ViewUserProfileActivity, msg, Toast.LENGTH_SHORT).show()
    }
    //checking if user is blocked from chatting with user he/she has choosen
    private fun startChat(toUserId: Int, toUserName: String) {

            //send info to server
            toastMsgShort("Processing, please wait..")

            val url = Constants.baseUrl + "index.php/app/checkBlock"
            //start sending request
            val queue = Volley.newRequestQueue(this)
            val stringRequest = object : StringRequest(Request.Method.POST, url,
                Response.Listener { response ->
                    //println("RESULT_TOKEN: $response")
                    if (response == "NOT_BLOCKED") {
                        val intent = Intent(this@ViewUserProfileActivity, ChatActivity::class.java)
                        startActivity(intent)
                    } else {
                        toastMsgShort("Sending Message to $toUserName is Restricted")
                    }
                },
                Response.ErrorListener { e ->
                    //toastMsg("Try again")
                }) {
                override fun getParams(): Map<String, String?> {
                    return mapOf(
                        "toUserId" to toUserId.toString(),
                        "userId" to tinyDB.getInt("userId").toString()
                    )
                }
            }
            stringRequest.retryPolicy = DefaultRetryPolicy(
                50000,
                10,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
            )
            queue.add(stringRequest)
            //end request code

    }

    override fun onBackPressed() {
        if (closeAd){
            finish()
        }
        else {
            toastMsgShort("Wait For Closing Button")
        }
    }

    //this function is used to check if there is internet connection using ping command
    fun isConnected():Boolean {
        val cm = this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetworkInfo
        return activeNetwork != null
    }



    private fun goCall(){
        val intent = Intent(this, VideoCallActivity::class.java)
        startActivity(intent)
    }

    private fun requestPerms() {
        //request location permission
        if (Build.VERSION.SDK_INT >= 23) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED

                || ActivityCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.MODIFY_AUDIO_SETTINGS
                ) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(
                        android.Manifest.permission.RECORD_AUDIO,
                        android.Manifest.permission.MODIFY_AUDIO_SETTINGS,
                        android.Manifest.permission.CAMERA
                    ), REQUESTCODE
                )
                return
            }
            else
            {
                goCall()
            }

        }
        else{
            goCall()
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            REQUESTCODE -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    goCall()

                } else {
                    toastMsgShort("Allow Paired To Access These Permissions. Calling Will Not Work")
                }
                return
            }
            // Add other 'when' lines to check for other
            // permissions this app might request.
            else -> {
                // Ignore all other requests.
            }
        }
    }

    //this function can be used to get distance between two points, but currently has not been used anywhere in this project
    private fun getDistanceBetweenTwoPoints(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {

        val distance = FloatArray(2)

        Location.distanceBetween(
            lat1, lon1,
            lat2, lon2, distance
        )

        return distance[0]/1000
    }


}
