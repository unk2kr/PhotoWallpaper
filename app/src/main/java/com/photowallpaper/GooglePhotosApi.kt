package com.photowallpaper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Работа с Google Photos Library API.
 * https://developers.google.com/photos/library/reference/rest
 */
class GooglePhotosApi {

    private val client = OkHttpClient()

    companion object {
        private const val BASE_URL = "https://photoslibrary.googleapis.com/v1"
    }

    data class Album(
        val id: String,
        val title: String,
        val coverPhotoUrl: String?,
        val mediaItemsCount: Int
    )

    data class MediaItem(
        val id: String,
        val baseUrl: String,
        val width: Int,
        val height: Int,
        val mimeType: String
    ) {
        fun getSizedUrl(maxWidth: Int, maxHeight: Int): String {
            return "$baseUrl=w$maxWidth-h$maxHeight"
        }
    }

    suspend fun getAlbums(accessToken: String): Result<List<Album>> =
        withContext(Dispatchers.IO) {
            try {
                val albums = mutableListOf<Album>()
                var pageToken: String? = null
                do {
                    val url = "$BASE_URL/albums?pageSize=50" +
                        (if (pageToken != null) "&pageToken=$pageToken" else "")
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer $accessToken")
                        .build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string()
                    if (!response.isSuccessful || body == null) {
                        return@withContext Result.failure(Exception("API ${response.code}: $body"))
                    }
                    val json = JSONObject(body)
                    val arr = json.optJSONArray("albums")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val a = arr.getJSONObject(i)
                            albums.add(Album(
                                id = a.getString("id"),
                                title = a.getString("title"),
                                coverPhotoUrl = a.optString("coverPhotoBaseUrl", null),
                                mediaItemsCount = a.optInt("mediaItemsCount", 0)
                            ))
                        }
                    }
                    pageToken = json.optString("nextPageToken", null)
                } while (pageToken != null)
                Result.success(albums)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getMediaItems(accessToken: String, albumId: String): Result<List<MediaItem>> =
        withContext(Dispatchers.IO) {
            try {
                val items = mutableListOf<MediaItem>()
                var pageToken: String? = null
                do {
                    val jsonBody = JSONObject().apply {
                        put("albumId", albumId)
                        put("pageSize", 100)
                        if (pageToken != null) put("pageToken", pageToken)
                    }
                    val reqBody = jsonBody.toString()
                        .toRequestBody("application/json".toMediaType())
                    val request = Request.Builder()
                        .url("$BASE_URL/mediaItems:search")
                        .addHeader("Authorization", "Bearer $accessToken")
                        .post(reqBody).build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string()
                    if (!response.isSuccessful || body == null) {
                        return@withContext Result.failure(Exception("API ${response.code}: $body"))
                    }
                    val json = JSONObject(body)
                    val arr = json.optJSONArray("mediaItems")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val item = arr.getJSONObject(i)
                            val mime = item.optString("mimeType", "")
                            if (mime.startsWith("image/")) {
                                val meta = item.optJSONObject("mediaMetadata")
                                items.add(MediaItem(
                                    id = item.getString("id"),
                                    baseUrl = item.getString("baseUrl"),
                                    width = meta?.optInt("width", 0) ?: 0,
                                    height = meta?.optInt("height", 0) ?: 0,
                                    mimeType = mime
                                ))
                            }
                        }
                    }
                    pageToken = json.optString("nextPageToken", null)
                } while (pageToken != null)
                Result.success(items)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun downloadImage(imageUrl: String, targetFile: File): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(imageUrl).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Download ${response.code}"))
                }
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(targetFile).use { output -> input.copyTo(output) }
                }
                Result.success(targetFile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}