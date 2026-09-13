package com.example.data.remote.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RecommendTagResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String,
    @Json(name = "data") val data: RecommendTagData?
)

@JsonClass(generateAdapter = true)
data class RecommendTagData(
    @Json(name = "tags") val tags: List<HomeTagItem>
)

@JsonClass(generateAdapter = true)
data class HomeTagItem(
    @Json(name = "tagName") val tagName: String,
    @Json(name = "tagDisplayName") val tagDisplayName: String,
    @Json(name = "order") val order: Int,
    @Json(name = "subTags") val subTags: List<String> = emptyList(),
    @Json(name = "subTagDetails") val subTagDetails: List<SubTagDetail> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SubTagDetail(
    @Json(name = "tagName") val tagName: String,
    @Json(name = "tagDisplayName") val tagDisplayName: String,
    @Json(name = "tagType") val tagType: String? = null,
    @Json(name = "opId") val opId: String? = null
)

@JsonClass(generateAdapter = true)
data class RecommendMediaResponse(
    @Json(name = "code") val code: Int = 0,
    @Json(name = "message") val message: String = "",
    @Json(name = "data") val data: RecommendMediaData? = null
)

@JsonClass(generateAdapter = true)
data class RecommendMediaData(
    @Json(name = "movieItems") val movieItems: List<LookrMovieItem>? = null,
    @Json(name = "pager") val pager: LookrPager? = null
)

@JsonClass(generateAdapter = true)
data class LookrMovieItem(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String? = null,
    @Json(name = "describe") val describe: String? = null,
    @Json(name = "cover") val cover: String? = null,
    @Json(name = "itemUrl") val itemUrl: String? = null,
    @Json(name = "classification") val classification: String? = null,
    @Json(name = "updateTime") val updateTime: String? = null,
    @Json(name = "tag") val tag: String? = null,
    @Json(name = "subjectType") val subjectType: String? = null,
    @Json(name = "ops") val ops: String? = null,
    @Json(name = "thumbnail") val thumbnail: String? = null
)

@JsonClass(generateAdapter = true)
data class LookrPager(
    @Json(name = "hasMore") val hasMore: Boolean? = false,
    @Json(name = "nextPage") val nextPage: String? = null,
    @Json(name = "page") val page: String? = "1",
    @Json(name = "perPage") val perPage: Int? = 10,
    @Json(name = "totalCount") val totalCount: Int? = 0
)
