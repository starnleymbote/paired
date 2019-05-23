package com.mobitechstudio.linkup


import android.annotation.TargetApi
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.*
import java.text.SimpleDateFormat
import android.app.NotificationChannel
import android.graphics.Color
import com.sinch.android.rtc.*
import com.sinch.android.rtc.calling.CallClient

/*
* Fire base notification service, handles fire base fcm notifications and Sinch calls notifications
* */
class FCMMessagingService:FirebaseMessagingService() {
    lateinit var tinyDB: TinyDB

    companion object {
        var sinchClient: SinchClient? = null
        var callClient: CallClient? = null
    }

    override fun onNewToken(token:String) {
        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // Instance ID token to your app server.
        tinyDB = TinyDB(applicationContext)
        sendTokenToServer(token)
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


    private fun currentTime(): String{
        val c = Calendar.getInstance()
        val df = SimpleDateFormat("dd-MM-yyyy HH:mm:ss")
        return df.format(c.time)
    }

   override fun onMessageReceived(message: RemoteMessage?) {
        super.onMessageReceived(message)

       if(SinchHelpers.isSinchPushPayload(message!!.data)){
           initSinch() // init sinch and call client for background call
           val result = sinchClient!!.relayRemotePushNotificationPayload(message.data)     // relay the background incomming call
            if(result.isCall){
                val headers = result.callResult.headers
                val callerImage = headers["profileImage"]
                val userId = headers["userId"]

                if(result.callResult.isCallCanceled || result.callResult.isTimedOut) {
                    val intent = Intent(this, CallLogsActivity::class.java)
                    //save call log here
                    sendCallLogToServer(userId!!)
                    val callsNoti = tinyDB.getBoolean("callsNoti")
                    val changedSettings = tinyDB.getString("changedSettings")
                    if(callsNoti || changedSettings=="") {
                        notificationMessage("MISSED CALL", "You Have Missed Call From " + result.displayName, intent)
                    }
                }
                else
                {
                    //set caller name and call image file name in shared preferences on call received
                    tinyDB.putString("caller_name", result.displayName)
                    tinyDB.putString("caller_image", callerImage)
                }

            }
       }
       else {

           if (message.data.isNotEmpty()) {
               val body = message.data!!["body"]
               val title = message.data!!["title"]
               sendTheNotification(title, body)
           } else if (message.notification != null) {
               val body = message.notification!!.body
               val title = message.notification!!.title
               sendTheNotification(title, body)
           }
       }
    }

    override fun onCreate() {
        tinyDB = TinyDB(applicationContext)
        initSinch()
        super.onCreate()
    }

    private fun initSinch() {
        if (sinchClient == null)
        {

            val context = this.applicationContext
            sinchClient = Sinch.getSinchClientBuilder().context(context)
                .applicationKey(Constants.sinchKey)
                .applicationSecret(Constants.sinchSecret)
                .environmentHost(Constants.sinchHost)
                .userId(tinyDB.getInt("userId").toString()).build()
            sinchClient!!.setSupportCalling(true)
            sinchClient!!.setSupportActiveConnectionInBackground(false)
            sinchClient!!.startListeningOnActiveConnection()
            sinchClient!!.setSupportManagedPush(true)
            sinchClient!!.setPushNotificationDisplayName(tinyDB.getString("name"))
            sinchClient!!.addSinchClientListener(object: SinchClientListener {
                override fun onClientStarted(client:SinchClient) {

                }
                override fun onClientStopped(client:SinchClient) {

                }
                override fun onClientFailed(client:SinchClient, error: SinchError) {

                }
                override fun onRegistrationCredentialsRequired(client:SinchClient, registrationCallback:ClientRegistration) {
                }
                override fun onLogMessage(level:Int, area:String, message:String) {
                }
            })
            callClient = sinchClient!!.callClient
            callClient!!.setRespectNativeCalls(true)
            callClient!!.addCallClientListener { callClient, INCOMMINGCALL ->
                if (INCOMMINGCALL.details.isVideoOffered) {
                    val it = Intent(applicationContext, ReceiveCallActivity::class.java)
                    it.putExtra("mCall", INCOMMINGCALL.callId)
                    it.putExtra("mCall_caller", INCOMMINGCALL.remoteUserId)
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(it)
                }
            }
        }
        if (sinchClient != null && !sinchClient!!.isStarted)
        {
            sinchClient!!.start()
        }
    }



   private fun sendTheNotification(title: String?, body: String?){
       when {
           title!!.contains("NEW CHAT") -> {
               val chatsNoti = tinyDB.getBoolean("chatsNoti") //check if user allowed receiving Chat notification
               val changedSettings = tinyDB.getString("changedSettings") //check if user has ever set Notification Priorities
               if(chatsNoti || changedSettings=="") {
                   tinyDB.putString("sentOrReceived", "Received Chats")
                   val intent = Intent(this, MyChatsActivity::class.java)
                   notificationMessage(title, body!!, intent)
               }
           }
           title.contains("NEW LIKE") -> {
               val likeNoti = tinyDB.getBoolean("likeNoti")
               val changedSettings = tinyDB.getString("changedSettings")
               if(likeNoti || changedSettings=="") {
                   val intent = Intent(this, MyLikesActivity::class.java)
                   notificationMessage(title, body!!, intent)
               }
           }
           title.contains("PROFILE VISITOR") -> {
               val visitorNoti = tinyDB.getBoolean("visitorNoti")
               val changedSettings = tinyDB.getString("changedSettings")
               if(visitorNoti || changedSettings=="") {
                   val intent = Intent(this, MyViewsActivity::class.java)
                   notificationMessage(title, body!!, intent)
               }
           }
           else -> {
               val intent = Intent(this, MainActivity::class.java)
               notificationMessage(title, body!!, intent)
           }
       }

   }


    private fun notificationMessage(title: String, msg: String, intent: Intent){
        val bitmap = BitmapFactory.decodeResource(applicationContext.resources, R.drawable.logo)
        val notificationId = System.currentTimeMillis().toInt()


        val pIntent = PendingIntent.getActivity(this, notificationId, intent, 0)

        val bigTextStyle = NotificationCompat
            .BigTextStyle()
            .bigText(msg)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val mBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel(notificationManager)
            NotificationCompat.Builder(applicationContext)
                .setSmallIcon(R.mipmap.paired_circle_round)
                .setLargeIcon(bitmap)
                .setContentTitle(title)
                .setChannelId(Constants.fcmNotificationTopic)
                .setContentText(msg)
                .setStyle(bigTextStyle)
                .setDefaults(NotificationCompat.DEFAULT_SOUND)
                .setAutoCancel(true)
                .setContentIntent(pIntent)

        } else {
            NotificationCompat.Builder(applicationContext)
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
        val channelId = Constants.fcmNotificationTopic
        val channelName = Constants.fcmNotificationTopic
        val importance = NotificationManager.IMPORTANCE_HIGH
        val notificationChannel = NotificationChannel(channelId, channelName, importance)
        notificationChannel.enableLights(true)
        notificationChannel.lightColor = Color.RED
        notificationChannel.enableVibration(true)
        notificationChannel.vibrationPattern = longArrayOf(0, 500)
        notificationManager.createNotificationChannel(notificationChannel)
    }


    private fun sendCallLogToServer(fromUserId: String) {
        //send info to server
        val url = Constants.baseUrl + "index.php/app/saveMissedCall"
        //start sending request
        val queue = Volley.newRequestQueue(applicationContext)
        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { response ->
                if (response == "Success") {
                    //toastMsg("Sent successfully")
                } else {
                    //toastMsg("Failed to Send, Try again")
                }
            },
            Response.ErrorListener { e ->
                //toastMsg("Try again")
            }) {
            override fun getParams(): Map<String, String?> {
                return mapOf(
                    "fromUserId" to fromUserId,
                    "toUserId" to tinyDB.getInt("userId").toString()
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