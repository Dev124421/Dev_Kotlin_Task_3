package com.example.task_3

data class Blog(
    var id: String = "",
    val title: String = "",
    val content: String = "",
    val category: String = "Tech",
    val imageUrl: String = "",
    val author: String = "Anonymous",
    val authorId: String = "",
    val likesCount: Long = 0,
    val commentsCount: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    var isLiked: Boolean = false,
    var isSaved: Boolean = false
)
