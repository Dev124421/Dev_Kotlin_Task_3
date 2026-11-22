package com.example.task_3

data class FollowRequest(
    var id: String = "",
    val fromUid: String = "",
    val fromName: String = "",
    val toUid: String = "",
    val status: String = "pending", // "pending", "accepted", "removed"
    val timestamp: Long = System.currentTimeMillis()
)
