package com.mobitechstudio.linkup

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.app.ActivityCompat
import androidx.appcompat.app.ActionBar
import android.view.Menu
import android.view.MenuItem
import android.view.View
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
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.iid.InstanceIdResult
import com.google.firebase.messaging.FirebaseMessaging
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_edit_images.*
import net.gotev.uploadservice.*
import org.json.JSONArray
import java.io.FileNotFoundException
import java.util.*
import kotlin.concurrent.thread

class EditImagesActivity : AppCompatActivity(), UploadStatusDelegate {

    private var uploadReceiver:UploadServiceSingleBroadcastReceiver? =null
    var countriesList = ArrayList<Country>()
    var yearsList = ArrayList<DobYear>()
    //images uploads permissions
    val PICK_IMGONE_REQUEST = 111
    val PICK_IMGTWO_REQUEST = 112
    val STORAGE_PERMISSON_ONE = 1
    val LOCATION_REQUEST = 333
    lateinit var tinyDB: TinyDB
    private var progress_two: ProgressDialog? = null
    private var filePath: Uri? = null
    private var filePath2: Uri? = null
    private lateinit var mAdView: AdView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        customBar()
        setContentView(R.layout.activity_edit_images)
        uploadReceiver      = UploadServiceSingleBroadcastReceiver(this)
        tinyDB = TinyDB(this)
        progress_two = ProgressDialog(this)
        requestPerms()
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

                getCurrentTokenSend()
            }

        }
        else{

            getCurrentTokenSend()

        }
    }



    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            LOCATION_REQUEST -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    getCurrentTokenSend()
                } else {
                    toastMsg("Please Allow TM to access storage")
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
        Toast.makeText(this@EditImagesActivity, msg, Toast.LENGTH_LONG).show()
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

    fun saveData(v: View){
        btnSave.isEnabled = false
        saveImages()

    }

    private fun saveImages(){

        try{
          val path = getPath(filePath!!)
          val path2 = getPath(filePath2!!)
            val uploadUrl = Constants.baseUrl + "index.php/app/editUserProfileImages"
            tinyDB = TinyDB(this)
            if(path !="NA" && path2 !="NA") {
                val uploadId = UUID.randomUUID().toString()
                uploadReceiver!!.setUploadID(uploadId)
                MultipartUploadRequest(applicationContext, uploadId, uploadUrl)
                    .addFileToUpload(path, "imgOne") //Adding file
                    .addFileToUpload(path2, "imgTwo") //Adding file
                    .addParameter("userId",tinyDB.getInt("userId").toString())
                    .setNotificationConfig(UploadNotificationConfig())
                    .setMaxRetries(2)
                    .startUpload() //Starting the upload

            }
            else{
                toastMsg("No images attached, try again")

            }

        }catch (ex: Exception){
            toastMsg("Failed to upload, try again")
            btnSave.isEnabled = true
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
                var document_id = cursor.getString(0)
                document_id = document_id.substring(document_id.lastIndexOf(":") + 1)
                cursor.close()

                cursor = contentResolver.query(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    null,
                    MediaStore.Images.Media._ID + " = ? ",
                    arrayOf(document_id),
                    null
                )
                cursor!!.moveToFirst()
                path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA))
                cursor.close()
            }
            catch (ex: Exception){
                println("ERROR: ${ex.message}")
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

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMGONE_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.data != null) {
            filePath = data.data

            if (filePath == null) {
                Toast.makeText(this@EditImagesActivity, "Select Image again please", Toast.LENGTH_LONG).show()
            } else {
                 try {
                    Picasso.get()
                        .load(filePath)
                        .noPlaceholder()
                        .resize(100, 100)
                        .centerInside()
                        .into(imgProfileOne)
                }catch (ex: Exception){
                    toastMsg("Failed to Select Photo, try Again")
                }
            }

        } else if (requestCode == PICK_IMGTWO_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.data != null) {
            filePath2 = data.data
            if (filePath2 == null) {
                Toast.makeText(this@EditImagesActivity, "Select Image again please", Toast.LENGTH_LONG).show()
            } else {
                  try {
                    Picasso.get()
                        .load(filePath2)
                        .noPlaceholder()
                        .resize(100, 100)
                        .centerInside()
                        .into(imgProfileTwo)
                }catch (ex: Exception){
                    toastMsg("Failed to Select Photo, try Again")
                }
            }
        }

    }

    @Throws(FileNotFoundException::class)
    fun decodeUri(c: Context, uri:Uri, requiredSize:Int): Bitmap {
        val o = BitmapFactory.Options()
        o.inJustDecodeBounds = true
        BitmapFactory.decodeStream(c.contentResolver.openInputStream(uri), null, o)
        var width_tmp = o.outWidth
        var height_tmp = o.outHeight
        var scale = 1
        while (true)
        {
            if (width_tmp / 2 < requiredSize || height_tmp / 2 < requiredSize)
                break
            width_tmp /= 2
            height_tmp /= 2
            scale *= 2
        }
        val o2 = BitmapFactory.Options()
        o2.inSampleSize = scale
        return BitmapFactory.decodeStream(c.contentResolver.openInputStream(uri), null, o2)
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
        btnSave.isEnabled = true
        Toast.makeText(applicationContext, "Canceled upload images, try again", Toast.LENGTH_LONG).show()
    }

    override fun onProgress(context: Context?, uploadInfo: UploadInfo?) {
        //show upload progress
        tvProgress.text = """Uploading images, please wait ${uploadInfo!!.progressPercent}%"""
    }

    override fun onError(
        context: Context?,
        uploadInfo: UploadInfo?,
        serverResponse: ServerResponse?,
        exception: java.lang.Exception?
    ) {
        btnSave.isEnabled = true
        if(progress_two !=null) {
            if(progress_two!!.isShowing) {
                if(!this@EditImagesActivity.isFinishing) {
                    progress_two!!.dismiss()
                }
            }
        }
        tvProgress.text = "Failed to update, please try again"
        Toast.makeText(applicationContext, "Failed to update, please try again", Toast.LENGTH_LONG).show()
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
                if(!this@EditImagesActivity.isFinishing) {
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
                        val profileImage = user.getString("profileImage")
                        when (status) {
                            "SUCCESS" -> {
                                tinyDB.putString("profileImage", profileImage)
                                tinyDB.putBoolean("isLoggedIn", true)
                                finish()
                            }
                            "FAILED" -> {
                                toastMsg(alert)
                                btnSave.isEnabled = true
                            }
                            "IMG_UPLOAD_ERROR" ->{
                                btnSave.isEnabled = true
                                toastMsg(alert)
                            }
                            else -> {
                                btnSave.isEnabled = true
                                toastMsg("Failed to update, please try again")
                            }
                        }

                    }

                } else {
                    toastMsg("Failed to upload images, please try again")
                    btnSave.isEnabled = true
                }


            } catch (ex: Exception) {
                toastMsg("Failed to upload images, please try again")
                //println("ERROR: ${ex.message}")
                btnSave.isEnabled = true
            }
        }
        else
        {
            btnSave.isEnabled = true
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
        loadPopAd()
        super.onDestroy()
    }
}
