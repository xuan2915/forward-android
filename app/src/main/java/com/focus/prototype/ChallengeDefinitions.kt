package com.focus.prototype

import org.json.JSONArray
import org.json.JSONObject

enum class ChallengeCategory(val displayName: String) {
    CAPABILITY_BREAKTHROUGH("能力突破"),
    CREATIVE_DELIVERY("创造交付"),
    LIFE_EXPLORATION("生活探索"),
    BEHAVIOR_RESHAPING("行为重塑"),
}

enum class ChallengeDifficulty(val displayName: String) {
    ADVANCED("进阶"),
    BREAKTHROUGH("突破"),
    LIMIT("极限"),
}

enum class ChallengeStatus {
    OFFERED,
    REPLACED,
    ACTIVE,
    PAUSED,
    REVIEW_PENDING,
    COMPLETED,
    PARTIAL,
    INCOMPLETE,
    ABANDONED,
    SKIPPED,
    EXPIRED,
}

enum class MilestoneStatus {
    PENDING,
    COMPLETED,
    SKIPPED,
}

enum class VerificationType {
    AUTOMATIC,
    RULE_BASED,
    MANUAL,
}

enum class EvidenceType {
    TEXT,
    IMAGE,
    DOCUMENT,
    URI,
}

enum class EvidenceStatus {
    SUBMITTED,
    VERIFIED,
    REJECTED,
}

enum class ChallengeEventType {
    CHALLENGE_ISSUED,
    CHALLENGE_REPLACED,
    CHALLENGE_ACCEPTED,
    MILESTONE_COMPLETED,
    EVIDENCE_SUBMITTED,
    CHALLENGE_PAUSED,
    CHALLENGE_RESUMED,
    REVIEW_SUBMITTED,
    CHALLENGE_COMPLETED,
    CHALLENGE_ABANDONED,
    CHALLENGE_SKIPPED,
    CHALLENGE_EXPIRED,
    CHALLENGE_SETTLED,
    REWARD_GRANTED,
}

data class ChallengeMilestoneDefinition(
    val id: String,
    val sequence: Int,
    val title: String,
    val description: String,
    val progressWeight: Int?, // null until the product owner approves the template weights
    val required: Boolean = true,
    val verificationType: VerificationType = VerificationType.MANUAL,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("sequence", sequence)
        put("title", title)
        put("description", description)
        put("progressWeight", progressWeight ?: JSONObject.NULL)
        put("required", required)
        put("verificationType", verificationType.name)
    }

    companion object {
        fun fromJson(json: JSONObject): ChallengeMilestoneDefinition = ChallengeMilestoneDefinition(
            id = json.getString("id"),
            sequence = json.getInt("sequence"),
            title = json.getString("title"),
            description = json.getString("description"),
            progressWeight = if (json.has("progressWeight") && !json.isNull("progressWeight")) json.getInt("progressWeight") else null,
            required = json.optBoolean("required", true),
            verificationType = VerificationType.valueOf(json.optString("verificationType", VerificationType.MANUAL.name)),
        )
    }
}

data class ChallengeDifficultyConfig(
    val difficulty: ChallengeDifficulty,
    val description: String,
    val acceptanceCriteria: String,
    val recommendedHoursMin: Int?,
    val recommendedHoursMax: Int?,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("difficulty", difficulty.name)
        put("description", description)
        put("acceptanceCriteria", acceptanceCriteria)
        put("recommendedHoursMin", recommendedHoursMin ?: JSONObject.NULL)
        put("recommendedHoursMax", recommendedHoursMax ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(json: JSONObject): ChallengeDifficultyConfig = ChallengeDifficultyConfig(
            difficulty = ChallengeDifficulty.valueOf(json.getString("difficulty")),
            description = json.getString("description"),
            acceptanceCriteria = json.getString("acceptanceCriteria"),
            recommendedHoursMin = if (json.has("recommendedHoursMin") && !json.isNull("recommendedHoursMin")) json.getInt("recommendedHoursMin") else null,
            recommendedHoursMax = if (json.has("recommendedHoursMax") && !json.isNull("recommendedHoursMax")) json.getInt("recommendedHoursMax") else null,
        )
    }
}

data class ChallengeStoryContent(
    val offerStory: String,
    val milestone1Story: String,
    val milestone2Story: String,
    val finalStory: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("offerStory", offerStory)
        put("milestone1Story", milestone1Story)
        put("milestone2Story", milestone2Story)
        put("finalStory", finalStory)
    }

    companion object {
        fun fromJson(json: JSONObject): ChallengeStoryContent = ChallengeStoryContent(
            offerStory = json.getString("offerStory"),
            milestone1Story = json.getString("milestone1Story"),
            milestone2Story = json.getString("milestone2Story"),
            finalStory = json.getString("finalStory"),
        )
    }
}

data class ChallengeTemplate(
    val templateId: String,
    val version: Int,
    val category: ChallengeCategory,
    val title: String,
    val description: String,
    val coverAsset: String,
    val collectionAsset: String,
    val collectionTitle: String,
    val difficultyConfigs: Map<ChallengeDifficulty, ChallengeDifficultyConfig>,
    val milestoneDefinitions: List<ChallengeMilestoneDefinition>,
    val storyContent: ChallengeStoryContent,
    val tags: List<String>,
    val publishedStatus: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("templateId", templateId)
        put("version", version)
        put("category", category.name)
        put("title", title)
        put("description", description)
        put("coverAsset", coverAsset)
        put("collectionAsset", collectionAsset)
        put("collectionTitle", collectionTitle)
        put("publishedStatus", publishedStatus)

        val diffArray = JSONArray()
        difficultyConfigs.values.forEach { diffArray.put(it.toJson()) }
        put("difficultyConfigs", diffArray)

        val msArray = JSONArray()
        milestoneDefinitions.forEach { msArray.put(it.toJson()) }
        put("milestoneDefinitions", msArray)

        put("storyContent", storyContent.toJson())

        val tagArray = JSONArray()
        tags.forEach { tagArray.put(it) }
        put("tags", tagArray)
    }

    companion object {
        fun fromJson(json: JSONObject): ChallengeTemplate {
            val diffMap = mutableMapOf<ChallengeDifficulty, ChallengeDifficultyConfig>()
            val diffArray = json.getJSONArray("difficultyConfigs")
            for (i in 0 until diffArray.length()) {
                val cfg = ChallengeDifficultyConfig.fromJson(diffArray.getJSONObject(i))
                diffMap[cfg.difficulty] = cfg
            }

            val msList = mutableListOf<ChallengeMilestoneDefinition>()
            val msArray = json.getJSONArray("milestoneDefinitions")
            for (i in 0 until msArray.length()) {
                msList.add(ChallengeMilestoneDefinition.fromJson(msArray.getJSONObject(i)))
            }

            val tagList = mutableListOf<String>()
            val tagArray = json.optJSONArray("tags")
            if (tagArray != null) {
                for (i in 0 until tagArray.length()) {
                    tagList.add(tagArray.getString(i))
                }
            }

            return ChallengeTemplate(
                templateId = json.getString("templateId"),
                version = json.getInt("version"),
                category = ChallengeCategory.valueOf(json.getString("category")),
                title = json.getString("title"),
                description = json.getString("description"),
                coverAsset = json.getString("coverAsset"),
                collectionAsset = json.getString("collectionAsset"),
                collectionTitle = json.optString("collectionTitle", ""),
                difficultyConfigs = diffMap,
                milestoneDefinitions = msList,
                storyContent = ChallengeStoryContent.fromJson(json.getJSONObject("storyContent")),
                tags = tagList,
                publishedStatus = json.optBoolean("publishedStatus", true),
            )
        }
    }
}

object ChallengeCatalog {
    const val TEMPLATE_CAPABILITY = "template.capability.uncharted"
    const val TEMPLATE_CREATIVE = "template.creative.deliver_work"
    const val TEMPLATE_LIFE = "template.life.beyond_familiar"
    const val TEMPLATE_BEHAVIOR = "template.behavior.change_habit"

    val TEMPLATES: Map<String, ChallengeTemplate> by lazy {
        val list = listOf(
            // 1. 能力突破：未曾抵达之处
            ChallengeTemplate(
                templateId = TEMPLATE_CAPABILITY,
                version = 1,
                category = ChallengeCategory.CAPABILITY_BREAKTHROUGH,
                title = "未曾抵达之处",
                description = "选择一项具体能力，建立可重复的基准测试，通过针对性实践，在月底独立完成一项具有可比性的任务。",
                coverAsset = "assets/challenges/capability_cover.png",
                collectionAsset = "assets/collections/cross_ridge.png",
                collectionTitle = "越界",
                difficultyConfigs = mapOf(
                    ChallengeDifficulty.ADVANCED to ChallengeDifficultyConfig(
                        difficulty = ChallengeDifficulty.ADVANCED,
                        description = "超出目前的日常水平，有较高可完成性",
                        acceptanceCriteria = "独立完成1项与基准难度相当的变式任务，通过全部关键检查",
                        recommendedHoursMin = 3,
                        recommendedHoursMax = 4,
                    ),
                    ChallengeDifficulty.BREAKTHROUGH to ChallengeDifficultyConfig(
                        difficulty = ChallengeDifficulty.BREAKTHROUGH,
                        description = "持续投入解决真实问题，成果明显超出基准状态",
                        acceptanceCriteria = "独立完成2项任务，其中至少1项为未提前练习的迁移题",
                        recommendedHoursMin = 5,
                        recommendedHoursMax = 7,
                    ),
                    ChallengeDifficulty.LIMIT to ChallengeDifficultyConfig(
                        difficulty = ChallengeDifficulty.LIMIT,
                        description = "高难度、独立性更强或更复杂的成果",
                        acceptanceCriteria = "独立完成一项小型综合任务，提供运行或实操演示，并解释关键原理",
                        recommendedHoursMin = 8,
                        recommendedHoursMax = 10,
                    ),
                ),
                milestoneDefinitions = listOf(
                    ChallengeMilestoneDefinition("m1_baseline", 1, "确定起点", "完成基准测试并记录初始指标", null, true, VerificationType.MANUAL),
                    ChallengeMilestoneDefinition("m2_practice", 2, "针对性实践", "完成至少3次针对薄弱点的有效专项练习", null, true, VerificationType.MANUAL),
                    ChallengeMilestoneDefinition("m3_transfer", 3, "迁移验证", "完成不同条件下的变式任务实践", null, true, VerificationType.MANUAL),
                    ChallengeMilestoneDefinition("m4_final", 4, "最终证明", "完成对应难度的最终测试并提交验收", null, true, VerificationType.MANUAL),
                ),
                storyContent = ChallengeStoryContent(
                    offerStory = "从会做，到真正能独立完成，中间还有一段距离。这个月，我们走完它。",
                    milestone1Story = "第一次尝试暴露的问题，不是失败记录，而是接下来需要处理的对象。",
                    milestone2Story = "重复练习已经不够了。换一个条件，看看你是否真的掌握。",
                    finalStory = "这次挑战留下的不只是一个结果，还有一条能够证明、能够复现的进步记录。",
                ),
                tags = listOf("技能", "突破", "实操"),
                publishedStatus = false,
            ),

            // 2. 创造交付：留下作品
            ChallengeTemplate(
                templateId = TEMPLATE_CREATIVE,
                version = 1,
                category = ChallengeCategory.CREATIVE_DELIVERY,
                title = "留下作品",
                description = "选一个想法，把它做成一个可以独立展示、使用或交付的完整作品。",
                coverAsset = "assets/challenges/creative_cover.png",
                collectionAsset = "assets/collections/taking_shape.png",
                collectionTitle = "成形",
                difficultyConfigs = mapOf(
                    ChallengeDifficulty.ADVANCED to ChallengeDifficultyConfig(
                        difficulty = ChallengeDifficulty.ADVANCED,
                        description = "完成小作品，满足核心需求",
                        acceptanceCriteria = "完成一个具有明确用途的作品，满足预设的3项核心要求",
                        recommendedHoursMin = 3,
                        recommendedHoursMax = 5,
                    ),
                    ChallengeDifficulty.BREAKTHROUGH to ChallengeDifficultyConfig(
                        difficulty = ChallengeDifficulty.BREAKTHROUGH,
                        description = "经历实质性修改与反馈",
                        acceptanceCriteria = "完成作品，至少经历2轮实质性修改，并通过一次独立测试或反馈",
                        recommendedHoursMin = 5,
                        recommendedHoursMax = 8,
                    ),
                    ChallengeDifficulty.LIMIT to ChallengeDifficultyConfig(
                        difficulty = ChallengeDifficulty.LIMIT,
                        description = "完整可用成果与全套验收",
                        acceptanceCriteria = "交付一个完整可用的成果，包含核心功能、必要说明、演示和至少5项验收检查",
                        recommendedHoursMin = 8,
                        recommendedHoursMax = 12,
                    ),
                ),
                milestoneDefinitions = listOf(
                    ChallengeMilestoneDefinition("m1_charter", 1, "立项", "确定作品名称、具体用途与核心验收标准", null, true, VerificationType.MANUAL),
                    ChallengeMilestoneDefinition("m2_draft", 2, "初稿", "完成可以演示或展示的第一版完整雏形", null, true, VerificationType.MANUAL),
                    ChallengeMilestoneDefinition("m3_polish", 3, "打磨", "根据测试或他人反馈进行修改与迭代", null, true, VerificationType.MANUAL),
                    ChallengeMilestoneDefinition("m4_deliver", 4, "交付", "提交最终完整成果并记录验收", null, true, VerificationType.MANUAL),
                ),
                storyContent = ChallengeStoryContent(
                    offerStory = "想法不缺，真正留下来的作品却不多。这个月，为其中一个想法画上一个可以交付的句号。",
                    milestone1Story = "第一版不需要完美，但必须真实存在。现在，你已经有了可以继续改进的东西。",
                    milestone2Story = "做出来只是开始。真正的作品，还要经得起使用、检查和修改。",
                    finalStory = "你留下了一件可以展示、使用或继续发展的成果。它不再只是一个想法。",
                ),
                tags = listOf("创造", "交付", "作品"),
                publishedStatus = false,
            ),

            // 3. 生活探索：熟悉世界之外
            ChallengeTemplate(
                templateId = TEMPLATE_LIFE,
                version = 1,
                category = ChallengeCategory.LIFE_EXPLORATION,
                title = "熟悉世界之外",
                description = "给自己留出几次走出熟悉日常范围的机会，完成真正不同于以往的体验并真实记录。",
                coverAsset = "assets/challenges/life_cover.png",
                collectionAsset = "assets/collections/letters_from_afar.png",
                collectionTitle = "远方来信",
                difficultyConfigs = mapOf(
                    ChallengeDifficulty.ADVANCED to ChallengeDifficultyConfig(
                        difficulty = ChallengeDifficulty.ADVANCED,
                        description = "2次新体验，至少1类",
                        acceptanceCriteria = "完成2次新体验，覆盖至少1种新体验类别（每次≥30分钟）",
                        recommendedHoursMin = 2,
                        recommendedHoursMax = 4,
                    ),
                    ChallengeDifficulty.BREAKTHROUGH to ChallengeDifficultyConfig(
                        difficulty = ChallengeDifficulty.BREAKTHROUGH,
                        description = "4次新体验，至少2类",
                        acceptanceCriteria = "完成4次新体验，覆盖至少2种不同类别（每次≥30分钟）",
                        recommendedHoursMin = 4,
                        recommendedHoursMax = 6,
                    ),
                    ChallengeDifficulty.LIMIT to ChallengeDifficultyConfig(
                        difficulty = ChallengeDifficulty.LIMIT,
                        description = "6次新体验，至少3类",
                        acceptanceCriteria = "完成6次新体验，覆盖至少3种不同类别（每次≥30分钟）",
                        recommendedHoursMin = 6,
                        recommendedHoursMax = 8,
                    ),
                ),
                milestoneDefinitions = listOf(
                    ChallengeMilestoneDefinition("m1_plan", 1, "打开地图", "确定本月探索方向与备选新体验清单", null, true, VerificationType.MANUAL),
                    ChallengeMilestoneDefinition("m2_first_step", 2, "第一次出发", "完成首次新体验并记录新发现", null, true, VerificationType.MANUAL),
                    ChallengeMilestoneDefinition("m3_further", 3, "走得更远", "达到所选难度的阶段数量及类别要求", null, true, VerificationType.MANUAL),
                    ChallengeMilestoneDefinition("m4_collect", 4, "收集归来", "完成全部体验并提交体验记录与发现总结", null, true, VerificationType.MANUAL),
                ),
                storyContent = ChallengeStoryContent(
                    offerStory = "你不必走得很远，但可以尝试去一些以前不会选择的地方，做一些以前不会主动做的事。",
                    milestone1Story = "熟悉的路线之外，还有许多尚未体验的东西。第一次尝试已经开始。",
                    milestone2Story = "这次不要只选择最容易的事情。试着接触一个此前不太了解的领域。",
                    finalStory = "这个月，你的生活多了几段过去没有的经历。把它们留下来。",
                ),
                tags = listOf("探索", "体验", "生活"),
                publishedStatus = false,
            ),

            // 4. 行为重塑：改变一种习惯
            ChallengeTemplate(
                templateId = TEMPLATE_BEHAVIOR,
                version = 1,
                category = ChallengeCategory.BEHAVIOR_RESHAPING,
                title = "改变一种习惯",
                description = "选择一种具体行为，用为期一个月的行为实验与实际行动，验证真实改变是否发生。",
                coverAsset = "assets/challenges/behavior_cover.png",
                collectionAsset = "assets/collections/new_growth.png",
                collectionTitle = "新生",
                difficultyConfigs = mapOf(
                    ChallengeDifficulty.ADVANCED to ChallengeDifficultyConfig(
                        difficulty = ChallengeDifficulty.ADVANCED,
                        description = "计划行动达成率至少70%",
                        acceptanceCriteria = "行为实验中，预先冻结的计划行动达成率达到≥70%",
                        recommendedHoursMin = null,
                        recommendedHoursMax = null,
                    ),
                    ChallengeDifficulty.BREAKTHROUGH to ChallengeDifficultyConfig(
                        difficulty = ChallengeDifficulty.BREAKTHROUGH,
                        description = "计划行动达成率至少80%",
                        acceptanceCriteria = "行为实验中，预先冻结的计划行动达成率达到≥80%",
                        recommendedHoursMin = null,
                        recommendedHoursMax = null,
                    ),
                    ChallengeDifficulty.LIMIT to ChallengeDifficultyConfig(
                        difficulty = ChallengeDifficulty.LIMIT,
                        description = "计划行动达成率至少90%",
                        acceptanceCriteria = "行为实验中，预先冻结的计划行动达成率达到≥90%",
                        recommendedHoursMin = null,
                        recommendedHoursMax = null,
                    ),
                ),
                milestoneDefinitions = listOf(
                    ChallengeMilestoneDefinition("m1_observe", 1, "观察", "完成基准期记录与初始数据记录", null, true, VerificationType.MANUAL),
                    ChallengeMilestoneDefinition("m2_substitute", 2, "改变", "明确触发条件并启动替代行为实验", null, true, VerificationType.MANUAL),
                    ChallengeMilestoneDefinition("m3_persist", 3, "坚持", "完成中期连续执行并核算执行率", null, true, VerificationType.MANUAL),
                    ChallengeMilestoneDefinition("m4_compare", 4, "验证", "完成最终前后对比与习惯改变评估", null, true, VerificationType.MANUAL),
                ),
                storyContent = ChallengeStoryContent(
                    offerStory = "知道问题在哪里，不代表问题已经改变。这个月，选一件具体的事，看看你能否真正建立新的行为方式。",
                    milestone1Story = "先看清楚原来的行为模式。没有基准，就无法判断改变是否发生。",
                    milestone2Story = "现在最重要的不是偶尔表现得很好，而是让新的行动逐渐变得稳定。",
                    finalStory = "改变不一定一次完成，但这次你已经有了真实的执行记录和前后对比。",
                ),
                tags = listOf("习惯", "行为", "自律"),
                publishedStatus = false,
            ),
        )
        list.associateBy { it.templateId }
    }

    fun getTemplate(id: String): ChallengeTemplate? = TEMPLATES[id]

    fun validateCatalog() {
        require(TEMPLATES.size == 4) { "Must contain exactly 4 official templates, found ${TEMPLATES.size}" }
        TEMPLATES.values.forEach { template ->
            require(template.templateId.isNotBlank()) { "Template ID cannot be blank" }
            require(template.version > 0) { "Template version must be positive" }
            require(template.title.isNotBlank()) { "Template title cannot be blank" }
            require(template.milestoneDefinitions.isNotEmpty()) { "Milestone definitions cannot be empty" }

            if (template.publishedStatus) {
                require(template.milestoneDefinitions.all { (it.progressWeight ?: 0) > 0 }) {
                    "Published template ${template.templateId} has unresolved milestone weights"
                }
                val totalWeight = template.milestoneDefinitions.sumOf { it.progressWeight ?: 0 }
                require(totalWeight == 100) { "Template ${template.templateId} milestone weights sum to $totalWeight, must be exactly 100" }
                require(template.difficultyConfigs.values.all {
                    it.recommendedHoursMin != null && it.recommendedHoursMax != null && it.recommendedHoursMin > 0 && it.recommendedHoursMax >= it.recommendedHoursMin
                }) { "Published template ${template.templateId} has unresolved time estimates" }
            } else {
                require(template.milestoneDefinitions.all { it.progressWeight == null }) {
                    "Unpublished template ${template.templateId} must not keep unapproved milestone weights"
                }
            }

            require(template.difficultyConfigs.containsKey(ChallengeDifficulty.ADVANCED)) { "Template ${template.templateId} missing ADVANCED" }
            require(template.difficultyConfigs.containsKey(ChallengeDifficulty.BREAKTHROUGH)) { "Template ${template.templateId} missing BREAKTHROUGH" }
            require(template.difficultyConfigs.containsKey(ChallengeDifficulty.LIMIT)) { "Template ${template.templateId} missing LIMIT" }
        }
    }
}
