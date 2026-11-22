package com.example.task_3

data class UserItem(
    val uid: String = "",
    val name: String = "",
    val username: String = "",
    val bio: String = "",
    var followStatus: String = "none", // "none", "pending", "accepted", "removed"
    val photoUrl: String = ""
)
