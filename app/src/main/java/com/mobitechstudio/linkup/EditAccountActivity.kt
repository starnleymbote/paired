package com.mobitechstudio.linkup

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.appcompat.app.ActionBar
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.iid.InstanceIdResult
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.android.synthetic.main.activity_edit_account.*
import net.gotev.uploadservice.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread
/*
This class is used to edit user profile
 */
class EditAccountActivity : AppCompatActivity(), UploadStatusDelegate {
    private var uploadReceiver:UploadServiceSingleBroadcastReceiver? =null
    var countriesList = ArrayList<Country>()
    var yearsList = ArrayList<DobYear>()
    //images uploads permissions
    val LOCATIONREQUEST = 111
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var myLocation: Location? =null
    var currentLocationName = "NA"
    var currentLocationLat = 0.0
    var currentLocationLong = 0.0
    lateinit var tinyDB: TinyDB
    private var progressTwo: ProgressDialog? = null
    private lateinit var mAdView: AdView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        customBar()
        setContentView(R.layout.activity_edit_account)
        uploadReceiver      = UploadServiceSingleBroadcastReceiver(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        tinyDB = TinyDB(this)
        progressTwo = ProgressDialog(this)
        requestPerms()
        initAds()


    }
    private fun setCurrentValues(){
        getUserProfile()
        editName.setText(tinyDB.getString("name"))
        editBio.setText(tinyDB.getString("bio"))
        //set dob year
        var i=0
        while(i< yearsList.size){
            if (spinnerYear.getItemAtPosition(i) == tinyDB.getInt("dobYear")) {
                spinnerYear.setSelection(i)
            }
            i++
        }

        val sex = tinyDB.getString("sex")
        if(sex=="Male"){
            spinnerSex.setSelection(0)
        }
        else{
            spinnerSex.setSelection(1)
        }


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
        super.onBackPressed()
        finish()
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
                    ), LOCATIONREQUEST
                )
                return
            }
            else{
                getCurrentLocation()
                fetchCountries()
                getCurrentTokenSend()
                fetchYears()
            }

        }
        else{
            getCurrentLocation()
            fetchCountries()
            getCurrentTokenSend()
            fetchYears()
        }
    }



    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            LOCATIONREQUEST -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    getCurrentLocation()
                    fetchCountries()
                    getCurrentTokenSend()
                    fetchYears()
                } else {
                    toastMsg("Please Allow TM to access location and storage")
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
        Toast.makeText(this@EditAccountActivity, msg, Toast.LENGTH_LONG).show()
    }

    //this function get current user location
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


    //get user current address name from Location IQ using current user latitude and longitude
    private fun getMyAddress(latitude: Double, longitude: Double){
        // Instantiate the RequestQueue.
        val queue = Volley.newRequestQueue(this)
        val url = "https://us1.locationiq.com/v1/reverse.php?key=${Constants.locationIqKey}&lat=$latitude&lon=$longitude&format=json"

        // Request a string response from the provided URL.
        val stringRequest = StringRequest(
            Request.Method.GET, url,
            Response.Listener<String> { response ->
                //println("RESULT: $response")
                val json = JSONObject(response)
                val addressName = json.getString("display_name")
                currentLocationName = addressName
                tinyDB.putString("defaultAddressName", currentLocationName)

            },
            Response.ErrorListener {
                //error loading location, handle error here, if you want user not to continue editing
                //toastMsg("Failed to find your Location, try again")
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
        val queue = Volley.newRequestQueue(this@EditAccountActivity)
        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { response ->
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
                        toastMsg("No countries found")
                        onBackPressed()
                    }


                } catch (ex: Exception) {
                    toastMsg("Error loading countries, try gain")
                    onBackPressed()
                }


            },
            Response.ErrorListener { e ->
                toastMsg("Reload again")
               onBackPressed()

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
            categories.add(countriesList[i].countryName)
        }
        // Creating adapter for spinner
        val spinnerAdapter = ArrayAdapter(this@EditAccountActivity, android.R.layout.simple_spinner_item, categories)
        // Drop down bar_layout style - list view with radio button
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        // attaching data adapter to spinner
        spinnerCountry.adapter = spinnerAdapter
        setCurrentValues()
    }

    private fun populateSpinnerYears() {
        val categories = java.util.ArrayList<Int>()
        for (i in yearsList.indices) {
            categories.add(yearsList[i].yearName)
        }
        // Creating adapter for spinner
        val spinnerAdapter = ArrayAdapter(this@EditAccountActivity, android.R.layout.simple_spinner_item, categories)
        // Drop down bar_layout style - list view with radio button
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        // attaching data adapter to spinner
        spinnerYear.adapter = spinnerAdapter
    }

    //get current user FCM token
    private fun getCurrentTokenSend() {
        thread {
            try {
                //subscribe to notification topic
                FirebaseMessaging.getInstance().subscribeToTopic("TM")
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

                if (response == "Success") {
                    //toastMsg("Token Updated successfully")
                } else {
                    //toastMsg("Failed to Update, Try again")
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

    fun registerMe(v: View){
        if(countriesList.size>0) {
            val name = editName.text.toString()
            val newPassword = editNewPassword.text.toString()
            val bio = editBio.text.toString()
            val userPassword = editPassword.text.toString()
            val sex = tinyDB.getString("sex")
            val maritalStatus = spinnerMarital.selectedItem.toString()
            val sexOrientation = spinnerSexOrientation.selectedItem.toString()
            val smoking = spinnerSmoke.selectedItem.toString()
            val drinking = spinnerDrink.selectedItem.toString()
            val education = spinnerEducation.selectedItem.toString()
            val religion = spinnerReligion.selectedItem.toString()
            val ethnicity = spinnerEthnicity.selectedItem.toString()
            val dobYear = yearsList[spinnerYear.selectedItemPosition].yearName
            val countryId = countriesList[spinnerCountry.selectedItemPosition].countryId
            val countryIso = countriesList[spinnerCountry.selectedItemPosition].phoneCode
            if (newPassword.isNotBlank()) {
                tinyDB.putString("newPassword", newPassword)
            } else {
                tinyDB.putString("newPassword", "NA")
            }
            if (name.isNotBlank() && userPassword.isNotBlank() && bio.isNotBlank()) {
                tinyDB.putString("name", name)
                tinyDB.putString("userPassword", userPassword)
                tinyDB.putString("sex", sex)
                tinyDB.putString("maritalStatus", maritalStatus)
                tinyDB.putString("sexOrientation", sexOrientation)
                tinyDB.putString("smoking", smoking)
                tinyDB.putString("drinking", drinking)
                tinyDB.putString("education", education)
                tinyDB.putString("religion", religion)
                tinyDB.putString("ethnicity", ethnicity)
                tinyDB.putInt("dobYear", dobYear)
                tinyDB.putString("countryId", countryId.toString())
                tinyDB.putString("countryIso", countryIso)
                tinyDB.putString("bio", bio)
                ageDialogMsg()
            } else {
                toastMsg("Enter all details")
            }
        }
        else
        {
            toastMsg("Failed to load countries, Close and Open this window")
        }
    }
    //show important hint
    private fun ageDialogMsg(){
        val builder = AlertDialog.Builder(this@EditAccountActivity)
        builder.setTitle("Important Hint")
        builder.setMessage("Make Sure You Selected Correct Birth Year And Sex, They Affect Who You Can Communicate")
        builder.setCancelable(false)
        builder.setIcon(R.mipmap.paired_circle_round)
        builder.setNegativeButton("Correct") { dialog, _ ->
            dialog.cancel()
        }
        builder.setNeutralButton("Continue"){ _, _ ->
            registerUser()
        }
        val alert = builder.create()
        alert.show()
    }
//    fun saveData(v: View){
//        btnSignIn.isEnabled = false
//        registerUser()}

     fun saveData(v: View){
         btnSignIn.isEnabled=false
         registerUser()
     }
    private fun registerUser(){
        try{
            val uploadUrl = Constants.baseUrl + "index.php/app/editUserProfile"
            tinyDB = TinyDB(this)
                val uploadId = UUID.randomUUID().toString()
                uploadReceiver!!.setUploadID(uploadId)
                MultipartUploadRequest(applicationContext, uploadId, uploadUrl)
                    .addParameter("name", tinyDB.getString("name"))
                    .addParameter("newPassword",  tinyDB.getString("newPassword"))
                    .addParameter("userPassword",  tinyDB.getString("userPassword"))
                    .addParameter("sex",  tinyDB.getString("sex"))
                    .addParameter("dobYear",  tinyDB.getInt("dobYear").toString())
                    .addParameter("token", tinyDB.getString("token"))
                    .addParameter("countryId",tinyDB.getString("countryId"))
                    .addParameter("countryIso",tinyDB.getString("countryIso"))
                    .addParameter("locLon",tinyDB.getDouble("defaultLon", 0.0).toString())
                    .addParameter("locLat",tinyDB.getDouble("defaultLat", 0.0).toString())
                    .addParameter("locName",tinyDB.getString("defaultAddressName"))
                    .addParameter("bio",tinyDB.getString("bio"))
                    .addParameter("token",tinyDB.getString("token"))
                    .addParameter("sexOrientation",tinyDB.getString("sexOrientation"))
                    .addParameter("maritalStatus",tinyDB.getString("maritalStatus"))
                    .addParameter("smoking",tinyDB.getString("smoking"))
                    .addParameter("drinking",tinyDB.getString("drinking"))
                    .addParameter("education",tinyDB.getString("education"))
                    .addParameter("religion",tinyDB.getString("religion"))
                    .addParameter("ethnicity",tinyDB.getString("ethnicity"))
                    .addParameter("payRef",tinyDB.getString("payReference"))
                    .addParameter("userId",tinyDB.getInt("userId").toString())
                    .setNotificationConfig(UploadNotificationConfig())
                    .setMaxRetries(2)
                    .startUpload() //Starting the upload

        }catch (ex: Exception){
            toastMsg("Failed to send data, try again")
        }

    }
    private fun removeProgress(){
        if(progressTwo !=null) {
            if(progressTwo!!.isShowing) {
                progressTwo!!.dismiss()
            }
        }
    }




    //this function is used to check if there is internet connection using ping command
    fun isConnected():Boolean {
        val cm = this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetworkInfo
        return activeNetwork != null
    }
    //initialize admob banner ads
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
                    Log.d("AD_LOADED", "AD LOADED")
                }

                override fun onAdFailedToLoad(i: Int) {
                    super.onAdFailedToLoad(i)
                    Log.d("AD_FAILED","FAILED To LOAD AD: $i")
                }

            }

        }
    }

    override fun onCancelled(context: Context?, uploadInfo: UploadInfo?) {
        removeProgress()
        Toast.makeText(applicationContext, "Canceled data upload, try again", Toast.LENGTH_LONG).show()
    }

    override fun onProgress(context: Context?, uploadInfo: UploadInfo?) {
        if (!progressTwo!!.isShowing) {
            progressTwo!!.setMessage("Sending data, please wait....")
            progressTwo!!.show()
        }
    }

    override fun onError(
        context: Context?,
        uploadInfo: UploadInfo?,
        serverResponse: ServerResponse?,
        exception: java.lang.Exception?
    ) {
        if(progressTwo !=null) {
            if(progressTwo!!.isShowing) {
                if(!this@EditAccountActivity.isFinishing) {
                    progressTwo!!.dismiss()
                }
            }
        }
        Toast.makeText(applicationContext, "Failed to register, please try again", Toast.LENGTH_LONG).show()
        if (serverResponse != null) {
            println("RESULT: " + serverResponse.bodyAsString)
        }
        if(exception !=null){
            println("ERROR_FAILED: ${exception.message}")
        }

    }

    override fun onCompleted(context: Context?, uploadInfo: UploadInfo?, serverResponse: ServerResponse?) {
        if(progressTwo !=null) {
            if(progressTwo!!.isShowing) {
                if(!this@EditAccountActivity.isFinishing) {
                    progressTwo!!.dismiss()
                }
            }
        }
        if (serverResponse != null) {
            try {
                val json = JSONArray(serverResponse.bodyAsString)
                val size = json.length()
                if (size > 0) {
                    for (i in 0 until size) {

                        val user = json.getJSONObject(i)
                        val status = user.getString("status")
                        val alert = user.getString("message")
                        when (status) {
                            "SUCCESS" -> {
                                tinyDB.putBoolean("isLoggedIn", true)
                                finish()
                            }
                            "FAILED" -> toastMsg(alert)
                            "INVALID_PASSWORD" -> toastMsg(alert)
                            else -> toastMsg("Failed to update, please try again")
                        }

                    }

                } else {
                    toastMsg("Failed to register, please try again")
                }


            } catch (ex: Exception) {
                toastMsg("Failed to register, please try again")
                println("ERROR: ${ex.message}")
            }
        }
        else
        {
            toastMsg("Failed to register, please try again")
        }
    }

    override fun onResume() {
        super.onResume()
        uploadReceiver!!.register(this)

    }

    override fun onPause() {
        super.onPause()
        uploadReceiver!!.unregister(this)
    }


    private fun getUserProfile(){
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
                            val sexOrientation = user.getString("sexOrientation")
                            val maritalStatus = user.getString("maritalStatus")
                            val smoking = user.getString("smoking")
                            val drinking = user.getString("drinking")
                            val education = user.getString("education")
                            val religion = user.getString("religion")
                            val ethnicity = user.getString("ethnicity")

                            //set default values
                            spinnerSexOrientation.setSelection(getIndex(spinnerSexOrientation, sexOrientation))
                            spinnerMarital.setSelection(getIndex(spinnerMarital, maritalStatus))
                            spinnerSmoke.setSelection(getIndex(spinnerSmoke, smoking))
                            spinnerDrink.setSelection(getIndex(spinnerDrink, drinking))
                            spinnerEducation.setSelection(getIndex(spinnerEducation, education))
                            spinnerReligion.setSelection(getIndex(spinnerReligion, religion))
                            spinnerEthnicity.setSelection(getIndex(spinnerEthnicity, ethnicity))



                        }


                    }
                    else
                    {
                        toastMsg("Your Profile Was Not Found")
                        finish()
                    }

                }catch (ex:Exception){
                    toastMsg("Error loading Profile Details, try gain")
                    finish()
                }


            },
            Response.ErrorListener { _ ->
                toastMsg("Error loading Profile Details, try gain")
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
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
        queue.add(stringRequest)
        //end request code
    }

    //private method of your class
    private fun getIndex(spinner: Spinner, myString:String):Int {
        for (i in 0 until spinner.count)
        {
            if (spinner.getItemAtPosition(i).toString() ==myString)
            {
                return i
            }
        }
        return 0
    }

    /*
   this function loads facebook native ad as popup ad When this Activity is Destroyed.
   popup ads are only displayed on free users, change this if you want to show ads for all users, Remove Condition
   if(premiumExpired=="Yes")
    */
    private fun loadPopAd(){
        val premiumExpired = tinyDB.getString("premiumExpired")
        if(premiumExpired=="Yes"){
            val intent = Intent(this, FacebookAdActivity::class.java)
            startActivity(intent)
        }
    }
    override fun onDestroy() {
        loadPopAd()
        super.onDestroy()
    }
}
