package com.CXXX

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.delay
import org.jsoup.nodes.Element

class Perverzija : MainAPI() {
    override var name = "category-Perverzija"
    override var mainUrl = "https://tube.perverzija.com/"
    override val supportedTypes = setOf(TvType.NSFW)

    override val hasDownloadSupport = true
    override val hasMainPage = true

    private val cfInterceptor = CloudflareKiller()

    override val mainPage = mainPageOf(
        "$mainUrl/page/%d/" to "Home",
        "$mainUrl/tag/anal/page/%d/?orderby=date" to "Anal",
        "$mainUrl/tag/big-ass/page/%d/?orderby=date" to "Big Ass",
        "$mainUrl/tag/bubble-butt/page/%d/?orderby=date" to "Bubble Butt",
        "$mainUrl/tag/big-dick/page/%d/?orderby=date" to "Big Dick",
        "$mainUrl/tag/caught/page/%d/?orderby=date" to "Caught",
        "$mainUrl/tag/cheating/page/%d/?orderby=date" to "Cheating",
        "$mainUrl/tag/cheating-wife/page/%d/?orderby=date" to "Cheating Wife",
        "$mainUrl/tag/double-penetration/page/%d/?orderby=date" to "Double DP",
        "$mainUrl/tag/doctor/page/%d/?orderby=date" to "Doctor",
        "$mainUrl/tag/family/page/%d/?orderby=date" to "Family",
        "$mainUrl/tag/family-taboo/page/%d/?orderby=date" to "Family Taboo",
        "$mainUrl/tag/foursome/page/%d/?orderby=date" to "Foursome",
        "$mainUrl/tag/hotwife/page/%d/?orderby=date" to "Hotwife",
        "$mainUrl/tag/latina/page/%d/?orderby=date" to "Latina",
        "$mainUrl/tag/massage/page/%d/?orderby=date" to "Massage",
        "$mainUrl/tag/nurse/page/%d/?orderby=date" to "Nurse",
        "$mainUrl/tag/office/page/%d/?orderby=date" to "Office",
        "$mainUrl/tag/old-and-young/page/%d/?orderby=date" to "Old and Young",
        "$mainUrl/tag/outdoor/page/%d/?orderby=date" to "Outdoor",
        "$mainUrl/tag/orgy/page/%d/?orderby=date" to "Orgy",
        "$mainUrl/tag/parody/page/%d/?orderby=date" to "Parody",
        "$mainUrl/tag/police/page/%d/?orderby=date" to "Police",
        "$mainUrl/tag/squirt/page/%d/?orderby=date" to "Squirt",
        "$mainUrl/tag/shower/page/%d/?orderby=date" to "Shower",
        "$mainUrl/tag/sneaky/page/%d/?orderby=date" to "Sneaky",
        "$mainUrl/tag/sister/page/%d/?orderby=date" to "Sister",
        "$mainUrl/tag/threesome/page/%d/?orderby=date" to "Threesome",
        "$mainUrl/tag/tushy/page/%d/?orderby=date" to "Tushy",
        "$mainUrl/tag/teacher/page/%d/?orderby=date" to "Teacher",
        "$mainUrl/tag/wife/page/%d/?orderby=date" to "Wife",
        "$mainUrl/tag/waitress/page/%d/?orderby=date" to "Waitress",
        "$mainUrl/tag/wedding/page/%d/?orderby=date" to "Wedding",
        "$mainUrl/tag/yoga/page/%d/?orderby=date" to "yoga",
        "$mainUrl/tag/workout/page/%d/?orderby=date" to "workout",
        "$mainUrl/tag/sport/page/%d/?orderby=date" to "Sport",
        "$mainUrl/tag/poolside/page/%d/?orderby=date" to "Pool Side",

    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data.format(page), interceptor = cfInterceptor).document
        val home = document.select("div.row div div.post").mapNotNull {
            it.toSearchResult()
        }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name, list = home, isHorizontalImages = true
            ), hasNext = true
        )
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val posterUrl = fixUrlNull(this.select("dt a img").attr("src"))
        val title = this.select("dd a").text() ?: return null
        val href = fixUrlNull(this.select("dt a").attr("href")) ?: return null

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }

    }

    private fun Element.toSearchResult(): SearchResponse? {
        val posterUrl = fixUrlNull(this.select("div.item-thumbnail img").attr("src"))
        val title = this.select("div.item-head a").text() ?: return null
        val href = fixUrlNull(this.select("div.item-head a").attr("href")) ?: return null

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }

    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        val maxPages = if (query.contains(" ")) 4 else 10
        for (i in 1..maxPages) {
            val url = if (query.contains(" ")) {
            "$mainUrl/page/$i/?s=${query.replace(" ", "+")}&orderby=date"
            } else {
                "$mainUrl/page/$i/?s=$query&orderby=date"
            }

            val results = app.get(url, interceptor = cfInterceptor).document
                .select("div.row div div.post").mapNotNull {
                    it.toSearchResult()
                }.distinctBy { it.url }
            if (results.isEmpty()) break
            else delay((100L..500L).random())
            searchResponse.addAll(results)
        }
        return searchResponse.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, interceptor = cfInterceptor).document

        val poster = document.select("div#featured-img-id img").attr("src")
        val title = document.select("div.title-info h1.light-title.entry-title").text()
        val pTags = document.select("div.item-content p")
        val description = StringBuilder().apply {
            pTags.forEach {
                append(it.text())
            }
        }.toString()

        val tags = document.select("div.item-tax-list div a").map { it.text() }

        val recommendations =
            document.select("div.related-gallery dl.gallery-item").mapNotNull {
                it.toRecommendationResult()
            }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val response = app.get(data)
        val document = response.document

        val iframeUrl = document.select("div#player-embed iframe").attr("src")

        return loadExtractor(iframeUrl, subtitleCallback, callback)
    }
}
