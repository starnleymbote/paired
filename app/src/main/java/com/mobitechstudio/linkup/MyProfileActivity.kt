package com.mobitechstudio.linkup

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.InputType
import android.util.Log
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import android.view.*
import android.widget.EditText
import android.widget.Toast
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.rbrooks.indefinitepagerindicator.IndefinitePagerIndicator
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.action_bar_layout.view.*
import kotlinx.android.synthetic.main.activity_my_profile.*
import kotlinx.android.synthetic.main.user_list_profile_image.view.*
import kotlinx.android.synthetic.main.vip_account_dialog.view.*
import org.json.JSONArray

class MyProfileActivity : AppCompatActivity() {
    lateinit var tinyDB: TinyDB
    private var mAdView: AdView?=null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_profile)
        tinyDB = TinyDB(this)
        customBar()
        initAds()


        btnMyViews.setOnClickListener {
            val intent = Intent(this, MyViewsActivity::class.java)
            startActivity(intent)
        }
        btnMyLikes.setOnClickListener {
            val intent = Intent(this, MyLikesActivity::class.java)
            startActivity(intent)
        }

        btnRecharge.setOnClickListener {

            confirmRechargeGlobal("Recharge Account", "Recharge Your Account, Touch Recharge Button")

        }
        btnEditProfile.setOnClickListener {
            val intent = Intent(this@MyProfileActivity, EditAccountActivity::class.java)
            startActivity(intent)
        }
        btnEditEmail.setOnClickListener {
            val intent = Intent(this@MyProfileActivity, SetupEmailActivity::class.java)
            startActivity(intent)
        }
        btnEditProfileImages.setOnClickListener {
            val intent = Intent(this@MyProfileActivity, EditImagesActivity::class.java)
            startActivity(intent)
        }
        btnChangeAccountType.setOnClickListener {
            val accountType = tinyDB.getString("accountType")
            if(accountType == "Normal"){
                //change to premium
                val vipNotes = "VIP Account Features\n" +
                        "1. Your Account is Only Visible to people you start chat with\n" +
                        "2. View Users From Any Location In the World\n" +
                        "3. Start Chat With Any UserObject You find In Any Location\n" +
                        "4. Uploading Profile Photo is Optional"
                        noticeVip(vipNotes)
            }
            else
            {
                //change to normal
                //query server
                confirmGoToNormal()
            }
        }
        btnRemoveAccount.setOnClickListener {
            confirmDeleteAccount()
        }
        preventScreenShot()
    }


    private fun confirmGoToNormal(){
        val vipNotes = "IMPORTANT NOTES\n" +
                "1. Your Account Will Now be Visible To Any One\n" +
                "2. Uploading Profile Photo Will Be Mandatory"
        val builder = AlertDialog.Builder(this@MyProfileActivity)
        builder.setTitle("Confirm")
        builder.setMessage(vipNotes)
        builder.setCancelable(false)
        builder.setIcon(R.mipmap.paired_circle_round)
        builder.setPositiveButton("Yes, Continue") { dialog, which ->
            sendRequestToServer("Normal")
        }
        builder.setNegativeButton("No, Cancel") { dialog, which ->
            dialog.cancel()
        }
        val alert = builder.create()
        alert.show()
    }
    private fun sendRequestToServer(accountType: String) {
        //send info to server
        toastMsg("Processing, Please wait..")
        val url = Constants.baseUrl + "index.php/app/updateAccountType"
        //start sending request
        val queue = Volley.newRequestQueue(this)
        val stringRequest = object : StringRequest(Request.Method.POST, url,
            Response.Listener { response ->
                //println("RESULT_TOKEN: $response")

                if (response == "SUCCESS") {
                   tinyDB.putString("accountType", accountType)
                    updateLabels()
                    if(accountType=="VIP") {
                        tinyDB.putString("topUpType", "Normal")
                        goToStorePayments()
                    }
                    else{
                        toastMsg("Changed Successfully")
                    }
                } else {
                    toastMsg("Failed to change, try again")
                }
            },
            Response.ErrorListener { e ->
                toastMsg("Failed, try again")
            }) {
            override fun getParams(): Map<String, String?> {
                return mapOf(
                    "accountType" to accountType,
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

    private fun noticeVip(vipNotes: String){
        val dialog = AlertDialog.Builder(this@MyProfileActivity, android.R.style.Theme_Light_NoTitleBar)
        val view = layoutInflater.inflate(R.layout.vip_account_dialog, null)
        view.tvVipNotes.text = vipNotes
        dialog.setView(view)
        dialog.setCancelable(false)
        val customDialog = dialog.create()
        customDialog.setTitle("VIP Account")
        customDialog.setIcon(R.mipmap.paired_circle_round)
        customDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        customDialog.window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        customDialog.show()
        view.btnContinue.setOnClickListener {
            sendRequestToServer("VIP")
            if(customDialog.isShowing){
                customDialog.dismiss()
            }
        }
        view.btnCancel.setOnClickListener {
            if(customDialog.isShowing){
                customDialog.dismiss()
            }
        }


    }


    private fun preventScreenShot(){
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    }
    override fun onResume() {
        super.onResume()
        updateLabels()
        loadProfile()
    }


    private fun updateLabels(){
        val accountType = tinyDB.getString("accountType")
        val premiumExpired = tinyDB.getString("premiumExpired")
        val premiumExpireDate = tinyDB.getString("premiumExpireDate")
        if(accountType=="Normal"){
            btnChangeAccountType.text = "Upgrade to VIP"
        }
        else{
            btnChangeAccountType.text = "Downgrade to FREE"
        }
        if(premiumExpired=="Yes"){
            if(accountType=="Normal"){

                tvAccountStatus.text = "PREMIUM Account Expired"
            }
            else
            {
                tvAccountStatus.text = "Your VIP ACCOUNT Expired On: $premiumExpireDate"
            }

        }
        else{

            if(accountType=="Normal"){
                val amPremium = tinyDB.getString("amPremium")
                if(amPremium == "Yes") {
                    tvAccountStatus.text = "PREMIUM ACCOUNT Expires On: $premiumExpireDate"
                }
                else {
                    tvAccountStatus.text = "PREMIUM Offer Expires On: $premiumExpireDate"
                }
            }
            else
            {
                tvAccountStatus.text = "VIP ACCOUNT Expires On: $premiumExpireDate"
            }

        }
    }

    private fun customBar(){
        supportActionBar!!.displayOptions = ActionBar.DISPLAY_SHOW_CUSTOM
        supportActionBar!!.setDisplayShowCustomEnabled(true)
        supportActionBar!!.setCustomView(R.layout.action_bar_layout)
        val view = supportActionBar!!.customView
        view.tvTitle.text = getString(R.string.my_profile)
    }
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.share_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.action_share) {
            shareMe()
        }
        return super.onOptionsItemSelected(item)
    }
    private fun shareMe() {
        val sendIntent = Intent()
        val msg = getString(R.string.share_message)
        sendIntent.action = Intent.ACTION_SEND
        sendIntent.putExtra(Intent.EXTRA_TEXT, msg)
        sendIntent.type = "text/plain"
        startActivity(sendIntent)
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
            mAdView = findViewById(R.id.adViewProfile)
            val adRequest = AdRequest
                .Builder()
                .addTestDevice("28480BE4C8AF9DAA06B7514F14DE6DDA")
                .addTestDevice("99C784B0B9C5ED479BFF3B1557A6309F")
                .build()
            mAdView!!.loadAd(adRequest)

            mAdView!!.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    super.onAdLoaded()
                    Log.d("AD_LOADED","AD LOADED")
                }

                override fun onAdFailedToLoad(i: Int) {
                    super.onAdFailedToLoad(i)
                    Log.d("FAILED_AD_LOAD", "FAILED To LOAD AD: $i")
                }

            }

        }
    }

    /*
       this function loads facebook native ad as popup ad When this Activity is Destroyed.
       popup ads are only displayed on free users, change this if you want to show ads for all users, Remove Condition
       if(premiumExipired=="Yes")
        */
    private fun loadPopAd(){
        val premiumExpired = tinyDB.getString("premiumExpired")
        if(premiumExpired=="Yes"){
            val intent = Intent(this, FacebookAdActivity::class.java)
            startActivity(intent)
        }
    }
    override fun onDestroy() {
        // Destroy the AdView
        loadPopAd()
        if(mAdView !=null) {
            (mAdView!!.parent as ViewGroup).removeView(mAdView)
            mAdView!!.destroy()
        }

        super.onDestroy()
    }

    private fun loadProfile(){
        //send info to server
        toastMsg("Loading profile, please wait...")
        val url = Constants.baseUrl + "index.php/app/userProfile"
        //start sending request
        val queue = Volley.newRequestQueue(this)
        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { response ->
                //println("RESULT_PROFILE: $response")
                try {
                    val json = JSONArray(response)
                    val size = json.length()
                    if (size > 0){

                        for(i in 0 until size){
                            val user = json.getJSONObject(i)
                            val id = user.getInt("userId")
                            val age = user.getInt("age")
                            val viewsCount = user.getInt("viewsCount")
                            val likesCount = user.getInt("likes_count")
                            val name = user.getString("name")
                            val bio = user.getString("bio")
                            val emailAddress = user.getString("emailAddress")
                            val sex = user.getString("sex")
                            val dobYear = user.getInt("dobYear")
                            val locLat = user.getDouble("locLat")
                            val callCredits = user.getDouble("callCredits")
                            val locLon = user.getDouble("locLon")
                            val locationName = user.getString("locName")
                            val profileImage = user.getString("profileImage")
                            val premiumExpired = user.getString("premiumExpired")
                            val premiumExpireDate = user.getString("premiumExpireDate")
                            val premiumAccount      = user.getString("premiumAccount")
                            val sponsored      = user.getString("sponsored")

                            btnMyLikes.text = "Likes $likesCount"
                            btnMyViews.text = "Viewers $viewsCount"
                            tvMyBio.text = bio
                            tinyDB.putString("premiumExpired", premiumExpired)
                            tinyDB.putString("amPremium", premiumAccount)
                            tinyDB.putString("sponsored", sponsored)
                            tinyDB.putString("premiumExpireDate", premiumExpireDate)
                            tinyDB.putString("profileImage", profileImage)
                            tinyDB.putDouble("callCredits", callCredits)

                            updateLabels()


                        }

                        //load user images here
                        fetchUserImages(tinyDB.getInt("userId"),  myImagesListView, myPagesIndicator)
                    }
                    else
                    {
                        toastMsg("Profile Not Found Here")
                    }

                }catch (ex:Exception){
                    toastMsg("Error loading images, try gain")
                }


            },
            Response.ErrorListener { e ->
                toastMsg("Error loading images")
            }) {
            override fun getParams(): Map<String, String?> {
                return mapOf(
                    "userId" to tinyDB.getInt("userId").toString()
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


    private fun fetchUserImages(userId: Int, imageListV: androidx.recyclerview.widget.RecyclerView, pagerIndicator: IndefinitePagerIndicator)
    {
        val imagesList = arrayListOf<UserImage>()
        val height = tinyDB.getInt("height")/2 //image height half of device width, this device height is found in MainActivity.kt 0n setDimensions() function
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
                        imageListV.adapter = ImageAdapter(imagesList)
                        pagerIndicator.attachToRecyclerView(imageListV)
                    }
                    else
                    {
                        toastMsg("No Images Found Here")
                    }

                }catch (ex:Exception){
                    toastMsg("Error loading images, try gain")
                }


            },
            Response.ErrorListener { _ ->
                toastMsg("Error loading images, try gain")
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

    inner class ImageAdapter(private val listImages: ArrayList<UserImage>): androidx.recyclerview.widget.RecyclerView.Adapter<ImageAdapter.MessageViewHolder>(){

        inner class MessageViewHolder(val card: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(card)

        //specify the contents for the shown items/habits
        override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
            val image = listImages[position]
            val imgId = image.imgId
            val imgName = image.imgName
            //load image
            val height = tinyDB.getInt("height")/2
            val width = tinyDB.getInt("width")

            Picasso.get()
                .load(Constants.baseUrl+"images/userimages/"+imgName)
                .placeholder(R.drawable.progress_animation)
                .resize(width, height)
                .centerInside()
                .into(holder.card.imgMyProfile)

        }

        //create new view holder for each item
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.user_list_profile_image, parent, false)
            return MessageViewHolder(view)
        }

        override fun getItemCount()= listImages.size

    }

    private fun toastMsg(msg: String){
        Toast.makeText(this@MyProfileActivity, msg, Toast.LENGTH_SHORT).show()
    }


    private fun toastMsgShort(msg: String) {
        Toast.makeText(this@MyProfileActivity, msg, Toast.LENGTH_SHORT).show()
    }

    private fun confirmRechargeGlobal(title: String, msg: String){
        val builder = AlertDialog.Builder(this@MyProfileActivity)
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

    private fun goToStorePayments(){
        val pkgManager = this.packageManager
        val installerPackageName = pkgManager.getInstallerPackageName(this.packageName)
        if ("com.android.vending" == installerPackageName) //if installed from Play Store
        {
            val intent = Intent(this@MyProfileActivity, GPayActivity::class.java)
            startActivity(intent)
        }
        else
        {
            val intent = Intent(this@MyProfileActivity, AmazonPayActivity::class.java)
            startActivity(intent)
        }
    }


    private fun confirmDeleteAccount(){
        val builder = AlertDialog.Builder(this@MyProfileActivity)
        builder.setTitle("Confirm")

        val input = EditText(this)
        input.hint = "Enter Your Password"
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        builder.setView(input)
        builder.setMessage("Are you sure you want to DELETE Your Account?")
        builder.setCancelable(false)
        builder.setIcon(R.mipmap.paired_circle_round)
        builder.setPositiveButton("Yes") { dialog, which ->
            //delete account here
            val password = input.text.toString()
            if(password.isNotBlank()){
                deleteAccount(password)
            }
            else{
                toastMsg("Enter Password")
            }

        }
        builder.setNegativeButton("No") { dialog, which ->
            dialog.cancel()
        }
        val alert = builder.create()
        alert.show()
    }

    private fun deleteAccount(password: String) {
        //send info to server
        toastMsg("Deleting, Please wait...")
        val url = Constants.baseUrl + "index.php/app/deleteAccount"
        //start sending request
        val queue = Volley.newRequestQueue(this)
        val stringRequest = object : StringRequest(Request.Method.POST, url,
            Response.Listener { response ->
                when (response) {
                    "SUCCESS" -> {
                        toastMsg("Deleted successfully")
                        tinyDB.putBoolean("isLoggedIn", false)
                        val homeIntent = Intent(Intent.ACTION_MAIN)
                        homeIntent.addCategory(Intent.CATEGORY_HOME)
                        homeIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                        startActivity(homeIntent)
                        finish()
                    }
                    "INVALID_PASSWORD" -> toastMsg("Invalid Password")
                    else -> toastMsg("Failed to Delete, Try again")
                }
            },
            Response.ErrorListener { e ->
                //toastMsg("Try again")
            }) {
            override fun getParams(): Map<String, String?> {
                return mapOf(
                    "password" to password,
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

}
