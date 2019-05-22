package com.mobitechstudio.linkup

//sent message object
class Message(
    var messageStatus:String ? ="",
    var messageDate: Long? =0,
    var senderId: Int? =0,
    var messageBody: String? =""
)