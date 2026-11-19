package com.jarvis.assistant.data

import com.jarvis.assistant.commands.NewsController
import com.jarvis.assistant.commands.NewsItem
import com.jarvis.assistant.gesture.HandGesture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NewsUiState(
    val visible: Boolean = false,
    val loading: Boolean = false,
    val query: String = "",
    val items: List<NewsItem> = emptyList(),
    val index: Int = 0,
    val detailOpen: Boolean = false,
    val related: List<NewsItem> = emptyList(),
    val relatedLoading: Boolean = false,
    val gestureEnabled: Boolean = false,
    val frontCamera: Boolean = true,
    val speakOnNavigate: Boolean = true,
    val gestureStatus: String = "",
    val lastAction: String = "",
    val error: String? = null
)

/**
 * Haber panelinin tüm mantığı: liste, seçili haber, detay, arama, el hareketi ve sesli komutlar.
 * Dokunma, ses ve el hareketi aynı fonksiyonları çağırır, bu yüzden hepsi aynı durumu paylaşır.
 */
class NewsSession(
    private val scope: CoroutineScope,
    private val speak: (String) -> Unit
) {
    private val _state = MutableStateFlow(NewsUiState())
    val state: StateFlow<NewsUiState> = _state.asStateFlow()
    private var relatedJob: Job? = null

    // ------------------------------------------------------------------ açma / kapama / arama

    /** Haberleri getirip paneli açar. Sesli yanıt metnini döndürür. */
    suspend fun open(query: String?): String {
        val q = query?.trim().orEmpty()
        relatedJob?.cancel()
        _state.update {
            it.copy(visible = true, loading = true, query = q, items = emptyList(), index = 0,
                detailOpen = false, related = emptyList(), relatedLoading = false, error = null)
        }
        val items = NewsController.fetchItems(q.ifBlank { null }, 15)
        if (items == null) {
            _state.update { it.copy(loading = false, error = "Haberler alınamadı, bağlantını kontrol et.") }
            return "Haberleri şu an alamadım, bağlantı hatası olmuş olabilir."
        }
        _state.update { it.copy(loading = false, items = items, index = 0) }
        if (items.isEmpty()) {
            return if (q.isBlank()) "Şu an için haber bulamadım." else "$q için haber bulamadım."
        }
        val label = if (q.isBlank()) "Haber" else "$q haberleri"
        return "$label açıldı, ${items.size} başlık var. İlk haber: ${items[0].title}"
    }

    /** Dokunmatik arayüzden açma/arama. */
    fun openFromUi(query: String?) {
        scope.launch { open(query) }
    }

    fun close() {
        relatedJob?.cancel()
        _state.update { it.copy(visible = false, detailOpen = false) }
    }

    /** Geri tuşu: detaydaysa listeye, listedeyse panelden çık. */
    fun onBack() {
        if (_state.value.detailOpen) closeDetail() else close()
    }

    // ------------------------------------------------------------------ gezinme

    fun select(i: Int) {
        val s = _state.value
        if (i in s.items.indices && i != s.index) {
            _state.update { it.copy(index = i) }
            if (s.detailOpen) loadRelated(s.items[i])
        }
    }

    fun move(delta: Int, announce: Boolean): String {
        val s = _state.value
        if (s.items.isEmpty()) return "Gösterilecek haber yok."
        val ni = s.index + delta
        if (ni !in s.items.indices) return if (delta > 0) "Son haberdesin." else "İlk haberdesin."
        _state.update { it.copy(index = ni) }
        val item = s.items[ni]
        if (s.detailOpen) loadRelated(item)
        val msg = "${ni + 1}. haber: ${item.title}"
        if (announce && s.speakOnNavigate) speak(msg)
        return msg
    }

    fun openDetail(announce: Boolean): String {
        val s = _state.value
        val item = s.items.getOrNull(s.index) ?: return "Açılacak haber yok."
        _state.update { it.copy(detailOpen = true) }
        loadRelated(item)
        val msg = buildString {
            append("Detay: ${item.title}.")
            if (item.source.isNotBlank()) append(" Kaynak: ${item.source}.")
            if (item.timeLabel.isNotBlank()) append(" ${item.timeLabel}.")
        }
        if (announce && s.speakOnNavigate) speak(msg)
        return msg
    }

    fun closeDetail() {
        relatedJob?.cancel()
        _state.update { it.copy(detailOpen = false, related = emptyList(), relatedLoading = false) }
    }

    /** Seçili haberi (ve varsa aynı konudaki diğer kaynak başlıklarını) sesli okur. */
    fun readCurrent(): String {
        val s = _state.value
        val item = s.items.getOrNull(s.index) ?: return "Okunacak haber yok."
        return buildString {
            append(item.title).append(".")
            if (item.source.isNotBlank()) append(" Kaynak: ${item.source}.")
            val others = s.related.take(3)
            if (s.detailOpen && others.isNotEmpty()) {
                append(" Aynı konuda diğer kaynaklar: ")
                append(others.joinToString(". ") { (if (it.source.isNotBlank()) it.source + ": " else "") + it.title })
            }
        }
    }

    /** Aynı olayı başka kaynaklar nasıl vermiş: başlıkla arama yapıp benzer haberleri getirir. */
    private fun loadRelated(item: NewsItem) {
        relatedJob?.cancel()
        _state.update { it.copy(related = emptyList(), relatedLoading = true) }
        relatedJob = scope.launch {
            val r = NewsController.fetchItems(item.title, 10)
                ?.filter { it.link != item.link && it.title != item.title }
                ?.take(6)
                ?: emptyList()
            _state.update {
                if (it.items.getOrNull(it.index)?.link == item.link) it.copy(related = r, relatedLoading = false) else it
            }
        }
    }

    // ------------------------------------------------------------------ ayarlar

    fun setGesture(enabled: Boolean) {
        _state.update { it.copy(gestureEnabled = enabled, gestureStatus = if (enabled) it.gestureStatus else "") }
    }
    fun flipCamera() { _state.update { it.copy(frontCamera = !it.frontCamera) } }
    fun speakText(text: String) { speak(text) }
    fun toggleSpeak() { _state.update { it.copy(speakOnNavigate = !it.speakOnNavigate) } }
    fun onGestureStatus(text: String) { _state.update { it.copy(gestureStatus = text) } }

    // ------------------------------------------------------------------ el hareketi

    fun onGesture(g: HandGesture) {
        val s = _state.value
        if (!s.visible || s.items.isEmpty()) return
        when (g) {
            HandGesture.NEXT -> { flash("◀  SONRAKİ"); move(+1, true) }
            HandGesture.PREVIOUS -> { flash("ÖNCEKİ  ▶"); move(-1, true) }
            HandGesture.OPEN_DETAIL -> if (!s.detailOpen) { flash("DETAY"); openDetail(true) }
            HandGesture.CLOSE_DETAIL -> if (s.detailOpen) { flash("LİSTE"); closeDetail() }
        }
    }

    private fun flash(label: String) {
        _state.update { it.copy(lastAction = label) }
        scope.launch {
            delay(1200)
            _state.update { if (it.lastAction == label) it.copy(lastAction = "") else it }
        }
    }

    // ------------------------------------------------------------------ sesli komutlar

    private val generic = setOf(
        "son", "güncel", "bugünkü", "bugünün", "tüm", "bütün", "bana", "şu", "en", "yeni", "sıcak",
        "günün", "gündem", "canlı", "lütfen", "jarvis", "hey", "bir", "bazı", "bu", "benim", "bizim"
    )
    private val openWords = listOf("göster", "aç", "gez", "incele", "listele", "getir", "yenile")
    private val ordinals = mapOf(
        "birinci" to 1, "ilk" to 1, "ikinci" to 2, "üçüncü" to 3, "dördüncü" to 4, "beşinci" to 5,
        "altıncı" to 6, "yedinci" to 7, "sekizinci" to 8, "dokuzuncu" to 9, "onuncu" to 10
    )
    private val searchPatterns = listOf(
        Regex("^(.+?)\\s+(?:ile ilgili|hakkındaki|hakkında|konulu)\\s+haber\\w*(?:\\s+(?:ara|bul|göster|aç))?$"),
        Regex("^(.+?)\\s+haber\\w*\\s+(?:ara|bul)$"),
        Regex("^haber\\w*\\s+(.+?)\\s+(?:ara|bul)$"),
        Regex("^(?:ara|bul)\\s+(.+?)\\s+haber\\w*$")
    )
    private val topicBeforeNews = Regex("^(.+?)\\s+haber")

    private fun cleanTopic(raw: String): String? {
        val words = raw.split(Regex("\\s+")).filter { it.isNotBlank() && it !in generic }
        return words.joinToString(" ").ifBlank { null }
    }

    /**
     * Haber paneliyle ilgili bir komut mu? Evet ise yanıt metnini, hayır ise null döndürür
     * (null dönünce komut her zamanki akışa devam eder — mevcut komutlar bozulmaz).
     * [text] küçük harfe çevrilmiş Türkçe cümledir.
     */
    suspend fun handleVoice(text: String): String? {
        val s = _state.value
        val t = text.trim().trimEnd('.', '!', '?', ',')
        val wc = t.split(Regex("\\s+")).size
        val hasHaber = t.contains("haber")

        if (s.visible) {
            // "3. haber", "üçüncü habere git", "ilk haber"
            val num = Regex("(\\d+)\\s*\\.?\\s*(?:haber|başlık)").find(t)?.groupValues?.get(1)?.toIntOrNull()
                ?: ordinals.entries.firstOrNull { Regex("\\b${it.key}\\s+(?:haber|başlık)").containsMatchIn(t) }?.value
            if (num != null) {
                if (num !in 1..s.items.size) return "Listede $num. haber yok, ${s.items.size} başlık var."
                _state.update { it.copy(index = num - 1) }
                return openOrRead(num - 1, s.detailOpen)
            }
            if (t.contains("kapat") && (hasHaber || wc <= 2) || t == "çık" || t.contains("haberlerden çık")) {
                close(); return "Haberleri kapattım."
            }
            if (wc <= 4 && Regex("\\b(sonraki|sıradaki|ileri)\\b").containsMatchIn(t)) return move(+1, false)
            if (wc <= 4 && Regex("\\b(önceki)\\b").containsMatchIn(t)) return move(-1, false)
            if (wc <= 3 && (t.startsWith("geri") || t.contains("listeye dön"))) {
                return if (s.detailOpen) { closeDetail(); "Listeye döndüm." } else move(-1, false)
            }
            if (wc <= 4 && t.contains("oku")) return readCurrent()
            val itemRef = Regex("\\b(haberi|habere)\\b").containsMatchIn(t)
            if (wc <= 5 && (t.contains("detay") || t.contains("ayrıntı") || t == "incele" ||
                        (itemRef && (t.contains("aç") || t.contains("göster") || t.contains("incele"))))) {
                return openDetail(false)
            }
            if (wc <= 6 && itemRef && (t.contains("tarayıcı") || t.contains("tamamı"))) {
                return "Tam haber için ekrandaki \"Tarayıcıda aç\" düğmesine dokun."
            }
        }

        if (!hasHaber) return null

        // Arama: "ekonomi haberlerini ara", "deprem hakkında haberler", "haberlerde enflasyon ara"
        for (re in searchPatterns) {
            val topic = re.find(t)?.groupValues?.get(1)?.let { cleanTopic(it) }
            if (topic != null) return open(topic)
        }

        // Açma: "haberleri göster", "spor haberlerini aç", "haber başlıklarını gezelim"
        if (openWords.any { t.contains(it) } && !Regex("\\b(haberi|habere)\\b").containsMatchIn(t)) {
            val topic = topicBeforeNews.find(t)?.groupValues?.get(1)?.let { cleanTopic(it) }
            return open(topic ?: s.query.takeIf { s.visible && t.contains("yenile") })
        }
        return null
    }

    private fun openOrRead(index: Int, detail: Boolean): String {
        val item = _state.value.items[index]
        return if (detail) { loadRelated(item); "${index + 1}. haber: ${item.title}" }
        else "${index + 1}. haber: ${item.title}"
    }
}
