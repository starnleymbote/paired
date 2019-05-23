package com.mobitechstudio.linkup

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.appcompat.app.ActionBar
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import android.view.Menu
import android.widget.Toast
import com.amazon.device.iap.PurchasingListener
import com.amazon.device.iap.PurchasingService
import com.amazon.device.iap.model.*
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONArray
import java.util.*

/**
 * This class handles Amazon Payments
 * */
class AmazonPayActivity : AppCompatActivity(), PurchasingListener {

    lateinit var tinyDB: TinyDB
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tinyDB = TinyDB(this)
        customBar()
        setContentView(R.layout.activity_amazonpay)

        PurchasingService.registerListener(applicationContext, this)
        try {
            val accountType = tinyDB.getString("accountType")
            val topUpType = tinyDB.getString("topUpType")
            if(topUpType=="Normal" && accountType=="VIP") {
                PurchasingService.purchase(Constants.vipToken)
               tinyDB.putString("payToken", Constants.vipToken)
            }
            else if(topUpType=="Normal" && accountType !="VIP"){
               PurchasingService.purchase(Constants.premiumToken)
                tinyDB.putString("payToken", Constants.premiumToken)
            }
            else if(topUpType=="Call"){
                PurchasingService.purchase(Constants.callToken)
                tinyDB.putString("payToken", Constants.callToken)
            }
            else if(topUpType=="Classic"){
                PurchasingService.purchase(Constants.classicToken)
                tinyDB.putString("payToken", Constants.classicToken)
            }


        }catch (ex: Exception){
            toastMsgShort("Failed to Process Payment, try again")
            debugAlert() //remove this when you want to publish app to app stores
        }

    }
    private fun toastMsgShort(msg: String) {
        Toast.makeText(this@AmazonPayActivity, msg, Toast.LENGTH_SHORT).show()
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



    private fun sendPaymentToServer() {
        //send info to server
        toastMsg("Wait finalizing payment....")
        val url = Constants.baseUrl + "index.php/payment/savePayments"
        //start sending request
        val queue = Volley.newRequestQueue(this)
        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { response ->
                println("RESULT_PAYMENT $response") //for debugging purpose
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
                                    toastMsg("Paid Successfully")
                                    finish()
                                }
                                "NOT_FOUND" -> {
                                    toastMsg("Try again please")
                                    debugAlert() //remove this when you want to publish app to app stores

                                }
                                else -> {
                                    toastMsg("Failed to finalize payment") // cancel payment
                                    debugAlert() //remove this when you want to publish app to app stores
                                }
                            }

                        }

                    } else {
                        toastMsg("Failed to process payment, try again")
                        debugAlert() //remove this when you want to publish app to app stores

                    }


                } catch (ex: Exception) {
                    toastMsg("Failed to process payment, try again")
                    debugAlert() //remove this when you want to publish app to app stores
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

    private fun toastMsg(msg: String) {
        Toast.makeText(this@AmazonPayActivity, msg, Toast.LENGTH_LONG).show()
    }

    override fun onBackPressed() {
        confirmGoBack()
    }
    private fun confirmGoBack(){
        val builder = AlertDialog.Builder(this@AmazonPayActivity)
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

    override fun onResume() {
        super.onResume()
        /*make sure these tokens are registered in Amazon In App Items and PlayStore In App Products(managed)
        * To edit token values assigned to user when purchased use Admin Control Panel
        * */
        val productSkus =  HashSet<String>()
        productSkus.add(Constants.premiumToken) //Premium Token
        productSkus.add(Constants.vipToken) //VIP Token
        productSkus.add(Constants.classicToken) //Classic Sponsor Token
        productSkus.add(Constants.callToken) // Call Credits token
        try {
            PurchasingService.getProductData(productSkus)
            PurchasingService.getPurchaseUpdates(true)
        }catch (ex: Exception){
            toastMsgShort("Failed To Get Purchase Product, try Again")
            debugAlert()
        }


    }

    override fun onProductDataResponse(productDataResponse: ProductDataResponse?) {
        //toastMsg("productDataResponse")
        println("productDataResponse: ${productDataResponse!!.requestStatus}")
    }

    override fun onPurchaseResponse(purchaseResponse: PurchaseResponse?) {
        val status = purchaseResponse!!.requestStatus
        val receipt = purchaseResponse.receipt
        println("purchaseResponse $status")

        when (status.toString()) {
            "SUCCESSFUL" -> {
                //send token to server here and full fill
                PurchasingService.notifyFulfillment(receipt.receiptId,
                    FulfillmentResult.FULFILLED)
                sendPaymentToServer()
            }
            "FAILED" ->{
                toastMsgShort("Failed Process Payment, Try Again")
                debugAlert() //remove this when you want to publish app to app stores
            }
            "NOT_SUPPORTED" -> {
                toastMsgShort("Failed Process Payment, Try Again")
                debugAlert() //remove this when you want to publish app to app stores
            }
            else -> {
                toastMsgShort("Failed Process Payment, Try Again")
                debugAlert() //remove this when you want to publish app to app stores
            }
        }

    }

    override fun onPurchaseUpdatesResponse(purchaseUpdatesResponse: PurchaseUpdatesResponse?) {
        val requestStatus = purchaseUpdatesResponse!!.requestStatus
        when (requestStatus.toString()) {
            "SUCCESSFUL" -> {
                for (receipt in purchaseUpdatesResponse.receipts)
                {
                    PurchasingService.notifyFulfillment(receipt.receiptId,
                        FulfillmentResult.FULFILLED)
                }
                if(purchaseUpdatesResponse.hasMore())
                {
                    PurchasingService.getPurchaseUpdates(true)
                }
            }
            else -> {
                //toastMsg("Failed FulfillmentResult")
            }
        }

    }

    override fun onUserDataResponse(userDataResponse: UserDataResponse?) {
        toastMsg("userDataResponse")
    }

    private fun debugAlert() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage("Make Sure You have Amazon App Tester Installed and Configured Correctly To Test Amazon Pay During Testing")
            .setCancelable(false)
            .setNeutralButton("Ok") { _, _ ->
                finish()
            }
        val alert = builder.create()
        alert.show()
    }

}
