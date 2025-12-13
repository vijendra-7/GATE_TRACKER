package com.gate.tracker.data.repository

import android.util.Log
import com.gate.tracker.data.remote.RemoteConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Repository for fetching remote configuration from GitHub
 * 
 * To use this:
 * 1. Push config.json to your GitHub repo
 * 2. Get the Raw URL: https://raw.githubusercontent.com/YOUR_USERNAME/GATE_TRACKER/main/config.json
 * 3. Update CONFIG_URL constant below
 */
class RemoteConfigRepository {
    
    companion object {
        private const val TAG = "RemoteConfigRepository"
        
        // TODO: Update this URL after pushing config.json to GitHub
        // Example: https://raw.githubusercontent.com/Vijendra-chaudhary/GATE_TRACKER/main/config.json
        private const val CONFIG_URL = "https://raw.githubusercontent.com/Vijendra-chaudhary/GATE_TRACKER/main/config.json"
        
        private const val TIMEOUT_MS = 10000
    }
    
    private val gson = Gson()
    
    /**
     * Fetches remote configuration from GitHub
     * @return Result<RemoteConfig> - Success with config or Failure with exception
     */
    suspend fun fetchConfig(): Result<RemoteConfig> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching config from: $CONFIG_URL")
            
            val url = URL(CONFIG_URL)
            val connection = url.openConnection() as HttpURLConnection
            
            try {
                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    setRequestProperty("Accept", "application/json")
                }
                
                val responseCode = connection.responseCode
                Log.d(TAG, "Response code: $responseCode")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d(TAG, "Received JSON: ${jsonString.take(100)}...")
                    
                    val config = gson.fromJson(jsonString, RemoteConfig::class.java)
                    Log.d(TAG, "Config parsed successfully")
                    
                    Result.success(config)
                } else {
                    val errorMessage = "HTTP error: $responseCode"
                    Log.e(TAG, errorMessage)
                    Result.failure(Exception(errorMessage))
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching config", e)
            Result.failure(e)
        }
    }
    
    /**
     * Validates if the config URL is properly set (not the default placeholder)
     */
    fun isConfigUrlSet(): Boolean {
        return CONFIG_URL.contains("raw.githubusercontent.com") && 
               !CONFIG_URL.contains("YOUR_USERNAME")
    }
}
