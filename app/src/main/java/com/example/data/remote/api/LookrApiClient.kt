package com.example.data.remote.api

import com.example.data.remote.model.HomeTagItem
import com.example.data.remote.model.RecommendTagData
import com.example.data.remote.model.RecommendTagResponse
import com.example.data.remote.model.SubTagDetail
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.GzipSource
import okio.buffer
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object LookrApiClient {

    private const val BASE_URL = "https://api.lookr.cloud/"

    private const val HEADER_CLIENT_INFO =
        """{"package_name":"com.wecloud.lookr","version_name":"3.0.16.0706.03","version_code":316,"os":"android","os_version":"14","install_ch":"google-play","device_id":"45fc9f58647307f975f5c2e70048a08e","install_store":"ps","gaid":"ad74fab5dc9bf1f9","brand":"Infinix","model":"Infinix X6711","system_language":"en","net":"NETWORK_WIFI","region":"IN","timezone":"Asia/Kolkata","sp_code":"405872"}"""
    private const val HEADER_AUTH =
        "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjU2NTI1NDEwNzg2NjMyMTg4MDAsImV4cCI6MTc5NzA0OTI1NiwiaWF0IjoxNzg5MjcyOTU2fQ.9lDW-RX6eRIdW0GYGIn-Q9HvHRASck7WC8SlTJC0yE0"
    private const val HEADER_SIGNATURE = "1789275802910|2|"

    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val headerInterceptor = Interceptor { chain ->
        val original = chain.request()
        val signature = HEADER_SIGNATURE.ifBlank { "${System.currentTimeMillis()}|2|" }
        val requestWithHeaders = original.newBuilder()
            .header("x-client-info", HEADER_CLIENT_INFO)
            .header("x-client-status", "0")
            .header("x-tr-signature", signature)
            .header("authorization", HEADER_AUTH)
            .header("user-agent", "okhttp/4.12.0")
            .build()
        chain.proceed(requestWithHeaders)
    }

    private val gzipInterceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        val encoding = response.header("Content-Encoding")
        if (encoding != null && encoding.equals("gzip", ignoreCase = true)) {
            val body = response.body
            if (body != null) {
                val gzipSource = GzipSource(body.source())
                val buffer = gzipSource.buffer()
                val contentType = body.contentType()
                val newBody = buffer.readByteArray().toResponseBody(contentType)
                val strippedHeaders = response.headers.newBuilder()
                    .removeAll("Content-Encoding")
                    .removeAll("Content-Length")
                    .build()
                return@Interceptor response.newBuilder()
                    .headers(strippedHeaders)
                    .body(newBody)
                    .build()
            }
        }
        response
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(headerInterceptor)
        .addInterceptor(gzipInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val apiService: LookrApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
            .build()
            .create(LookrApiService::class.java)
    }

    // High fidelity default fallback dataset exactly matching the API response
    const val FALLBACK_JSON_DATA = """{
  "code": 0,
  "message": "ok",
  "data": {
    "tags": [
      {
        "tagName": "BuzzBox",
        "subTags": [],
        "order": 1,
        "tagDisplayName": "Fun & Memes",
        "subTagDetails": []
      },
      {
        "tagName": "Movies",
        "subTags": ["Popular Movies"],
        "order": 2,
        "tagDisplayName": "Movies",
        "subTagDetails": [
          {"tagName": "Hollywood Movies", "tagType": "RECOMMEND_SUB_TAG_TYPE_OP", "opId": "4674513975244114264", "tagDisplayName": "Hollywood Movies"},
          {"tagName": "Bollywood Movies", "tagType": "RECOMMEND_SUB_TAG_TYPE_OP", "opId": "6017853301595819040", "tagDisplayName": "Bollywood Movies"},
          {"tagName": "For you", "tagType": "RECOMMEND_SUB_TAG_TYPE_RECOMMEND", "opId": "0", "tagDisplayName": "For you"}
        ]
      },
      {
        "tagName": "Series",
        "subTags": ["Trending drama"],
        "order": 3,
        "tagDisplayName": "Series",
        "subTagDetails": [
          {"tagName": "Indian Drama", "tagType": "RECOMMEND_SUB_TAG_TYPE_OP", "opId": "933386079317675808", "tagDisplayName": "Indian Drama"},
          {"tagName": "Western Drama", "tagType": "RECOMMEND_SUB_TAG_TYPE_OP", "opId": "8398270291826328248", "tagDisplayName": "Western Drama"},
          {"tagName": "Korean Drama", "tagType": "RECOMMEND_SUB_TAG_TYPE_OP", "opId": "4736632688541585768", "tagDisplayName": "Korean Drama"},
          {"tagName": "For you", "tagType": "RECOMMEND_SUB_TAG_TYPE_RECOMMEND", "opId": "0", "tagDisplayName": "For you"}
        ]
      },
      {
        "tagName": "Anime",
        "subTags": ["Trending Anime"],
        "order": 4,
        "tagDisplayName": "Anime",
        "subTagDetails": [
          {"tagName": "Trending Anime ", "tagType": "RECOMMEND_SUB_TAG_TYPE_OP", "opId": "9019462595941650792", "tagDisplayName": "Trending Anime "},
          {"tagName": "Burning with Ardour", "tagType": "RECOMMEND_SUB_TAG_TYPE_OP", "opId": "4503689175828416400", "tagDisplayName": "Burning with Ardour"},
          {"tagName": "Adventurers' Saga", "tagType": "RECOMMEND_SUB_TAG_TYPE_OP", "opId": "3134335833860375240", "tagDisplayName": "Adventurers' Saga"},
          {"tagName": "For you", "tagType": "RECOMMEND_SUB_TAG_TYPE_RECOMMEND", "opId": "0", "tagDisplayName": "For you"}
        ]
      },
      {
        "tagName": "Short TV",
        "subTags": [],
        "order": 5,
        "tagDisplayName": "Short TV",
        "subTagDetails": []
      },
      {
        "tagName": "Sports",
        "subTags": ["All", "AFCON", "Champions League", "Premier League", "LaLiga", "Serie A", "NBA", "Bundesliga", "Ligue 1", "Conference League", "Europa League"],
        "order": 6,
        "tagDisplayName": "Sports",
        "subTagDetails": [
          {"tagName": "All", "tagType": "RECOMMEND_SUB_TAG_TYPE_DEFAULT", "opId": "0", "tagDisplayName": "All"},
          {"tagName": "AFCON", "tagType": "RECOMMEND_SUB_TAG_TYPE_DEFAULT", "opId": "0", "tagDisplayName": "AFCON"},
          {"tagName": "Champions League", "tagType": "RECOMMEND_SUB_TAG_TYPE_DEFAULT", "opId": "0", "tagDisplayName": "Champions League"},
          {"tagName": "Premier League", "tagType": "RECOMMEND_SUB_TAG_TYPE_DEFAULT", "opId": "0", "tagDisplayName": "Premier League"},
          {"tagName": "LaLiga", "tagType": "RECOMMEND_SUB_TAG_TYPE_DEFAULT", "opId": "0", "tagDisplayName": "LaLiga"},
          {"tagName": "Serie A", "tagType": "RECOMMEND_SUB_TAG_TYPE_DEFAULT", "opId": "0", "tagDisplayName": "Serie A"},
          {"tagName": "NBA", "tagType": "RECOMMEND_SUB_TAG_TYPE_DEFAULT", "opId": "0", "tagDisplayName": "NBA"},
          {"tagName": "Bundesliga", "tagType": "RECOMMEND_SUB_TAG_TYPE_DEFAULT", "opId": "0", "tagDisplayName": "Bundesliga"},
          {"tagName": "Ligue 1", "tagType": "RECOMMEND_SUB_TAG_TYPE_DEFAULT", "opId": "0", "tagDisplayName": "Ligue 1"},
          {"tagName": "Conference League", "tagType": "RECOMMEND_SUB_TAG_TYPE_DEFAULT", "opId": "0", "tagDisplayName": "Conference League"},
          {"tagName": "Europa League", "tagType": "RECOMMEND_SUB_TAG_TYPE_DEFAULT", "opId": "0", "tagDisplayName": "Europa League"}
        ]
      }
    ]
  }
}"""

    fun parseFallbackTags(): List<HomeTagItem> {
        return try {
            val adapter = moshi.adapter(RecommendTagResponse::class.java)
            val parsed = adapter.fromJson(FALLBACK_JSON_DATA)
            parsed?.data?.tags ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
