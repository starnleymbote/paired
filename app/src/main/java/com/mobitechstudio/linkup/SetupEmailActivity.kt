package com.mobitechstudio.linkup

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.appcompat.app.ActionBar
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import kotlinx.android.synthetic.main.activity_setup_email.*
import net.gotev.uploadservice.*
import org.json.JSONArray
import java.util.*

class SetupEmailActivity : AppCompatActivity(), UploadStatusDelegate {


    private var uploadReceiver:UploadServiceSingleBroadcastReceiver? =null

    lateinit var tinyDB: TinyDB
    private var progress_two: ProgressDialog? = null
    private lateinit var mAdView: AdView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        customBar()
        setContentView(R.layout.activity_setup_email)
        uploadReceiver      = UploadServiceSingleBroadcastReceiver(this)
        tinyDB = TinyDB(this)
        progress_two = ProgressDialog(this)
        initAds()

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


    private fun toastMsg(msg: String) {
        Toast.makeText(this@SetupEmailActivity, msg, Toast.LENGTH_LONG).show()
    }



    fun saveEmail(v: View){

            val emailOne = editEmail.text.toString()
            val emailTwo = editEmailTwo.text.toString()

            if (emailOne.isNotBlank() && emailTwo.isNotBlank()) {
                if(emailOne==emailTwo){
                    tinyDB.putString("emailAddress", emailOne)
                    registerEmail()
                }
                else{
                    toastMsg("Emails mismatch")
                }


            } else {
                toastMsg("Enter your email address in both areas")
            }

    }

    private fun registerEmail(){
        try{
            val uploadUrl = Constants.baseUrl + "index.php/app/editUserEmailAddress"
            tinyDB = TinyDB(this)
                val uploadId = UUID.randomUUID().toString()
                uploadReceiver!!.setUploadID(uploadId)
                MultipartUploadRequest(applicationContext, uploadId, uploadUrl)
                    .addParameter("emailAddress", tinyDB.getString("emailAddress"))
                    .addParameter("userId",tinyDB.getInt("userId").toString())
                    .setNotificationConfig(UploadNotificationConfig())
                    .setMaxRetries(2)
                    .startUpload() //Starting the upload

        }catch (ex: Exception){
            toastMsg("Failed to send data, try again")
        }

    }
    private fun removeProgress(){
        if(progress_two !=null) {
            if(progress_two!!.isShowing) {
                progress_two!!.dismiss()
            }
        }
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
                    //println("FAILED To LOAD AD: $i")
                }

            }

        }
    }

    override fun onCancelled(context: Context?, uploadInfo: UploadInfo?) {
        removeProgress()
        Toast.makeText(applicationContext, "Canceled data upload, try again", Toast.LENGTH_LONG).show()
    }

    override fun onProgress(context: Context?, uploadInfo: UploadInfo?) {
        if (!progress_two!!.isShowing) {
            progress_two!!.setMessage("Sending your email, please wait....")
            progress_two!!.show()
        }
    }

    override fun onError(
        context: Context?,
        uploadInfo: UploadInfo?,
        serverResponse: ServerResponse?,
        exception: java.lang.Exception?
    ) {
        if(progress_two !=null) {
            if(progress_two!!.isShowing) {
                if(!this@SetupEmailActivity.isFinishing) {
                    progress_two!!.dismiss()
                }
            }
        }
        Toast.makeText(applicationContext, "Failed to save, please try again", Toast.LENGTH_LONG).show()
        if (serverResponse != null) {
            //println("RESULT: " + serverResponse.bodyAsString)
        }
        if(exception !=null){
            //println("ERROR_FAILED: ${exception.message}")
        }

    }

    override fun onCompleted(context: Context?, uploadInfo: UploadInfo?, serverResponse: ServerResponse?) {
        if(progress_two !=null) {
            if(progress_two!!.isShowing) {
                if(!this@SetupEmailActivity.isFinishing) {
                    progress_two!!.dismiss()
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
                                toastMsg("Email successfully saved")
                                finish()
                            }
                            "FAILED" -> toastMsg(alert)
                            "INVALID_PASSWORD" -> toastMsg(alert)
                            else -> toastMsg("Failed to update, please try again")
                        }

                    }

                } else {
                    toastMsg("Failed to save, please try again")
                }


            } catch (ex: Exception) {
                toastMsg("Failed to save, please try again")
                println("ERROR: ${ex.message}")
            }
        }
        else
        {
            toastMsg("Failed to save, please try again")
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
}
