package com.mobitechstudio.linkup



import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent

class SplashActivity : AppCompatActivity() {
    lateinit var tinyDB: TinyDB
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tinyDB = TinyDB(this)

            if (intent.extras != null) {//checking if activity was started by clicking notification in phone notification bar
                var notificationTitle = ""
                var notificationBody = ""
                for (key in intent.extras!!.keySet()) {
                    if (key == "title") {
                        notificationTitle = intent.extras!!.get(key).toString() // value will represent your message title... Enjoy It

                    }
                    if (key == "body") {
                        notificationBody = intent.extras!!.get(key).toString() // value will represent your message body... Enjoy It

                    }
                }

                if (notificationBody != "" && notificationTitle != "") {
                    sendTheNotification(notificationTitle, notificationBody)
                } else {
                    checkLoggedIn()
                }

            } else {
                checkLoggedIn()
            }


    }


    private fun checkLoggedIn(){
        val isLoggedIn = tinyDB.getBoolean("isLoggedIn")
        if(isLoggedIn) {
            val intent = Intent(this@SplashActivity, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
        else{
            val intent = Intent(this@SplashActivity, RegisterActivity::class.java)
            startActivity(intent)
            finish()
        }
    }


    private fun sendTheNotification(title: String?, body: String?){
        //store notifications in database here
        when {
            title!!.contains("NEW CHAT") -> {
                tinyDB.putString("sentOrReceived", "Received Chats")
                val intent = Intent(this, MyChatsActivity::class.java)
                startActivity(intent)
                finish()
            }
            title.contains("NEW LIKE") -> {
                val intent = Intent(this, MyLikesActivity::class.java)
                startActivity(intent)
                finish()
            }
            title.contains("PROFILE VISITOR") -> {
                val intent = Intent(this, MyViewsActivity::class.java)
                startActivity(intent)
                finish()
            }
            title.contains("MISSED CALL") -> {
                val intent = Intent(this, CallLogsActivity::class.java)
                startActivity(intent)
                finish()
            }
            else -> {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            }
        }

    }


}
