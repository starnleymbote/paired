package com.mobitechstudio.linkup
//message retrieved from firebase object structure
class MessageObject(
    var fbKey:String ? ="",
    var messageStatus:String ? ="",
    var messageDate: Long? =0,
    var senderId: Int? =0,
    var messageBody: String? =""
)