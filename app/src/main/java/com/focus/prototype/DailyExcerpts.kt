package com.focus.prototype

internal data class DailyExcerpt(
    val id: String,
    val original: String,
    val chinese: String,
    val author: String,
    val work: String,
    val source: String,
    val themes: List<String>,
    val translated: Boolean = true,
) {
    /** Reader-facing title; locator details remain in the source record, not on the keepsake. */
    val displayWork: String
        get() = work
            .replace(Regex("\\s*§\\s*\\d+.*$"), "")
            .replace(Regex("\\s*[IVXLCDM]+(?:\\.\\d+)+$"), "")
            .replace(Regex("\\s*其[一二三四五六七八九十]+$"), "")
            .replace(Regex("\\s*第[一二三四五六七八九十百]+章$"), "")
            .replace(Regex("\\s*格言与箭.*$"), "")
            .trim()

    val attribution: String get() = "$author · $displayWork"
}

internal object DailyExcerpts {
    private const val NietzscheFW270 = "https://www.nietzschesource.org/texts/eKGWB/FW-270"
    private const val NietzscheFW276 = "https://www.nietzschesource.org/texts/eKGWB/FW-276"
    private const val NietzscheFW335 = "https://www.nietzschesource.org/texts/eKGWB/FW-335"
    private const val NietzscheJGB = "https://www.nietzschesource.org/texts/eKGWB/JGB-272"
    private const val NietzscheGD = "https://www.nietzschesource.org/texts/eKGWB/GD-Sprueche-12"
    private const val LacanSeminar = "https://ecole-lacanienne.net/seminaires/le-seminaire-livre-vii-lethique-de-la-psychanalyse/"
    private const val SenecaI = "https://la.wikisource.org/wiki/De_brevitate_vitae#I"
    private const val SenecaII = "https://la.wikisource.org/wiki/De_brevitate_vitae#II"
    private const val EpictetusI = "https://la.wikisource.org/wiki/Epicteti_Enchiridion#Caput_I"
    private const val EpictetusV = "https://la.wikisource.org/wiki/Epicteti_Enchiridion#Caput_V"
    private const val EpictetusVIII = "https://la.wikisource.org/wiki/Epicteti_Enchiridion#Caput_VIII"
    private const val EpictetusIX = "https://la.wikisource.org/wiki/Epicteti_Enchiridion#Caput_IX"
    private const val Pascal347 = "https://fr.wikisource.org/wiki/Pensées_(Pascal,_éd._Brunschvicg)/Pensées/Section_VI#347"
    private const val Pascal348 = "https://fr.wikisource.org/wiki/Pensées_(Pascal,_éd._Brunschvicg)/Pensées/Section_VI#348"

    val entries = listOf(
        DailyExcerpt("fw270", "Was sagt dein Gewissen? — Du sollst der werden, der du bist.", "你的良知说什么？你应当成为你自己", "尼采", "《快乐的科学》§270", NietzscheFW270, listOf("自我认识")),
        DailyExcerpt("brev1a", "Non exiguum temporis habemus, sed multum perdimus.", "我们拥有的时间并不少，只是失去了许多", "塞涅卡", "《论生命之短暂》I.3", SenecaI, listOf("时间")),
        DailyExcerpt("ench1", "τῶν ὄντων τὰ μέν ἐστιν ἐφʼ ἡμῖν, τὰ δὲ οὐκ ἐφʼ ἡμῖν.", "有些事由我们决定，有些事不由我们决定", "爱比克泰德", "《手册》§1", EpictetusI, listOf("自由", "自制")),
        DailyExcerpt("pascal-reed", "L’homme n’est qu’un roseau, le plus faible de la nature ; mais c’est un roseau pensant.", "人不过是自然中最脆弱的一茎芦苇，却是一茎会思想的芦苇", "帕斯卡", "《思想录》§347", Pascal347, listOf("思想", "脆弱")),
        DailyExcerpt("fw276", "Amor fati: das sei von nun an meine Liebe!", "爱命运，愿这从此成为我的爱", "尼采", "《快乐的科学》§276", NietzscheFW276, listOf("命运")),
        DailyExcerpt("brev1b", "non accipimus brevem vitam, sed facimus, nec inopes eius sed prodigi sumus.", "我们得到的生命并不短，是我们让它变短。我们并非匮乏，而是挥霍", "塞涅卡", "《论生命之短暂》I.4", SenecaI, listOf("时间", "选择")),
        DailyExcerpt("ench5", "ταράσσει τοὺς ἀνθρώπους οὐ τὰ πράγματα, ἀλλὰ τὰ περὶ τῶν πραγμάτων δόγματα", "困扰人的并非事情本身，而是人对事情的看法", "爱比克泰德", "《手册》§5", EpictetusV, listOf("判断")),
        DailyExcerpt("fw335", "Wir aber wollen Die werden, die wir sind, — die Neuen, die Einmaligen, die Unvergleichbaren, die Sich-selber-Gesetzgebenden, die Sich-selber-Schaffenden!", "我们愿成为我们自己，成为崭新的、独一无二的、不可比拟的人，为自己立法，也创造自己", "尼采", "《快乐的科学》§335", NietzscheFW335, listOf("创造", "自我超越")),
        DailyExcerpt("brev2", "vita, si uti scias, longa est.", "若懂得如何使用生命，生命便是漫长的", "塞涅卡", "《论生命之短暂》II.1", SenecaII, listOf("时间")),
        DailyExcerpt("ench8", "μὴ ζήτει τὰ γινόμενα γίνεσθαι ὡς θέλεις, ἀλλὰ θέλε τὰ γινόμενα ὡς γίνεται καὶ εὐроήσεις.", "别要求万事按你的愿望发生，试着接受它们发生的样子，生活便会顺畅些", "爱比克泰德", "《手册》§8", EpictetusVIII, listOf("接受")),
        DailyExcerpt("pascal-dignity", "Ce n’est point de l’espace que je dois chercher ma dignité, mais c’est du règlement de ma pensée.", "我的尊严不在于占有多少空间，而在于如何约束自己的思想", "帕斯卡", "《思想录》§348", Pascal348, listOf("尊严", "思想")),
        DailyExcerpt("ench9", "νόσος σώματός ἐστιν ἐμπόδιον, προαιρέσεως δὲ οὔ, ἐὰν μὴ αὐτὴ θέλῃ.", "疾病妨碍身体，却不必妨碍人的选择，除非选择本身让步", "爱比克泰德", "《手册》§9", EpictetusIX, listOf("选择")),
        DailyExcerpt("jgb272", "Zeichen der Vornehmheit: niemals daran denken, unsre Pflichten zu Pflichten für Jedermann herabzudrücken; die eigne Verantwortlichkeit nicht abwälzen, nicht theilen wollen.", "高贵的标志：从不想把我们自己的责任降为任何人的责任；不愿推卸、不愿分享自己的责任", "尼采", "《善恶的彼岸》§272", NietzscheJGB, listOf("责任", "自我认识")),
        DailyExcerpt("zara-why", "Hat man sein warum? des Lebens, so verträgt man sich fast mit jedem wie?", "知晓生命之为何的人，便能承受几乎任何一种怎样", "尼采", "《偶像的黄昏》格言与箭§12", NietzscheGD, listOf("目标", "意义")),
        DailyExcerpt("lacan-desire", "Ne pas céder sur son désir.", "绝不在自身真正的愿望上退让", "拉康", "《研讨班VII：精神分析的伦理学》第24章", LacanSeminar, listOf("自制", "选择")),

        // 历史已有快照向下兼容保留（新生成卡不再引用）
        DailyExcerpt("zhuang-water", "且夫水之積也不厚，則負大舟也無力", "且夫水之积也不厚，则负大舟也无力", "庄子", "《逍遥游》", "https://zh.wikisource.org/wiki/莊子/逍遙遊", listOf("积累"), false),
        DailyExcerpt("tao-reading", "奇文共欣賞，疑義相與析", "奇文共欣赏，疑义相与析", "陶渊明", "《移居》其一", "https://zh.wikisource.org/wiki/移居", listOf("阅读", "友谊"), false),
        DailyExcerpt("liu-room", "苔痕上階綠，草色入簾青。談笑有鴻儒，往來無白丁", "苔痕上阶绿，草色入帘青\n谈笑有鸿儒，往来无白丁", "刘禹锡", "《陋室铭》", "https://zh.wikisource.org/wiki/陋室銘", listOf("日常"), false),
    )

    /** 严格溯源且可定位章节的哲学家语句池，供新卡生成使用；排除非哲学诗词、拉康及善恶彼岸存疑语句 */
    val generationPool: List<DailyExcerpt> = entries.filter {
        it.id !in setOf("zhuang-water", "tao-reading", "liu-room", "jgb272", "lacan-desire")
    }

    fun isSelectable(id: String): Boolean = generationPool.any { it.id == id }

    fun find(id: String): DailyExcerpt? = entries.firstOrNull { it.id == id }
}
