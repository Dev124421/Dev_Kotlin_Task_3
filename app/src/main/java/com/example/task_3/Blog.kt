package com.example.task_3

data class Blog(
    var id: String = "",
    val title: String = "",
    val content: String = "",
    val category: String = "Tech",
    val imageUrl: String = "",
    val author: String = "Anonymous",
    val authorId: String = "",
    val authorPhotoUrl: String = "",
    val likesCount: Long = 0,
    var commentsCount: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val likedBy: List<String> = emptyList(),
    var isLiked: Boolean = false,
    var isSaved: Boolean = false
)
