package lovehan1me.logic

import lovehan1me.core.util.LogUtil
import lovehan1me.logic.NetworkRepo.handleException
import lovehan1me.logic.NetworkRepo.throwRequestException
import lovehan1me.data.network.HanimeNetwork
import lovehan1me.core.domain.state.WebsiteState
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

object GetchuNetworkRepo {
    // P4b：EUC-JP 解码改走 expect/actual（jvmMain Charset / iosMain NSString），常量删除
    fun getGetchuPreview(date: String) = websiteIOFlow(
        request = {
            HanimeNetwork.getchuService.getPreviewList(
                year = date.take(4),
                month = date.takeLast(2),
            )
        },
        bodyToString = { it.getchuString() },
        action = { GetchuParser.getchuPreview(it, date) }
    )
    fun getGetchuPreviewDetail(id: String) = websiteIOFlow(
        request = { HanimeNetwork.getchuService.getPreviewDetail(id) },
        bodyToString = { it.bodyAsText() },
    ) { body ->
        val detailState = GetchuParser.getchuPreviewDetail(body, id)
        if (detailState !is WebsiteState.Success) return@websiteIOFlow detailState

        val parentId = body.extractGetchuSeriesParentId() ?: return@websiteIOFlow detailState
        val seriesItems = try {
            val response = HanimeNetwork.getchuService.getSeriesItems(parentIdArray = parentId)
            if (!response.status.isSuccess()) {
                emptyList()
            } else {
                response.getchuString().let(GetchuParser::getchuSeriesItems).orEmpty()
            }
        } catch (_: Throwable) {
            emptyList()
        }
        LogUtil.d(
            "GetchuPreviewParser",
            "series ajax id=$id parentId=$parentId items=${seriesItems.size}"
        )
        if (seriesItems.isEmpty()) {
            detailState
        } else {
            val detail = detailState.info
            val mergedSeriesItems = (detail.seriesItems + seriesItems)
                .distinctBy { it.id }
                .filterNot { it.id == id }
            WebsiteState.Success(
                detail.copy(
                    seriesItems = mergedSeriesItems,
                    relatedItems = mergedSeriesItems,
                )
            )
        }
    }
    private fun <T> websiteIOFlow(
        request: suspend () -> HttpResponse,
        permittedSuccessCode: IntArray? = null,
        bodyToString: suspend (HttpResponse) -> String = { it.bodyAsText() },
        action: suspend (String) -> WebsiteState<T>,
    ) = flow {
        val requestResult = request.invoke()
        val permitted = permittedSuccessCode?.contains(requestResult.status.value) == true
        if ((permitted || requestResult.status.isSuccess())) {
            emit(action.invoke(bodyToString(requestResult)))
        } else {
            requestResult.throwRequestException()
        }
    }.catch { e ->
        emit(WebsiteState.Error(handleException(e)))
    }.flowOn(ioDispatcher)

    private suspend fun HttpResponse.getchuString(): String {
        return bodyAsBytes().decodeEucJp()
    }

    private fun String.extractGetchuSeriesParentId(): String? {
        return Regex("[\"']parent_id_array[\"']\\s*:\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
    }
}
