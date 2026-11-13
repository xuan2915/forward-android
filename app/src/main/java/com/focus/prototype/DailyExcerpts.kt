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
            .trim()

    val attribution: String get() = "$author · $displayWork"
}

internal object DailyExcerpts {
    private const val Nietzsche = "https://www.nietzschesource.org/texts/eKGWB/FW"
    private const val Seneca = "https://la.wikisource.org/wiki/De_brevitate_vitae"
    private const val Epictetus = "https://adfonteslibrary.org/work-epictetus-enchiridion"
    private const val Pascal = "https://fr.wikisource.org/wiki/Pensées_(Pascal,_éd._Brunschvicg)/Pensées/Section_VI"
    // Existing IDs are editions: add new entries for future revisions instead of editing these.
    val entries = listOf(
        DailyExcerpt("fw270", "Was sagt dein Gewissen? — Du sollst der werden, der du bist.", "你的良知说什么？你应当成为你自己", "尼采", "《快乐的科学》§270", Nietzsche, listOf("自我认识")),
        DailyExcerpt("brev1a", "Non exiguum temporis habemus, sed multum perdimus.", "我们拥有的时间并不少，只是失去了许多", "塞涅卡", "《论生命之短暂》I.3", Seneca, listOf("时间")),
        DailyExcerpt("ench1", "τῶν ὄντων τὰ μέν ἐστιν ἐφʼ ἡμῖν, τὰ δὲ οὐκ ἐφʼ ἡμῖν.", "有些事由我们决定，有些事不由我们决定", "爱比克泰德", "《手册》§1", Epictetus, listOf("自由", "自制")),
        DailyExcerpt("pascal-reed", "L’homme n’est qu’un roseau, le plus faible de la nature ; mais c’est un roseau pensant.", "人不过是自然中最脆弱的一茎芦苇，却是一茎会思想的芦苇", "帕斯卡", "《思想录》§347", Pascal, listOf("思想", "脆弱")),
        DailyExcerpt("fw276", "Amor fati: das sei von nun an meine Liebe!", "爱命运，愿这从此成为我的爱", "尼采", "《快乐的科学》§276", Nietzsche, listOf("命运")),
        DailyExcerpt("brev1b", "non accipimus brevem vitam, sed facimus, nec inopes eius sed prodigi sumus.", "我们得到的生命并不短，是我们让它变短。我们并非匮乏，而是挥霍", "塞涅卡", "《论生命之短暂》I.4", Seneca, listOf("时间", "选择")),
        DailyExcerpt("ench5", "ταράσσει τοὺς ἀνθρώπους οὐ τὰ πράγματα, ἀλλὰ τὰ περὶ τῶν πραγμάτων δόγματα", "困扰人的并非事情本身，而是人对事情的看法", "爱比克泰德", "《手册》§5", Epictetus, listOf("判断")),
        DailyExcerpt("fw335", "Wir aber wollen Die werden, die wir sind, — die Neuen, die Einmaligen, die Unvergleichbaren, die Sich-selber-Gesetzgebenden, die Sich-selber-Schaffenden!", "我们愿成为我们自己，成为崭新的、独一无二的、不可比拟的人，为自己立法，也创造自己", "尼采", "《快乐的科学》§335", Nietzsche, listOf("创造", "自我超越")),
        DailyExcerpt("brev2", "vita, si uti scias, longa est.", "若懂得如何使用生命，生命便是漫长的", "塞涅卡", "《论生命之短暂》II.1", Seneca, listOf("时间")),
        DailyExcerpt("ench8", "μὴ ζήτει τὰ γινόμενα γίνεσθαι ὡς θέλεις, ἀλλὰ θέλε τὰ γινόμενα ὡς γίνεται καὶ εὐροήσεις.", "别要求万事按你的愿望发生，试着接受它们发生的样子，生活便会顺畅些", "爱比克泰德", "《手册》§8", Epictetus, listOf("接受")),
        DailyExcerpt("pascal-dignity", "Ce n’est point de l’espace que je dois chercher ma dignité, mais c’est du règlement de ma pensée.", "我的尊严不在于占有多少空间，而在于如何约束自己的思想", "帕斯卡", "《思想录》§348", Pascal, listOf("尊严", "思想")),
        DailyExcerpt("ench9", "νόσος σώματός ἐστιν ἐμπόδιον, προαιρέσεως δὲ οὔ, ἐὰν μὴ αὐτὴ θέλῃ.", "疾病妨碍身体，却不必妨碍人的选择，除非选择本身让步", "爱比克泰德", "《手册》§9", Epictetus, listOf("选择")),
        DailyExcerpt("zhuang-water", "且夫水之積也不厚，則負大舟也無力", "且夫水之积也不厚，则负大舟也无力", "庄子", "《逍遥游》", "https://zh.wikisource.org/wiki/莊子/逍遙遊", listOf("积累"), false),
        DailyExcerpt("tao-reading", "奇文共欣賞，疑義相與析", "奇文共欣赏，疑义相与析", "陶渊明", "《移居》其一", "https://zh.wikisource.org/wiki/移居", listOf("阅读", "友谊"), false),
        DailyExcerpt("liu-room", "苔痕上階綠，草色入簾青。談笑有鴻儒，往來無白丁", "苔痕上阶绿，草色入帘青\n谈笑有鸿儒，往来无白丁", "刘禹锡", "《陋室铭》", "https://zh.wikisource.org/wiki/陋室銘", listOf("日常"), false),
    )
    fun find(id: String): DailyExcerpt? = entries.firstOrNull { it.id == id }
}
