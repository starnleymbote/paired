package com.mobitechstudio.linkup


import android.Manifest
import android.annotation.TargetApi
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import com.google.android.material.navigation.NavigationView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.view.GravityCompat
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.util.DisplayMetrics
import android.view.*
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
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
import com.google.android.gms.location.*
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.iid.InstanceIdResult
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

import com.sinch.android.rtc.*
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.age_dialog_layout.view.*
import kotlinx.android.synthetic.main.app_bar_main.*
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.android.synthetic.main.notification_dialog_layout.view.*
import kotlinx.android.synthetic.main.pickup_addresses_layout.view.*
import kotlinx.android.synthetic.main.user_filter_dialog_layout.view.*
import kotlinx.android.synthetic.main.user_list_item_random_layout.view.*
import kotlinx.android.synthetic.main.user_list_item_top_layout.view.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    lateinit var fusedTrackerCallback: LocationCallback
    lateinit var fusedLocationClient: FusedLocationProviderClient
    val LOCATION_REQUEST = 111
    lateinit var tinyDB: TinyDB
    var pickupAddressList = arrayListOf<Address>()

    var usersList = mutableListOf<UserObject>()
    var topUsersList = mutableListOf<UserObject>()
    var hasMoreTopUsers = true
    var hasMoreUsers = true
    var currentSize = 0
    var currentSizeTop = 0
    var scrollPosition = 0
    var scrollPositionTop = 0
    private var mAdView: AdView?=null
    internal var lastLocation: Location? = null

         //remote config
     private var mFirebaseRemoteConfig: FirebaseRemoteConfig? = null
     private val VERSION_CODE = "tm_version"

    //facebook ads
    private var interstitialAd: InterstitialAd? = null

    //sinch client
    private lateinit var sinchClient: SinchClient
    //set age filter
    private var fromAgeList = java.util.ArrayList<DobYear>()
    private var toAgeList = java.util.ArrayList<DobYear>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //initialize facebook ads
        setContentView(R.layout.activity_main)
        interstitialAd = InterstitialAd(this, Constants.facebookInterstitialId) //change ID in Constants.kt file
        setSupportActionBar(toolbar)
        tinyDB = TinyDB(this)
        tinyDB.putInt("adShown", 0)

        setLayoutManager()

        val userSex = tinyDB.getString("sex")
        tinyDB.putString("userCategory", "All")
        if(userSex=="Male"){
            tinyDB.putString("viewSex", "Female")
        }
        else{
            tinyDB.putString("viewSex", "Male")
        }
        val toggle = ActionBarDrawerToggle(
            this,
            drawer_layout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawer_layout.addDrawerListener(toggle)
        //changing default menu icon
        toggle.isDrawerIndicatorEnabled = false
        toggle.toolbarNavigationClickListener = View.OnClickListener {
            drawer_layout.openDrawer(GravityCompat.START)
        }
        toggle.setHomeAsUpIndicator(R.drawable.menu3)
        //end change icon
        toggle.syncState()

        nav_view.setNavigationItemSelectedListener(this)
        nav_view.itemIconTintList = null


        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        //init functions here
        isLocationEnabled()





        editLocation.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
               searchArea()
            }
            true
        }


        swipeRefreshUsers.setOnRefreshListener {
            hasMoreUsers = true
            fetchNearByUsers(0, tinyDB.getString("defaultAddressName"), false)
        }

        buttonsClicks()
        initAds()
        checkUpdate()
        tinyDB.putInt("checkedLocation", 0)
        checkEmail()
        setProfile() // set profile image and check expire date
        preventScreenShot()
        animateBtn(btnBeHere)
        btnBeHere.setOnClickListener {
            tinyDB.putString("topUpType", "Classic")
            confirmRecharge("Be Classic", "Recharge Your Account To Be Part Of Classic Users. You Will Be Noticed By Other Users Easily")
        }
       showHideBeClassic()
        setNotificationAlarm()
        setDailyNotification()

        if(isConnected()) {
            checkAccountStatus()
            getCurrentLocation()
            getCurrentTokenSend()
            fetchTopUsers(0, tinyDB.getString("defaultAddressName"), false)
            loadFacebookAd()
            initSinch()
        }
        else
        {
            toastMsgShort("There is NO INTERNET CONNECTION")
        }

        //getAdId()

    }
    private fun showHideBeClassic(){
        val sponsored = tinyDB.getString("sponsored")
        val accountType = tinyDB.getString("accountType")
        if((sponsored=="" || sponsored=="No") && accountType !="VIP"){
            btnBeHere.visibility = View.VISIBLE
        }
        else
        {
            btnBeHere.visibility = View.GONE
        }
    }
    private fun initSinch(){
        sinchClient = Sinch.getSinchClientBuilder().context(this)
            .applicationKey(Constants.sinchKey)
            .applicationSecret(Constants.sinchSecret)
            .environmentHost(Constants.sinchHost)
            .userId(tinyDB.getInt("userId").toString())
            .build()

        //capabilities
        sinchClient.callClient.setRespectNativeCalls(false)
        sinchClient.setSupportCalling(true)
        sinchClient.setSupportManagedPush(true)
        sinchClient.setPushNotificationDisplayName(tinyDB.getString("name"))

        sinchClient.addSinchClientListener(object: SinchClientListener {
            override fun onClientStarted(client:SinchClient) {
                //println("CALL: Client Started")
            }
            override fun onClientStopped(client:SinchClient) {
                // toastMsgShort("Client Stopped")
                //println("CALL: Client Stopped")
            }
            override fun onClientFailed(client:SinchClient, error: SinchError) {
                // toastMsgShort("Client Failed")
                //println("CALL: Client Failed")
            }
            override fun onRegistrationCredentialsRequired(client:SinchClient, registrationCallback: ClientRegistration) {
                //toastMsgShort("onRegistrationCredentialsRequired")
                //println("CALL: onRegistrationCredentialsRequired")
            }
            override fun onLogMessage(level:Int, area:String, message:String) {
                //toastMsgShort("Message Logged")
                //println("CALL: Message Logged")
            }
        })
        sinchClient.start()
    }

    private fun showFbAd(){
        //show ad
        val premiumExpired = tinyDB.getString("premiumExpired")
        if(premiumExpired=="Yes") {
            val adShown = tinyDB.getInt("adShown")
            if (adShown == 0) {
                try {
                    interstitialAd!!.show()
                } catch (ex: Exception) {
                }
            }
        }
    }


    private fun loadFacebookAd(){
        interstitialAd!!.setAdListener(object: InterstitialAdListener {
            override fun onInterstitialDisplayed(ad: Ad?) {
                tinyDB.putInt("adShown", 1) //control showing facebook interstitial ad only once per session
            }

            override fun onAdClicked(ad: Ad?) {
                //toastMsgShort("Ad Clicked")
            }

            override fun onInterstitialDismissed(add: Ad?) {
               // toastMsgShort("Ad Dismissed")
            }

            override fun onError(add: Ad?, error: AdError?) {
               // toastMsgShort("Ad Error")
                //println("AD_ERROR: ${error!!.errorMessage}")
            }

            override fun onAdLoaded(ad: Ad?) {
               // toastMsgShort("Ad Loaded")
                //interstitialAd!!.show()
            }

            override fun onLoggingImpression(ad: Ad?) {
                //toastMsgShort("Ad Impression logged")
            }

        })
        interstitialAd!!.loadAd()
    }



    private fun buttonsClicks(){
        btnAll.setOnClickListener {
            btnAll.setTextColor(resources.getColor(R.color.tmColor))
            btnOnline.setTextColor(resources.getColor(R.color.blue))
            btnNew.setTextColor(resources.getColor(R.color.blue))
            btnClassic.setTextColor(resources.getColor(R.color.blue))
            //show fb ad
            showFbAd()
            //load all users here
            hasMoreUsers = true
            tinyDB.putString("userCategory", "All")
            fetchNearByUsers(0, tinyDB.getString("defaultAddressName"), false)
        }
        btnOnline.setOnClickListener {
            btnAll.setTextColor(resources.getColor(R.color.blue))
            btnOnline.setTextColor(resources.getColor(R.color.tmColor))
            btnNew.setTextColor(resources.getColor(R.color.blue))
            btnClassic.setTextColor(resources.getColor(R.color.blue))
            //show fb ad
            showFbAd()
            //load online users here
            hasMoreUsers = true
            tinyDB.putString("userCategory", "Online")
            fetchNearByUsers(0, tinyDB.getString("defaultAddressName"), false)
        }
        btnNew.setOnClickListener {
            btnAll.setTextColor(resources.getColor(R.color.blue))
            btnOnline.setTextColor(resources.getColor(R.color.blue))
            btnNew.setTextColor(resources.getColor(R.color.tmColor))
            btnClassic.setTextColor(resources.getColor(R.color.blue))
            //show fb ad
            showFbAd()
            //load new users here
            hasMoreUsers = true
            tinyDB.putString("userCategory", "New")
            fetchNearByUsers(0, tinyDB.getString("defaultAddressName"), false)
        }
        btnClassic.setOnClickListener {
            btnAll.setTextColor(resources.getColor(R.color.blue))
            btnOnline.setTextColor(resources.getColor(R.color.blue))
            btnNew.setTextColor(resources.getColor(R.color.blue))
            btnClassic.setTextColor(resources.getColor(R.color.tmColor))
            //show fb ad
            showFbAd()
            //load classic users here
            hasMoreUsers = true
            tinyDB.putString("userCategory", "Classic")
            fetchNearByUsers(0, tinyDB.getString("defaultAddressName"), false)
        }
    }



    private fun animateBtn(myBtn: Button){
        val anim = AlphaAnimation(0.4f, 1.0f)
        anim.duration = 120 //You can manage the blinking time with this parameter
        anim.startOffset = 20
        anim.repeatMode = Animation.REVERSE
        anim.repeatCount = Animation.INFINITE
        myBtn.startAnimation(anim)

    }
    private fun preventScreenShot(){
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    }
    private fun checkEmail(){
        val emailAddress = tinyDB.getString("emailAddress")
        if(emailAddress=="" || emailAddress=="NA"){
            notificationMessage("Setup Email", "Setup Password Recovery Email Address", Intent(this, SetupEmailActivity::class.java), this@MainActivity)
        }
    }
    private fun setLayoutManager(){
        //set layout manager for Recylerview
        usersListView.setHasFixedSize(true)
        topUsersListView.setHasFixedSize(true)
        usersListView.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 3)
        topUsersListView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        //val staggeredGridLayoutManager = StaggeredGridLayoutManager(3, 1)
       // usersListView.layoutManager = staggeredGridLayoutManager
    }
    fun onGenderRadioButtonClicked(view: View) {
        if (view is RadioButton) {
            // Is the button now checked?
            val checked = view.isChecked

            // Check which radio button was clicked
            when (view.id) {
                R.id.radio_female ->
                    if (checked) {
                      //load females
                        layoutGender.visibility = View.GONE
                        tinyDB.putString("viewSex", "Female")
                        hasMoreUsers = true
                        fetchNearByUsers(0, tinyDB.getString("defaultAddressName"), false)
                        fetchTopUsers(0, tinyDB.getString("defaultAddressName"), false)
                    }
                R.id.radio_male ->
                    if (checked) {
                        // load males
                        layoutGender.visibility = View.GONE
                        tinyDB.putString("viewSex", "Male")
                        hasMoreUsers = true
                        fetchTopUsers(0, tinyDB.getString("defaultAddressName"), false)
                        fetchNearByUsers(0, tinyDB.getString("defaultAddressName"), false)
                    }
            }
        }
    }

     private fun checkUpdate() {
             //remote config check new update
             mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()
             val configSettings = FirebaseRemoteConfigSettings.Builder()
                 .setDeveloperModeEnabled(BuildConfig.DEBUG)
                 .build()
             mFirebaseRemoteConfig!!.setConfigSettings(configSettings)
             mFirebaseRemoteConfig!!.setDefaults(R.xml.remote_config_defaults)
             var cacheExpiration: Long = 3600
             if (mFirebaseRemoteConfig!!.info.configSettings.isDeveloperModeEnabled) {
                 cacheExpiration = 0
             }
             mFirebaseRemoteConfig!!.fetch(cacheExpiration)
                 .addOnCompleteListener(this) { task ->
                     if (task.isSuccessful) {
                         // After config data is successfully fetched, it must be activated before newly fetched
                         // values are returned.
                         mFirebaseRemoteConfig!!.activateFetched()
                     } else {
                         //Toast.makeText(MainActivity.this, "Fetch Failed", Toast.LENGTH_SHORT).show();
                     }
                     //check version code now
                     val config_version_code = mFirebaseRemoteConfig!!.getString(VERSION_CODE)

                     var pinfo: PackageInfo? = null
                     try {
                         pinfo = packageManager.getPackageInfo(packageName, 0)
                         val versionNumber = pinfo!!.versionCode

                         if (Integer.parseInt(config_version_code) > versionNumber) {
                             //update MyLock Dialog
                             val builder = android.app.AlertDialog.Builder(this@MainActivity)
                             builder.setTitle("New Update")
                             builder.setIcon(R.mipmap.paired_circle_round)
                             builder.setMessage("There is new update of TM App in Play Store")
                             builder.setCancelable(false)
                             builder.setNegativeButton("Ignore") { dialog, which -> dialog.cancel() }
                             builder.setPositiveButton("Update") { dialog, which ->
                                 val intent = Intent(android.content.Intent.ACTION_VIEW)
                                 intent.data = Uri.parse("https://play.google.com/store/apps/details?id=com.mobitechstudio.tafutampenzi")
                                 startActivity(intent)
                             }
                             val alert = builder.create()
                             alert.show()
                         }
                     } catch (e: PackageManager.NameNotFoundException) {
                         //e.printStackTrace();
                     }
                 }
         }




    private fun notificationMessage(title: String, msg: String, intent: Intent, context: Context?){
        val bitmap = BitmapFactory.decodeResource(context!!.resources, R.drawable.logo)
        val notificationId = System.currentTimeMillis().toInt()


        val pIntent = PendingIntent.getActivity(context, notificationId, intent, 0)

        val bigTextStyle = NotificationCompat
            .BigTextStyle()
            .bigText(msg)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val mBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel(notificationManager)
            NotificationCompat.Builder(context)
                .setSmallIcon(R.mipmap.paired_circle_round)
                .setLargeIcon(bitmap)
                .setContentTitle(title)
                .setChannelId("TM")
                .setContentText(msg)
                .setStyle(bigTextStyle)
                .setDefaults(NotificationCompat.DEFAULT_SOUND)
                .setAutoCancel(true)
                .setContentIntent(pIntent)

        } else {
            NotificationCompat.Builder(context)
                .setSmallIcon(R.mipmap.paired_circle_round)
                .setLargeIcon(bitmap)
                .setContentTitle(title)
                .setContentText(msg)
                .setStyle(bigTextStyle)
                .setDefaults(NotificationCompat.DEFAULT_SOUND)
                .setAutoCancel(true)
                .setContentIntent(pIntent)

        }

        notificationManager.notify(notificationId, mBuilder.build())
    }

    @TargetApi(Build.VERSION_CODES.O)
    fun createChannel(notificationManager: NotificationManager){
        val channelId = "TM"
        val channelName = "TM"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val notificationChannel = NotificationChannel(channelId, channelName, importance)
        notificationChannel.enableLights(true)
        notificationChannel.lightColor = Color.RED
        notificationChannel.enableVibration(true)
        notificationChannel.vibrationPattern = longArrayOf(0, 500)
        notificationManager.createNotificationChannel(notificationChannel)
    }
    private fun setProfile(){
        //set user profile name
        val hView = nav_view.getHeaderView(0)
        val tvProfile = hView.findViewById<TextView>(R.id.tvUserProfileName)
        val tvEmail = hView.findViewById<TextView>(R.id.textViewEmail)
        val imgProfile = hView.findViewById<ImageView>(R.id.imageViewProfile)
        val imgPremium = hView.findViewById<ImageView>(R.id.imgPremium)
        val imgFree = hView.findViewById<ImageView>(R.id.imgFree)
        val tvMyAccountType = hView.findViewById<TextView>(R.id.tvMyAccountType)
        tvProfile.text = tinyDB.getString("name")
        tvEmail.text = tinyDB.getString("phoneNumber")
        val amPremium = tinyDB.getString("amPremium")
        val accountType = tinyDB.getString("accountType")
        //load profile image here
        val profileImage = tinyDB.getString("profileImage")
        val premiumExpired = tinyDB.getString("premiumExpired")
        if(accountType=="Normal") {
            if (amPremium == "Yes") {
                imgPremium.visibility = View.VISIBLE
                imgFree.visibility = View.GONE
                tvMyAccountType.text = "PREMIUM Account"
            } else {
                imgPremium.visibility = View.GONE
                imgFree.visibility = View.VISIBLE
                tvMyAccountType.text = "FREE Account"
            }
        }
        else{
            imgPremium.setImageResource(R.drawable.viplarge)
            imgPremium.visibility = View.VISIBLE
            imgFree.visibility = View.GONE
            tvMyAccountType.text = "VIP Account"

        }


        if(profileImage !="NA") {
            Picasso.get()
                .load(Constants.baseUrl + "images/userimages/" + profileImage)
                .resize(200, 200)
                .error(R.mipmap.paired_circle_round)
                .transform(CircleTransform())
                .centerCrop(Gravity.CENTER_VERTICAL)
                .into(imgProfile)
            imgProfile.setOnClickListener {
                val intent = Intent(this@MainActivity, MyProfileActivity::class.java)
                startActivity(intent)
            }
            if(accountType=="VIP" && premiumExpired=="Yes"){
                confirmPayVIP()
            }
        }
        else
        {
            //alert to add profile image

            if(accountType == "Normal") {
                confirmProfileImage()
            }
            else if(accountType=="VIP" && premiumExpired=="Yes"){
                confirmPayVIP()
            }
        }
    }

    private fun viewUser(userId: Int){
        tinyDB.putInt("currentUserId", userId)
        val intent = Intent(this, ViewUserProfileActivity::class.java)
        startActivity(intent)
    }
    private fun defaultAgeRange(){
        val fromAge = tinyDB.getInt("fromAge")
        val myAge = tinyDB.getInt("myAge")
        if(fromAge==0){
            tinyDB.putInt("fromAge", 18)
            tinyDB.putInt("toAge", 120)
        }
        if(myAge==0){
            tinyDB.putInt("myAge", 25)
        }
    }
    override fun onResume() {
        tinyDB = TinyDB(this)

        defaultAgeRange()
        showHideBeClassic()
        setDimensions()
        requestSingleLocationUpdate()

        super.onResume()
    }
    private fun checkAccountStatus(){
        //get account status
        val lat = tinyDB.getDouble("defaultLat", 0.0)
        val lon = tinyDB.getDouble("defaultLon", 0.0)
        val defaultAddressName = tinyDB.getString("myLocation")
        if(defaultAddressName !="") {
            getAccountStatus(lat, lon, defaultAddressName)
        }
        else{
            getAccountStatus(0.0, 0.0, "NA")
        }
    }


    override fun onDestroy() {
         // Destroy the AdView
        if(mAdView !=null) {
            (mAdView!!.parent as ViewGroup).removeView(mAdView)
            mAdView!!.destroy()
        }

        super.onDestroy()
    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else {
            confirmGoBack()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        when (item.itemId) {
            R.id.action_share -> {
                shareMe()
            } R.id.action_my_chats -> {
                tinyDB.putString("sentOrReceived", "Received Chats")
                val intent = Intent(this@MainActivity, MyChatsActivity::class.java)
                startActivity(intent)
            }
            R.id.action_location->{
                if(changeLocaLayout.visibility == View.VISIBLE){
                    changeLocaLayout.visibility = View.GONE
                }
                else{
                    changeLocaLayout.visibility = View.VISIBLE
                    usersLayout.visibility = View.VISIBLE
                    btnBack.visibility = View.GONE
                }
            }

            R.id.action_profile -> {
               val intent = Intent(this, MyProfileActivity::class.java)
                startActivity(intent)
            }

            R.id.action_gender -> {
                ageFilterDialog()
            }
            R.id.action_filter_users -> {
                filterUsersDialog()
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return super.onOptionsItemSelected(item)
    }
    fun showUsersLayout(v: View){
        btnBack.visibility = View.GONE
        usersLayout.visibility = View.VISIBLE

    }
    private fun shareMe() {
        val sendIntent = Intent()
        val msg = getString(R.string.share_message)
        sendIntent.action = Intent.ACTION_SEND
        sendIntent.putExtra(Intent.EXTRA_TEXT, msg)
        sendIntent.type = "text/plain"
        startActivity(sendIntent)
    }
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        when (item.itemId) {
            R.id.nav_profile -> {
                val intent = Intent(this@MainActivity, MyProfileActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_edit_profile -> {
                val intent = Intent(this@MainActivity, EditAccountActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_edit_images -> {
                val intent = Intent(this@MainActivity, EditImagesActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_who_likes -> {
                val intent = Intent(this@MainActivity, MyLikesActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_who_viewed -> {
                val intent = Intent(this, MyViewsActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_my_likes -> {
                val intent = Intent(this@MainActivity, PeopleILikeActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_age -> {
                ageFilterDialog()
            }
            R.id.nav_chats -> {
                tinyDB.putString("sentOrReceived", "Received Chats")
                val intent = Intent(this@MainActivity, MyChatsActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_missed_call -> {
                val intent = Intent(this@MainActivity, CallLogsActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_top_up -> {
                topUpDialog()

            }
            R.id.nav_notification_settings -> {
              notificationSettings()
            }
            R.id.nav_chats_out -> {
                tinyDB.putString("sentOrReceived", "Pending Chats")
                val intent = Intent(this@MainActivity, MyChatsActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_logout -> {
                tinyDB.putBoolean("isLoggedIn", false)
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
                finish()
            }
        }

        drawer_layout.closeDrawer(GravityCompat.START)
        return true
    }
    private fun topUpDialog(){
        val builder = AlertDialog.Builder(this@MainActivity)
        val callCredits = tinyDB.getDouble("callCredits", 0.0)
        builder.setTitle("VIDEO CALL CREDITS")
        builder.setMessage("Your Video Call Credit Balance is:\n $callCredits Minute(s)")
        builder.setCancelable(false)
        builder.setIcon(R.mipmap.paired_circle_round)
        builder.setPositiveButton("Top Up Now") { dialog, which ->
            tinyDB.putString("topUpType", "Call")
            goToStorePayments()
        }
        builder.setNegativeButton("Close") { dialog, which ->
            dialog.cancel()
        }
        val alert = builder.create()
        alert.show()
    }
    private fun isLocationEnabled() {
        val isEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // This is new method provided in API 28
            val lm = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.isLocationEnabled
        } else {
            // This is Deprecated in API 28
            val mode = Settings.Secure.getInt(this.contentResolver, Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF)
            (mode != Settings.Secure.LOCATION_MODE_OFF)
        }

        if(!isEnabled){
            buildAlertMessageNoGps()
        }
        else{
            requestPerms()
        }

    }
    private fun buildAlertMessageNoGps() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage("Enable Location Service?")
            .setCancelable(false)
            .setPositiveButton("Yes") { dialog, id ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("No") { dialog, id ->
                finish()
            }
        val alert = builder.create()
        alert.show()
    }

    private fun requestPerms() {
        //request location permission
        if (Build.VERSION.SDK_INT >= 23) {
            if (
                ActivityCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
                ||
                ActivityCompat.checkSelfPermission(
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
            )

            {
                requestPermissions(
                    arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.CAMERA,
                        android.Manifest.permission.RECORD_AUDIO,
                        android.Manifest.permission.MODIFY_AUDIO_SETTINGS
                    ), LOCATION_REQUEST
                )
                return
            }
        }

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            LOCATION_REQUEST -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    getCurrentLocation()
                } else {
                    toastMsg("Paired Needs These Permissions To Work")
                    finish()
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
    private fun toastMsg(msg: String) {
        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
    }
    private fun toastMsgShort(msg: String) {
        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
    }

    private fun getMyAddress(latitude: Double, longitude: Double) {
        btnLocation.visibility = View.VISIBLE
        setRefreshingSwipeTrue()
        // Instantiate the RequestQueue.
        btnLocation.text = getString(R.string.searching_loc)
        val queue = Volley.newRequestQueue(this)
        val url = "https://us1.locationiq.com/v1/reverse.php?key=${Constants.locationIqKey}&lat=$latitude&lon=$longitude&format=json"
        // Request a string response from the provided URL.
        val stringRequest = StringRequest(
            Request.Method.GET, url,
            Response.Listener<String> { response ->
               //println("RESULT: $response")
                try {
                    val json = JSONObject(response)
                    val addressName = json.getString("display_name")
                    val lat = json.getDouble("lat")
                    val lon = json.getDouble("lon")
                    //show current user  location on map
                    btnLocation.text = addressName
                    //store current pickup location
                    tinyDB.putDouble("defaultLat", lat)
                    tinyDB.putDouble("defaultLon", lon)
                    tinyDB.putString("defaultAddressName", addressName)
                    tinyDB.putString("myLocation", addressName)
                    //load online users here and check user account status here
                    getAccountStatus(lat, lon, addressName)
                    fetchNearByUsers(0, addressName, false)
                    btnLocation.visibility = View.GONE
                    setRefreshingSwipeFalse()
                }catch (ex: Exception){

                    goLocationDefault()
                    setRefreshingSwipeFalse()
                }

            },
            Response.ErrorListener {
                //error loading
                setRefreshingSwipeFalse()
                goLocationDefault()
            })

        // Add the request to the RequestQueue.
        queue.add(stringRequest)

    }
     private fun getMyAddressUpdate(latitude: Double, longitude: Double) {
        // Instantiate the RequestQueue.
        //btnLocation.text = getString(R.string.searching_loc)
        val queue = Volley.newRequestQueue(this)
        val url = "https://us1.locationiq.com/v1/reverse.php?key=${Constants.locationIqKey}&lat=$latitude&lon=$longitude&format=json"
        // Request a string response from the provided URL.
        val stringRequest = StringRequest(
            Request.Method.GET, url,
            Response.Listener<String> { response ->
              // println("RESULT: $response")
                try {
                    val json = JSONObject(response)
                    val addressName = json.getString("display_name")
                    val lat = json.getDouble("lat")
                    val lon = json.getDouble("lon")
                    //store current pickup location
                    tinyDB.putDouble("defaultLat", lat)
                    tinyDB.putDouble("defaultLon", lon)
                    tinyDB.putString("defaultAddressName", addressName)
                    tinyDB.putString("myLocation", addressName)
                    //load online users here and check user account status here
                    getAccountStatus(lat, lon, addressName)
                }catch (ex: Exception){

                }

            },
            Response.ErrorListener {
            })

        // Add the request to the RequestQueue.
        queue.add(stringRequest)

    }
    private fun goLocationDefault(){
        val defaultLat = tinyDB.getDouble("defaultLat", 0.0)
        val defaultLon = tinyDB.getDouble("defaultLon", 0.0)
        val defaultAddress = tinyDB.getString("defaultAddressName")
        if(defaultAddress !="")
        {
            btnLocation.text = defaultAddress
            btnLocation.visibility = View.GONE
            getAccountStatus(defaultLat, defaultLon, defaultAddress)
            //load nearest users here
            fetchNearByUsers(0, defaultAddress, false)
        }
        else{
            btnLocation.text = getString(R.string.failed_loc)
        }
     }



    private fun getCurrentLocation() {
        btnLocation.text = getString(R.string.searching_loc)
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                lastLocation = location
                if (lastLocation != null) {
                    val lat = lastLocation!!.latitude
                    val lon = lastLocation!!.longitude
                    val defaultLat = tinyDB.getDouble("defaultLat", 0.0)
                    val defaultLon = tinyDB.getDouble("defaultLon", 0.0)
                    val defaultAddress = tinyDB.getString("defaultAddressName")
                    val myLocation = tinyDB.getString("myLocation")
                    if (defaultAddress == "") {
                        getMyAddress(lat, lon)
                    } else { //current location was not changed
                        btnLocation.text = defaultAddress
                        btnLocation.visibility = View.GONE
                        if(myLocation=="") { //if was never set
                            tinyDB.putString("myLocation", defaultAddress)
                        }
                        getAccountStatus(lat, lon, defaultAddress)
                        //load nearest users here
                        fetchNearByUsers(0, defaultAddress, false)
                    }
                }
                else
                {
                    goLocationDefault()
                }

            }

        }
    }


    private fun searchAddress(searchText: String) {
        //show dialog here
        btnLocation.visibility = View.VISIBLE
        val dialog = AlertDialog.Builder(this@MainActivity)
        val view = layoutInflater.inflate(R.layout.pickup_addresses_layout, null)
        view.tvSearchTitle.text = getString(R.string.searchingLocation)
        btnLocation.text = getString(R.string.searchingLocation)
        dialog.setView(view)
        val customDialog = dialog.create()
        customDialog.setTitle(getString(R.string.search_loc))
        customDialog.setIcon(R.mipmap.paired_circle_round)
        customDialog.show()
        //dialog.show()
        // Instantiate the RequestQueue.
        val queue = Volley.newRequestQueue(this)
        val url = "https://us1.locationiq.com/v1/search.php?key=${Constants.locationIqKey}&q=$searchText&format=json"

        // Request a string response from the provided URL.
        val stringRequest = StringRequest(
            Request.Method.GET, url,
            com.android.volley.Response.Listener<String> { response ->
                // println("RESULT: $response")
                //reset dialog message here
                view.tvSearchTitle.text = getString(R.string.choose_location)
                if (pickupAddressList.size > 0) {
                    pickupAddressList.clear()
                }
                try {
                    val json = JSONArray(response)
                    val size = json.length()
                    if (size > 0) {

                        for (i in 0 until size) {
                            val address = json.getJSONObject(i)
                            val addressName = address.getString("display_name")
                            val lat = address.getDouble("lat")
                            val lon = address.getDouble("lon")
                            pickupAddressList.add(Address(addressName, lat, lon))
                        }

                        val adapter = AddressAdapter(this, pickupAddressList)
                        view.pickupAddressesListView.adapter = adapter
                        view.pickupAddressesListView.setOnItemClickListener { parent, view, position, id ->
                            val pickupLat = pickupAddressList[position].latitude
                            val pickupLon = pickupAddressList[position].longitude
                            val addressName = pickupAddressList[position].displayName
                            tinyDB.putDouble("defaultLat", pickupLat)
                            tinyDB.putDouble("defaultLon", pickupLon)
                            tinyDB.putString("defaultAddressName", addressName)
                            //clear map here before adding new marker
                            btnLocation.text = addressName
                            btnLocation.visibility = View.GONE
                            editLocation.setText("")
                            hideKeyboard()
                            changeLocaLayout.visibility = View.GONE
                            //dismiss dialog here
                            customDialog.dismiss()

                            //load users here
                            hasMoreUsers = true //allow infinite scroll
                            fetchNearByUsers(0, addressName, false)
                        }

                    } else {
                        view.tvSearchTitle.text = getString(R.string.location_not_found)
                    }


                } catch (ex: Exception) {
                    view.tvSearchTitle.text = getString(R.string.location_not_found)

                }
            },
            com.android.volley.Response.ErrorListener {
                view.tvSearchTitle.text = getString(R.string.location_not_found)
            })

        // Add the request to the RequestQueue.
        queue.add(stringRequest)


    }

    fun searchLocation(v: View){
        searchArea()
    }

    private fun searchArea(){
        val searchedAddressName = editLocation.text.toString()
        val premiumExpired = tinyDB.getString("premiumExpired")
        val defaultAddress = tinyDB.getString("myLocation")
        if(searchedAddressName.isNotBlank()){
            if(premiumExpired =="No") {
                searchAddress(searchedAddressName)
            }
            else
            {
                confirmRechargeGlobal("Recharge Account", "To See users in Locations Other than your Current Location. Recharge your account to be PREMIUM User")
            }
        }
        else{
            toastMsg("Enter location name")
        }
    }
    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(drawer_layout.windowToken, 0)
    }


    private fun confirmGoBack(){
        val builder = AlertDialog.Builder(this@MainActivity)
        builder.setTitle("Confirm")
        builder.setMessage("Are you sure you want to close?")
        builder.setCancelable(false)
        builder.setIcon(R.mipmap.paired_circle_round)
        builder.setPositiveButton("Yes") { dialog, which ->
            finish()
        }
        builder.setNegativeButton("No") { dialog, which ->
            dialog.cancel()
        }
        val alert = builder.create()
        alert.show()
    }
    private fun noticeDialog(msg: String){
        val builder = AlertDialog.Builder(this@MainActivity)
        builder.setTitle("Notice")
        builder.setMessage(msg)
        builder.setCancelable(true)
        builder.setIcon(R.mipmap.paired_circle_round)

        builder.setPositiveButton("Ok") { dialog, which ->
            dialog.cancel()
        }
        val alert = builder.create()
        alert.show()
    }
    private fun confirmProfileImage(){
        val builder = AlertDialog.Builder(this@MainActivity)
        builder.setTitle("Profile Image")
        builder.setMessage("You do not have Profile Photo. Your account is not visible to others. Upload Photos now?")
        builder.setCancelable(false)
        builder.setIcon(R.mipmap.paired_circle_round)
        builder.setPositiveButton("Yes") { dialog, which ->
           val intent = Intent(this@MainActivity, EditImagesActivity::class.java)
            startActivity(intent)
        }
        builder.setNegativeButton("No") { dialog, which ->
            dialog.cancel()
        }
        val alert = builder.create()
        alert.show()
    }
    private fun confirmPayVIP(){
        val builder = AlertDialog.Builder(this@MainActivity)
        builder.setTitle("VIP Account EXPIRED")
        builder.setMessage("Your VIP Account is Expired. Do you want to Recharge Now?")
        builder.setCancelable(false)
        builder.setIcon(R.mipmap.paired_circle_round)
        builder.setPositiveButton("Yes") { dialog, which ->
            tinyDB.putString("topUpType", "Normal")
            goToStorePayments()
        }
        builder.setNegativeButton("No") { dialog, which ->
            dialog.cancel()
        }
        val alert = builder.create()
        alert.show()
    }
    private fun getCountry(myLocation: String): String{
        return if(myLocation.isNotBlank()) {
            val parts = myLocation.split((" ").toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
             parts[parts.size - 1]
        }
        else{
             "USA"
        }
    }

    private fun myCountry(): String{
        val myLocation = tinyDB.getString("myLocation")
        return if(myLocation.isNotBlank()) {
            val parts = myLocation.split((" ").toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            parts[parts.size - 1]
        }
        else{
            "USA"
        }
    }
    private fun confirmRecharge(title: String, msg: String){
        val builder = AlertDialog.Builder(this@MainActivity)
        builder.setTitle(title)
        builder.setMessage(msg)
        builder.setCancelable(false)
        builder.setIcon(R.mipmap.paired_circle_round)
        builder.setPositiveButton("Recharge") { dialog, which ->
            goToStorePayments()
        }
        builder.setNegativeButton("Close") { dialog, which ->
            dialog.cancel()
        }
        val alert = builder.create()
        alert.show()
    }
    private fun goToStorePayments(){
        val pkgManager = this.packageManager
        val installerPackageName = pkgManager.getInstallerPackageName(this.packageName)
        if ("com.android.vending" == installerPackageName)
        {
            val intent = Intent(this@MainActivity, GPayActivity::class.java)
            startActivity(intent)
        }
        else
        {
            val intent = Intent(this@MainActivity, AmazonPayActivity::class.java)
            startActivity(intent)
        }
    }
    private fun confirmRechargeGlobal(title: String, msg: String){
        val builder = AlertDialog.Builder(this@MainActivity)
        builder.setTitle(title)
        builder.setMessage(msg)
        builder.setCancelable(false)
        builder.setIcon(R.mipmap.paired_circle_round)
        builder.setPositiveButton("Recharge") { _, _ ->
            tinyDB.putString("topUpType", "Normal")
            goToStorePayments()
        }
        builder.setNeutralButton("Close") { dialog, _ ->
            dialog.cancel()
        }
        val alert = builder.create()
        alert.show()
    }


    inner class UserAdapter: androidx.recyclerview.widget.RecyclerView.Adapter<UserAdapter.MessageViewHolder>(){

        inner class MessageViewHolder(val card: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(card)

        //specify the contents for the shown items/habits
        override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
            val user = usersList[position]
            val userId = user.userId
            val profileImage = user.profileImage
            val name = user.name
            val sex = user.sex
            val locName = user.locName
            val age = user.age
            val likesCount = user.likesCount
            val locLat = user.locLat
            val locLon = user.locLon
            val premiumAccount = user.premiumAccount
            val lapseTime = user.lapseTime
            if(lapseTime < 61){
                holder.card.tvOnlineStatus.text = getString(R.string.online)
            }
            else
            {
                holder.card.tvOnlineStatus.text = getString(R.string.offline)
            }
            holder.card.tvTheName.text = name
            if(premiumAccount=="Yes"){
                holder.card.imgBadge.visibility = View.VISIBLE
            }
            else{
                holder.card.imgBadge.visibility = View.GONE
            }
            //add ads here

            //load images here
            val width = tinyDB.getInt("width")/4
            val height = tinyDB.getInt("width")/4

            if(sex=="Male") {
                Picasso.get()
                    .load(Constants.baseUrl + "images/userimages/" + profileImage)
                    .placeholder(R.drawable.progress_animation)
                    .error(R.drawable.man128)
                    .resize(width, height)
                    .centerCrop(Gravity.CENTER)
                    .transform(CircleTransform())
                    .into(holder.card.imgOfUser)
            }
            else{
                Picasso.get()
                    .load(Constants.baseUrl + "images/userimages/" + profileImage)
                    .placeholder(R.drawable.progress_animation)
                    .error(R.drawable.woman128)
                    .resize(width, height)
                    .centerCrop(Gravity.CENTER)
                    .transform(CircleTransform())
                    .into(holder.card.imgOfUser)
            }
            holder.card.setOnClickListener {
                viewUser(userId)
            }

            //load more videos when scrolled to bottom
            if((position >= itemCount - 1 && itemCount >= 10)){
                if(hasMoreUsers) {
                    val inArea = tinyDB.getBoolean("inArea")
                    if(inArea) {
                        fetchNearByUsers(userId, tinyDB.getString("defaultAddressName"), true)
                    }
                    else {
                        fetchUsersFarFromUserLocation(userId, tinyDB.getString("defaultAddressName"), true)
                    }

                }
            }
            else if(itemCount < 10){ //fetch from other areas
                val inArea = tinyDB.getBoolean("inArea")
                if(inArea) {
                    fetchUsersFarFromUserLocation(0, tinyDB.getString("defaultAddressName"), true)
                }
            }

        }

        //create new view holder for each item
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val view = layoutInflater.inflate(R.layout.user_list_item_random_layout, parent, false)
            return MessageViewHolder(view)
        }

        override fun getItemCount()= usersList.size



    }
    inner class TopUserAdapter: androidx.recyclerview.widget.RecyclerView.Adapter<TopUserAdapter.MessageViewHolder>(){

        inner class MessageViewHolder(val card: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(card)


        //specify the contents for the shown items/habits
        override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
            val user = topUsersList[position]
            val userId = user.userId
            val profileImage = user.profileImage
            val sex = user.sex

            if(sex=="Male") {
                Picasso.get()
                    .load(Constants.baseUrl + "images/userimages/" + profileImage)
                    .placeholder(R.drawable.progress_animation)
                    .error(R.drawable.man128)
                    .resize(80, 80)
                    .centerCrop(Gravity.CENTER)
                    .transform(CircleTransform())
                    //.memoryPolicy(MemoryPolicy.NO_CACHE, MemoryPolicy.NO_STORE)
                    .into(holder.card.imgOfTopUser)
            }
            else{
                Picasso.get()
                    .load(Constants.baseUrl + "images/userimages/" + profileImage)
                    .placeholder(R.drawable.progress_animation)
                    .error(R.drawable.woman128)
                    .resize(80, 80)
                    .centerCrop(Gravity.CENTER)
                    .transform(CircleTransform())
                    //.memoryPolicy(MemoryPolicy.NO_CACHE, MemoryPolicy.NO_STORE)
                    .into(holder.card.imgOfTopUser)
            }
            holder.card.setOnClickListener {
                viewUser(userId)
            }

            //load more users when scrolled to bottom
           if((position >= itemCount - 1 && itemCount >= 10)){
                if(hasMoreTopUsers) {
                    fetchTopUsers(userId, tinyDB.getString("defaultAddressName"), true)
                }
            }

        }

        //create new view holder for each item
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val view = layoutInflater.inflate(R.layout.user_list_item_top_layout, parent, false)
            return MessageViewHolder(view)
        }

        override fun getItemCount()= topUsersList.size



    }



    private fun setRefreshingSwipeFalse(){
        if(swipeRefreshUsers.isRefreshing){
            swipeRefreshUsers.isRefreshing = false
        }
    }

    private fun setRefreshingSwipeTrue(){
        if(!swipeRefreshUsers.isRefreshing){
            swipeRefreshUsers.isRefreshing = true
        }
    }

    //get device width and height for image resizing, store them in shared preferences using TinyDB Class
    private fun setDimensions(){
        //get window manager height and width
        val displayMetrics = DisplayMetrics()
        this@MainActivity.windowManager.defaultDisplay.getMetrics(displayMetrics)
        val deviceHeight = displayMetrics.heightPixels
        val deviceWidth = displayMetrics.widthPixels
        tinyDB.putInt("width", deviceWidth)
        tinyDB.putInt("height", deviceHeight)
    }


    /*
    this function fetches users near current users location
    loadMore-used to check if it first call of function or call of function for infinite scroll os users
     */
    private fun fetchNearByUsers(userId: Int, locName: String, loadMore: Boolean)
    {
        usersLayout.visibility = View.VISIBLE
        tinyDB.putBoolean("inArea", true) //fetching users from current location
        if (usersList.size>0){
            if(!loadMore){
                setRefreshingSwipeTrue()
                usersList.clear()
                //setLayoutManager()
            }
        }
        else{
            setRefreshingSwipeTrue()
        }
        //send info to server
        val url = Constants.baseUrl + "index.php/app/usersList"

        //start sending request
        val queue = Volley.newRequestQueue(this)
        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { response ->
                //process result here
                if(loadMore){ //get current list before loading more
                    currentSize = UserAdapter().itemCount
                    val myLayoutManager: androidx.recyclerview.widget.GridLayoutManager = usersListView.layoutManager as androidx.recyclerview.widget.GridLayoutManager
                    scrollPosition = myLayoutManager.findLastCompletelyVisibleItemPosition()
                }
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
                          val lapseTime = user.getDouble("lapseTime")

                            usersList.add(UserObject(
                                id,
                                name,
                                bio,
                                emailAddress,
                                sex,
                                dobYear,
                                locLat,
                                locLon,
                                locationName,
                                profileImage,
                                age,
                                likesCount,
                                premiumAccount,
                                lapseTime
                            ))

                        }
                        //first loading or reload
                        if(!loadMore) {

                            usersListView.adapter = UserAdapter()
                        }
                        if(loadMore) //loading more
                        {
                            UserAdapter().notifyDataSetChanged()
                            usersListView.scrollToPosition(scrollPosition)
                        }

                        setRefreshingSwipeFalse()
                        btnBack.visibility = View.GONE
                    }
                    else
                    {

                        setRefreshingSwipeFalse()
                        hasMoreUsers = true
                        if(usersList.size==0) {
                            fetchUsersFarFromUserLocation(0, tinyDB.getString("defaultAddressName"), false)
                        }
                        else{
                            fetchUsersFarFromUserLocation(0, tinyDB.getString("defaultAddressName"), true)
                        }
                    }


                }catch (ex:Exception){
                    toastMsg("Failed loading Users, Reload again")
                    setRefreshingSwipeFalse()
                }


            },
            Response.ErrorListener { e ->
                toastMsg("Failed loading Users, Reload again")
                setRefreshingSwipeFalse()
            }) {
            override fun getParams(): Map<String, String?> {
                return mapOf(
                    "locName" to locName,
                    "userCategory" to tinyDB.getString("userCategory"),
                    "fromAge" to tinyDB.getInt("fromAge").toString(),
                    "toAge" to tinyDB.getInt("toAge").toString(),
                    "sex" to tinyDB.getString("viewSex"),
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

    //this function fetches users who are not near users current location
    private fun fetchUsersFarFromUserLocation(userId: Int, locName: String, loadMore: Boolean)
    {
        usersLayout.visibility = View.VISIBLE
        tinyDB.putBoolean("inArea", false)
        if (usersList.size>0){
            if(!loadMore){
                setRefreshingSwipeTrue()
                usersList.clear()
                //setLayoutManager()
            }
        }
        else{
            setRefreshingSwipeTrue()
        }
        //send info to server
        val url = Constants.baseUrl + "index.php/app/usersListNotInArea"
        //start sending request
        val queue = Volley.newRequestQueue(this)
        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { response ->
                //process result here
                if(loadMore){ //get current list before loading more
                    currentSize = UserAdapter().itemCount
                   val myLayoutManager: androidx.recyclerview.widget.GridLayoutManager = usersListView.layoutManager as androidx.recyclerview.widget.GridLayoutManager
                    scrollPosition = myLayoutManager.findLastCompletelyVisibleItemPosition()
                }
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
                          val lapseTime = user.getDouble("lapseTime")

                            usersList.add(UserObject(
                                id,
                                name,
                                bio,
                                emailAddress,
                                sex,
                                dobYear,
                                locLat,
                                locLon,
                                locationName,
                                profileImage,
                                age,
                                likesCount,
                                premiumAccount,
                                lapseTime
                            ))


                        }
                        //first loading or reload
                        if(!loadMore) {

                            usersListView.adapter = UserAdapter()
                        }
                        if(loadMore) //loading more
                        {
                            UserAdapter().notifyDataSetChanged()
                            usersListView.scrollToPosition(scrollPosition)
                        }

                        btnBack.visibility = View.GONE
                    }
                    else
                    {
                        if(!loadMore) {
                            val viewSex = tinyDB.getString("viewSex")
                            if(viewSex == "Male") {
                                toastMsg("No Male Users Found Here $locName")
                            }
                            else{
                                toastMsg("No Female Users Found Here $locName")
                            }
                            //usersLayout.visibility = View.GONE
                            btnBack.visibility = View.GONE
                        }
                        else{
                            toastMsg("Reached end of List")
                        }
                        hasMoreUsers = false
                    }
                    setRefreshingSwipeFalse()

                }catch (ex:Exception){
                    toastMsg("Failed loading Users, Reload again")
                    setRefreshingSwipeFalse()
                }


            },
            Response.ErrorListener { e ->
                toastMsg("Failed loading Users, Reload again")
                setRefreshingSwipeFalse()
            }) {
            override fun getParams(): Map<String, String?> {
                return mapOf(
                    "locName" to locName,
                    "userCategory" to tinyDB.getString("userCategory"),
                    "fromAge" to tinyDB.getInt("fromAge").toString(),
                    "toAge" to tinyDB.getInt("toAge").toString(),
                    "sex" to tinyDB.getString("viewSex"),
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


    //this function gets classic(sponsored) users from server
    private fun fetchTopUsers(userId: Int, locName: String, loadMore: Boolean)
    {
        usersLayout.visibility = View.VISIBLE

        if (topUsersList.size>0){
            if(!loadMore){
                setRefreshingSwipeTrue()
                topUsersList.clear()
            }
        }
        else{
            setRefreshingSwipeTrue()
        }
        //send info to server
        val url = Constants.baseUrl + "index.php/app/usersList"
        //start sending request
        val queue = Volley.newRequestQueue(this)
        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { response ->
                //process result here
                if(loadMore){ //get current list before loading more
                    currentSizeTop = TopUserAdapter().itemCount
                   val myLayoutManager: androidx.recyclerview.widget.LinearLayoutManager = topUsersListView.layoutManager as androidx.recyclerview.widget.LinearLayoutManager
                    scrollPositionTop = myLayoutManager.findLastCompletelyVisibleItemPosition()
                }
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
                          val lapseTime = user.getDouble("lapseTime")

                            topUsersList.add(UserObject(
                                id,
                                name,
                                bio,
                                emailAddress,
                                sex,
                                dobYear,
                                locLat,
                                locLon,
                                locationName,
                                profileImage,
                                age,
                                likesCount,
                                premiumAccount,
                                lapseTime
                            ))
                        }
                        //first loading or reload
                        if(!loadMore) {

                            topUsersListView.adapter = TopUserAdapter()
                        }
                        if(loadMore) //loading more
                        {
                            TopUserAdapter().notifyDataSetChanged()
                            topUsersListView.scrollToPosition(scrollPositionTop)
                        }

                    }
                    else
                    {
                        if(!loadMore) {
                            val viewSex = tinyDB.getString("viewSex")
                            if(viewSex == "Male") {
                                toastMsg("No Male Users Found Here")
                            }
                            else{
                                toastMsg("No Female Users Found Here")
                            }
                            usersLayout.visibility = View.VISIBLE
                        }
                        else{
                            toastMsg("Reached end of List")
                        }
                        hasMoreTopUsers = false
                    }
                    setRefreshingSwipeFalse()

                }catch (ex:Exception){
                   //println("ERROR_USERS: ${ex.message}")
                    toastMsg("Failed loading Top Users, Reload again")
                    setRefreshingSwipeFalse()
                }


            },
            Response.ErrorListener { e ->
                toastMsg("Failed loading Top Users, Reload again")
                setRefreshingSwipeFalse()
            }) {
            override fun getParams(): Map<String, String?> {
                return mapOf(
                    "locName" to locName,
                    "userCategory" to "Classic",
                    "sex" to tinyDB.getString("viewSex"),
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

    //this function is used to check if there is internet connection using ping command
    fun isConnected():Boolean {
        val cm = this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetworkInfo
        return activeNetwork != null
    }
    private fun initAds() {
        if (isConnected()) {
            //initialize ads
           MobileAds.initialize(this, getString(R.string.appId))
            mAdView = adViewMain
            val adRequest = AdRequest
                .Builder()
                .addTestDevice("28480BE4C8AF9DAA06B7514F14DE6DDA")
                .addTestDevice("99C784B0B9C5ED479BFF3B1557A6309F")
                .build()
            mAdView!!.loadAd(adRequest)

            mAdView!!.adListener = object : AdListener() {

            }

        }
    }

    private fun getAccountStatus(locLat: Double, locLon: Double, locName: String) {
        //send info to server
        //toastMsg("Please wait...")
        val url = Constants.baseUrl + "index.php/app/getAccountStatus"
        //start sending request
        val queue = Volley.newRequestQueue(this)
        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { response ->
                //println("RESULT_ACCOUNT: $response")
                try {
                    val json = JSONArray(response)
                    val size = json.length()
                    if (size > 0) {
                       tinyDB.putInt("checkedLocation", 1)
                        for (i in 0 until size) {

                            val user = json.getJSONObject(i)
                            val userId = user.getInt("userId")
                            val myAge = user.getInt("myAge")
                            val dobYear = user.getInt("dobYear")
                            val status = user.getString("status")
                            val alert = user.getString("message")
                            val name = user.getString("name")
                            val profileImage = user.getString("profileImage")
                            val theEmail = user.getString("emailAddress")
                            val premiumExpired = user.getString("premiumExpired")
                            val premiumAccount = user.getString("premiumAccount")
                            val accountType = user.getString("accountType")
                            val sponsored = user.getString("sponsored")
                            val callCredits = user.getDouble("callCredits")
                            val sex = user.getString("sex")
                            val premiumExpireDate = user.getString("premiumExpireDate")
                            when (status) {
                                "SUCCESS" -> {
                                    if(premiumAccount=="No"){
                                        btnBeHere.visibility = View.VISIBLE
                                    }
                                    else{
                                        btnBeHere.visibility = View.GONE
                                    }
                                    tinyDB.putInt("userId", userId)
                                    tinyDB.putInt("myAge", myAge)
                                    tinyDB.putInt("dobYear", dobYear)
                                    tinyDB.putString("sex", sex)
                                    tinyDB.putString("premiumExpired", premiumExpired)
                                    tinyDB.putString("sponsored", sponsored)
                                    tinyDB.putString("amPremium", premiumAccount)
                                    tinyDB.putString("accountType", accountType)
                                    tinyDB.putString("premiumExpireDate", premiumExpireDate)
                                    tinyDB.putString("profileImage", profileImage)
                                    tinyDB.putString("emailAddress", theEmail)
                                    tinyDB.putString("name", name)
                                    tinyDB.putDouble("callCredits", callCredits)

                                }
                                "NOT_FOUND" -> {
                                    failedStatus()
                                }
                                else -> {
                                    failedStatus()
                                }
                            }

                        }

                    } else {
                        failedStatus()
                    }


                } catch (ex: Exception) {
                    failedStatus()

                }

            },
            Response.ErrorListener { e ->
                failedStatus()
            }) {
            override fun getParams(): Map<String, String?> {
                return mapOf(
                    "locLat" to locLat.toString(),
                    "locLon" to locLon.toString(),
                    "locName" to locName,
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
    private fun failedStatus(){
        toastMsg("Failed to check your account status")
        finish()
    }

    //get user device current fcm token and send to server top update his/her account
    private fun getCurrentTokenSend() {
        thread {
            try {
                //subscribe to TM notification topic, Change this in Constants.kt file
                FirebaseMessaging.getInstance().subscribeToTopic(Constants.fcmNotificationTopic)
                // Get token
                FirebaseInstanceId.getInstance().instanceId
                    .addOnCompleteListener(OnCompleteListener<InstanceIdResult> { task ->
                        if (!task.isSuccessful) {
                            return@OnCompleteListener
                        }
                        // Get new Instance ID token
                        val token = task.result!!.token
                        tinyDB.putString("token", token)
                        if (tinyDB.getInt("userId") > 0) { //user logged in at least once
                            sendTokenToServer(token)
                        }
                    })
            }catch (ex: Exception){}

        }
    }
    //send current device fcm token to server for update
    private fun sendTokenToServer(token: String) {
        //send info to server
        val url = Constants.baseUrl + "index.php/app/updateToken"
        //start sending request
        val queue = Volley.newRequestQueue(this)
        val stringRequest = object : StringRequest(Request.Method.POST, url,
            Response.Listener { response ->
                if (response == "Success") {
                    //toastMsg("Subscribed successfully")
                } else {
                    //toastMsg("Failed to Subscribe, Try again")
                }
            },
            Response.ErrorListener { _ ->
                //toastMsg("Try again")
            }) {
            override fun getParams(): Map<String, String?> {
                return mapOf(
                    "token" to token,
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


    //set alarm manager to check likes, profile views and new messages statuses from server
    fun setNotificationAlarm(){
        try {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.MINUTE, 1)

            calendar.set(Calendar.HOUR_OF_DAY, calendar.get(Calendar.HOUR_OF_DAY))
            calendar.set(Calendar.MINUTE, calendar.get(Calendar.MINUTE))
            calendar.set(Calendar.SECOND, calendar.get(Calendar.SECOND))

            val am = applicationContext!!.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(applicationContext, NotificationsBroadCastReceiver::class.java)
            intent.putExtra("message", "Check notifications")
            intent.action = "com.xbh.notification"
            val pi = PendingIntent.getBroadcast(applicationContext,1,intent, PendingIntent.FLAG_UPDATE_CURRENT)
            val intervalTime = Constants.alarmInterval
            am.setRepeating(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, intervalTime.toLong(), pi)
        }catch (ex: Exception){}

    }
    //set automated Notification Alarm Manager for each user who has not opened app within 6 hours interval
    fun setDailyNotification(){
        try {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.HOUR, Constants.NotificationIntervalHours)

            calendar.set(Calendar.HOUR_OF_DAY, calendar.get(Calendar.HOUR_OF_DAY))
            calendar.set(Calendar.MINUTE, calendar.get(Calendar.MINUTE))
            calendar.set(Calendar.SECOND, calendar.get(Calendar.SECOND))
            val am = applicationContext!!.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(applicationContext, NotificationsBroadCastReceiver::class.java)
            intent.putExtra("message", "Check Salute")
            intent.action = "com.xbh.salute"
            val pi = PendingIntent.getBroadcast(applicationContext,111,intent, PendingIntent.FLAG_UPDATE_CURRENT)
            am.setRepeating(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
        }catch (ex: Exception){}

    }

    //this function checks users current location on each session
     @TargetApi(16)
     fun requestSingleLocationUpdate() {
         val checkedLocation = tinyDB.getInt("checkedLocation")
         if(checkedLocation==0) { //check user location once each time he/she open the app
             // Looper.prepare();
             // only works with SDK Version 23 or higher
             if (android.os.Build.VERSION.SDK_INT >= 23) {
                 if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !== PackageManager.PERMISSION_GRANTED || this.checkSelfPermission(
                         Manifest.permission.ACCESS_COARSE_LOCATION
                     ) !== PackageManager.PERMISSION_GRANTED
                 ) {
                     // permission is not granted
                     //Log.e("SiSoLocProvider", "Permission not granted.")
                     return
                 } else {
                     //Log.d("SiSoLocProvider", "Permission granted.")
                 }
             } else {
                 // Log.d("SiSoLocProvider", "SDK < 23, checking permissions should not be necessary")
             }
             val startTime = System.currentTimeMillis()
             fusedTrackerCallback = object : LocationCallback() {
                 override fun onLocationResult(locationResult: LocationResult) {
                     // These lines of code will run on UI thread.
                     if ((locationResult.lastLocation != null) && (System.currentTimeMillis() <= startTime + 30 * 1000)) {
                         val locLat = locationResult.lastLocation.latitude
                         val locLon = locationResult.lastLocation.longitude
                         //get current location from Location IQ
                         getMyAddressUpdate(locLat, locLon)
                         //System.out.println("LOCATION: " + locationResult.getLastLocation().getLatitude() + "|" + locationResult.getLastLocation().getLongitude())
                         // System.out.println("ACCURACY: " + locationResult.getLastLocation().getAccuracy())
                         fusedLocationClient.removeLocationUpdates(fusedTrackerCallback)
                     } else {
                         //println("LastKnownNull? :: " + (locationResult.getLastLocation() == null))
                         //println("Time over? :: " + (System.currentTimeMillis() > startTime + 30 * 1000))
                     }

                     fusedLocationClient.removeLocationUpdates(this)
                 }
             }
             val req = LocationRequest()
             req.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
             req.fastestInterval = 2000
             req.interval = 2000
             // Receive location result on UI thread.
             fusedLocationClient.requestLocationUpdates(req, fusedTrackerCallback, Looper.getMainLooper())
         }
     }




    private fun setFromAgeYears(spinner: Spinner) {
        if (fromAgeList.size > 0) {
            fromAgeList.clear()

        }
        val maxYear = 120
        var minYear = 18
        while(minYear < maxYear){
            fromAgeList.add(DobYear(minYear))
            minYear++
        }

        val years = java.util.ArrayList<Int>()
        for (i in fromAgeList.indices) {
            years.add(fromAgeList[i].yearName)
        }
        // Creating adapter for spinner
        val spinnerAdapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, years)
        // Drop down bar_layout style - list view with radio button
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        // attaching data adapter to spinner
        spinner.adapter = spinnerAdapter
    }
    private fun setToAgeYears(spinner: Spinner) {
        if (toAgeList.size > 0) {
            toAgeList.clear()

        }
        val maxYear = 120
        var minYear = 18
        while(minYear <= maxYear){
            toAgeList.add(DobYear(minYear))
            minYear++
        }

        val years = java.util.ArrayList<Int>()
        for (i in toAgeList.indices) {
            years.add(toAgeList[i].yearName)
        }
        // Creating adapter for spinner
        val spinnerAdapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, years)
        // Drop down bar_layout style - list view with radio button
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        // attaching data adapter to spinner
        spinner.adapter = spinnerAdapter
    }

    private fun ageFilterDialog(){
        val dialog = AlertDialog.Builder(this@MainActivity, android.R.style.Theme_Light_NoTitleBar)
        val view = layoutInflater.inflate(R.layout.age_dialog_layout, null)
        setFromAgeYears(view.spinnerFrom)
        setToAgeYears(view.spinnerTo)
        setCurrentYear(view.spinnerFrom, fromAgeList, "fromAge")
        setCurrentYear(view.spinnerTo, toAgeList, "toAge")
        val viewSex = tinyDB.getString("viewSex")
        if(viewSex=="Male"){
            view.spinnerViewSex.setSelection(0)
        }
        else{
            view.spinnerViewSex.setSelection(1)
        }
        dialog.setView(view)
        dialog.setCancelable(false)

        val customDialog = dialog.create()
        customDialog.setTitle("Age & Sex Filter")
        customDialog.setIcon(R.mipmap.paired_circle_round)
        customDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        customDialog.window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        customDialog.show()

        view.btnSaveFilter.setOnClickListener {
            val fromAge = fromAgeList[view.spinnerFrom.selectedItemPosition].yearName
            val toAge = toAgeList[view.spinnerTo.selectedItemPosition].yearName
            val newSex = view.spinnerViewSex.selectedItem.toString()
            if(fromAge <= toAge) {
                tinyDB.putInt("fromAge", fromAge)
                tinyDB.putInt("toAge", toAge)
                tinyDB.putString("viewSex", newSex)
                customDialog.dismiss()
                hasMoreUsers = true
                fetchNearByUsers(0, tinyDB.getString("defaultAddressName"), false)
            }
            else
            {
                toastMsgShort("To Age Value Must Be Greater Than From Age")
            }
        }

        view.btnCancel.setOnClickListener {
            customDialog.dismiss()
        }
    }

    private fun filterUsersDialog(){
        val dialog = AlertDialog.Builder(this@MainActivity, android.R.style.Theme_Light_NoTitleBar)
        val view = layoutInflater.inflate(R.layout.user_filter_dialog_layout, null)
        val chatAgeMax = tinyDB.getInt("chatAgeMax")
        if(chatAgeMax==0){ //set default age limit
            tinyDB.putInt("chatAgeMax", 120)
        }
        setFromAgeYears(view.spinnerChatAgeMin)
        setToAgeYears(view.spinnerChatAgeMax)
        setCurrentYear(view.spinnerChatAgeMin, fromAgeList, "chatAgeMin")
        setCurrentYear(view.spinnerChatAgeMax, toAgeList, "chatAgeMax")

        val chatSex = tinyDB.getString("chatSex")

        if(chatSex=="All" || chatSex==""){
            view.spinnerChatSex.setSelection(0)
        }
        else if(chatSex=="Male"){
            view.spinnerChatSex.setSelection(1)
        }
        else{
            view.spinnerChatSex.setSelection(2)
        }
        val chatUserType = tinyDB.getString("chatUserType")
        if(chatUserType=="All" || chatUserType==""){
            view.spinnerChatUserType.setSelection(0)
        }
        else if(chatUserType=="Premium"){
            view.spinnerChatUserType.setSelection(1)
        }

        dialog.setView(view)
        dialog.setCancelable(false)

        val customDialog = dialog.create()
        customDialog.setTitle("Filter Users")
        customDialog.setIcon(R.mipmap.paired_circle_round)
        customDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        customDialog.window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        customDialog.show()

        view.btnSaveSettings.setOnClickListener {
            val fromAge = fromAgeList[view.spinnerChatAgeMin.selectedItemPosition].yearName
            val toAge = toAgeList[view.spinnerChatAgeMax.selectedItemPosition].yearName
            val newSex = view.spinnerChatSex.selectedItem.toString()
            val chatUser = view.spinnerChatUserType.selectedItem.toString()
            if(fromAge <= toAge) {
                tinyDB.putInt("chatAgeMin", fromAge)
                tinyDB.putInt("chatAgeMax", toAge)
                tinyDB.putString("chatSex", newSex)
                tinyDB.putString("chatUserType", chatUser)
                customDialog.dismiss()
                //save settings to server
                sendUsersFilter()
            }
            else
            {
                toastMsgShort("To Age Value Must Be Greater Than From Age")
            }
        }

        view.btnCancelSettings.setOnClickListener {
            customDialog.dismiss()
        }
    }

    private fun sendUsersFilter() {
        //send info to server
        val url = Constants.baseUrl + "index.php/app/updateUsersFilter"
        //start sending request
        val queue = Volley.newRequestQueue(this)
        val stringRequest = object : StringRequest(Request.Method.POST, url,
            Response.Listener { response ->
                //println("RESULT_TOKEN: $response")

                if (response == "SUCCESS") {
                    toastMsgShort("Saved successfully")
                } else {
                    toastMsg("Failed to Save, Try again")
                }
            },
            Response.ErrorListener { e ->
                //toastMsg("Try again")
            }) {
            override fun getParams(): Map<String, String?> {
                return mapOf(
                    "chatAgeMin" to tinyDB.getInt("chatAgeMin").toString(),
                    "chatAgeMax" to tinyDB.getInt("chatAgeMax").toString(),
                    "chatSex" to tinyDB.getString("chatSex"),
                    "chatUserType" to tinyDB.getString("chatUserType"),
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


    private fun setCurrentYear(spinnerYear: Spinner, yearsList: ArrayList<DobYear>, ageType: String){
        var i=0
        while(i< yearsList.size){
            if (spinnerYear.getItemAtPosition(i) == tinyDB.getInt(ageType)) {
                spinnerYear.setSelection(i)
            }
            i++
        }
    }

    private fun notificationSettings(){
        val dialog = AlertDialog.Builder(this@MainActivity, android.R.style.Theme_Light_NoTitleBar)
        val view = layoutInflater.inflate(R.layout.notification_dialog_layout, null)
        //set default settings
        val callsNoti = tinyDB.getBoolean("callsNoti")
        val chatsNoti = tinyDB.getBoolean("chatsNoti")
        val callTone = tinyDB.getBoolean("callTone")
        val likeNoti = tinyDB.getBoolean("likeNoti")
        val visitorNoti = tinyDB.getBoolean("visitorNoti")


            setCurrentState(view.btnCallsNoti, callsNoti)
            setCurrentState(view.btnChatsNoti, chatsNoti)
            setCurrentState(view.btnCallTone, callTone)
            setCurrentState(view.btnLikeNoti, likeNoti)
            setCurrentState(view.btnVisitorNoti, visitorNoti)


        dialog.setView(view)
        dialog.setCancelable(false)

        val customDialog = dialog.create()
        customDialog.setTitle("Notification Settings")
        customDialog.setIcon(R.mipmap.paired_circle_round)
        customDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        customDialog.window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        customDialog.show()
        setNotifications(view.btnCallsNoti, "callsNoti")
        setNotifications(view.btnChatsNoti, "chatsNoti")
        setNotifications(view.btnCallTone, "callTone")
        setNotifications(view.btnVisitorNoti, "likeNoti")
        setNotifications(view.btnLikeNoti, "visitorNoti")


        view.btnCancelWindow.setOnClickListener {
            customDialog.dismiss()
        }
    }

    private fun setNotifications(toggleButton: ToggleButton, notiType: String){
        toggleButton.setOnCheckedChangeListener { _, isChecked ->
            if(isChecked){
                tinyDB.putBoolean(notiType, true)
            }
            else{
                tinyDB.putBoolean(notiType, false)
            }
            tinyDB.putString("changedSettings", "Yes")
        }
    }

    private fun setCurrentState(toggleButton: ToggleButton, currentState: Boolean){
        val changedSettings = tinyDB.getString("changedSettings")
        if(changedSettings=="") {
            toggleButton.isChecked = true
        }
        else{
            toggleButton.isChecked = currentState
        }
    }
}
