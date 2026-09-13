package com.example.data.remote.api

import com.example.data.remote.model.RecommendMediaResponse
import com.example.data.remote.model.RecommendTagResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface LookrApiService {
    @GET("wefeed-gogo-bff/recommend_tag")
    suspend fun getRecommendTags(
        @Query("lang") lang: String = "en"
    ): RecommendTagResponse

    @GET("wefeed-gogo-bff/recommend")
    suspend fun getRecommend(
        @Query("tag") tag: String,
        @Query("subTag") subTag: String,
        @Query("opId") opId: String = "0",
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): RecommendMediaResponse
}
