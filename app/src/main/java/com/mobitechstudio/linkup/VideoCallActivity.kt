package com.mobitechstudio.linkup

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.sinch.android.rtc.*
import com.sinch.android.rtc.calling.Call
import com.sinch.android.rtc.video.VideoCallListener
import com.sinch.android.rtc.video.VideoScalingType
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_video_call.*

class VideoCallActivity : AppCompatActivity() {
    //video calls varibles
    lateinit var tinyDB: TinyDB
    //video calls varibles
    lateinit private var sinchClient: SinchClient
    private var currentCall: Call? = null
    val REQUEST_CODE = 111
    private var mediaPlayer: MediaPlayer?=null
    private var timer: CountDownTimer?=null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_call)
       tinyDB = TinyDB(this)
        loadImage()
        closeCallsBtn.setOnClickListener {
            callsLayout.visibility = View.GONE
            callingLayout.visibility = View.GONE
            finish()
        }

        cancellingCallBtn.setOnClickListener {
            if(currentCall !=null){
                currentCall!!.hangup()
                currentCall = null
                closeCallsBtn.visibility = View.VISIBLE
                callingLayout.visibility = View.GONE
                cancellingCallBtn.visibility = View.GONE
            }
            else{
                currentCall = null
                closeCallsBtn.visibility = View.VISIBLE
                callingLayout.visibility = View.GONE
                cancellingCallBtn.visibility = View.GONE
            }
        }
        receivingCallBtn.setOnClickListener {
            receivingCallBtn.visibility = View.GONE
            callsLayout.visibility = View.GONE
            callingLayout.visibility = View.VISIBLE
            if(currentCall !=null){
                volumeControlStream = AudioManager.STREAM_VOICE_CALL
                currentCall!!.answer()
                tvCallingStatus.text = "Call Received"
            }
            else{
                toastMsgShort("No Call")
            }
        }
        endInCallBtn.setOnClickListener {
            if(currentCall !=null){
                currentCall!!.hangup()
                currentCall = null
                closeCallsBtn.visibility = View.VISIBLE
                callsLayout.visibility = View.VISIBLE
                callingLayout.visibility = View.GONE
                cancellingCallBtn.visibility = View.GONE
            }
            else{
                currentCall = null
                closeCallsBtn.visibility = View.VISIBLE
                callsLayout.visibility = View.VISIBLE
                callingLayout.visibility = View.GONE
                cancellingCallBtn.visibility = View.GONE
            }
        }
    }

    override fun onResume() {
        tinyDB = TinyDB(this)
        initSinch()
        requestPerms()
        super.onResume()
    }

    override fun onDestroy() {
        //terminate sinch client
        sinchClient.terminate()
        super.onDestroy()
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
                    ), REQUEST_CODE
                )
                return
            }

        }

    }



    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_CODE -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {

                    //startCall()

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

    private fun toastMsgShort(msg: String) {
        Toast.makeText(this@VideoCallActivity, msg, Toast.LENGTH_SHORT).show()
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
                toastMsgShort("Client Started")
                    startCall()
               // println("CALL: Client Started")
            }
            override fun onClientStopped(client:SinchClient) {
                 //toastMsgShort("Video Call Ended")
                //println("CALL: Client Stopped")
            }
            override fun onClientFailed(client:SinchClient, error: SinchError) {
                // toastMsgShort("Client Failed")
               // toastMsgShort("Video Call Failed")
                finish()
               // println("CALL: Client Failed")
            }
            override fun onRegistrationCredentialsRequired(client:SinchClient, registrationCallback: ClientRegistration) {
                //toastMsgShort("onRegistrationCredentialsRequired")
                //toastMsgShort("Video Call Failed")
                finish()
                //println("CALL: onRegistrationCredentialsRequired")
            }
            override fun onLogMessage(level:Int, area:String, message:String) {
                //toastMsgShort("Message Logged")
                //println("CALL: Message Logged")
            }
        })
        sinchClient.start()
    }
    private fun loadImage(){
        val height = tinyDB.getInt("height")
        val width = tinyDB.getInt("width")
        Picasso.get()
            .load(Constants.baseUrl+"images/userimages/"+tinyDB.getString("toUserImage"))
            .placeholder(R.drawable.progress_animation)
            .resize(width, height)
            .centerCrop(Gravity.CENTER)
            .into(imgView)
    }
    private fun setSpeakerLoad(){
        val audioManager =  getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if(!audioManager.isSpeakerphoneOn){
            audioManager.isSpeakerphoneOn = true
        }
    }
    private fun removeViews(){
        try {
            callingLayoutPreview.removeAllViews()
            callingLayoutRemote.removeAllViews()
        }catch (ex: Exception){
            println("REMOVE_ERROR ${ex.message}")
        }

    }
    private fun playRingTone(){
        try {
            mediaPlayer = MediaPlayer()
            val afd = applicationContext.resources.openRawResourceFd(R.raw.diatone)
            mediaPlayer!!.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            mediaPlayer!!.prepare()
            mediaPlayer!!.isLooping = true
            mediaPlayer!!.start()
        }catch (ex: Exception){
            println("ERROR_AUDIO_START ${ex.message}")
        }

    }
    private fun stopAudio(){
        try {
            if(mediaPlayer !=null){
                if(mediaPlayer!!.isPlaying){
                    mediaPlayer!!.reset()
                    mediaPlayer!!.prepare()
                    mediaPlayer!!.stop()
                    mediaPlayer!!.release()
                }
            }
        }catch (ex: Exception){
            println("ERROR_AUDIO: ${ex.message}")
        }

    }
    private fun creditBalanceLabel(){
        //start timer here to check for seconds used vs call credits balance
        val callCredits = tinyDB.getDouble("callCredits", 0.0)
        val totalCreditsMillSeconds = callCredits * 60000.toDouble()
        tvTimer.text = "Credit Balance: "+(totalCreditsMillSeconds / 1000)+" Secs"
    }
    private fun startCall(){
        creditBalanceLabel()
        //check call credits balance here
        callsLayout.visibility = View.VISIBLE
        receivingCallBtn.visibility = View.GONE
        closeCallsBtn.visibility = View.GONE
        cancellingCallBtn.visibility = View.VISIBLE
        tvCallingStatus.text = "Video Call..."
        playRingTone()

        val callClient = sinchClient.callClient
        val headers = HashMap<String, String>()
        headers["profileImage"] = tinyDB.getString("profileImage")
        headers["userId"] = tinyDB.getInt("userId").toString()
        val call = callClient.callUserVideo(tinyDB.getInt("toUserId").toString(), headers)
        volumeControlStream = AudioManager.STREAM_VOICE_CALL

        call.addCallListener(object: VideoCallListener {
            override fun onVideoTrackAdded(cl: Call?) {
                if(cl!!.details.isVideoOffered){
                    currentCall = cl
                    setSpeakerLoad()
                    val vc = sinchClient.videoController
                    vc.setResizeBehaviour(VideoScalingType.ASPECT_BALANCED)
                    val myPreview = vc.localView
                    val remoteView = vc.remoteView
                    removeViews()
                    callingLayoutPreview.addView(myPreview)
                    callingLayoutRemote.addView(remoteView)
                    callingLayout.visibility = View.VISIBLE
                    imgView.visibility = View.GONE
                }
            }

            override fun onVideoTrackPaused(p0: Call?) {
                println("Video Paused")
            }

            override fun onVideoTrackResumed(p0: Call?) {
                println("Video Resumed")
            }

            override fun onCallEstablished(cl: Call?) {
                stopAudio()
                tvCallingStatus.text = "Talking.."
                callsLayout.visibility = View.GONE
                endInCallBtn.visibility = View.VISIBLE
                tvTimer.visibility = View.VISIBLE
                //start timer here to check for seconds used vs call credits balance
                val callCredits = tinyDB.getDouble("callCredits", 0.0)
                val totalCreditsMillSeconds = callCredits * 60000.toDouble()
               timer = object: CountDownTimer(totalCreditsMillSeconds.toLong(), 1000) {
                    override fun onTick(millisUntilFinished:Long) {
                        tvTimer.text = "Credit Balance: "+(millisUntilFinished / 1000)+" Secs"
                    }
                    override fun onFinish() {
                        if(currentCall !=null){
                            currentCall!!.hangup()
                        }
                        timer = null
                    }
                }.start()

            }

            override fun onCallProgressing(cl: Call?) {
                tvCallingStatus.text = "Calling "+tinyDB.getString("toUserName")
                currentCall = cl
                closeCallsBtn.visibility = View.GONE
                endInCallBtn.visibility = View.GONE
                cancellingCallBtn.visibility = View.VISIBLE

            }

            override fun onShouldSendPushNotification(cl: Call?, p1: MutableList<PushPair>?) {
                //toastMsgShort("Send Push Notification")
                println("CALL: Send Push Notification")
            }

            override fun onCallEnded(cl: Call?) {
                stopAudio()
                volumeControlStream = AudioManager.USE_DEFAULT_STREAM_TYPE
                val duration = cl!!.details.duration
                tvCallingStatus.text = """Call Ended: $duration Secs"""

                callingLayout.visibility = View.GONE
                callsLayout.visibility = View.VISIBLE
                closeCallsBtn.visibility = View.VISIBLE
                cancellingCallBtn.visibility = View.GONE
                removeViews()
                imgView.visibility = View.VISIBLE
                if(timer !=null){
                    timer!!.onFinish()
                }
                //update call credits balance
                if(duration > 0) {
                    val durationMinutes = roundNumber(duration.toDouble() / 60.toDouble())

                    val callCredits = tinyDB.getDouble("callCredits", 0.0)
                    val callCreditsBalance = callCredits - durationMinutes
                    tinyDB.putDouble("callCredits", callCreditsBalance)
                    sendCreditToServer()
                }
                currentCall = null
            }


        })
    }
    private fun roundNumber(theNumber: Double): Double{
        var seconds = theNumber
        seconds *= 100
        seconds = Math.round(seconds).toDouble()
        seconds /= 100
        return seconds
    }
    private fun sendCreditToServer() {
        //send info to server
        val url = Constants.baseUrl + "index.php/app/updateCredit"
        //start sending request
        val queue = Volley.newRequestQueue(applicationContext)
        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { response ->
                //println("RESULT_CALL: $response")

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
                    "callCredits" to tinyDB.getDouble("callCredits", 0.0).toString(),
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
