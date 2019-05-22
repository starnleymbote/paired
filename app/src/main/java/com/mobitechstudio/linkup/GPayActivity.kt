package com.mobitechstudio.linkup

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.appcompat.app.ActionBar
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import android.view.Menu
import android.widget.Toast
import com.android.billingclient.api.*
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import kotlinx.android.synthetic.main.activity_gpay.*
import org.json.JSONArray

/*
* handles Google payments
* */
class GPayActivity : AppCompatActivity(), PurchasesUpdatedListener {
    lateinit var tinyDB: TinyDB
    //gpay
    lateinit private var billingClient: BillingClient
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tinyDB = TinyDB(this)
        customBar()
        setContentView(R.layout.activity_gpay)
        getToken()
    }

    private fun getToken(){
        showProgress()

        val accountType = tinyDB.getString("accountType")
        val topUpType = tinyDB.getString("topUpType")

        /*make sure these tokens are registered in Amazon In App Items and PlayStore In App Products(managed)
        * To edit token values assigned to user when purchased use Admin Control Panel
        * */
        if(topUpType=="Normal" && accountType=="VIP") {
            tinyDB.putString("payToken",  Constants.vipToken)
            gPay( Constants.vipToken)
        }
        else if(topUpType=="Normal" && accountType !="VIP"){
            tinyDB.putString("payToken",  Constants.premiumToken)
            gPay( Constants.premiumToken)
        }
        else if(topUpType=="Call"){
            tinyDB.putString("payToken", Constants.callToken)
            gPay(Constants.callToken)
        }
        else if(topUpType=="Classic"){
            tinyDB.putString("payToken", Constants.classicToken)
            gPay(Constants.classicToken)
        }
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
    fun sendGPay(view: View){
        getToken()
    }

    //gPay methods
    private fun gPay(token: String){
        //google payment here initialize here
        billingClient = BillingClient.newBuilder(this).setListener(this).build()
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(@BillingClient.BillingResponse billingResponseCode: Int) {
                hideProgress()
                if (billingResponseCode == BillingClient.BillingResponse.OK) {
                    // The billing client is ready. You can query purchases here.
                    tinyDB.putString("payToken", token)
                    val flowParams = BillingFlowParams.newBuilder()
                        .setSku(token)
                        .setType(BillingClient.SkuType.INAPP) // SkuType.SUB for subscription
                        .build()
                    val responseCode = billingClient.launchBillingFlow(this@GPayActivity, flowParams)

                }
                else
                {
                    toastMsg("Failed to process payment")
                    showButton() //sho retry button
                }
            }
            override fun onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.
                //toastMsg("Billing client disconnected")
                toastMsg("Failed to Connect to Google Play")
                showButton()
                hideProgress()
            }
        })
    }

    override fun onPurchasesUpdated(responseCode: Int, purchases: MutableList<Purchase>?) =
        if (responseCode == BillingClient.BillingResponse.OK && purchases != null) {
            for ((count, purchase) in purchases.withIndex()) {
                if(count==0) {
                    billingClient.consumeAsync(tinyDB.getString("payToken")) { rCode, _ ->
                        if (rCode == BillingClient.BillingResponse.OK) {
                            sendPaymentToServer()
                        }
                        else{
                            toastMsg("Failed to process payment. Try Again")
                            showButton()
                        }
                    }
                }
            }
        } else if (responseCode == BillingClient.BillingResponse.USER_CANCELED) {
            // Handle an error caused by a user cancelling the purchase flow.
            toastMsg("You cancelled payment")
            showButton()
        } else if (responseCode == BillingClient.BillingResponse.ITEM_ALREADY_OWNED) {
            billingClient.consumeAsync(tinyDB.getString("payToken")) { rCode, _ ->
                if (rCode == BillingClient.BillingResponse.OK) {
                    showButton()
                    toastMsg("Touch Button To Try Again")
                }
                else{
                    toastMsg("Failed to process payments, try again")
                    showButton()
                }
            }

        }
        else
        {
            // Handle any other error codes.
            toastMsg("Failed to process payments, try again")
            showButton()
        }

    private fun sendPaymentToServer() {
        //send info to server
        toastMsg("Wait finalizing payment....")
        val url = Constants.baseUrl + "index.php/payment/savePayments"
        //start sending request
        val queue = Volley.newRequestQueue(this)
        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { response ->
                //println("RESULT_PAYMENT $response")

                try {
                    val json = JSONArray(response)
                    val size = json.length()
                    if (size > 0) {
                        for (i in 0 until size) {

                            val user = json.getJSONObject(i)
                            val status = user.getString("status")
                            val alert = user.getString("message")
                            val premiumExpired = user.getString("premiumExpired")
                            val premiumExpireDate = user.getString("premiumExpireDate")
                            val sponsored = user.getString("sponsored")
                            val callCredits = user.getDouble("callCredits")
                            when (status) {
                                "SUCCESS" -> {
                                    tinyDB.putString("premiumExpired", premiumExpired)
                                    tinyDB.putString("premiumExpireDate", premiumExpireDate)
                                    tinyDB.putString("sponsored", sponsored)
                                    tinyDB.putDouble("callCredits", callCredits)
                                   // toastMsg("Paid Successfully")
                                    //finish()

                                }
                                "NOT_FOUND" -> {
                                    toastMsg("Try again please")
                                    showButton()
                                }
                                else -> {
                                    toastMsg("Failed to finalize payment") // cancel payment
                                    showButton()
                                }
                            }

                        }

                    } else {
                        toastMsg("Failed to process payment, try again")
                        showButton()

                    }


                } catch (ex: Exception) {
                    //println("ERROR: ${ex.message}")
                    toastMsg("Failed to process payment, try again")
                }
            },
            Response.ErrorListener { e ->
                //toastMsg("Try again")
            }) {
            override fun getParams(): Map<String, String?> {
                return mapOf(
                    "token" to tinyDB.getString("payToken"),
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
    private fun hideButton(){
        btnGpay.visibility = View.GONE
        tvRetryTitle.visibility = View.GONE
        btnGpay.isEnabled = false
    }
    private fun showButton(){
        btnGpay.visibility = View.VISIBLE
        tvRetryTitle.visibility = View.VISIBLE
        btnGpay.isEnabled = true
    }
    private fun showProgress(){
        progressBar.visibility = View.VISIBLE
    }
    private fun hideProgress(){
        progressBar.visibility = View.GONE
    }


    private fun toastMsg(msg: String) {
        Toast.makeText(this@GPayActivity, msg, Toast.LENGTH_LONG).show()
    }

    override fun onBackPressed() {
        confirmGoBack()
    }
    //confirm closing activity
    private fun confirmGoBack(){
        val builder = AlertDialog.Builder(this@GPayActivity)
        builder.setTitle("Confirm")
        builder.setMessage("Are you sure you want to close?")
        builder.setCancelable(false)
        builder.setIcon(R.mipmap.paired_circle_round)
        builder.setPositiveButton("Yes") { _, _ ->
            finish()
        }
        builder.setNegativeButton("No") { dialog, _ ->
            dialog.cancel()
        }
        val alert = builder.create()
        alert.show()
    }
}
