package com.gate.tracker.data.remote

import com.google.gson.annotations.SerializedName

/**
 * Remote Configuration Models
 * These will be fetched from GitHub (or Firebase later)
 */

data class RemoteConfig(
    @SerializedName("dailyQuestion")
    val dailyQuestion: DailyQuestion?,
    
    @SerializedName("appUpdate")
    val appUpdate: AppUpdateInfo?
)

data class DailyQuestion(
    @SerializedName("question")
    val question: String,
    
    @SerializedName("options")
    val options: List<String>,
    
    @SerializedName("correctAnswer")
    val correctAnswer: Int,
    
    @SerializedName("explanation")
    val explanation: String,
    
    @SerializedName("category")
    val category: String = "General",
    
    @SerializedName("difficulty")
    val difficulty: String = "Medium"
)

data class AppUpdateInfo(
    @SerializedName("latestVersion")
    val latestVersion: String,
    
    @SerializedName("currentVersionCode")
    val currentVersionCode: Int,
    
    @SerializedName("updateMessage")
    val updateMessage: String,
    
    @SerializedName("isForceUpdate")
    val isForceUpdate: Boolean = false,
    
    @SerializedName("downloadUrl")
    val downloadUrl: String? = null
)
