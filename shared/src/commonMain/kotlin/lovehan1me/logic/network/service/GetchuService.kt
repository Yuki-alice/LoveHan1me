package lovehan1me.logic.network.service

import lovehan1me.GETCHU_BASE_URL
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Parameters
import io.ktor.http.encodeURLPathPart

/**
 * P3：Retrofit interface → Ktor 实现，URL/表单键与旧注解逐一对齐。
 */
class GetchuService(
    private val client: HttpClient,
    private val baseUrl: String = GETCHU_BASE_URL,
) {

    suspend fun getPreviewList(
        genre: String = "anime_dvd",
        gage: String = "adult",
        year: String,
        month: String,
        gc: String = "gc",
    ): HttpResponse = client.get(baseUrl + "all/month_title.html") {
        url {
            parameters.append("genre", genre)
            parameters.append("gage", gage)
            parameters.append("year", year)
            parameters.append("month", month)
            parameters.append("gc", gc)
        }
    }

    suspend fun getPreviewDetail(
        id: String,
        gc: String = "gc",
    ): HttpResponse = client.get(baseUrl + "item/" + id.encodeURLPathPart() + "/") {
        url { parameters.append("gc", gc) }
    }

    suspend fun getSeriesItems(
        productIdArray: String = "",
        parentIdArray: String,
        genre: String = "anime_dvd",
        subGenreArray: String = "",
        naSubGenreArray: String = "",
        subGenrePerfectMatching: String = "",
        brandIdArray: String = "",
        age: String = "",
        stockFlag: String = "",
        sortCondition: String = "release_date",
        sortOrder: String = "asc",
        limitCount: String = "30",
        limitCountLower: String = "1",
        imageExist: String = "",
        startDate: String = "",
        endDate: String = "",
        noveltyFlag: String = "",
        templateHtml: String = "item-series/item-series.html",
        paging: String = "",
        pageSize: String = "",
        javascriptId: String = "",
        searchWord: String = "",
        limitless: String = "1",
        lowerLimit: String = "",
        upperLimit: String = "",
        imageSize: String = "s",
        addQuery: String = "",
    ): HttpResponse {
        val form = Parameters.build {
            append("product_id_array", productIdArray)
            append("parent_id_array", parentIdArray)
            append("genre", genre)
            append("sub_genre_array", subGenreArray)
            append("NA_sub_genre_array", naSubGenreArray)
            append("sub_genre_perfect_matching", subGenrePerfectMatching)
            append("brand_id_array", brandIdArray)
            append("age", age)
            append("stock_flag", stockFlag)
            append("sort_condition", sortCondition)
            append("sort_order", sortOrder)
            append("limit_count", limitCount)
            append("limit_count_lower", limitCountLower)
            append("image_exist", imageExist)
            append("start_date", startDate)
            append("end_date", endDate)
            append("novelty_flag", noveltyFlag)
            append("template_html", templateHtml)
            append("paging", paging)
            append("page_size", pageSize)
            append("javascript_id", javascriptId)
            append("search_word", searchWord)
            append("limitless", limitless)
            append("lower_limit", lowerLimit)
            append("upper_limit", upperLimit)
            append("image_size", imageSize)
            append("add_query", addQuery)
        }
        return client.submitForm(baseUrl + "util/GetchuSearch/GetchuSearchAjax.php", form)
    }
}
