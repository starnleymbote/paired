package com.mobitechstudio.linkup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.appcompat.app.ActionBar
import androidx.recyclerview.widget.RecyclerView
import android.view.*
import android.widget.Toast
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.facebook.ads.AdSize
import com.google.android.gms.ads.AdView
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.action_bar_layout.view.*
import kotlinx.android.synthetic.main.activity_my_chats.*
import kotlinx.android.synthetic.main.chat_list_item.view.*
import org.json.JSONArray
import java.util.*
/*
* Chat History Activity
* */
class MyChatsActivity : AppCompatActivity(){
    lateinit var tinyDB: TinyDB

    private var mAdView: AdView?=null
    var usersList = mutableListOf<Chat>()
    var hasMoreChats = true
    var currentSize = 0
    var scrollPosition = 0

    //facebook ads variables
    private var adView: com.facebook.ads.AdView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_chats)
        //set layout manager for Recylerview
        chatsListView.setHasFixedSize(true)
        chatsListView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        tinyDB = TinyDB(this)
        customBar()

        loadChats(0, false)
        swipeRefreshChats.setOnRefreshListener {
            loadChats(0, false)
        }
        setNotificationAlarm()
        preventScreenShot()
        initFbAds()
    }

    private fun initFbAds(){
        adView = com.facebook.ads.AdView(this, Constants.facebookBannerId, AdSize.BANNER_HEIGHT_50)
        val adContainer = banner_container
        // Add the ad view to your activity layout
        adContainer.addView(adView)
        // Request an ad
        adView!!.loadAd()
    }
    private fun preventScreenShot(){
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
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
        val sentOrReceived = tinyDB.getString("sentOrReceived")
        if(sentOrReceived == "Received Chats") {
            view.tvTitle.text = getString(R.string.chats)
        }
        else {
            view.tvTitle.text = getString(R.string.pending_chats)
        }
    }
    //this function is used to check if there is internet connection using ping command
    fun isConnected():Boolean {
        val cm = this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetworkInfo
        return activeNetwork != null
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
        // Destroy the AdView
        loadPopAd()
        if(mAdView !=null) {
            (mAdView!!.parent as ViewGroup).removeView(mAdView)
            mAdView!!.destroy()
        }


        if (adView != null) {
            adView!!.destroy();
        }
        super.onDestroy()
    }


    private fun setRefreshingSwipeFalse(){
        if(swipeRefreshChats.isRefreshing){
            swipeRefreshChats.isRefreshing = false
        }
    }

    private fun setRefreshingSwipeTrue(){
        if(!swipeRefreshChats.isRefreshing){
            swipeRefreshChats.isRefreshing = true
        }
    }


    inner class ChatAdapter: androidx.recyclerview.widget.RecyclerView.Adapter<ChatAdapter.MessageViewHolder>(){

        inner class MessageViewHolder(val card: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(card)

        //specify the contents for the shown items/habits
        override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
            val chat = usersList[position]
            val chatId = chat.chatId
            val userName = chat.userName
            val profilePic = chat.profilePic
            val theUserId = chat.theUserId
            val lastUpdate = chat.lastUpdate
            val locLat  = chat.locLat
            val locLon  = chat.locLon
            val locName = chat.locName
            holder.card.tvUserName.text = userName
            holder.card.tvChatDate.text = lastUpdate
            //load image

            val premiumExpired = tinyDB.getString("premiumExpired")

            Picasso.get()
                .load(Constants.baseUrl+"images/userimages/"+profilePic)
                .resize(50, 50)
                .centerCrop(Gravity.CENTER)
                .transform(CircleTransform())
                .into(holder.card.imgProfilePic)


            holder.card.setOnClickListener {

                tinyDB.putInt("toUserId", theUserId)
                tinyDB.putString("toUserName", userName)
                tinyDB.putDouble("toUserLat", locLat)
                tinyDB.putDouble("toUserLon", locLon)
                tinyDB.putString("toUserAddress", locName)
                tinyDB.putString("toUserImage", profilePic)
                startChat(theUserId, userName)

            }


            //load more videos when scrolled to bottom
            if((position >= itemCount - 1 && itemCount >= 20)){
                if(hasMoreChats) {
                    loadChats(chatId, true)
                }
            }

        }

        //create new view holder for each item
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.chat_list_item, parent, false)
            return MessageViewHolder(view)
        }

        override fun getItemCount()= usersList.size

    }
    private fun toastMsgShort(msg: String) {
        Toast.makeText(this@MyChatsActivity, msg, Toast.LENGTH_SHORT).show()
    }
    private fun startChat(toUserId: Int, toUserName: String) {
        //send info to server
        toastMsgShort("Processing, please wait..")
        val url = Constants.baseUrl + "index.php/app/checkBlock"
        //start sending request
        val queue = Volley.newRequestQueue(this)
        val stringRequest = object : StringRequest(Request.Method.POST, url,
            Response.Listener { response ->
                //println("RESULT_TOKEN: $response")
                if (response == "NOT_BLOCKED") {
                    val intent = Intent(this@MyChatsActivity, ChatActivity::class.java)
                    startActivity(intent)
                }
                else
                {
                    toastMsgShort("Sending Message to $toUserName is Restricted")
                }
            },
            Response.ErrorListener { e ->
                //toastMsg("Try again")
            }) {
            override fun getParams(): Map<String, String?> {
                return mapOf(
                    "toUserId" to toUserId.toString(),
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

    private fun loadChats(chatId: Int, loadMore: Boolean)
    {
        setRefreshingSwipeTrue()
        if (usersList.size>0){
            if(!loadMore){
                usersList.clear()
            }
        }
        //send info to server
        val url = Constants.baseUrl + "index.php/app/myChatsList"
        //start sending request
        val queue = Volley.newRequestQueue(this)
        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { response ->
                //println("RESULT: $response")
                //process result here
                if(loadMore){ //get current list before loading more
                    currentSize = ChatAdapter().itemCount
                    val myLayoutManager: androidx.recyclerview.widget.LinearLayoutManager = chatsListView.layoutManager as androidx.recyclerview.widget.LinearLayoutManager
                    scrollPosition = myLayoutManager.findLastCompletelyVisibleItemPosition()
                }
                try {
                    val json = JSONArray(response)
                    val size = json.length()
                    if (size > 0){

                        for(i in 0 until size){
                            val chat = json.getJSONObject(i)
                            val theChatId = chat.getInt("chatId")
                            val userName = chat.getString("userName")
                            val profilePic = chat.getString("profilePic")
                            val theUserId = chat.getInt("theUserId")
                            val lastUpdate = chat.getString("lastUpdate")
                            val locLat = chat.getDouble("locLat")
                            val locLon = chat.getDouble("locLon")
                            val locName = chat.getString("locName")


                            usersList.add(Chat(
                                theChatId,
                                userName,
                                profilePic,
                                theUserId,
                                lastUpdate,
                                locLat,
                                locLon,
                                locName
                            ))

                        }
                        //first loading or reload
                        if(!loadMore) {
                            chatsListView.adapter = ChatAdapter()
                        }
                        if(loadMore) //loading more
                        {
                            ChatAdapter().notifyDataSetChanged()
                            chatsListView.scrollToPosition(scrollPosition)
                        }


                    }
                    else
                    {
                        toastMsg("No Chats Found")
                        hasMoreChats = false
                    }
                    setRefreshingSwipeFalse()

                }catch (ex:Exception){
                    toastMsg("Error loading chats, try again")
                    setRefreshingSwipeFalse()
                }


            },
            Response.ErrorListener { e ->
                toastMsg("Try to Reload again")
                setRefreshingSwipeFalse()
            }) {
            override fun getParams(): Map<String, String?> {
                return mapOf(
                    "chatId" to chatId.toString(),
                    "userId" to tinyDB.getInt("userId").toString(),
                    "sentOrReceived" to tinyDB.getString("sentOrReceived")
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

    private fun toastMsg(msg: String){
        Toast.makeText(this@MyChatsActivity, msg, Toast.LENGTH_SHORT).show()
    }


    private fun setNotificationAlarm(){
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MINUTE, 1)

        calendar.set(Calendar.HOUR_OF_DAY, calendar.get(Calendar.HOUR_OF_DAY))
        calendar.set(Calendar.MINUTE, calendar.get(Calendar.MINUTE))
        calendar.set(Calendar.SECOND, calendar.get(Calendar.SECOND))

        val am = applicationContext!!.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(applicationContext, NotificationsBroadCastReceiver::class.java)
        intent.putExtra("message", "Check notifications")
        intent.action = "com.xbh.notification"
        val pi = PendingIntent.getBroadcast(applicationContext,1,intent, PendingIntent.FLAG_UPDATE_CURRENT)
        am.setRepeating(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, Constants.alarmInterval.toLong(), pi)
    }

}
