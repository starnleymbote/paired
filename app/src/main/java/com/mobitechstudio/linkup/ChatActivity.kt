package com.mobitechstudio.linkup

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.appcompat.app.ActionBar
import android.text.util.Linkify
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.vanniktech.emoji.EmojiPopup
import kotlinx.android.synthetic.main.action_bar_layout_chat.view.*
import kotlinx.android.synthetic.main.activity_chat.*
import kotlinx.android.synthetic.main.message_list_layout.view.*
import kotlinx.android.synthetic.main.report_user_layout.view.*
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*

/*
* ChatActivity is for Live Chat between two users, It connects to fire base real time Database
* */
class ChatActivity : AppCompatActivity() {
    //define variables needed to connect to firebase database
    private lateinit var database: DatabaseReference
    private lateinit var toReference: DatabaseReference
    private var myReference: DatabaseReference? = null
    //array list of chat messages
    var messagesList = arrayListOf<MessageObject>()

    lateinit var tinyDB: TinyDB //Simple class to handle Shared Preferences values
      /*
     this variable countSent is used to check how many messages sent in single session in order to notify user on second part when first message is sent using fcm notification
     */
    private var countSent = 0

    lateinit var emojiPopup: EmojiPopup
    private var mAdView: AdView?=null
    var oldChat = true //used to check if it is fresh chat or is chat from history
    val REQUEST_CODE = 111 //request code for audio and camera permissions

    private lateinit var auth: FirebaseAuth //firebase Auth instant variable
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tinyDB = TinyDB(this)
        customBar()
        setContentView(R.layout.activity_chat)
        emojiPopup= EmojiPopup.Builder.fromRootView(rootView).build(editMessageBox)

        signUserToFireBase() //check Authentication to firebase database

        //handle emoji buttom click listener to show and hide emoji keyboard
        btnEmojis.setOnClickListener {
            if(emojiPopup.isShowing){
                emojiPopup.dismiss()
            }
            else{
                emojiPopup.toggle()
            }
        }


        //handle on long click action on message list
        chatsListView.onItemLongClickListener = object: AdapterView.OnItemLongClickListener {
            override fun onItemLongClick(arg0:AdapterView<*>, arg1:View, pos:Int, id:Long):Boolean {
               // toastMsgShort("Long pressed")
                val fbKey = messagesList[pos].fbKey
               // toastMsgShort(fbKey!!)
                confirmDeleteMsg(fbKey, pos)
                return true
            }
        }

        //prevent taking screen shot of activity, remove this if you want to allow chat screen shots
        preventScreenShot()


    }

    private fun signUserToFireBase(){
        // Initialize Fire Base Auth
        auth = FirebaseAuth.getInstance()

        auth.signInAnonymously()
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success
                   // val user = auth.currentUser, use this user if you want to get user id for any purpose
                    database = FirebaseDatabase.getInstance().reference
                    val myReferenceId = "User"+tinyDB.getInt("userId")
                    val toUserId = "User"+tinyDB.getInt("toUserId")
                    toReference = database.child("ChatMessages").child(toUserId).child(myReferenceId)
                    myReference = database.child("ChatMessages").child(myReferenceId).child(toUserId)
                    myReference!!.addChildEventListener(messageListener)
                    initAds()
                    checkChat()

                } else {
                    //If sign in fails, display a message to the user and finish activity
                    toastMsgShort("Authentication failed")
                    finish()
                }

            }
    }




    //confirm delete message dialog, on long press of message item in list
    private fun confirmDeleteMsg(fbKey: String?, pos:Int){
        val builder = AlertDialog.Builder(this@ChatActivity)
        builder.setTitle("Confirm Delete")
        builder.setMessage("Are you sure you want to Delete this message?")
        builder.setCancelable(false)
        builder.setIcon(R.mipmap.paired_icon)
        builder.setPositiveButton("Yes") { dialog, which ->

                try {
                    val currentPosition = chatsListView.lastVisiblePosition

                    myReference!!.child(fbKey!!).removeValue().addOnCompleteListener {
                        if (it.isSuccessful) {
                            //refresh list here
                            messagesList.remove(messagesList[pos])
                            MessageAdapter().notifyDataSetChanged()
                            chatsListView.setSelection(currentPosition)
                        } else {
                            toastMsgShort(getString(R.string.failed_delete))
                        }
                    }
                } catch (ex: Exception) {
                    toastMsgShort(getString(R.string.failed_delete))
                }

        }
        builder.setNegativeButton("No") { dialog, which ->
            dialog.cancel()
        }
        val alert = builder.create()
        alert.show()
    }

    private fun preventScreenShot(){
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    }
    //on destroy of activity remove firebase listener
    override fun onDestroy() {
        if(myReference !=null) {
            myReference!!.removeEventListener(messageListener)
        }
        super.onDestroy()
    }

    /*
  this function loads facebook native ad as popup ad after every 3 messages sent by free user per chat session
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
    //this function sets custom action bar of the app
    private fun customBar(){
        supportActionBar!!.displayOptions = ActionBar.DISPLAY_SHOW_CUSTOM
        supportActionBar!!.setDisplayShowCustomEnabled(true)
        supportActionBar!!.setCustomView(R.layout.action_bar_layout_chat)
        val view = supportActionBar!!.customView
        view.tvTitle.text = tinyDB.getString("toUserName")
        userLastSeen(view.tvLastSeenUser)
        view.imgLogo.setOnClickListener {
            viewUser()
        }
        view.tvTitle.setOnClickListener {
            viewUser()
        }
    }
    //this function takes you to user's profile on call log item click
    private fun viewUser(){
        tinyDB.putInt("currentUserId", tinyDB.getInt("toUserId"))
        val intent = Intent(this, ViewUserProfileActivity::class.java)
        startActivity(intent)
    }
    //link chat_menu.xml file to chat activity
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.chat_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }
    //handle menu item clicks
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        when (id) {
            R.id.action_share -> shareMe() //open app download link share
            R.id.action_map -> { //view user location on map
                val intent = Intent(this, UserMapActivity::class.java)
                startActivity(intent)
            }
            R.id.action_block -> blockUser() //block user and report user to admins
            R.id.action_call -> {
                //initiate video call from chat, check if user has enough call credits
                val callCredits = tinyDB.getDouble("callCredits", 0.0)
                if(callCredits > 0.0) {
                    requestPerms()
                }
                else {
                    confirmRechargeTopUp("Top Up", "You Have 0 Credits For Live talk Consider Topping Up")
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    //confirm top up call credits dialog
    private fun confirmRechargeTopUp(title: String, msg: String){
        val builder = AlertDialog.Builder(this@ChatActivity)
        builder.setTitle(title)
        builder.setMessage(msg)
        builder.setCancelable(false)
        builder.setIcon(R.mipmap.paired_icon)
        builder.setPositiveButton("Top Up") { _, _ ->
            tinyDB.putString("topUpType", "Call")
            goToStorePayments()
        }
        builder.setNeutralButton("Close") { dialog, _ ->
            dialog.cancel()
        }
        val alert = builder.create()
        alert.show()
    }
    //go to payment payment according to where the app was downloaded, if from playstore 'com.android.vending' take user to GPay Activity otherwise to Amazon pay
    private fun goToStorePayments(){
        val pkgManager = this.packageManager
        val installerPackageName = pkgManager.getInstallerPackageName(this.packageName)
        if ("com.android.vending" == installerPackageName)
        {
            val intent = Intent(this@ChatActivity, GPayActivity::class.java)
            startActivity(intent)
        }
        else
        {
            val intent = Intent(this@ChatActivity, AmazonPayActivity::class.java)
            startActivity(intent)
        }
    }

    //block user custom dialog, asks blocking user to enter reason
    private fun blockUser(){
        val dialog = AlertDialog.Builder(this@ChatActivity, android.R.style.Theme_Light_NoTitleBar)
        val view = layoutInflater.inflate(R.layout.report_user_layout, null)
        dialog.setView(view)
        dialog.setCancelable(false)
        val customDialog = dialog.create()
        customDialog.setTitle("Block And Report")
        customDialog.setIcon(R.drawable.blockuser)
        customDialog.show()

        view.btnSendReport.setOnClickListener {
            val reason = view.editTextReason.text.toString()
            if(reason.isNotBlank()){
                if(reason.length >= 16) { // at least 16 characters of reason must be entered
                    sendReportedUser(reason)
                    customDialog.dismiss()
                }
                else{
                    toastMsgShort("Reason must be at least 16 characters")
                }
            }
            else{
                toastMsgShort("Enter Reason")
            }
        }
        view.btnCancelReport.setOnClickListener {
            if(customDialog.isShowing){
                customDialog.dismiss()
            }
        }


    }

    private fun toastMsgShort(msg: String) {
        Toast.makeText(this@ChatActivity, msg, Toast.LENGTH_SHORT).show()
    }

    //sending report to server
    private fun sendReportedUser(reason: String)
    {
        toastMsgShort("Reporting...")
        //send info to server
        val url = Constants.baseUrl + "index.php/app/reportBlockUser"
        //start sending request
        val queue = Volley.newRequestQueue(this)
        val stringRequest = object : StringRequest(
            com.android.volley.Request.Method.POST, url,
            Response.Listener { response ->
                when {
                    response.contains("SUCCESS") -> {
                        //sent notification
                        toastMsgShort("Blocked and Reported Successfully")
                    }
                    response.contains("FAILED") -> {

                        toastMsgShort(getString(R.string.failed_report))
                    }
                    else -> {
                        toastMsgShort(getString(R.string.failed_report))
                    }
                }



            },
            Response.ErrorListener { e ->
                toastMsgShort(getString(R.string.failed_report))

            }) {
            override fun getParams(): Map<String, String?> {
                return mapOf(
                    "reason" to reason,
                    "reportingUser" to tinyDB.getInt("userId").toString(),
                    "reportedUser" to tinyDB.getInt("toUserId").toString()
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
    /*
 this function opens share dialog to share app download from store(play Store or Amazon App store)
 it is your choice where you want to direct your users
  */
    private fun shareMe() {
        val sendIntent = Intent()
        val msg = getString(R.string.share_message)
        sendIntent.action = Intent.ACTION_SEND
        sendIntent.putExtra(Intent.EXTRA_TEXT, msg)
        sendIntent.type = "text/plain"
        startActivity(sendIntent)
    }

    //chat messages listener
    private val messageListener = object : ChildEventListener {
        override fun onCancelled(dataSnapshot: DatabaseError) {
            println("Canceled")
        }

        //message moved function
        override fun onChildMoved(dataSnapshot: DataSnapshot, p1: String?) {
            toastMsgShort("Moved")
        }
        //message updated function, check if user received message and notify sender on current session
        override fun onChildChanged(dataSnapshot: DataSnapshot, p1: String?) {
                val fbKey = dataSnapshot.key
                for(message in messagesList){
                    val msgKey = message.fbKey
                    if(fbKey == msgKey){
                        toastMsgShort("Message received")
                        message.messageStatus = "Received"
                        MessageAdapter().notifyDataSetChanged()
                        chatsListView.setSelection(MessageAdapter().count-1)
                    }
                }

        }

        //on new message received, update messages list
        override fun onChildAdded(dataSnapshot: DataSnapshot, p1: String?) {
            // Get Message object and use the values to update the UI
            val message = dataSnapshot.getValue(Message::class.java)
            try {
                val fbKey = dataSnapshot.key
                val messageStatus = message!!.messageStatus
                val messageDate = message.messageDate
                val senderId = message.senderId
                val messageBody = message.messageBody
                //update received status


                val userId = tinyDB.getInt("userId")
                if(senderId != userId){ //received message update status on sender that the message was received

                    if(messageStatus !="NA"){
                        toReference.child(messageStatus!!).child("messageStatus").setValue("Received") //update sender message to show received message
                    }
                }
                //create MessageObect instance from each message
                messagesList.add(
                    MessageObject(
                        fbKey,
                        messageStatus,
                        messageDate,
                        senderId,
                        messageBody
                    )
                )
                chatsListView.adapter = MessageAdapter()
                chatsListView.setSelection(MessageAdapter().count-1)

            }catch (ex: Exception){
               Log.d("ERROR_MSG", "Error retrieving messages")
            }
        }

        override fun onChildRemoved(dataSnapshot: DataSnapshot) {
            Log.d("MSG_REMOVED", "Message removed")
        }

    }

    //confirm recharge account to get unlimited chats,
    private fun confirmRechargePremium(title: String, msg: String){
        val builder = AlertDialog.Builder(this@ChatActivity)
        builder.setTitle(title)
        builder.setMessage(msg)
        builder.setCancelable(false)
        builder.setIcon(R.mipmap.paired_icon)
        builder.setPositiveButton("Get Unlimited Chats") { _, _ ->
            tinyDB.putString("topUpType", "Normal")
            goToStorePayments()
        }

        builder.setNeutralButton("Close") { dialog, _ ->
            dialog.cancel()
        }
        val alert = builder.create()
        alert.show()
    }


    //process send message when send button clicked
    fun sendMessage(view: View){
        val premiumExpired = tinyDB.getString("premiumExpired")
        val chatLimited = tinyDB.getString("chatLimited") //shared preference which holds chat limit status per day for free user
        val chatLimitDate = tinyDB.getString("chatLimitDate") //shared preference which holds chat limit date per day for free user
        val mySex = tinyDB.getString("sex")
        //checking if user is not premium and has reached maximum of 10 new users chats per day
        if(chatLimited=="Yes" && chatLimitDate==currentDate() && mySex=="Male" && premiumExpired=="Yes" && !oldChat)
        {
            confirmRechargePremium("Maxed Chats", "You Have maxed your Free chats today. You Can Chat With Only 10 new Users In a Day")
        }
        else
        {
            val thisTime = System.currentTimeMillis()
            val currentMessage = editMessageBox.text.toString()
            if (countSent == 0) {
                sendChatRequest()
                countSent++
            }
            else{
                if(countSent %3 == 0){ //load facebook popup ads after each 3 messages sent by free user
                    loadPopAd()
                }
                countSent++
            }
            if (currentMessage.isNotBlank()) {
                editMessageBox.setText("")
                val message = Message(
                    "NA",
                    thisTime,
                    tinyDB.getInt("userId"),
                    currentMessage
                )
                myReference!!.push().setValue(
                    message
                ) { dbError, dbRef ->
                    val messageKey = dbRef.key
                    val message2 = Message(
                        messageKey,
                        thisTime,
                        tinyDB.getInt("userId"),
                        currentMessage
                    )
                    toReference.push().setValue(message2)
                }

            }
       }

    }

    //message adapter handles display of messages
    inner class MessageAdapter() : BaseAdapter() {
        var tinyDB: TinyDB = TinyDB(this@ChatActivity)
        private val inflater: LayoutInflater =
            this@ChatActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        //get total messages count
        override fun getCount(): Int {
            return messagesList.size
        }

        //get message item at specific position in list
        override fun getItem(position: Int): Any {
            return messagesList[position]
        }

        //get message item id
        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        //create message view
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            // Get view for row item
            val rowView = inflater.inflate(R.layout.message_list_layout, parent, false)
            val messageBody = messagesList[position].messageBody
            val sentTime = messagesList[position].messageDate
            val messageStatus = messagesList[position].messageStatus
           val messageDate = millsToDateFormat(sentTime!!)
            val senderId = messagesList[position].senderId
            //linkify phone numbers
            rowView.tvMessageRight.autoLinkMask = Linkify.PHONE_NUMBERS
            rowView.tvMessageLeft.autoLinkMask = Linkify.PHONE_NUMBERS

            if(senderId == tinyDB.getInt("userId")) {
                rowView.tvMessageRight.text = messageBody
                rowView.tvMessageRightTime.text = messageDate
                rowView.leftLayout.visibility = View.GONE
                if(messageStatus=="Received")
                {
                    rowView.imgReceivedStatus.visibility = View.VISIBLE
                    rowView.imgReceivedStatusTwo.visibility = View.VISIBLE
                    rowView.imgSentTick.visibility = View.GONE
                }
                else{
                    rowView.imgReceivedStatus.visibility = View.GONE
                    rowView.imgReceivedStatusTwo.visibility = View.GONE
                    rowView.imgSentTick.visibility = View.VISIBLE
                }
            }
            else if(senderId == tinyDB.getInt("toUserId")){
                rowView.tvMessageLeft.text = messageBody
                rowView.tvMessageLeftTime.text = messageDate
                rowView.rightLayout.visibility = View.GONE
            }
            return rowView
        }
    }

    //change milliseconds to date and time format
    fun millsToDateFormat(mills: Long): String {
        val date = Date(mills)
        val formatter = SimpleDateFormat("dd-MM-yyyy HH:mm:ss")
        return formatter.format(date) //note that it will give you the time in GMT+0
    }

    //notify user that has received chat, when current user starts session and sends first message
    private fun sendChatRequest()
    {
        //send info to server
        val url = Constants.baseUrl + "index.php/app/userSentChat"
        //start sending request
        val queue = Volley.newRequestQueue(this)
        val stringRequest = object : StringRequest(
            com.android.volley.Request.Method.POST, url,
            Response.Listener { response ->
                when {
                    response.contains("DONE") -> {

                    }
                    response.contains("CHAT_LIMIT") -> {
                        val premiumExpired = tinyDB.getString("premiumExpired")
                        if(premiumExpired=="Yes") {
                            tinyDB.putString("chatLimited", "Yes")
                            tinyDB.putString("chatLimitDate", currentDate())
                        }
                        else{
                            tinyDB.putString("chat_limited", "No")
                        }

                    }
                    response.contains("NOT_DONE") -> {
                        //handle if error sending notification to server

                    }
                    else -> {

                        //handle if error sending notification to server
                    }
                }



            },
            Response.ErrorListener { e ->
                //handle if error sending notification to server

            }) {
            override fun getParams(): Map<String, String?> {
                return mapOf(
                    "name" to tinyDB.getString("name"),
                    "userId" to tinyDB.getInt("userId").toString(),
                    "toUserId" to tinyDB.getInt("toUserId").toString()
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

    //check if chat is new or from chat history, to handle new chat limit per day
    private fun checkChat()
    {
        //send info to server
        val url = Constants.baseUrl + "index.php/app/checkChat"
        //start sending request
        val queue = Volley.newRequestQueue(this)
        val stringRequest = object : StringRequest(
            com.android.volley.Request.Method.POST, url,
            Response.Listener { response ->
                oldChat = when {
                    response.contains("NEW_CHAT") -> {
                        false
                    }
                    response.contains("OLD_CHAT") -> {
                        true
                    }
                    else -> {
                        true
                    }
                }

            },
            Response.ErrorListener { e ->
               // toastMsg(getString(R.string.error_loading_requests))

            }) {
            override fun getParams(): Map<String, String?> {
                return mapOf(
                    "name" to tinyDB.getString("name"),
                    "userId" to tinyDB.getInt("userId").toString(),
                    "toUserId" to tinyDB.getInt("toUserId").toString()
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

    //get current date to handle chat limit per day for free user
    private fun currentDate(): String{
        val c = Calendar.getInstance()
        val df = SimpleDateFormat("dd-MM-yyyy")
        return df.format(c.time)
    }

    //get user last seen time from server
    private fun userLastSeen(tvName: TextView)
    {
        //send info to server
        val url = Constants.baseUrl + "index.php/app/lastSeen"
        //start sending request
        val queue = Volley.newRequestQueue(this)
        val stringRequest = object : StringRequest(
            com.android.volley.Request.Method.POST, url,
            Response.Listener { response ->

               if(response !="NA"){
                   tvName.text = "Last seen: $response"
               }

            },
            Response.ErrorListener { e ->
               // toastMsg(getString(R.string.error_loading_requests))

            }) {
            override fun getParams(): Map<String, String?> {
                return mapOf(
                    "userId" to tinyDB.getInt("toUserId").toString()
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

    //this function is used to check if there is internet connection using ping command
    fun isConnected():Boolean {
        val cm = this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetworkInfo
        return activeNetwork != null
    }
    //init admob banner ad on top of chat
  private fun initAds() {
        if (isConnected()) {
            //initialize ads
            MobileAds.initialize(this, getString(R.string.appId))
            mAdView = findViewById(R.id.adViewChat)
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


    private fun goCall(){
        val intent = Intent(this, VideoCallActivity::class.java)
        startActivity(intent)
    }
    //this function requests audio and camera permssions
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
            else
            {
                goCall()
            }

        }
        else{
            goCall()
        }
    }


    //handle permission request reuslt
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_CODE -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    goCall()

                } else {
                    toastMsgShort("Allow NDating To Access These Permissions. Calling Will Not Work")
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


}
