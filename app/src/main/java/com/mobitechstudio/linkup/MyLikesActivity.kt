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
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.action_bar_layout.view.*
import kotlinx.android.synthetic.main.activity_my_likes.*
import kotlinx.android.synthetic.main.chat_list_item.view.*
import org.json.JSONArray

class MyLikesActivity : AppCompatActivity() {
    lateinit var tinyDB: TinyDB
    private var mAdView: AdView?=null
    var usersList = mutableListOf<Chat>()
    var hasMoreChats = true
    var currentSize = 0
    var scrollPosition = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_likes)
        //set layout manager for Recylerview
        chatsListView.setHasFixedSize(true)

        chatsListView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        tinyDB = TinyDB(this)
        customBar()
        initAds()
        loadLikes(0, false)
        swipeRefreshChats.setOnRefreshListener {
            loadLikes(0, false)
        }

        preventScreenShot()
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
        view.tvTitle.text = getString(R.string.my_likes)
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
            mAdView = adViewChats
            val adRequest = AdRequest
                .Builder()
                .addTestDevice("28480BE4C8AF9DAA06B7514F14DE6DDA")
                .addTestDevice("99C784B0B9C5ED479BFF3B1557A6309F")
                .build()
            mAdView!!.loadAd(adRequest)

            mAdView!!.adListener = object : AdListener() {
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


    inner class LikerAdapter: androidx.recyclerview.widget.RecyclerView.Adapter<LikerAdapter.MessageViewHolder>(){

        inner class MessageViewHolder(val card: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(card)

        //specify the contents for the shown items/habits
        override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
            val chat = usersList[position]
            val chatId = chat.chatId
            val userName = chat.userName
            val profilePic = chat.profilePic
            val theUserId = chat.theUserId
            val lastUpdate = chat.lastUpdate
            holder.card.tvUserName.text = userName
            holder.card.tvChatDate.text = lastUpdate
            //load image
            val height = 50
            val width = 50

            Picasso.get()
                .load(Constants.baseUrl+"images/userimages/"+profilePic)
                .placeholder(R.drawable.progress_animation)
                .resize(width, height)
                .error(R.mipmap.paired_circle_round)
                .centerCrop(Gravity.CENTER)
                .transform(CircleTransform())
                .into(holder.card.imgProfilePic)

            //handle view user profile
            holder.card.setOnClickListener {
                viewUser(theUserId)
            }


            //load more videos when scrolled to bottom
            if((position >= itemCount - 1 && itemCount >= 20)){
                if(hasMoreChats) {
                    loadLikes(chatId, true)
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
    private fun viewUser(userId: Int){
        tinyDB.putInt("currentUserId", userId)
        val intent = Intent(this, ViewUserProfileActivity::class.java)
        startActivity(intent)
    }
    private fun loadLikes(id: Int, loadMore: Boolean)
    {
        setRefreshingSwipeTrue()
        if (usersList.size>0){
            if(!loadMore){
                usersList.clear()
            }
        }
        //send info to server
        val url = Constants.baseUrl + "index.php/app/usersLikesMe"
        //start sending request
        val queue = Volley.newRequestQueue(this)
        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { response ->
                //process result here
                if(loadMore){ //get current list before loading more
                    currentSize = LikerAdapter().itemCount
                    val myLayoutManager: androidx.recyclerview.widget.LinearLayoutManager = chatsListView.layoutManager as androidx.recyclerview.widget.LinearLayoutManager
                    scrollPosition = myLayoutManager.findLastCompletelyVisibleItemPosition()
                }
                try {
                    val json = JSONArray(response)
                    val size = json.length()
                    if (size > 0){

                        for(i in 0 until size){
                            val chat = json.getJSONObject(i)
                            val likeId = chat.getInt("likeId")
                            val userName = chat.getString("userName")
                            val profilePic = chat.getString("profilePic")
                            val theUserId = chat.getInt("theUserId")
                            val lastUpdate = chat.getString("lastUpdate")
                            val locLat = chat.getDouble("locLat")
                            val locLon = chat.getDouble("locLon")
                            val locName = chat.getString("locName")

                            usersList.add(Chat(
                                likeId,
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
                            chatsListView.adapter = LikerAdapter()
                        }
                        if(loadMore) //loading more
                        {
                            LikerAdapter().notifyDataSetChanged()
                            chatsListView.scrollToPosition(scrollPosition)
                        }


                    }
                    else
                    {
                        toastMsg("No Likes Found")
                        hasMoreChats = false
                    }
                    setRefreshingSwipeFalse()

                }catch (ex:Exception){
                    toastMsg("Error loading Who Likes You, try again")
                    setRefreshingSwipeFalse()
                }


            },
            Response.ErrorListener { e ->
                toastMsg("Try to Reload again")
                setRefreshingSwipeFalse()
            }) {
            override fun getParams(): Map<String, String?> {
                return mapOf(
                    "likeId" to id.toString(),
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



    private fun toastMsg(msg: String){
        Toast.makeText(this@MyLikesActivity, msg, Toast.LENGTH_SHORT).show()
    }

}
