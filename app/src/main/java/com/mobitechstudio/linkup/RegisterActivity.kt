package com.mobitechstudio.linkup

import android.Manifest
import android.app.ProgressDialog
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.view.*
import androidx.core.app.ActivityCompat
import androidx.appcompat.app.ActionBar
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.location.*
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.iid.InstanceIdResult
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.android.synthetic.main.activity_register.*
import kotlinx.android.synthetic.main.vip_account_dialog.view.*
import net.gotev.uploadservice.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileNotFoundException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

class RegisterActivity : AppCompatActivity(), UploadStatusDelegate {
    private var uploadReceiver:UploadServiceSingleBroadcastReceiver? =null
    lateinit var fusedTrackerCallback: LocationCallback
     var askedLocation = false
    var countriesList = ArrayList<Country>()
    var yearsList = ArrayList<DobYear>()
    //images uploads permissions
    val PICK_IMGONE_REQUEST = 111
    val PICK_IMGTWO_REQUEST = 112
    val STORAGE_PERMISSON_ONE = 1
    val LOCATION_REQUEST = 111
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var myLocation: Location? =null
    var currentLocationName = "NA"
    var currentLocationLat = 0.0
    var currentLocationLong = 0.0
    lateinit var tinyDB: TinyDB
    private var progress_two: ProgressDialog? = null
    private var filePath: Uri? = null
    private var filePath2: Uri? = null
    private lateinit var mAdView: AdView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        customBar()
        setContentView(R.layout.activity_register)
        uploadReceiver      = UploadServiceSingleBroadcastReceiver(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        tinyDB = TinyDB(this)
        progress_two = ProgressDialog(this)
        isLocationEnabled()
        initAds()
        tinyDB.putString("accountType", "Normal") //default account type
    }

    override fun onResume() {
        super.onResume()
        uploadReceiver!!.register(this)
        isLocationEnabled()

    }

    override fun onPause() {
        super.onPause()
        uploadReceiver!!.unregister(this)
    }
    private fun customBar(){
        supportActionBar!!.displayOptions = ActionBar.DISPLAY_SHOW_CUSTOM
        supportActionBar!!.setDisplayShowCustomEnabled(true)
        supportActionBar!!.setCustomView(R.layout.action_bar_layout)
        val view = supportActionBar!!.customView
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
    override fun onBackPressed() {
        if(progress_two !=null){
            if(progress_two!!.isShowing){
                toastMsg("Do not close, still uploading images")
            }else{
                confirmGoBack()
            }
        }
        else {
            confirmGoBack()
        }
    }
    private fun confirmGoBack(){
        val builder = AlertDialog.Builder(this@RegisterActivity)
        builder.setTitle("Confirm")
        builder.setMessage("Are you sure you want to close?")
        builder.setCancelable(false)
        builder.setIcon(R.mipmap.paired_circle_round)
        builder.setPositiveButton("Yes") { dialog, which ->
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
        builder.setNegativeButton("No") { dialog, which ->
            dialog.cancel()
        }
        val alert = builder.create()
        alert.show()
    }
    private fun requestPerms() {
        //request location permission
        if (Build.VERSION.SDK_INT >= 23) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED

                || ActivityCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ), LOCATION_REQUEST
                )
                return
            }
            else{
                if(isConnected()) {
                    requestSingleLocationUpdate()
                    getCurrentLocation()
                    fetchCountries()
                    getCurrentTokenSend()
                    fetchYears()
                }
                else{
                    toastMsg("There is NO INTERNET CONNECTION")
                }

            }

        }
        else{
            if(isConnected()) {
                requestSingleLocationUpdate()
                getCurrentLocation()
                fetchCountries()
                getCurrentTokenSend()
                fetchYears()
            }else{
                toastMsg("There is NO INTERNET CONNECTION")
            }
        }
    }



    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            LOCATION_REQUEST -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    getCurrentLocation()
                    fetchCountries()
                    getCurrentTokenSend()
                    fetchYears()
                } else {
                    toastMsg("Please Allow LinkUP to access location and storage")
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
        Toast.makeText(this@RegisterActivity, msg, Toast.LENGTH_LONG).show()
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                myLocation = location
                if(myLocation !=null) {
                    currentLocationLat = myLocation!!.latitude
                    currentLocationLong = myLocation!!.longitude
                    tinyDB.putDouble("defaultLat", currentLocationLat)
                    tinyDB.putDouble("defaultLon", currentLocationLong)
                    getMyAddress(currentLocationLat, currentLocationLong)
                }
                else{
                    //toastMsg("Failed to find your Location, try again")
                }
            }
        }
    }


    private fun getMyAddress(latitude: Double, longitude: Double){
        // Instantiate the RequestQueue.
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
                    currentLocationName = addressName
                    tinyDB.putString("defaultAddressName", currentLocationName)
                    tinyDB.putString("myLocation", currentLocationName)
                }catch (ex: Exception){
                    tinyDB.putString("defaultAddressName", "Tanzania")
                }


            },
            Response.ErrorListener {
                //error loading user location
                tinyDB.putString("defaultAddressName", "USA")
            })

        // Add the request to the RequestQueue.
        queue.add(stringRequest)

    }

    private fun fetchCountries() {
        if (countriesList.size > 0) {
            countriesList.clear()

        }
        //send info to server
        val url = Constants.baseUrl + "index.php/app/countriesList"
        //start sending request
        val queue = Volley.newRequestQueue(this@RegisterActivity)
        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { response ->
                // println("RESULT: $response")

                try {
                    val json = JSONArray(response)
                    val size = json.length()
                    if (size > 0) {
                        for (i in 0 until size) {

                            val category = json.getJSONObject(i)
                            val countryId = category.getInt("countryId")
                            val countryName = category.getString("countryName")
                            val phoneCode = category.getString("phoneCode")
                            val iso = category.getString("iso")

                            countriesList.add(Country(
                                countryId,
                                countryName,
                                phoneCode,
                                iso
                            ))

                        }
                        populateSpinnerCategories()

                    } else {
                        toastMsg("No countries found, try again")
                        finish()

                    }


                } catch (ex: Exception) {
                    toastMsg("Failed loading countries, try gain")
                   finish()
                }


            },
            Response.ErrorListener { e ->
                toastMsg("Failed loading countries, try gain")
                finish()

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
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )
        queue.add(stringRequest)
        //end request code
    }

    private fun currentYear(): Int{
        val c = java.util.Calendar.getInstance()
        val df = SimpleDateFormat("yyyy")
        return df.format(c.time).toInt()
    }

    private fun fetchYears() {
        if (yearsList.size > 0) {
            yearsList.clear()

        }
       val currentYear = currentYear()
        var maxYear = currentYear - 18
        val minYear = maxYear - 70
        while(maxYear > minYear){
            yearsList.add(DobYear(maxYear))
            maxYear--
        }
        populateSpinnerYears()
    }


    private fun populateSpinnerCategories() {
        val categories = java.util.ArrayList<String>()
        for (i in countriesList.indices) {
            categories.add(countriesList[i].countryName+"(+"+countriesList[i].phoneCode+")")
        }
        // Creating adapter for spinner
        val spinnerAdapter = ArrayAdapter(this@RegisterActivity, android.R.layout.simple_spinner_item, categories)
        // Drop down bar_layout style - list view with radio button
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        // attaching data adapter to spinner
        spinnerCountry.adapter = spinnerAdapter
    }

    private fun populateSpinnerYears() {
        val categories = java.util.ArrayList<Int>()
        for (i in yearsList.indices) {
            categories.add(yearsList[i].yearName)
        }
        // Creating adapter for spinner
        val spinnerAdapter = ArrayAdapter(this@RegisterActivity, android.R.layout.simple_spinner_item, categories)
        // Drop down bar_layout style - list view with radio button
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        // attaching data adapter to spinner
        spinnerYear.adapter = spinnerAdapter
    }


    private fun getCurrentTokenSend() {
        thread {
            try {
                //subscribe to notification topic
                FirebaseMessaging.getInstance().subscribeToTopic("Haven")
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

    private fun sendTokenToServer(token: String) {
        //send info to server
        val url = Constants.baseUrl + "index.php/app/updateToken"
        //start sending request
        val queue = Volley.newRequestQueue(this)
        val stringRequest = object : StringRequest(Request.Method.POST, url,
            Response.Listener { response ->
                //println("RESULT: $response")

                if (response == "Success") {
                    //toastMsg("Subscribed successfully")
                } else {
                    //toastMsg("Failed to Subscribe, Try again")
                }
            },
            Response.ErrorListener { e ->
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
    fun goSignIn(v: View){
        val intent = Intent(this@RegisterActivity, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    fun registerUser(v: View){
        if(countriesList.size > 0) {
            val name = editName.text.toString()
            val bio = "NA"
            val phone = editPhone.text.toString()
            val userPassword = editPassword.text.toString()
            val sex = spinnerSex.selectedItem.toString()
            val maritalStatus = spinnerMarital.selectedItem.toString()
            val sexOrientation = spinnerSexOrientation.selectedItem.toString()
            val dobYear = yearsList[spinnerYear.selectedItemPosition].yearName
            val countryId = countriesList[spinnerCountry.selectedItemPosition].countryId
            val countryIso = countriesList[spinnerCountry.selectedItemPosition].iso
            val countryName = countriesList[spinnerCountry.selectedItemPosition].countryName
            val phoneCode = countriesList[spinnerCountry.selectedItemPosition].phoneCode
            if(name.isNotBlank() && phone.isNotBlank() && userPassword.isNotBlank() && bio.isNotBlank()) {

                    tinyDB.putString("name", name)
                    tinyDB.putString("phone", phone)
                    tinyDB.putString("userPassword", userPassword)
                    tinyDB.putString("sex", sex)
                    tinyDB.putString("maritalStatus", maritalStatus)
                    tinyDB.putString("sexOrientation", sexOrientation)
                    tinyDB.putInt("dobYear", dobYear)
                    tinyDB.putInt("countryId", countryId)
                    tinyDB.putString("countryIso", countryIso)
                    tinyDB.putString("countryName", countryName)
                    tinyDB.putString("phoneCode", phoneCode)
                    tinyDB.putInt("fromAge", 0)
                    tinyDB.putInt("toAge", 0)
                    tinyDB.putString("bio", bio)
                    val defaultAddressName = tinyDB.getString("defaultAddressName")
                    if(defaultAddressName=="" || defaultAddressName=="Tanzania"){
                        tinyDB.putString("defaultAddressName",countryName)
                    }
                    hintsDialog()


            }
            else{
                noticeDialog("Enter all details please")
            }
        }else{
            toastMsg("Failed to load Countries phone codes, Re-Launch again")
            finish()

        }
    }

    fun onGenderRadioButtonClicked(view: View) {
        if (view is RadioButton) {
            // Is the button now checked?
            val checked = view.isChecked

            // Check which radio button was clicked
            when (view.id) {
                R.id.radio_normal ->
                    if (checked) {
                        tinyDB.putString("accountType", "Normal")
                        toastMsg("Normal")
                    }
                R.id.radio_vip ->
                    if (checked) {
                        tinyDB.putString("accountType", "VIP")
                        val vipNotes = "VIP Account Features\n" +
                                "1. Your Account is Only Visible to people you start chat with\n" +
                                "2. View Users From Any Location In the World\n" +
                                "3. Start Chat With Any UserObject You find In Any Location\n" +
                                "4. Uploading Profile Photo is Optional"
                        noticeVip(vipNotes,view)
                    }
            }
        }
    }
    private fun noticeVip(vipNotes: String, radioBtn: RadioButton){
        val dialog = AlertDialog.Builder(this@RegisterActivity, android.R.style.Theme_Light_NoTitleBar)
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
            if(customDialog.isShowing){
                customDialog.dismiss()
            }
        }
        view.btnCancel.setOnClickListener {
            radioBtn.isChecked = false
            val normalBtn = findViewById<RadioButton>(R.id.radio_normal)
            normalBtn.isChecked = true
            if(customDialog.isShowing){
                customDialog.dismiss()
            }
            tinyDB.putString("accountType", "Normal")

        }


    }
    private fun noticeDialog(msg: String){
        val builder = AlertDialog.Builder(this@RegisterActivity)
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



    private fun saveData(){

        try{
            btnRegister.isEnabled = false //disable register button
           val uploadUrl = Constants.baseUrl + "index.php/app/registerUser"
            tinyDB = TinyDB(this)
                val uploadId = UUID.randomUUID().toString()
                uploadReceiver!!.setUploadID(uploadId)
                MultipartUploadRequest(this, uploadId, uploadUrl)
                    .addParameter("name", tinyDB.getString("name"))
                    .addParameter("phone",  tinyDB.getString("phone"))
                    .addParameter("userPassword",  tinyDB.getString("userPassword"))
                    .addParameter("sex",  tinyDB.getString("sex"))
                    .addParameter("dobYear",  tinyDB.getInt("dobYear").toString())
                    .addParameter("token", tinyDB.getString("token"))
                    .addParameter("countryId",tinyDB.getInt("countryId").toString())
                    .addParameter("countryIso",tinyDB.getString("countryIso"))
                    .addParameter("phoneCode",tinyDB.getString("phoneCode"))
                    .addParameter("locLon",tinyDB.getDouble("defaultLon", 0.0).toString())
                    .addParameter("locLat",tinyDB.getDouble("defaultLat", 0.0).toString())
                    .addParameter("locName",tinyDB.getString("defaultAddressName"))
                    .addParameter("bio",tinyDB.getString("bio"))
                    .addParameter("token",tinyDB.getString("token"))
                    .addParameter("sexOrientation",tinyDB.getString("sexOrientation"))
                    .addParameter("maritalStatus",tinyDB.getString("maritalStatus"))
                    .addParameter("accountType",tinyDB.getString("accountType"))
                    .setNotificationConfig(UploadNotificationConfig())
                    .setMaxRetries(2)
                    .startUpload() //Starting the upload


        }catch (ex: Exception){
            toastMsg("Failed to send data, try again")
            btnRegister.isEnabled = true
        }

    }

    override fun onCancelled(context: Context?, uploadInfo: UploadInfo?) {
        removeProgress()
        btnRegister.isEnabled = true
        Toast.makeText(applicationContext, "Canceled upload images, try again", Toast.LENGTH_LONG).show()
    }

    override fun onProgress(context: Context?, uploadInfo: UploadInfo?) {
        val progressPercent = uploadInfo!!.progressPercent
        if (!progress_two!!.isShowing) {
            progress_two!!.setMessage("Uploading data, please wait, don't close")
            progress_two!!.show()

        }
        if(progressPercent==100){
            toastMsg("Finishing registration!")
        }
    }

    override fun onError(
        context: Context?,
        uploadInfo: UploadInfo?,
        serverResponse: ServerResponse?,
        exception: java.lang.Exception?
    ) {
        btnRegister.isEnabled = true
        if(progress_two !=null) {
            if(progress_two!!.isShowing) {
                if(!this@RegisterActivity.isFinishing) {
                    progress_two!!.dismiss()
                }
            }
        }
        Toast.makeText(applicationContext, "Failed to register, please try again", Toast.LENGTH_LONG).show()
        if (serverResponse != null) {
           // println("RESULT: " + serverResponse.bodyAsString)
        }
        if(exception !=null){
            //println("ERROR_FAILED: ${exception.message}")
        }
    }

    override fun onCompleted(context: Context?, uploadInfo: UploadInfo?, serverResponse: ServerResponse?) {
        if(progress_two !=null) {
            if(progress_two!!.isShowing) {
                if(!this@RegisterActivity.isFinishing) {
                    progress_two!!.dismiss()
                    toastMsg("Wait, Finishing registration..")
                }
            }
        }
        if (serverResponse != null) {
          //  println("RESULT: " + serverResponse.bodyAsString)
            try {
                val json = JSONArray(serverResponse.bodyAsString)
                val size = json.length()
                if (size > 0) {
                    for (i in 0 until size) {

                        val user = json.getJSONObject(i)
                        val userId = user.getInt("userId")
                        val status = user.getString("status")
                        val alert = user.getString("message")
                        val phoneNumber = user.getString("phoneNumber")
                        val premiumExpired = user.getString("premiumExpired")
                        val premiumExpireDate = user.getString("premiumExpireDate")
                        when (status) {
                            "SUCCESS" -> {
                                tinyDB.putInt("userId", userId)
                                tinyDB.putString("emailAddress", "NA")
                                tinyDB.putString("phoneNumber", phoneNumber)
                                tinyDB.putString("premiumExpired", premiumExpired)
                                tinyDB.putString("amPremium", "No")
                                tinyDB.putString("premiumExpireDate", premiumExpireDate)
                                tinyDB.putString("profileImage", "NA")
                                tinyDB.putString("verificationStatus", "Verified")
                                tinyDB.putBoolean("isLoggedIn", true)
                                //default age ranges
                                tinyDB.putInt("fromAge", 18)
                                tinyDB.putInt("toAge", 120)
                                //set default notification settings
                                tinyDB.putBoolean("callsNoti", true)
                                tinyDB.putBoolean("chatsNoti", true)
                                tinyDB.putBoolean("callTone", true)
                                tinyDB.putBoolean("likeNoti", true)
                                tinyDB.putBoolean("visitorNoti", true)

                                //check account type
                                val accountType = tinyDB.getString("accountType")
                                    val intent = Intent(this@RegisterActivity, MainActivity::class.java)
                                    startActivity(intent)
                                    finish()

                            }
                            "FAILED" -> toastMsg(alert)
                            "VERIFICATION_ERROR" -> toastMsg(alert)
                            "DOUBLICATE_EMAIL" -> toastMsg(alert)
                            "IMG_UPLOAD_ERROR" -> toastMsg(alert)
                            else -> toastMsg("Failed to register, please try again")
                        }

                    }

                } else {
                    toastMsg("Failed to register, please try again")
                }


            } catch (ex: Exception) {
                toastMsg("Failed to register, please try again")
                //println("ERROR: ${ex.message}")
                btnRegister.isEnabled = true //disable register button
            }
        }
        else
        {
            btnRegister.isEnabled = true //disable register button
            toastMsg("Failed to register, please try again")
        }
    }



    private fun removeProgress(){
        if(progress_two !=null) {
            if(progress_two!!.isShowing) {
                progress_two!!.dismiss()
            }
        }
    }
    //method to get the file path from uri
    private fun getPath(uri: Uri?): String {
        var path  = "NA"
        if(uri !=null) {
            try {
                var cursor = contentResolver.query(uri, null, null, null, null)
                cursor!!.moveToFirst()
                var documentId = cursor.getString(0)
                documentId = documentId.substring(documentId.lastIndexOf(":") + 1)
                cursor.close()

                cursor = contentResolver.query(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    null,
                    MediaStore.Images.Media._ID + " = ? ",
                    arrayOf(documentId),
                    null
                )
                cursor!!.moveToFirst()
                path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA))
                cursor.close()
            }
            catch (ex: Exception){
                //println("ERROR: ${ex.message}")
            }
        }
        return path
    }

    private fun takeImage(requestCode: Int) {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(intent, "Select Image"), requestCode)
    }

    fun takeImageOne(v: View){
        takeImage(PICK_IMGONE_REQUEST)
    }

    fun takeImageTwo(v: View){
        takeImage(PICK_IMGTWO_REQUEST)
    }


    @Throws(FileNotFoundException::class)
    fun decodeUri(c: Context, uri:Uri, requiredSize:Int): Bitmap? {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeStream(c.contentResolver.openInputStream(uri), null, options)
        var width_tmp = options.outWidth
        var height_tmp = options.outHeight
        var scale = 1
        while (true)
        {
            if (width_tmp / 2 < requiredSize || height_tmp / 2 < requiredSize)
                break
            width_tmp /= 2
            height_tmp /= 2
            scale *= 2
        }
        val options2 = BitmapFactory.Options()
        options2.inSampleSize = scale
        return BitmapFactory.decodeStream(c.contentResolver.openInputStream(uri), null, options2)
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
            mAdView = findViewById(R.id.adViewRegister)
            val adRequest = AdRequest
                .Builder()
                .addTestDevice("28480BE4C8AF9DAA06B7514F14DE6DDA")
                .addTestDevice("99C784B0B9C5ED479BFF3B1557A6309F")
                .build()
            mAdView.loadAd(adRequest)

            mAdView.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    super.onAdLoaded()
                    //println("AD LOADED")
                }

                override fun onAdFailedToLoad(i: Int) {
                    super.onAdFailedToLoad(i)
                   // println("FAILED To LOAD AD: $i")
                }

            }

        }
    }


    private fun requestSingleLocationUpdate() {
        // Looper.prepare();
        // only works with SDK Version 23 or higher
        if (android.os.Build.VERSION.SDK_INT >= 23)
        {
            if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !== PackageManager.PERMISSION_GRANTED || this.checkSelfPermission(
                    Manifest.permission.ACCESS_COARSE_LOCATION) !== PackageManager.PERMISSION_GRANTED)
            {
                // permission is not granted
                //Log.e("SiSoLocProvider", "Permission not granted.")
                return
            }
            else
            {
                //Log.d("SiSoLocProvider", "Permission granted.")
            }
        }
        else
        {
            // Log.d("SiSoLocProvider", "SDK < 23, checking permissions should not be necessary")
        }
        val startTime = System.currentTimeMillis()
        fusedTrackerCallback = object: LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                // These lines of code will run on UI thread.
                if ((locationResult.lastLocation != null) && (System.currentTimeMillis() <= startTime + 30 * 1000))
                {
                    val locLat = locationResult.lastLocation.latitude
                    val locLon = locationResult.lastLocation.longitude
                    tinyDB.putDouble("defaultLon", locLon)
                    tinyDB.putDouble("defaultLat", locLat)
                    //get current location from Location IQ
                    getMyAddress(locLat, locLon)
                    //System.out.println("LOCATION: " + locationResult.getLastLocation().getLatitude() + "|" + locationResult.getLastLocation().getLongitude())
                    // System.out.println("ACCURACY: " + locationResult.getLastLocation().getAccuracy())
                    fusedLocationClient.removeLocationUpdates(fusedTrackerCallback)
                }
                else
                {
                   // println("LastKnownNull? :: " + (locationResult.lastLocation == null))
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
            if(!askedLocation) {
                buildAlertMessageNoGps()
            }
        }
        else{
            requestPerms()
        }

    }

    private fun buildAlertMessageNoGps() {
        askedLocation = true
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




    /* Get uri related content real local file path. */
    private fun getUriRealPath(ctx:Context, uri:Uri):String {
        var ret = "NA"
        ret = if (isAboveKitKat()) {
            // Android OS above sdk version 19.
            getUriRealPathAboveKitkat(ctx, uri)
        } else {
            // Android OS below sdk version 19
            getImageRealPath(contentResolver, uri, null)
        }
        return ret
    }

    private fun getUriRealPathAboveKitkat(ctx:Context, uri:Uri?):String {
        var ret = "NA"
        if (uri != null)
        {
            if (isContentUri(uri))
            {
                ret = if (isGooglePhotoDoc(uri.authority!!)) {
                    uri.lastPathSegment!!
                } else {
                    getImageRealPath(contentResolver, uri, null)
                }
            }
            else if (isFileUri(uri))
            {
                ret = uri.path!!
            }
            else if (isDocumentUri(ctx, uri))
            {
                // Get uri related document id.
                var documentId = ""
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    documentId = DocumentsContract.getDocumentId(uri)
                }
                // Get uri authority.
                val uriAuthority = uri.authority
                if (isMediaDoc(uriAuthority!!))
                {
                    val idArr = documentId.split((":").toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (idArr.size == 2)
                    {
                        // First item is document type.
                        val docType = idArr[0]
                        // Second item is document real id.
                        val realDocId = idArr[1]
                        // Get content uri by document type.
                        var mediaContentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        when (docType) {
                            "image" -> mediaContentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            "video" -> mediaContentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            "audio" -> mediaContentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        }
                        // Get where clause with real document id.
                        // Get where clause with real document id.
                        val whereClause = MediaStore.Images.Media._ID + " = " + realDocId
                        ret = getImageRealPath(contentResolver, mediaContentUri, whereClause)
                    }
                }
                else if (isDownloadDoc(uriAuthority))
                {
                    // Build download uri.
                    val downloadUri = Uri.parse("content://downloads/public_downloads")
                    // Append download document id at uri end.
                    val downloadUriAppendId = ContentUris.withAppendedId(downloadUri, java.lang.Long.valueOf(documentId))
                    ret = getImageRealPath(contentResolver, downloadUriAppendId, null)
                }
                else if (isExternalStoreDoc(uriAuthority))
                {
                    val idArr = documentId.split((":").toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (idArr.size == 2)
                    {
                        val type = idArr[0]
                        val realDocId = idArr[1]
                        if ("primary".equals(type, ignoreCase = true))
                        {
                            ret = Environment.getExternalStorageDirectory().toString() + "/" + realDocId
                        }
                    }
                }
            }
        }
        return ret
    }

    /* Check whether current android os version is bigger than kitkat or not. */
    private fun isAboveKitKat():Boolean {
        val ret: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
        return ret
    }
    /* Check whether this uri represent a document or not. */
    private fun isDocumentUri(ctx:Context, uri:Uri):Boolean {
        var ret = false
        if (ctx != null && uri != null)
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                ret = DocumentsContract.isDocumentUri(ctx, uri)
            }
        }
        return ret
    }

    /* Check whether this uri is a content uri or not.
    * content uri like content://media/external/images/media/1302716
    * */
    private fun isContentUri(uri:Uri):Boolean {
        var ret = false
        if (uri != null)
        {
            val uriSchema = uri.scheme
            if ("content".equals(uriSchema, ignoreCase = true))
            {
                ret = true
            }
        }
        return ret
    }
    /* Check whether this uri is a file uri or not.
    * file uri like file:///storage/41B7-12F1/DCIM/Camera/IMG_20180211_095139.jpg
    * */
    private fun isFileUri(uri:Uri):Boolean {
        var ret = false
        if (uri != null)
        {
            val uriSchema = uri.scheme
            if ("file".equals(uriSchema, ignoreCase = true))
            {
                ret = true
            }
        }
        return ret
    }

    /* Check whether this document is provided by ExternalStorageProvider. */
    private fun isExternalStoreDoc(uriAuthority:String):Boolean {
        var ret = false
        if ("com.android.externalstorage.documents" == uriAuthority)
        {
            ret = true
        }
        return ret
    }
    /* Check whether this document is provided by DownloadsProvider. */
    private fun isDownloadDoc(uriAuthority:String):Boolean {
        var ret = false
        if ("com.android.providers.downloads.documents" == uriAuthority)
        {
            ret = true
        }
        return ret
    }
    /* Check whether this document is provided by MediaProvider. */
    private fun isMediaDoc(uriAuthority:String):Boolean {
        var ret = false
        if ("com.android.providers.media.documents" == uriAuthority)
        {
            ret = true
        }
        return ret
    }

    /* Check whether this document is provided by google photos. */
    private fun isGooglePhotoDoc(uriAuthority:String):Boolean {
        var ret = false
        if ("com.google.android.apps.photos.content" == uriAuthority)
        {
            ret = true
        }
        return ret
    }
    /* Return uri represented document file real local path.*/
    private fun getImageRealPath(contentResolver: ContentResolver, uri:Uri, whereClause: String?):String {
        var ret = "NA"
        // Query the uri with condition.
        val cursor = contentResolver.query(uri, null, whereClause, null, null)
        if (cursor != null)
        {
            val moveToFirst = cursor.moveToFirst()
            if (moveToFirst)
            {
                // Get columns name by uri type.
                var columnName = MediaStore.Images.Media.DATA
                when {
                    uri === MediaStore.Images.Media.EXTERNAL_CONTENT_URI -> columnName = MediaStore.Images.Media.DATA
                    uri === MediaStore.Audio.Media.EXTERNAL_CONTENT_URI -> columnName = MediaStore.Audio.Media.DATA
                    uri === MediaStore.Video.Media.EXTERNAL_CONTENT_URI -> columnName = MediaStore.Video.Media.DATA
                }
                // Get column index.
                // Get column value which is the uri related file local path.
                // Get column index.
                val imageColumnIndex = cursor.getColumnIndex(columnName)
                // Get column value which is the uri related file local path.
                ret = cursor.getString(imageColumnIndex)
            }
        }
        return ret
    }

    private fun hintsDialog(){
        val builder = AlertDialog.Builder(this@RegisterActivity)
        builder.setTitle("Important Hint")
        builder.setMessage("Make Sure You Selected Correct Birth Year, Sex and Country Phone Code, They Affect Who You Can Communicate")
        builder.setCancelable(false)
        builder.setIcon(R.mipmap.paired_circle_round)
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }
        builder.setNeutralButton("Register"){ _, _ ->
            saveData()
        }
        val alert = builder.create()
        alert.show()
    }
}
