package com.mobitechstudio.linkup

class Constants(){
    companion object {
        const val baseUrl = "http://163.172.177.201/" //change this to your hosting/cloud account domain
        //http://pairedmatch.mobitechtechnologies.com/
        //http://163.172.185.224/
        
        /*
        *sinch library for video calls go to sinch.com to create account 
        *and obtain these parameters below for your app
        */
        const val sinchKey              = "e447cd2c-6666-499f-a725-889faf2bd2d3"
        const val sinchSecret           = "AuHjRxMfj0qrBTka20dBEA=="
        const val sinchHost             = "clientapi.sinch.com"

        //got to locationiq.com create account and obtain your key
        const val locationIqKey         = "13b54ffc331729"
        
        //facebook ads
        const val facebookBannerId      = "YOUR_PLACEMENT_ID" //facebook banner ads placement ID, change this to yours
        const val facebookNativeId      = "YOUR_PLACEMENT_ID" //facebook native ads placement ID, change this to yours
        const val facebookInterstitialId = "YOUR_PLACEMENT_ID" //facebook Interstitial ads placement ID, change this to yours

        const val fcmNotificationTopic  = "TM" //this is used to send FCM notification from Admin Panel to all users, so each subscribes to this topic automatically when opens this app
        const val alarmInterval         = 600000 //alarm manager interval time to check likes, profile viewers and new messages statuses from server
        const val NotificationIntervalHours = 6 //Alarm Manager for each user who has not opened app within 6 hours interval

        /*below are in app products, You Must make sure are registered
        * in PlayStore In App products and Amazon In app Items(If you publish app in Amazon app Store)
        * And manage the products values(Days and Minutes) assigned to each product in Admin Control panel
        * */
        const val vipToken      = "9001"
        const val classicToken  = "6001"
        const val callToken     = "8001"
        const val premiumToken  = "301"

    }
}
