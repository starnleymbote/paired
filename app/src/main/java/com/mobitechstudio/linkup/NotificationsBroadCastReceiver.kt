package com.mobitechstudio.linkup


import android.annotation.TargetApi
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.ConnectivityManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import org.json.JSONArray

/**this class handles automated notification which runs once per day per each user, general message is set in firebase remote
 * config parameters, Android Alarm manager is used to handle auto run notifications
 * **/
class NotificationsBroadCastReceiver: BroadcastReceiver(){
    //remote config
    private var mFirebaseRemoteConfig: FirebaseRemoteConfig? = null
    private val TM_MESSAGE = "tm_message"
    private val TM_TITLE = "tm_title"
    override fun onReceive(context: Context?, intent: Intent?) {
        if(intent!!.action=="com.xbh.notification"){
           //check new likes and messages here if there is internet connection
            if(isConnected(context!!)){
                //check new likes
                fetchUserLikes(context)
                fetchUserChats(context)
            }

        }
        else if(intent.action=="com.xbh.salute"){ //if automated broadcast receiver for notification is received
            showSaluteNotification(context)
        }
        else if(intent.action == "android.intent.action.BOOT_COMPLETED"){
            //reset alarm on phone reboot
            val activity = MainActivity()
            activity.setNotificationAlarm()
            activity.setDailyNotification()
        }
    }

    //this function creates automated notifications per user who has not opened the app within 6 hours in a day
    private fun showSaluteNotification(context: Context?) {
        //remote config check new automated message value
        mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setDeveloperModeEnabled(BuildConfig.DEBUG) //turn this off in production
            .build()
        mFirebaseRemoteConfig!!.setConfigSettings(configSettings)
        mFirebaseRemoteConfig!!.setDefaults(R.xml.remote_config_defaults)
        var cacheExpiration: Long = 3600
        if (mFirebaseRemoteConfig!!.info.configSettings.isDeveloperModeEnabled) {
            cacheExpiration = 0
        }
        mFirebaseRemoteConfig!!.fetch(cacheExpiration).addOnCompleteListener {
            if (it.isSuccessful) {
                // After config data is successfully fetched, it must be activated before newly fetched
                // values are returned.
                mFirebaseRemoteConfig!!.activateFetched()
            } else {
                //Toast.makeText(MainActivity.this, "Fetch Failed", Toast.LENGTH_SHORT).show();
            }
            //check current message now now
            val tmMessage = mFirebaseRemoteConfig!!.getString(TM_MESSAGE)
            val tmTitle = mFirebaseRemoteConfig!!.getString(TM_TITLE)
            notificationMessage(tmTitle, tmMessage, Intent(context, MainActivity::class.java), context)

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
                .setSmallIcon(R.drawable.logo)
                .setLargeIcon(bitmap)
                .setContentTitle(title)
                .setChannelId("TM") //change channel ID here
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
        val channelId = Constants.fcmNotificationTopic //change  FCM channel ID here
        val channelName = Constants.fcmNotificationTopic //change FCM channel Name here
        val importance = NotificationManager.IMPORTANCE_HIGH
        val notificationChannel = NotificationChannel(channelId, channelName, importance)
        notificationChannel.enableLights(true)
        notificationChannel.lightColor = Color.RED
        notificationChannel.enableVibration(true)
        notificationChannel.vibrationPattern = longArrayOf(0,500)
        notificationManager.createNotificationChannel(notificationChannel)
    }

    //this function is used to check if there is internet connection using ping command
    fun isConnected(context: Context):Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetworkInfo
        return activeNetwork != null
    }

    //this function get pending likes notification from app server
    private fun fetchUserLikes(context: Context?)
    {
        val tinyDB = TinyDB(context!!)
        //send info to server
        val url = Constants.baseUrl + "index.php/app/userLikes"
        //start sending request
        val queue = Volley.newRequestQueue(context)
        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { response ->
                try {
                    val json = JSONArray(response)
                    val size = json.length()
                    if (size > 0){

                        for(i in 0 until size){
                            val like = json.getJSONObject(i)
                            val userId = like.getInt("user_id")
                            val name = like.getString("user_name")
                            notificationMessage("NEW LIKE", "$name likes your profile", Intent(context, MainActivity::class.java), context)
                        }

                    }


                }catch (ex:Exception){
                   //show error
                }


            },
            Response.ErrorListener { e ->
                //show error
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

    //this functional get user pending chats from server to notify for new chat
    private fun fetchUserChats(context: Context?)
    {
        val tinyDB = TinyDB(context!!)
        //send info to server
        val url = Constants.baseUrl + "index.php/app/userChats"
        //start sending request
        val queue = Volley.newRequestQueue(context)
        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { response ->
                try {
                    val json = JSONArray(response)
                    val size = json.length()
                    if (size > 0){

                        for(i in 0 until size){
                            val chat = json.getJSONObject(i)
                            val fromUserId = chat.getInt("fromUserId")
                            val fromUserName = chat.getString("fromUserName")
                            tinyDB.putString("sentOrReceived", "Received Chats")
                            notificationMessage("NEW CHAT", "$fromUserName Sent you message", Intent(context, MyChatsActivity::class.java), context)
                        }

                    }


                }catch (ex:Exception){
                   //show error
                }


            },
            Response.ErrorListener { e ->
                //show error
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

}