package com.mobitechstudio.linkup

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.appcompat.app.ActionBar
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
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
import kotlinx.android.synthetic.main.activity_login.*
import org.json.JSONArray
import java.util.ArrayList

class LoginActivity : AppCompatActivity() {
    lateinit var tinyDB: TinyDB
    var countriesList = ArrayList<Country>()
    private lateinit var mAdView: AdView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        customBar()
        setContentView(R.layout.activity_login)
        tinyDB  = TinyDB(this)
       initAds()
        if(isConnected()) {
            fetchCountries()
        }
        else
        {
            toastMsg("There is NO INTERNET CONNECTION")
        }
    }
    private fun fetchCountries() {
        if (countriesList.size > 0) {
            countriesList.clear()

        }
        //send info to server
        val url = Constants.baseUrl + "index.php/app/countriesList"
        //start sending request
        val queue = Volley.newRequestQueue(this@LoginActivity)
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
    private fun populateSpinnerCategories() {
        val categories = java.util.ArrayList<String>()
        for (i in countriesList.indices) {
            categories.add(countriesList[i].countryName+"(+"+countriesList[i].phoneCode+")")
        }
        // Creating adapter for spinner
        val spinnerAdapter = ArrayAdapter(this@LoginActivity, android.R.layout.simple_spinner_item, categories)
        // Drop down bar_layout style - list view with radio button
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        // attaching data adapter to spinner
        spinnerCountry.adapter = spinnerAdapter
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
    private fun customBar(){
        supportActionBar!!.displayOptions = ActionBar.DISPLAY_SHOW_CUSTOM
        supportActionBar!!.setDisplayShowCustomEnabled(true)
        supportActionBar!!.setCustomView(R.layout.action_bar_layout)
        val view = supportActionBar!!.customView
    }

    fun registerMe(v: View){
        val intent = Intent(this, RegisterActivity::class.java)
        startActivity(intent)
        finish()
    }
    fun logMeIn(v: View){
        if(countriesList.size>0) {
            val phoneCode = countriesList[spinnerCountry.selectedItemPosition].phoneCode
            val phone = editPhone.text.toString()
            val userPassword = editPassword.text.toString()
            if(phone.isNotBlank() && userPassword.isNotBlank()){
                loginUser(phone, userPassword, phoneCode)
            }
            else
            {
                toastMsg("Enter Email Address and Password")
            }
        }
        else
        {
            toastMsg("Failed to load Countries phone codes, Re-Launch again")
            finish()
        }
    }

    fun showRecoverForm(v: View){
        if(editEmailAddress.visibility==View.GONE){
            editEmailAddress.visibility = View.VISIBLE
            btnRecoverEmail.visibility = View.VISIBLE
            btnForgotPassword.visibility = View.GONE
        }
        else{
            editEmailAddress.visibility = View.GONE
            btnRecoverEmail.visibility = View.GONE
        }
    }
    fun recoverPassword(v: View){
        val emailAddress = editEmailAddress.text.toString()
        if(emailAddress.isNotBlank()){
            sendRecoverPassword(emailAddress)
        }
        else{
            toastMsg("Enter Email Address")
        }
    }

    private fun toastMsg(msg: String) {
        Toast.makeText(this@LoginActivity, msg, Toast.LENGTH_LONG).show()
    }
    private fun loginUser(phone: String, userPassword: String, phoneCode: String) {
        //send info to server
        toastMsg("Please wait...")
        val url = Constants.baseUrl + "index.php/app/loginUser"
        //start sending request
        val queue = Volley.newRequestQueue(this)
        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { response ->
               println("RESULT_LOGIN: $response")
                try {
                    val json = JSONArray(response)
                    val size = json.length()
                    if (size > 0) {
                        for (i in 0 until size) {

                            val user = json.getJSONObject(i)
                            val userId = user.getInt("userId")
                            val countryId = user.getInt("countryId")
                            val dobYear = user.getInt("dobYear")
                            val status = user.getString("status")
                            val alert = user.getString("message")
                            val name = user.getString("name")
                            val bio = user.getString("bio")
                            val profileImage = user.getString("profileImage")
                            val phoneNumber = user.getString("phoneNumber")
                            val premiumExpired = user.getString("premiumExpired")
                            val accountType = user.getString("accountType")
                            val emailAddress = user.getString("emailAddress")
                            val sponsored = user.getString("sponsored")
                            val callCredits = user.getDouble("callCredits")
                            val sex = user.getString("sex")
                            val premiumExpireDate = user.getString("premiumExpireDate")
                            when (status) {
                                "SUCCESS" -> {
                                    if(premiumExpired=="Yes"){
                                        toastMsg("Premium Period Expired, Consider Recharging")
                                    }
                                    tinyDB.putInt("userId", userId)
                                    tinyDB.putInt("countryId", countryId)
                                    tinyDB.putInt("dobYear", dobYear)
                                    tinyDB.putString("emailAddress", emailAddress)
                                    tinyDB.putString("sex", sex)
                                    tinyDB.putString("bio", bio)
                                    tinyDB.putString("premiumExpired", premiumExpired)
                                    tinyDB.putString("premiumExpireDate", premiumExpireDate)
                                    tinyDB.putString("profileImage", profileImage)
                                    tinyDB.putString("phoneNumber", phoneNumber)
                                    tinyDB.putString("sponsored", sponsored)
                                    tinyDB.putString("accountType", accountType)
                                    tinyDB.putDouble("callCredits", callCredits)
                                    tinyDB.putString("name", name)
                                    tinyDB.putString("verificationStatus", "Verified")
                                    tinyDB.putBoolean("isLoggedIn", true)
                                    //got to driver activity here
                                    val intent = Intent(this@LoginActivity, MainActivity::class.java)
                                    startActivity(intent)
                                    finish()
                                }
                                "NOT_FOUND" -> toastMsg(alert)
                                else -> toastMsg("Failed to login, please try again")
                            }

                        }

                    } else {
                        toastMsg("Failed to login, please try again")
                    }


                } catch (ex: Exception) {
                    toastMsg("Failed to login, please try again")
                    println("ERROR: ${ex.message}")
                }

            },
            Response.ErrorListener { e ->
                //toastMsg("Try again")
            }) {
            override fun getParams(): Map<String, String?> {
                return mapOf(
                    "phone" to phone,
                    "phoneCode" to phoneCode,
                    "userPassword" to userPassword
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
    private fun sendRecoverPassword(emailAddress: String) {
        //send info to server
        toastMsg("Please wait...")
        val url = Constants.baseUrl + "index.php/app/recoverPassword"
        //start sending request
        val queue = Volley.newRequestQueue(this)
        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { response ->
                //println("RESULT: $response")
                try {
                    val json = JSONArray(response)
                    val size = json.length()
                    if (size > 0) {
                        for (i in 0 until size) {

                            val user = json.getJSONObject(i)
                            val status = user.getString("status")
                            val message = user.getString("message")

                            when (status) {
                                "SUCCESS" -> {
                                  toastMsg(message)
                                }
                                else -> toastMsg(message)
                            }

                        }

                    } else {
                        toastMsg("Failed to recover password, please try again")
                    }


                } catch (ex: Exception) {
                    toastMsg("Failed to recover, please try again")
                    //println("ERROR: ${ex.message}")
                }

            },
            Response.ErrorListener { e ->
                //toastMsg("Try again")
            }) {
            override fun getParams(): Map<String, String?> {
                return mapOf(
                    "emailAddress" to emailAddress
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
            mAdView = findViewById(R.id.adViewLogin)
            val adRequest = AdRequest
                .Builder()
                .addTestDevice("28480BE4C8AF9DAA06B7514F14DE6DDA")
                .addTestDevice("99C784B0B9C5ED479BFF3B1557A6309F")
                .build()
            mAdView.loadAd(adRequest)

            mAdView.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    super.onAdLoaded()
                    println("AD LOADED")
                }

                override fun onAdFailedToLoad(i: Int) {
                    super.onAdFailedToLoad(i)
                    println("FAILED To LOAD AD: $i")
                }

            }

        }
    }
}
