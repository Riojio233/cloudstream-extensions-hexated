package com.hexated

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.VPNStatus
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class FullPorner : MainAPI() {
    override var mainUrl = "https://fullporner.com"
    override var name = "FullPorner"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val vpnStatus = VPNStatus.MightBeNeeded
    override val supportedTypes = setOf(TvType.NSFW)

    private val imageUrlHolder = HashMap<String, String>()

    override val mainPage = mainPageOf(
        "home" to "Featured Videos",
        "category/threesome" to "Threesome",
        "category/dp" to "DP",
        "category/massage" to "Massage",
        "category/public" to "Public",
        "category/outdoor" to "Outdoor",
        "category/squirt" to "Squirt",
        "category/anal" to "Anal",
    )

    override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/$page").document
        val home = document.select("div.video-block div.video-card").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(
            list = HomePageList(
                name = request.name, list = home, isHorizontalImages = true
            ), hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.video-card div.video-card-body div.video-title a")?.text()
            ?: return null
        val href = fixUrl(
            this.selectFirst("div.video-card div.video-card-body div.video-title a")!!.attr("href")
        )
        val posterUrl =
            fixUrl(this.select("div.video-card div.video-card-image a img").attr("data-src"))

        imageUrlHolder[href] = posterUrl
        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }

    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        for (i in 1..20) {
            val document = app.get(
                "$mainUrl/search?q=${query.replace(" ", "+")}&p=$i"
            ).document
            val results = document.select("div.video-block div.video-card").mapNotNull {
                it.toSearchResult()
            }.distinctBy { it.url }
            if (results.isEmpty()) break
            searchResponse.addAll(results)
        }
        return searchResponse.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title =
            document.selectFirst("div.video-block div.single-video-left div.single-video-title h2")
                ?.text()?.trim().toString()
        val poster = imageUrlHolder[url]

        val tags =
            document.select("div.video-blockdiv.single-video-left div.single-video-title p.tag-link span a")
                .map { it.text() }
        val description =
            document.selectFirst("div.video-block div.single-video-left div.single-video-title h2")
                ?.text()?.trim().toString()
        val actors =
            document.select("div.video-block div.single-video-left div.single-video-info-content p a")
                .map { it.text() }
        val recommendations =
            document.select("div.video-block div.video-recommendation div.video-card").mapNotNull {
                it.toSearchResult()
            }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
            addActors(actors)
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        val iframeUrl = fixUrlNull(
            document.selectFirst("div.video-block div.single-video-left div.single-video iframe")
                ?.attr("src")
        ) ?: ""
        val extlinkList = mutableListOf<ExtractorLink>()
        if (iframeUrl.contains("videotr")) {
            val porntrexUrl = app.get(
                iframeUrl, interceptor = WebViewResolver(Regex("""porntrex"""))
            ).url

            loadExtractor(porntrexUrl,  subtitleCallback, callback)
        } else if (iframeUrl.contains("videoh")) {
            val iframeDocument = app.get(
                iframeUrl, interceptor = WebViewResolver(Regex("""mydaddy"""))
            ).document
            val videoDocument = Jsoup.parse(
                "<video" + iframeDocument.selectXpath("//script[contains(text(),'\$(\"#jw\").html(')]")
                    .first()?.toString()
                    ?.replace("\\", "")?.substringAfter("<video")?.substringAfter("<video")
                    ?.substringBefore("</video>") + "</video>"
            )
            videoDocument.select("source").map { res ->
                extlinkList.add(
                    ExtractorLink(name,
                        name,
                        fixUrl(res.attr("src")),
                        referer = data,
                        quality = Regex("(\\d+.)").find(res.attr("title"))?.groupValues?.get(1)
                            .let { getQualityFromName(it) })
                )
            }
        } else {
            val iframeDocument = app.get(iframeUrl).document
            val videoDocument = Jsoup.parse(
                "<video" + iframeDocument.selectXpath("//script[contains(text(),'\$(\"#jw\").html(')]")
                    .first()?.toString()
                    ?.replace("\\", "")?.substringAfter("<video")
                    ?.substringBefore("</video>") + "</video>"
            )
            videoDocument.select("source").map { res ->
                extlinkList.add(
                    ExtractorLink(this.name,
                        this.name,
                        fixUrl(res.attr("src")),
                        referer = mainUrl,
                        quality = Regex("(\\d+.)").find(res.attr("title"))?.groupValues?.get(1)
                            .let { getQualityFromName(it) })
                )
            }
        }
        extlinkList.forEach(callback)
        return true
    }

}