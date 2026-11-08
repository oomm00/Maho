package com.omsati.dropwatch

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Pulls title / image / price from a product page on-device.
 * Fast path: OkHttp (following redirects) + Jsoup / JSON-LD / inline JSON.
 * Fallback: Hidden WebView with JS rendering and 20-second timeout.
 */
object Scraper {
    data class Result(
        val title: String?,
        val imageUrl: String?,
        val price: Double?,
        val currency: String?,
        val finalUrl: String? = null,
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    const val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private const val MAX_RESPONSE_SIZE = 2 * 1024 * 1024L // 2 MB cap

    suspend fun fetch(ctx: Context, url: String): Result = withContext(Dispatchers.IO) {
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return@withContext Result(title = null, imageUrl = null, price = null, currency = null, finalUrl = url)
        }

        if (BuildConfig.DEBUG) Log.d("Scraper", "Fetching initial URL: $url")
        var finalUrl = url
        var body: String? = null

        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-IN,en;q=0.9,hi;q=0.8")
                .header("Cookie", "cart_currency=INR; localization=IN; country=IN")
                .build()
            client.newCall(req).execute().use { resp ->
                finalUrl = resp.request.url.toString()
                if (BuildConfig.DEBUG) Log.d("Scraper", "OkHttp followed redirects to final URL: $finalUrl (HTTP ${resp.code})")
                if (resp.isSuccessful) {
                    val stream = resp.body?.byteStream()
                    if (stream != null) {
                        val buffer = ByteArray(8192)
                        var totalRead = 0L
                        val baos = java.io.ByteArrayOutputStream()
                        var bytesRead: Int
                        while (stream.read(buffer).also { bytesRead = it } != -1) {
                            totalRead += bytesRead
                            if (totalRead > MAX_RESPONSE_SIZE) break
                            baos.write(buffer, 0, bytesRead)
                        }
                        body = baos.toString(Charsets.UTF_8.name())
                    }
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("Scraper", "OkHttp fetch error for $url: ${e.message}", e)
        }

        var result: Result? = null
        if (!body.isNullOrBlank()) {
            result = runCatching { parse(body!!, finalUrl) }.getOrNull()
        }

        // Shopify store check & API fetch
        var shopifyRes: Result? = null
        if (finalUrl.contains("/products/", ignoreCase = true) || (body != null && body!!.contains("Shopify", ignoreCase = true))) {
            shopifyRes = runCatching { fetchShopifyApi(finalUrl) }.getOrNull()
        }

        if (shopifyRes != null) {
            result = Result(
                title = result?.title ?: shopifyRes.title,
                imageUrl = result?.imageUrl ?: shopifyRes.imageUrl,
                price = shopifyRes.price ?: result?.price,
                currency = result?.currency ?: shopifyRes.currency,
                finalUrl = finalUrl,
            )
        }

        // Fallback to WebView if price or image missing
        if (result == null || result.price == null || result.imageUrl == null) {
            val webViewResult = withContext(Dispatchers.Main) {
                runCatching { runWebView(ctx.applicationContext, finalUrl) }.getOrNull()
            }
            if (webViewResult != null) {
                val mergedPrice = result?.price ?: webViewResult.price
                val mergedCurrency = result?.currency ?: webViewResult.currency
                val mergedImage = result?.imageUrl ?: webViewResult.imageUrl
                val mergedTitle = result?.title ?: webViewResult.title
                result = Result(
                    title = mergedTitle,
                    imageUrl = sanitizeImageUrl(mergedImage, finalUrl),
                    price = mergedPrice,
                    currency = mergedCurrency,
                    finalUrl = finalUrl,
                )
            }
        }

        result ?: Result(title = null, imageUrl = null, price = null, currency = null, finalUrl = finalUrl)
    }

    private fun fetchShopifyApi(url: String): Result? {
        return try {
            val uri = URI(url)
            val host = uri.host ?: return null
            val path = uri.path ?: return null
            if (!path.contains("/products/")) return null
            val handle = path.substringAfter("/products/").substringBefore("/").substringBefore("?").substringBefore("#").trim()
            if (handle.isBlank()) return null

            val jsonUrl = "https://$host/products/$handle.json"
            val req = Request.Builder()
                .url(jsonUrl)
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .header("Accept-Language", "en-IN,en;q=0.9")
                .header("Cookie", "cart_currency=INR; localization=IN; country=IN")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val jsonStr = resp.body?.string()
                    if (!jsonStr.isNullOrBlank()) {
                        val root = JSONObject(jsonStr)
                        val prod = root.optJSONObject("product")
                        if (prod != null) {
                            val title = prod.optString("title").ifBlank { null }
                            val variants = prod.optJSONArray("variants")
                            var price: Double? = null
                            if (variants != null && variants.length() > 0) {
                                val v0 = variants.optJSONObject(0)
                                price = parsePrice(v0?.opt("price"))
                            }
                            val images = prod.optJSONArray("images")
                            var img: String? = null
                            if (images != null && images.length() > 0) {
                                img = images.optJSONObject(0)?.optString("src")
                            }
                            return Result(title = title, imageUrl = img, price = price, currency = null, finalUrl = url)
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun parse(html: String, baseUrl: String): Result {
        val doc = Jsoup.parse(html, baseUrl)
        var title: String? = null
        var image: String? = null
        var price: Double? = null
        var currency: String? = null

        // 1. JSON-LD strategy
        for (s in doc.select("script[type=application/ld+json]")) {
            val root = runCatching { JSONTokener(s.data()).nextValue() }.getOrNull() ?: continue
            for (node in flatten(root)) {
                if (!isProduct(node)) continue
                if (title == null) title = node.optString("name").ifBlank { null }
                if (image == null) image = firstImage(node.opt("image"))
                if (price == null) {
                    offerPrice(node.opt("offers"))?.let {
                        price = it.first
                        currency = it.second
                    }
                }
            }
        }

        // 2. Meta tags strategy
        fun meta(vararg names: String): String? {
            for (n in names) {
                val c = doc.selectFirst("meta[property=\"$n\"], meta[name=\"$n\"], meta[itemprop=\"$n\"]")?.attr("content")
                if (!c.isNullOrBlank()) return c.trim()
            }
            return null
        }

        if (title == null) {
            title = meta("og:title", "twitter:title") ?: doc.title().ifBlank { null }
        }
        if (image == null) {
            image = meta("og:image", "twitter:image")
        }
        if (price == null) {
            val metaPrice = parsePrice(meta("product:price:amount", "og:price:amount", "price"))
            if (metaPrice != null) {
                price = metaPrice
                currency = meta("product:price:currency", "og:price:currency", "priceCurrency")
            }
        }

        // 3. Itemprop microdata strategy
        if (price == null) {
            val el = doc.selectFirst("[itemprop=price]")
            val itemPrice = parsePrice(el?.attr("content")?.ifBlank { null } ?: el?.text())
            if (itemPrice != null) {
                price = itemPrice
                val curEl = doc.selectFirst("[itemprop=priceCurrency]")
                currency = curEl?.attr("content")?.ifBlank { null } ?: curEl?.text()?.ifBlank { null }
            }
        }

        // 4. Store-specific selectors (Amazon, Flipkart, Myntra, Ajio)
        if (price == null || image == null) {
            if (baseUrl.contains("amazon.", ignoreCase = true)) {
                if (price == null) {
                    val amzPriceEl = doc.selectFirst(
                        "#corePrice_feature_div .a-offscreen, #corePriceDisplay_desktop_feature_div .a-offscreen, " +
                        ".apexPriceToPay .a-offscreen, #priceblock_ourprice, #priceblock_dealprice, span.a-price span.a-offscreen"
                    )
                    val p = parsePrice(amzPriceEl?.text())
                    if (p != null) {
                        price = p
                        currency = "INR"
                    }
                }
                if (image == null) {
                    val imgEl = doc.selectFirst("#landingImage, #imgBlkFront, #main-image")
                    val src = imgEl?.attr("data-old-hires")?.ifBlank { null }
                        ?: imgEl?.attr("src")?.ifBlank { null }
                    if (src != null) {
                        image = src
                    }
                }
            }

            if (baseUrl.contains("flipkart.com", ignoreCase = true)) {
                if (price == null) {
                    val fkPriceEl = doc.selectFirst("div._30jeq3, div.Nx9bqj, div._16Jk6d, div.hl05eU div")
                    val p = parsePrice(fkPriceEl?.text())
                    if (p != null) {
                        price = p
                        currency = "INR"
                    }
                }
                if (image == null) {
                    val fkImgEl = doc.selectFirst("img._396cs4, img._2r_T1I, img.DByuf4")
                    val src = fkImgEl?.attr("src")?.ifBlank { null }
                    if (src != null) {
                        image = src
                    }
                }
            }

            if (baseUrl.contains("myntra.com", ignoreCase = true) || baseUrl.contains("ajio.com", ignoreCase = true)) {
                if (price == null) {
                    val pEl = doc.selectFirst("span.pdp-price strong, div.pdp-price, span.prod-sp")
                    val p = parsePrice(pEl?.text())
                    if (p != null) {
                        price = p
                        currency = "INR"
                    }
                }
            }
        }

        // 5. Inline JSON
        if (price == null || image == null) {
            for (script in doc.select("script")) {
                val data = script.data()
                if (data.contains("__NEXT_DATA__") || data.contains("pdpData") || data.contains("window.__myx")) {
                    val found = extractFromJsonText(data)
                    if (price == null && found.first != null) {
                        price = found.first
                        currency = found.second ?: "INR"
                    }
                    if (image == null && found.third != null) {
                        image = found.third
                    }
                    if (price != null && image != null) break
                }
            }
        }

        // 6. Regex fallback for Rupee sign / INR
        if (price == null) {
            val text = doc.body()?.text().orEmpty()
            val rupeeRegex = Regex("""(?:₹|Rs\.?|INR)\s*([1-9][0-9,]*(\.[0-9]{1,2})?)""")
            val m = rupeeRegex.find(text)
            if (m != null) {
                val p = parsePrice(m.groupValues[1])
                if (p != null) {
                    price = p
                    currency = "INR"
                }
            }
        }

        if (price != null && currency.isNullOrBlank() && (baseUrl.contains(".in") || baseUrl.contains("flipkart") || baseUrl.contains("myntra") || baseUrl.contains("ajio"))) {
            currency = "INR"
        }

        image = sanitizeImageUrl(image, baseUrl)
        return Result(title?.trim(), image, price, currency?.trim()?.ifBlank { null }, baseUrl)
    }

    private suspend fun runWebView(appCtx: Context, url: String): Result? = withTimeoutOrNull(20_000L) {
        suspendCancellableCoroutine { cont ->
            var webView: WebView? = null
            val cleanup = {
                try {
                    webView?.let { wv ->
                        wv.stopLoading()
                        wv.webChromeClient = null
                        wv.webViewClient = WebViewClient()
                        wv.destroy()
                    }
                    webView = null
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.e("Scraper", "WebView cleanup error: ${e.message}")
                }
            }

            cont.invokeOnCancellation {
                Handler(Looper.getMainLooper()).post { cleanup() }
            }

            try {
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setCookie(url, "cart_currency=INR; localization=IN; country=IN")

                val wv = WebView(appCtx)
                webView = wv
                wv.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    userAgentString = UA
                    loadsImagesAutomatically = true
                    databaseEnabled = true
                }

                var evaluated = false
                val evaluateScript = {
                    if (!evaluated && cont.isActive) {
                        evaluated = true
                        val js = """
                            (function() {
                                var res = {};
                                res.title = document.title || "";
                                var metas = {};
                                var metaEls = document.querySelectorAll('meta');
                                for (var i = 0; i < metaEls.length; i++) {
                                    var m = metaEls[i];
                                    var k = m.getAttribute('property') || m.getAttribute('name') || m.getAttribute('itemprop');
                                    var c = m.getAttribute('content');
                                    if (k && c && !metas[k]) metas[k] = c;
                                }
                                res.metas = metas;
                                var scripts = [];
                                var sEls = document.querySelectorAll('script[type="application/ld+json"]');
                                for (var j = 0; j < sEls.length; j++) {
                                    var txt = sEls[j].textContent || sEls[j].innerText || "";
                                    if (txt) scripts.push(txt);
                                }
                                res.ldJson = scripts;
                                var itemPrices = [];
                                var ipEls = document.querySelectorAll('[itemprop="price"]');
                                for (var k = 0; k < ipEls.length; k++) {
                                    var v = ipEls[k].getAttribute('content') || ipEls[k].innerText || ipEls[k].textContent || "";
                                    if (v) itemPrices.push(v);
                                }
                                res.itempropPrices = itemPrices;
                                var amzPrice = document.querySelector('#corePrice_feature_div .a-offscreen, #corePriceDisplay_desktop_feature_div .a-offscreen, .apexPriceToPay .a-offscreen, #priceblock_ourprice, #priceblock_dealprice, span.a-price span.a-offscreen');
                                res.amazonPrice = amzPrice ? (amzPrice.innerText || amzPrice.textContent || "") : "";
                                var amzImg = document.querySelector('#landingImage, #imgBlkFront, #main-image');
                                res.amazonImage = amzImg ? (amzImg.getAttribute('data-old-hires') || amzImg.getAttribute('src') || "") : "";
                                res.html = document.documentElement ? document.documentElement.outerHTML : "";
                                return JSON.stringify(res);
                            })();
                        """.trimIndent()

                        wv.evaluateJavascript(js) { rawResult ->
                            try {
                                if (rawResult.isNullOrBlank() || rawResult == "null") {
                                    cont.resume(null)
                                } else {
                                    val unquoted = if (rawResult.startsWith("\"") && rawResult.endsWith("\"")) {
                                        JSONTokener(rawResult).nextValue().toString()
                                    } else rawResult
                                    val json = JSONObject(unquoted)
                                    val parsed = parseWebViewJson(json, url)
                                    cont.resume(parsed)
                                }
                            } catch (e: Exception) {
                                cont.resume(null)
                            } finally {
                                cleanup()
                            }
                        }
                    }
                }

                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                        super.onPageFinished(view, finishedUrl)
                        Handler(Looper.getMainLooper()).postDelayed({
                            evaluateScript()
                        }, 3000L)
                    }

                    override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                        // ignore error
                    }
                }

                wv.loadUrl(url)
            } catch (e: Exception) {
                cleanup()
                cont.resume(null)
            }
        }
    }

    private fun parseWebViewJson(json: JSONObject, baseUrl: String): Result {
        var title: String? = json.optString("title").ifBlank { null }
        var image: String? = json.optString("amazonImage").ifBlank { null }
        var price: Double? = parsePrice(json.optString("amazonPrice").ifBlank { null })
        var currency: String? = if (price != null) "INR" else null

        val metas = json.optJSONObject("metas")
        if (metas != null) {
            if (title == null) {
                title = metas.optString("og:title").ifBlank { null }
                    ?: metas.optString("twitter:title").ifBlank { null }
            }
            if (image == null) {
                image = metas.optString("og:image").ifBlank { null }
                    ?: metas.optString("twitter:image").ifBlank { null }
            }
            if (price == null) {
                price = parsePrice(metas.optString("product:price:amount").ifBlank { null })
                    ?: parsePrice(metas.optString("og:price:amount").ifBlank { null })
                    ?: parsePrice(metas.optString("price").ifBlank { null })
                if (price != null) {
                    currency = metas.optString("product:price:currency").ifBlank { null }
                        ?: metas.optString("og:price:currency").ifBlank { null }
                        ?: metas.optString("priceCurrency").ifBlank { null }
                }
            }
        }

        val ldArray = json.optJSONArray("ldJson")
        if (ldArray != null) {
            for (i in 0 until ldArray.length()) {
                val str = ldArray.optString(i)
                val root = runCatching { JSONTokener(str).nextValue() }.getOrNull() ?: continue
                for (node in flatten(root)) {
                    if (!isProduct(node)) continue
                    if (title == null) title = node.optString("name").ifBlank { null }
                    if (image == null) image = firstImage(node.opt("image"))
                    if (price == null) {
                        offerPrice(node.opt("offers"))?.let {
                            price = it.first
                            currency = it.second
                        }
                    }
                }
            }
        }

        if (price == null) {
            val ipArray = json.optJSONArray("itempropPrices")
            if (ipArray != null) {
                for (i in 0 until ipArray.length()) {
                    val p = parsePrice(ipArray.opt(i))
                    if (p != null) {
                        price = p
                        break
                    }
                }
            }
        }

        val html = json.optString("html")
        if ((price == null || image == null) && html.isNotBlank()) {
            val parsedHtml = parse(html, baseUrl)
            if (title == null) title = parsedHtml.title
            if (image == null) image = parsedHtml.imageUrl
            if (price == null) {
                price = parsedHtml.price
                currency = parsedHtml.currency
            }
        }

        if (price != null && currency.isNullOrBlank() && (baseUrl.contains(".in") || baseUrl.contains("amazon") || baseUrl.contains("flipkart"))) {
            currency = "INR"
        }

        image = sanitizeImageUrl(image, baseUrl)
        return Result(title?.trim(), image, price, currency?.trim()?.ifBlank { null }, baseUrl)
    }

    fun sanitizeImageUrl(imageUrl: String?, baseUrl: String): String? {
        if (imageUrl.isNullOrBlank()) return null
        var url = imageUrl.trim()
        if (url.startsWith("//")) {
            url = "https:$url"
        } else {
            url = runCatching { URI(baseUrl).resolve(url).toString() }.getOrNull() ?: url
        }
        if (url.startsWith("http://", ignoreCase = true)) {
            url = "https://" + url.substring(7)
        }
        return url.ifBlank { null }
    }

    private fun extractFromJsonText(text: String): Triple<Double?, String?, String?> {
        var price: Double? = null
        var currency: String? = null
        var image: String? = null

        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start in 0 until end) {
            val jsonStr = text.substring(start, end + 1)
            val root = runCatching { JSONTokener(jsonStr).nextValue() }.getOrNull()
            if (root != null) {
                val nodes = flatten(root)
                for (node in nodes) {
                    if (price == null) {
                        val p = parsePrice(node.opt("sellingPrice"))
                            ?: parsePrice(node.opt("price"))
                            ?: parsePrice(node.opt("offerPrice"))
                            ?: parsePrice(node.opt("discountedPrice"))
                            ?: parsePrice(node.opt("finalPrice"))
                            ?: parsePrice(node.opt("mrp"))
                        if (p != null) {
                            price = p
                            currency = node.optString("currency").ifBlank { null }
                                ?: node.optString("priceCurrency").ifBlank { null }
                        }
                    }
                    if (image == null) {
                        val img = firstImage(node.opt("image"))
                            ?: firstImage(node.opt("images"))
                            ?: node.optString("imageUrl").ifBlank { null }
                        if (img != null) image = img
                    }
                    if (price != null && image != null) break
                }
            }
        }
        return Triple(price, currency, image)
    }

    private fun flatten(v: Any?): List<JSONObject> = when (v) {
        is JSONArray -> (0 until v.length()).flatMap { flatten(v.opt(it)) }
        is JSONObject -> listOf(v) + flatten(v.opt("@graph"))
        else -> emptyList()
    }

    private fun isProduct(o: JSONObject): Boolean = when (val t = o.opt("@type")) {
        is String -> t == "Product"
        is JSONArray -> (0 until t.length()).any { t.optString(it) == "Product" }
        else -> false
    }

    private fun firstImage(v: Any?): String? = when (v) {
        is String -> v.ifBlank { null }
        is JSONArray -> (0 until v.length()).firstNotNullOfOrNull { firstImage(v.opt(it)) }
        is JSONObject -> v.optString("url").ifBlank { null } ?: v.optString("src").ifBlank { null }
        else -> null
    }

    private fun offerPrice(v: Any?): Pair<Double, String?>? = when (v) {
        is JSONArray -> (0 until v.length()).firstNotNullOfOrNull { offerPrice(v.opt(it)) }
        is JSONObject -> {
            val p = parsePrice(v.opt("price")) ?: parsePrice(v.opt("lowPrice"))
                ?: (v.opt("priceSpecification") as? JSONObject)?.let { parsePrice(it.opt("price")) }
            val cur = v.optString("priceCurrency").ifBlank { null }
                ?: (v.opt("priceSpecification") as? JSONObject)?.optString("priceCurrency")?.ifBlank { null }
            if (p != null) p to cur else offerPrice(v.opt("offers"))
        }
        else -> null
    }

    fun parsePrice(raw: Any?): Double? {
        if (raw == null) return null
        if (raw is Number) return raw.toDouble().takeIf { it > 0 }
        var s = raw.toString().filter { it.isDigit() || it == '.' || it == ',' }
        if (s.isEmpty()) return null
        val dot = s.lastIndexOf('.')
        val comma = s.lastIndexOf(',')
        s = when {
            dot >= 0 && comma >= 0 ->
                if (dot > comma) s.replace(",", "") else s.replace(".", "").replace(',', '.')
            comma >= 0 -> if (Regex(",\\d{1,2}$").containsMatchIn(s)) s.replace(',', '.') else s.replace(",", "")
            else -> s
        }
        return s.toDoubleOrNull()?.takeIf { it > 0 }
    }
}
