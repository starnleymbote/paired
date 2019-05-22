package com.mobitechstudio.linkup

/**
 * Chat History User Object Structure
 *
 * */
class  Chat(
    var chatId: Int,
    var userName: String,
    var profilePic: String,
    var theUserId: Int,
    var lastUpdate: String,
    var locLat: Double,
    var locLon: Double,
    var locName: String
)