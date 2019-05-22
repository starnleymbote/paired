package com.mobitechstudio.linkup

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.sinch.android.rtc.*
import com.sinch.android.rtc.calling.Call
import com.sinch.android.rtc.video.VideoCallListener
import com.sinch.android.rtc.video.VideoScalingType
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_video_call.*

class ReceiveCallActivity : AppCompatActivity() {
    //video calls variables
    lateinit var tinyDB: TinyDB
    lateinit private var sinchClient: SinchClient
    private var currentCall: Call? = null
    private var mediaPlayer: MediaPlayer?=null
    val REQUEST_CODE = 111
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_call)
        tinyDB = TinyDB(this)
        mediaPlayer = MediaPlayer()
        if (intent.extras.get("mCall")!=null)
        {
            loadImage() //load caller image
            playRingTone() //play incoming call ringtone
            requestPerms() //request audio and camera permissions
        }


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
            stopAudio()
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

    private fun receiveCall(){

        currentCall =FCMMessagingService.callClient!!.getCall(intent.extras.get("mCall").toString())
        val vc = FCMMessagingService.sinchClient!!.videoController
        vc.setResizeBehaviour(VideoScalingType.ASPECT_BALANCED)
        callsLayout.visibility = View.VISIBLE
        tvCallingStatus.text = """Video Call From ${tinyDB.getString("caller_name")}"""
        callingLayout.visibility = View.GONE
        endInCallBtn.visibility = View.GONE
        closeCallsBtn.visibility = View.GONE
        receivingCallBtn.visibility = View.VISIBLE
        cancellingCallBtn.visibility = View.VISIBLE
        currentCall!!.addCallListener(object: VideoCallListener {

            override fun onVideoTrackAdded(call: Call?) {
                setSpeakerLoad()
                val myPreview = vc.localView
                val remoteView = vc.remoteView
                removeViews()
                callingLayoutPreview.addView(myPreview)
                callingLayoutRemote.addView(remoteView)
                imgView.visibility = View.GONE
            }

            override fun onVideoTrackPaused(cl: Call?) {
                println("Video Paused")
                currentCall = cl
            }

            override fun onVideoTrackResumed(cl: Call?) {
                println("Video Resumed")
                currentCall = cl
            }

            override fun onCallEstablished(cl: Call?) {
                currentCall = cl
                toastMsgShort("Call Established")
                tvCallingStatus.text = "Talking..."
                callsLayout.visibility = View.GONE
                endInCallBtn.visibility = View.VISIBLE
                callingLayout.visibility = View.VISIBLE
                stopAudio()
            }

            override fun onCallProgressing(cl: Call?) {
                currentCall = cl
                toastMsgShort("Call Progress")
                tvCallingStatus.text = """Video Call From ${tinyDB.getString("caller_name")}"""
                callsLayout.visibility = View.VISIBLE
                callingLayout.visibility = View.GONE
                receivingCallBtn.visibility = View.VISIBLE
                endInCallBtn.visibility = View.GONE
                closeCallsBtn.visibility = View.GONE
                cancellingCallBtn.visibility = View.VISIBLE
            }

            override fun onShouldSendPushNotification(cl: Call?, p1: MutableList<PushPair>?) {
                //toastMsgShort("Send Push Notification")
                println("CALL: Send Push Notification")
                currentCall = cl
            }

            override fun onCallEnded(cl: Call?) {
                toastMsgShort("Call Ended")
                volumeControlStream = AudioManager.USE_DEFAULT_STREAM_TYPE
                tvCallingStatus.text = """Call Ended: ${cl!!.details.duration} Secs"""
                currentCall = null
                callingLayout.visibility = View.GONE
                closeCallsBtn.visibility = View.VISIBLE
                callsLayout.visibility = View.VISIBLE
                cancellingCallBtn.visibility = View.GONE
                receivingCallBtn.visibility = View.GONE
                removeViews()
                stopAudio()
                finish()
            }

        })
    }
    private fun removeViews(){
        try {
            callingLayoutPreview.removeAllViews()
            callingLayoutRemote.removeAllViews()
        }catch (ex: Exception){
            println("REMOVE_ERROR ${ex.message}")
        }

    }
    private fun setSpeakerLoad(){
        val audioManager =  getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if(!audioManager.isSpeakerphoneOn){
            audioManager.isSpeakerphoneOn = true
        }
    }

    private fun requestPerms() {
        //request location permission
        if (Build.VERSION.SDK_INT >= 23) {
            if (
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
            ) {
                requestPermissions(
                    arrayOf(
                        android.Manifest.permission.CAMERA,
                        android.Manifest.permission.RECORD_AUDIO,
                        android.Manifest.permission.MODIFY_AUDIO_SETTINGS
                    ), REQUEST_CODE
                )
                return
            }
            else{
                confirmCallPermissions()
            }

        }
        else{
            confirmCallPermissions()
        }

    }

    private fun loadImage(){
        val height = tinyDB.getInt("height")
        val width = tinyDB.getInt("width")
        Picasso.get()
            .load(Constants.baseUrl+"images/userimages/"+tinyDB.getString("caller_image"))
            .placeholder(R.drawable.progress_animation)
            .resize(width, height)
            .centerCrop(Gravity.CENTER)
            .into(imgView)
    }

    private fun confirmCallPermissions(){
        if (Build.VERSION.SDK_INT >= 23) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                && ActivityCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.MODIFY_AUDIO_SETTINGS
                ) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                receiveCall()
            }
        }else{
            receiveCall()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_CODE -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {

                    confirmCallPermissions()

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
        Toast.makeText(this@ReceiveCallActivity, msg, Toast.LENGTH_SHORT).show()
    }

    private fun playRingTone(){
        val callTone = tinyDB.getBoolean("callTone")
        val changedSettings = tinyDB.getString("changedSettings")
        if(callTone || changedSettings=="") {
            val afd = applicationContext.resources.openRawResourceFd(R.raw.calltune)
            mediaPlayer!!.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            mediaPlayer!!.prepare()
            mediaPlayer!!.isLooping = true
            mediaPlayer!!.start()
        }
    }
    private fun stopAudio(){
       try {
            if(mediaPlayer !=null){
                if(mediaPlayer!!.isPlaying){
                    mediaPlayer!!.reset()
                    mediaPlayer!!.stop()
                    mediaPlayer!!.release()
                    mediaPlayer = null
                }
            }
        }catch (ex: Exception){
            println("ERROR_AUDIO: ${ex.message}")
        }

    }

    override fun onDestroy() {
        try {
            stopAudio()
        }catch (ex:Exception){}
        super.onDestroy()
    }
}
