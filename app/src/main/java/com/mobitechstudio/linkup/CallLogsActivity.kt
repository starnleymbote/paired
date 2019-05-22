package com.mobitechstudio.linkup

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.appcompat.app.ActionBar
import androidx.recyclerview.widget.RecyclerView
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.facebook.ads.AdSize
import com.google.android.gms.ads.AdView
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.action_bar_layout.view.*
import kotlinx.android.synthetic.main.activity_my_views.*
import kotlinx.android.synthetic.main.viewers_list_item.view.*
import org.json.JSONArray

/*
* Call Logs History Activity
*
* Shows Only Missed Calls not Received Calls History
*
* */
class CallLogsActivity : AppCompatActivity() {
    lateinit var tinyDB: TinyDB
    private var mAdView: AdView?=null
    var usersList = mutableListOf<ProfileViewer>()
    var hasMoreChats = true
    var currentSize = 0
    var scrollPosition = 0

    //facebook ads
    private var adView: com.facebook.ads.AdView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_views)
        //set layout manager for Recylerview
        chatsListView.setHasFixedSize(true)
        chatsListView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        tinyDB = TinyDB(this)
        customBar()
        loadCallLogs(0, false)
        swipeRefreshChats.setOnRefreshListener {
            loadCallLogs(0, false)
        }

        initFbAds() //initialize facebook banner ads
    }
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.call_log_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.action_share) {
            shareMe()
        }
        else if(id == R.id.action_delete){
            //confirm clear logs here when recycle bin icon is touched on call log window
            if(usersList.size> 0) {
                confirmDeleteMessages()
            }
            else{
                toastMsgShort("No Call Logs Found")
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun confirmDeleteMessages(){
        val builder = AlertDialog.Builder(this@CallLogsActivity)
        builder.setTitle("Confirm Delete")
        builder.setMessage("Are you sure you want to Delete All Call Logs?")
        builder.setCancelable(false)
        builder.setIcon(R.mipmap.paired_icon)
        builder.setPositiveButton("Yes") { _, _ ->
            toastMsgShort("Deleting Call Logs")
            deleteCallLogs()
        }
        builder.setNegativeButton("No") { dialog, _ ->
            dialog.cancel()
        }
        val alert = builder.create()
        alert.show()
    }
    //send delete command to server to delete all call logs of the current user
    private fun deleteCallLogs() {
        //send info to server
        val url = Constants.baseUrl + "index.php/app/deleteCallLogs"
        //start sending request
        val queue = Volley.newRequestQueue(this)
        val stringRequest = object : StringRequest(Request.Method.POST, url,
            Response.Listener { response ->
                if (response == "SUCCESS") {
                    if(usersList.size> 0) {
                        usersList.clear() //clear call log list
                    }
                    finish() //finish activity
                    toastMsg("Deleted successfully") //notify user that all call logs has been deleted
                } else {
                   toastMsg("Failed to Delete, Try again")
                }
            },
            Response.ErrorListener { e ->
                toastMsg("Failed to delete, Try Again")
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

    /*
    this function opens share dialog to share app download from store(play Store or Amazon App store)
    it is your choice where you want to direct your users
     */
    private fun shareMe() {
        val sendIntent = Intent()
        val msg = getString(R.string.share_message) //change this to your store listing url in strings.xml file
        sendIntent.action = Intent.ACTION_SEND
        sendIntent.putExtra(Intent.EXTRA_TEXT, msg)
        sendIntent.type = "text/plain"
        startActivity(sendIntent)
    }
    //this function sets custom action bar of the app
    private fun customBar(){
        supportActionBar!!.displayOptions = ActionBar.DISPLAY_SHOW_CUSTOM
        supportActionBar!!.setDisplayShowCustomEnabled(true)
        supportActionBar!!.setCustomView(R.layout.action_bar_layout)
        val view = supportActionBar!!.customView
        view.tvTitle.text = getString(R.string.missed_calls)
    }

    //this function is used to check if there is internet connection using ping command
    fun isConnected():Boolean {
        val cm = this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetworkInfo
        return activeNetwork != null
    }


    //this function initializes facebook banner ad
    private fun initFbAds(){
        adView = com.facebook.ads.AdView(this, Constants.facebookBannerId, AdSize.BANNER_HEIGHT_50)
        val adContainer = banner_container
        // Add the ad view to your activity layout
        adContainer.addView(adView)
        // Request an ad
        adView!!.loadAd()
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
        // Destroy the AdView
        if(mAdView !=null) {
            (mAdView!!.parent as ViewGroup).removeView(mAdView)
            mAdView!!.destroy()
        }

        super.onDestroy()
    }

    //this function show swipe refresh on top of call logs list
    private fun setRefreshingSwipeFalse(){
        if(swipeRefreshChats.isRefreshing){
            swipeRefreshChats.isRefreshing = false
        }
    }
    //this function hides swipe refresh on top of call logs list
    private fun setRefreshingSwipeTrue(){
        if(!swipeRefreshChats.isRefreshing){
            swipeRefreshChats.isRefreshing = true
        }
    }

    //this class handles display of Call log item
    inner class LogAdapter: androidx.recyclerview.widget.RecyclerView.Adapter<LogAdapter.MessageViewHolder>(){

        inner class MessageViewHolder(val card: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(card)

        //specify the contents for the shown logs
        override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
            val chat = usersList[position]
            val chatId = chat.viewId
            val viewsCount = chat.viewsCount
            val userName = chat.userName
            val profilePic = chat.profilePic
            val theUserId = chat.theUserId
            val lastUpdate = chat.lastUpdate
            holder.card.tvUserName.text = userName
            holder.card.tvChatDate.text = lastUpdate
            holder.card.tvViewsCount.visibility = View.GONE //this is not required here, but is used in who viewed me activity
            //load image
            val height = 50
            val width = 50

            Picasso.get()
                .load(Constants.baseUrl+"images/userimages/"+profilePic)
                .placeholder(R.drawable.progress_animation)
                .resize(width, height)
                .error(R.mipmap.paired_icon)
                .centerCrop(Gravity.CENTER)
                .transform(CircleTransform())
                .into(holder.card.imgProfilePic)


            holder.card.setOnClickListener {
                viewUser(theUserId)
            }


            //load more videos when scrolled to bottom
            if((position >= itemCount - 1 && itemCount >= 20)){
                if(hasMoreChats) {
                    loadCallLogs(chatId, true)
                }
            }

        }

        //create new view holder for each item
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.viewers_list_item, parent, false)
            return MessageViewHolder(view)
        }

        override fun getItemCount()= usersList.size

    }

    //this function takes you to user's profile on call log item click
    private fun viewUser(userId: Int){
        tinyDB.putInt("currentUserId", userId)
        val intent = Intent(this, ViewUserProfileActivity::class.java)
        startActivity(intent)
    }

    //this function loads call logs from server
    private fun loadCallLogs(id: Int, loadMore: Boolean)
    {
        setRefreshingSwipeTrue()
        if (usersList.size>0){
            if(!loadMore){
                usersList.clear()
            }
        }
        //send info to server
        val url = Constants.baseUrl + "index.php/app/myCallLogs"
        //start sending request
        val queue = Volley.newRequestQueue(this)
        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { response ->
                //process result here
                if(loadMore){ //get current list before loading more
                    currentSize = LogAdapter().itemCount
                    val myLayoutManager: androidx.recyclerview.widget.LinearLayoutManager = chatsListView.layoutManager as androidx.recyclerview.widget.LinearLayoutManager
                    scrollPosition = myLayoutManager.findLastCompletelyVisibleItemPosition()
                }
                try {
                    val json = JSONArray(response)
                    val size = json.length()
                    if (size > 0){

                        for(i in 0 until size){
                            val chat = json.getJSONObject(i)
                            val viewId = chat.getInt("viewId")
                            val userName = chat.getString("userName")
                            val profilePic = chat.getString("profilePic")
                            val theUserId = chat.getInt("theUserId")
                            val viewsCount = chat.getInt("viewsCount")
                            val lastViewDate = chat.getString("lastViewDate")

                            //create user instant per each item in json result using ProfileViewer object
                            usersList.add(
                                ProfileViewer(
                                viewId,
                                userName,
                                profilePic,
                                theUserId,
                                viewsCount,
                                lastViewDate
                            )
                            )

                        }
                        //checking if it first load or infinite scroll call
                        if(!loadMore) {
                            chatsListView.adapter = LogAdapter()
                        }
                        if(loadMore) //loading more
                        {
                            LogAdapter().notifyDataSetChanged()
                            chatsListView.scrollToPosition(scrollPosition)
                        }


                    }
                    else
                    {
                        toastMsg("No Call Logs Found")
                        hasMoreChats = false
                    }
                    setRefreshingSwipeFalse()

                }catch (ex:Exception){
                    toastMsg("Error loading Call Log, try again")
                    setRefreshingSwipeFalse() //hide refreshing loading indicator
                }


            },
            Response.ErrorListener { e ->
                toastMsg("Error loading Call Log, try again")
                setRefreshingSwipeFalse()
            }) {
            override fun getParams(): Map<String, String?> {
                return mapOf(
                    "callId" to id.toString(), //last call log id, for infinite scroll
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

    //this function shows toast message
    private fun toastMsgShort(msg: String) {
        Toast.makeText(this@CallLogsActivity, msg, Toast.LENGTH_SHORT).show()
    }
    private fun toastMsg(msg: String){
        Toast.makeText(this@CallLogsActivity, msg, Toast.LENGTH_LONG).show()
    }
}
