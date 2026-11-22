package com.example.task_3

data class Comment(
    var id: String = "",
    val blogId: String = "",
    val author: String = "Anonymous",
    val authorId: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
