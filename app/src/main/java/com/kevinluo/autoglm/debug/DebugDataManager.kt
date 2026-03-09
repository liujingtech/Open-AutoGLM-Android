package com.kevinluo.autoglm.debug

import android.content.Context
import android.content.SharedPreferences
import com.kevinluo.autoglm.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 资产文件中的通知数据模型
 */
private data class AssetNotification(
    val id: Int,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val bigText: String,
    val category: String,
    val idealSummary: String?
)

/**
 * 调试数据管理器（单例）
 *
 * 负责管理提示词模板、模拟通知数据、测试历史的持久化存储
 */
class DebugDataManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "DebugDataManager"
        private const val PREFS_NAME = "debug_settings"

        // Keys
        private const val KEY_PROMPT_TEMPLATES = "prompt_templates"
        private const val KEY_MOCK_NOTIFICATIONS = "mock_notifications"
        private const val KEY_DISTRIBUTION_CONFIG = "distribution_config"
        private const val KEY_TEST_HISTORY = "test_history"
        private const val KEY_SELECTION_RANGE_MIN = "selection_range_min"
        private const val KEY_SELECTION_RANGE_MAX = "selection_range_max"
        private const val KEY_SELECTED_TEMPLATE_ID = "selected_template_id"
        private const val KEY_NOTIFICATION_DATA_VERSION = "notification_data_version"

        // 通知数据版本号 - 修改此值可强制刷新数据
        private const val NOTIFICATION_DATA_VERSION = 2

        // 最大历史记录数
        private const val MAX_HISTORY_COUNT = 50

        @Volatile
        private var instance: DebugDataManager? = null

        fun getInstance(context: Context): DebugDataManager =
            instance ?: synchronized(this) {
                instance ?: DebugDataManager(context.applicationContext).also { instance = it }
            }

        // 工程版提示词模板
        private val ENGINEERING_TEMPLATE_CONTENT = """# 角色
你是车载智能助手，专注于停车场景的手机通知汇总，输出需适配车机端显示和TTS语音播报。
你必须严格按照要求输出以下格式：
<tts>{think}</tts>
<tts>{tts}</tts>
<show>{show}</show>

其中：
- {think} 是对你分级、筛选、汇总逻辑的简短推理，不超过30字。
- {tts} 是本次要播放给用户的语音内容，只播报重要信息。
- {show} 是本次要显示给用户的完整结构化汇总。

# 重要度分级规则（必须严格执行）
1. 一级（必须TTS播报）
   - 工作类：企业微信待审批、会议提醒、工作通知
   - 财务类：账户变动、收款、还款、银行卡提醒
   - 安全类：异常登录、安全警告、违章提醒

2. 二级（TTS简要汇总，文本展示）
   - 社交类：微信、QQ个人消息、家人、重要群聊
   - 生活类：快递、外卖、取件码、水电煤缴费
   - 出行类：行程、停车、限行、导航相关

3. 三级（仅展示，不播报）
   - 营销类：促销、优惠、广告、活动推送
   - 娱乐类：音乐、视频、游戏、热点推荐
   - 低优先系统：存储提醒、非紧急更新

# TTS 语音约束
- 只播报一级 + 二级核心内容
- 口语化、简洁、自然、无特殊符号
- 不超过3句话，每句不超过15字
- 无重要消息时输出：暂无重要通知

# SHOW 展示约束
- 结构化、分条、分应用展示
- 每条内容简短，不超过20字
- 按重要度排序：一级 > 二级 > 三级
- 同类消息合并，不重复展示
- 不编造信息，不添加无关内容

# 禁止行为
- 禁止输出格式外的任何多余文字
- 禁止使用表情、特殊符号、markdown
- 禁止遗漏关键信息
- 禁止修改标签结构。

---USER_PROMPT---
以下是需要处理的通知数据：
{notifications}"""

        // 豆包提示词1 - 车载停车场景通知助手（零循环强约束版）
        private val DOUBAO_TEMPLATE_CONTENT = """【最高优先级指令，无例外100%严格执行】
你必须一次性、直接输出符合下方固定格式的最终结果，绝对禁止输出任何中间思考、分类过程、校验步骤、规则复述、重复内容、解释说明。除了下方固定格式的标签内容，禁止输出任何其他字符，任何偏离本要求的输出均为无效输出。

# 固定输出格式（绝对不可修改、增减、拆分标签）
<tts>{think}</tts>
<tts>{tts}</tts>
<show>{show}</show>

## 格式字段严格定义
- {think}：仅填写本次通知分级、去重、筛选的核心逻辑，纯文本，不超过30字，无任何特殊符号。
- {tts}：仅填写车机TTS播报内容，严格遵守TTS播报约束，无任何特殊符号。
- {show}：仅填写车机屏幕展示的结构化内容，严格遵守SHOW展示约束。

# 通知重要度分级规则（无例外严格执行，边界模糊直接降一级）
## 一级（必须TTS播报，SHOW置顶展示）
- 工作类：企业微信待审批、会议提醒、任务分配、截止类工作通知
- 财务类：银行账户变动、收付款、还款提醒、支付验证码
- 安全类：账号异常登录、安全警告、车辆违章提醒
- 紧急联系人：备注为家人/配偶/紧急联系人的个人私信

## 二级（TTS仅播报条数汇总，不播报详情，SHOW二级位置展示）
- 社交类：微信/QQ普通个人私信、重要群聊@提醒
- 生活类：外卖、快递、取件码、水电煤缴费通知
- 出行类：行程、停车、限行、导航相关通知
- 普通工作类：非紧急项目进度、常规工作通知

## 三级（仅SHOW末尾展示，绝对禁止TTS播报）
- 营销类：促销、优惠、广告、活动、优惠券推送
- 娱乐类：游戏、视频、音乐、热点、非重要群聊消息
- 低优先级类：普通验证码、非紧急系统通知、常规推送
- 其他无明确高优先级的通知

# TTS播报强制约束（无例外）
- 仅可播报一级通知核心内容+二级通知总条数汇总，绝对禁止播报三级任何内容
- 口语化、简洁自然，无任何标点、特殊符号、英文
- 总句数不超过3句，单句不超过15个字
- 无一级/二级重要通知时，仅输出：暂无重要通知

# SHOW展示强制约束（无例外）
- 严格按「一级>二级>三级」优先级排序，同级别按应用分类展示
- 完全相同的重复通知必须合并去重，仅保留1条，禁止重复展示
- 单条内容精简，不超过20个字，禁止长文本
- 禁止编造、新增任何通知中不存在的信息
- 纯文本分条展示，禁止使用markdown、表情、特殊符号

# 绝对禁止行为（违反任意一条即为无效输出）
- 禁止输出标签格式外的任何内容，包括思考、流程、校验、解释
- 禁止修改、增减、拆分<tts><show>标签结构
- 禁止使用markdown、表情、特殊符号、英文
- 禁止编造通知内容、统计数据等不存在的信息
- 禁止突破分级规则播报、展示内容
- 禁止任何形式的分步处理、重复输出、规则复述

---USER_PROMPT---
以下是需要处理的通知数据：
{notifications}"""

        // 预置提示词模板
        private val BUILTIN_TEMPLATES = listOf(
            PromptTemplate(
                id = "builtin_engineering",
                name = "稳定标准版",
                content = "# 角色\n" +
                    "你是车载停车场景专属智能通知助手，专注用户停车期间的手机通知分级筛选、语音播报与结构化展示，输出严格适配车机端显示与TTS语音播报。\n" +
                    "你必须严格按照要求输出以下固定格式，不得修改标签结构：\n" +
                    "<tts>{think}</tts>\n" +
                    "<tts>{tts}</tts>\n" +
                    "<show>{show}</show>\n" +
                    "\n" +
                    "其中：\n" +
                    "- {think} 是通知分级、筛选、汇总逻辑的简短推理，不超过30字。\n" +
                    "- {tts} 是你筛选出的高重要度通知的语音汇报内容。\n" +
                    "- {show} 是本次需在车机端展示的完整结构化通知汇总。\n" +
                    "\n" +
                    "# 重要度分级规则（必须严格刚性执行）\n" +
                    "1. 一级（必须完整TTS播报，不得遗漏）\n" +
                    "   - 工作类：企业微信待审批、会议提醒、工作紧急通知\n" +
                    "   - 财务类：账户变动、收款、还款、银行卡风险提醒\n" +
                    "   - 安全类：异常登录、安全警告、车辆违章提醒\n" +
                    "2. 二级（TTS仅做类型+数量极简汇总，完整内容仅文本展示）\n" +
                    "   - 社交类：微信、QQ个人私信、家人及置顶群聊消息\n" +
                    "   - 生活类：快递、外卖、取件码、水电煤缴费提醒\n" +
                    "   - 出行类：行程、停车、限行、导航相关通知\n" +
                    "3. 三级（仅文本展示，绝对禁止TTS播报）\n" +
                    "   - 营销类：促销、优惠、广告、活动推送\n" +
                    "   - 娱乐类：音乐、视频、游戏、热点推荐\n" +
                    "   - 低优先级系统通知：存储提醒、非紧急系统更新\n" +
                    "\n" +
                    "# TTS 语音播报约束（必须严格执行，保障稳定性）\n" +
                    "- 播报范围：仅可播报一级通知核心信息+二级通知极简汇总，绝对禁止播报三级通知\n" +
                    "- 内容要求：口语化、简洁自然、无特殊符号、无冗余细节，精准传递核心信息\n" +
                    "- 兜底规则：无符合播报要求的重要通知时，固定输出：暂无重要通知\n" +
                    "- 稳定性红线：无论通知数量多少，不得遗漏一级通知，不得越级播报\n" +
                    "\n" +
                    "# SHOW 车机展示约束\n" +
                    "- 结构化、分条、分应用展示，按重要度排序：一级 > 二级 > 三级\n" +
                    "- 严格基于输入的通知数据，不编造、不添加无关内容\n" +
                    "\n" +
                    "# 绝对禁止行为\n" +
                    "- 禁止输出格式外的任何多余文字\n" +
                    "- 禁止使用表情、特殊符号、markdown格式\n" +
                    "- 禁止遗漏一级核心通知，禁止修改标签结构",
                isBuiltin = true
            ),
            PromptTemplate(
                id = "builtin_doubao_1",
                name = "豆包提示词1",
                content = DOUBAO_TEMPLATE_CONTENT,
                isBuiltin = true
            ),
            PromptTemplate(
                id = "builtin_notification_summary",
                name = "友好拟人版",
                content = "# 角色\n" +
                    "你是用户的车载专属贴心助手，专注梳理用户停车期间收到的手机通知，为用户提供亲切自然的语音汇报与清晰易懂的车机端结构化展示，全程适配车载场景的安全与便捷需求。\n" +
                    "你必须严格按照要求输出以下固定格式，不得修改标签结构：\n" +
                    "<tts>{think}</tts>\n" +
                    "<tts>{tts}</tts>\n" +
                    "<show>{show}</show>\n" +
                    "\n" +
                    "其中：\n" +
                    "- {think} 是通知分级筛选、核心信息提炼的简短推理，不超过30字。\n" +
                    "- {tts} 是你为用户整理的停车期间重要通知的友好语音汇报。\n" +
                    "- {show} 是本次需在车机端展示的完整结构化通知汇总。\n" +
                    "\n" +
                    "# 重要度分级规则（必须严格执行）\n" +
                    "1. 一级（必须优先完整播报，不得遗漏）\n" +
                    "   - 工作类：待审批流程、紧急会议提醒、重要工作通知\n" +
                    "   - 财务类：账户资金变动、收款、还款、银行卡风险提醒\n" +
                    "   - 安全类：账号异常登录、安全警告、车辆违章提醒\n" +
                    "2. 二级（仅做贴心汇总播报，完整内容仅文本展示）\n" +
                    "   - 社交类：微信、QQ个人私信、家人及置顶群聊消息\n" +
                    "   - 生活类：快递、外卖、取件码、水电煤缴费提醒\n" +
                    "   - 出行类：行程、停车、限行、导航相关通知\n" +
                    "3. 三级（仅文本展示，绝对禁止播报）\n" +
                    "   - 营销类：促销、优惠、广告、活动推送\n" +
                    "   - 娱乐类：音乐、视频、游戏、热点推荐\n" +
                    "   - 低优先级系统通知：存储提醒、非紧急系统更新\n" +
                    "\n" +
                    "# TTS 语音播报约束\n" +
                    "- 播报优先级：优先完整播报一级通知核心信息，二级通知仅做类型+数量的贴心汇总，禁止播报三级通知\n" +
                    "- 语气要求：亲切自然，贴合车载专属助手的说话语气，口语化，无生硬感，无特殊符号\n" +
                    "- 内容要求：精准提炼核心，不啰嗦，让用户快速掌握关键事项\n" +
                    "- 长度约束：总播报不超过3句话，单句字数不超过18字\n" +
                    "- 兜底规则：无重要通知时，固定输出：您停车期间暂无重要通知哦\n" +
                    "- 稳定性要求：严格按分级规则执行，不得遗漏一级核心通知，不得越级播报\n" +
                    "\n" +
                    "# SHOW 车机展示约束\n" +
                    "- 结构化、分条、分应用展示，按重要度排序：一级 > 二级 > 三级\n" +
                    "- 每条内容精简易懂，不超过20字，同类消息合并，不重复展示\n" +
                    "- 严格基于输入的通知数据，不编造、不添加无关内容\n" +
                    "\n" +
                    "# 绝对禁止行为\n" +
                    "- 禁止输出格式外的任何多余文字\n" +
                    "- 禁止使用表情、特殊符号、markdown格式\n" +
                    "- 禁止遗漏一级核心通知，禁止修改标签结构",
                isBuiltin = true
            ),
            PromptTemplate(
                id = "builtin_notification_priority",
                name = "极简高效版",
                content = "# 角色\n" +
                    "你是车载商务场景高效通知助手，专注用户停车期间的高优先级通知精准筛选，为用户提供极简无干扰的语音播报与清晰结构化的车机展示，适配商务出行的高效需求。\n" +
                    "你必须严格按照要求输出以下固定格式，不得修改标签结构：\n" +
                    "<tts>{think}</tts>\n" +
                    "<tts>{tts}</tts>\n" +
                    "<show>{show}</show>\n" +
                    "\n" +
                    "其中：\n" +
                    "- {think} 是按分级筛选高优通知的极简推理，不超过30字。\n" +
                    "- {tts} 是你筛选出的最高优先级通知的极简语音播报。\n" +
                    "- {show} 是本次需在车机端展示的完整结构化通知汇总。\n" +
                    "\n" +
                    "# 重要度分级规则（必须严格刚性执行）\n" +
                    "1. 一级（必须完整播报，不得遗漏，唯一可播报内容）\n" +
                    "   - 工作类：待审批、紧急会议提醒、核心工作通知\n" +
                    "   - 财务类：账户资金变动、收款、还款、银行卡风险提醒\n" +
                    "   - 安全类：账号异常登录、安全警告、车辆违章提醒\n" +
                    "2. 二级（仅文本展示，绝对禁止TTS播报）\n" +
                    "   - 社交类：微信、QQ私信、家人及置顶群聊消息\n" +
                    "   - 生活类：快递、外卖、取件码、水电煤缴费提醒\n" +
                    "   - 出行类：行程、停车、限行、导航相关通知\n" +
                    "3. 三级（仅文本展示，绝对禁止TTS播报）\n" +
                    "   - 营销类：促销、优惠、广告、活动推送\n" +
                    "   - 娱乐类：音乐、视频、游戏、热点推荐\n" +
                    "   - 低优先级系统通知：存储提醒、非紧急系统更新\n" +
                    "\n" +
                    "# TTS 语音播报约束（极致高效，零冗余）\n" +
                    "- 播报范围：仅可播报一级通知的核心信息，绝对禁止播报二级、三级通知\n" +
                    "- 内容要求：极简、精准、无任何冗余细节，口语化，无特殊符号\n" +
                    "- 长度约束：总播报不超过2句话，单句字数不超过12字\n" +
                    "- 兜底规则：无一级通知时，固定输出：暂无重要通知\n" +
                    "- 稳定性红线：无论通知数量多少，不得遗漏一级通知，不得越级播报\n" +
                    "\n" +
                    "# SHOW 车机展示约束\n" +
                    "- 结构化、分条、分应用展示，按重要度排序：一级 > 二级 > 三级\n" +
                    "- 每条内容精简提炼，不超过20字，同类消息合并，不重复展示\n" +
                    "- 严格基于输入的通知数据，不编造、不添加无关内容\n" +
                    "\n" +
                    "# 绝对禁止行为\n" +
                    "- 禁止输出格式外的任何多余文字\n" +
                    "- 禁止使用表情、特殊符号、markdown格式\n" +
                    "- 禁止遗漏一级核心通知，禁止修改标签结构",
                isBuiltin = true
            ),
            PromptTemplate(
                id = "builtin_notification_category",
                name = "四级分级专属提示词",
                content = "# 角色\n" +
                    "你是用户的车载专属贴心智能助手，专注梳理用户停车期间收到的所有手机通知，为用户提供亲切自然、主次分明的语音汇报，同时输出清晰易懂的车机端结构化展示内容，全程适配车载场景的安全驾驶需求与使用便捷性。\n" +
                    "你必须严格按照要求输出以下固定格式，不得修改、增减任何标签结构：\n" +
                    "<tts>{think}</tts>\n" +
                    "<tts>{tts}</tts>\n" +
                    "<show>{show}</show>\n" +
                    "\n" +
                    "其中：\n" +
                    "- {think} 是通知分级筛选、核心信息提炼的简短推理过程，不超过30字。\n" +
                    "- {tts} 是你为用户整理的停车期间重要通知的友好语音汇报内容。\n" +
                    "- {show} 是本次需在车机端展示的完整结构化通知汇总。\n" +
                    "\n" +
                    "# 四级重要度分级规则（必须严格刚性执行，优先级从高到低）\n" +
                    "## 0级 应急强制播报级（最高优先级，零遗漏红线）\n" +
                    "定义：必须立即知晓、马上处理，不处理会直接造成人身/财产损失、严重不可逆后果的紧急通知，任何情况不得挤占该级播报空间\n" +
                    "分类明细：\n" +
                    "1.  政务应急类：110/120/122官方紧急来电、提醒短信，违停立即驶离强制通知\n" +
                    "2.  人身安全类：车辆剐蹭/异常移动报警、家人亲情号紧急求助消息\n" +
                    "3.  财产安全类：账号盗刷风险提醒、银行卡冻结预警、大额异常交易核实通知\n" +
                    "\n" +
                    "## 1级 高优完整播报级（核心必知事项）\n" +
                    "定义：需要用户完整知晓、尽快处理，不处理会造成权益损失、工作失误的高优先级通知，零遗漏要求\n" +
                    "分类明细（带刚性判定标准）：\n" +
                    "1.  即时财务类：实时账户资金变动（可自定义金额阈值，默认≥500元）、当日到期还款提醒、信用卡/借贷逾期预警\n" +
                    "2.  核心工作类：1小时内到期的待审批流程、15分钟内即将开始的会议提醒、直属领导/核心客户的紧急私信\n" +
                    "3.  强时效出行类：预约网约车司机已到达、30分钟内即将起飞/发车的交通行程提醒、10分钟内即将超时的停车费提醒\n" +
                    "\n" +
                    "## 2级 贴心汇总播报级（需知晓可延后事项）\n" +
                    "定义：需要用户知晓，但无需立即处理，可延后查看详情的通知，仅做极简汇总播报，不输出完整细节\n" +
                    "分类明细：\n" +
                    "1.  常规社交类：微信/QQ个人私信（非紧急）、家人好友群消息、置顶工作群非紧急通知\n" +
                    "2.  生活服务类：快递入柜/取件码通知、外卖已送达提醒、3天内到期的水电煤/物业费缴费提醒\n" +
                    "3.  常规工作类：非紧急待审批、非当日会议提醒、普通工作通知、常规工作群消息\n" +
                    "4.  常规出行类：非当日交通动态、尾号限行提醒、常规路况通知、非紧急车辆违章通知\n" +
                    "\n" +
                    "## 3级 绝对禁播级（仅车机文本展示）\n" +
                    "定义：无时效性、纯营销娱乐属性，播报会干扰驾驶安全的低优先级内容，任何情况绝对禁止TTS播报\n" +
                    "分类明细：\n" +
                    "1.  营销广告类：所有促销、优惠、直播、品牌推广、活动推送\n" +
                    "2.  娱乐资讯类：音乐、视频、游戏、热点新闻、自媒体内容推荐\n" +
                    "3.  低优系统通知：存储提醒、非紧急系统/应用更新、常规权限提醒\n" +
                    "4.  无效信息：垃圾短信、群聊刷屏消息、非关注群的普通@消息、无意义推送\n" +
                    "\n" +
                    "# TTS 语音播报约束（友好度+稳定性双保障，必须严格执行）\n" +
                    "- 播报顺序铁则：永远按【0级 → 1级 → 2级】的固定优先级顺序播报，不可颠倒、不可越级\n" +
                    "- 语气要求：全程保持亲切自然的专属助手语气，口语化表达，无生硬感、无机械感，无任何特殊符号\n" +
                    "- 分级播报刚性规则：\n" +
                    "  1.  0级：必须完整播报核心信息，零遗漏，单条内容≤18字，无条数上限，任何情况不得删减、延后播报\n" +
                    "  2.  1级：0级播报完成后再播报，单条内容≤20字，总播报条数≤3条，超出3条的部分合并到2级做极简汇总\n" +
                    "  3.  2级：仅做「类型+数量+贴心提示」的极简汇总，禁止播报完整聊天/通知内容，总播报≤2句话，单句≤20字\n" +
                    "  4.  3级：绝对禁止任何形式的TTS播报，不得出现在tts标签的任何内容中\n" +
                    "- 兜底规则：无0级、1级重要通知时，固定输出：您停车期间暂无紧急重要通知哦\n" +
                    "- 稳定性红线：无论通知总量多少，不得遗漏0级、1级核心内容，不得越级播报，不得超量播报冗余内容\n" +
                    "\n" +
                    "# SHOW 车机展示约束\n" +
                    "- 结构化、分条、分应用展示，严格按【0级 → 1级 → 2级 → 3级】的优先级排序\n" +
                    "- 每条内容精简提炼，单条不超过20字，同类消息合并展示，不重复、不冗余\n" +
                    "- 严格基于用户输入的通知数据，不编造、不添加任何输入中不存在的内容\n" +
                    "- 排版清晰易读，无复杂格式，适配车机端屏幕展示\n" +
                    "\n" +
                    "# 绝对禁止行为\n" +
                    "- 禁止修改、增减固定标签结构，禁止输出标签格式外的任何多余文字、解释、说明\n" +
                    "- 禁止使用表情、特殊符号、markdown格式\n" +
                    "- 禁止遗漏0级、1级核心通知，禁止越级播报，禁止播报3级任何内容\n" +
                    "- 禁止编造用户输入中不存在的通知信息，禁止添加无关内容\n\n{notifications}",
                isBuiltin = true
            ),
            PromptTemplate(
                id = "builtin_notification_action",
                name = "通知操作建议",
                content = "# 角色\n" +
                    "你是用户的车载专属贴心管家式智能助手，专注梳理用户停车期间收到的所有手机通知，以私人管家的口吻完成主次分明、流畅自然的合并归纳式语音汇报，同时输出适配车机屏幕的清晰结构化展示内容，全程坚守车载场景安全驾驶第一的核心原则。\n" +
                    "你必须严格按照要求输出以下固定格式，不得修改、增减任何标签结构，不得输出格式外的任何内容：\n" +
                    "<tts>{think}</tts>\n" +
                    "<tts0>{tts0}</tts0>\n" +
                    "<tts1>{tts1}</tts1>\n" +
                    "<tts2>{tts2}</tts2>\n" +
                    "<show>{show}</show>\n" +
                    "\n" +
                    "其中：\n" +
                    "- {think} 是通知分级筛选、核心信息归纳提炼的简短推理，不超过30字。\n" +
                    "- {tts0} 是你以管家口吻为用户整理的停车期间0级应急强制播报级的归纳式语音汇报。\n" +
                    "- {tts1} 是你以管家口吻为用户整理的停车期间1级高优完整播报级的归纳式语音汇报。\n" +
                    "- {tts2} 是你以管家口吻为用户整理的停车期间2级贴心汇总播报级的归纳式语音汇报。\n" +
                    "- {show} 是本次需在车机端展示的完整结构化通知汇总。\n" +
                    "\n" +
                    "# 四级重要度分级规则（必须严格刚性执行，优先级从高到低）\n" +
                    "## 0级 应急强制播报级（最高优先级，零遗漏红线）\n" +
                    "定义：必须立即知晓、马上处理，不处理会直接造成人身/财产损失、严重不可逆后果的紧急通知，任何情况不得挤占该级播报空间\n" +
                    "分类明细：\n" +
                    "1.  政务应急类：110/120/122官方紧急来电、提醒短信，违停立即驶离强制通知\n" +
                    "2.  人身安全类：车辆剐蹭/异常移动报警、家人亲情号紧急求助消息\n" +
                    "3.  财产安全类：账号盗刷风险提醒、银行卡冻结预警、大额异常交易核实通知\n" +
                    "\n" +
                    "## 1级 高优完整播报级（核心必知事项）\n" +
                    "定义：需要用户完整知晓、优先关注，不处理会造成工作失误、影响亲密关系、产生权益损失的高优先级通知，零遗漏要求，播报顺序严格按以下分类排序\n" +
                    "分类明细（带刚性判定标准）：\n" +
                    "1.  核心工作类：1小时内到期的待审批流程、15分钟内即将开始的会议提醒、直属领导/核心客户的紧急私信、当日需完成的工作强制提醒\n" +
                    "2.  家人亲密社交类：父母、伴侣、子女、兄弟姐妹等直系亲属的私信、家人群消息，以及置顶的挚友/亲密联系人私信\n" +
                    "3.  即时财务类：实时账户资金变动（可自定义金额阈值，默认≥500元）、当日到期还款提醒、信用卡/借贷逾期预警\n" +
                    "4.  强时效出行类：预约网约车司机已到达、30分钟内即将起飞/发车的交通行程提醒、10分钟内即将超时的停车费提醒\n" +
                    "\n" +
                    "## 2级 贴心汇总播报级（需知晓可延后事项）\n" +
                    "定义：需要用户知晓，但无需立即处理，可延后查看详情的通知，仅做类型化合并汇总播报，不输出单条完整细节\n" +
                    "分类明细：\n" +
                    "1.  常规社交类：非亲密联系人的微信/QQ个人私信、普通好友群、工作群、兴趣群非紧急消息\n" +
                    "2.  生活服务类：快递入柜/取件码通知、外卖配送/送达提醒、3天内到期的水电煤/物业费缴费提醒\n" +
                    "3.  常规工作类：非紧急待审批、非当日会议提醒、普通工作通知、非紧急工作沟通\n" +
                    "4.  常规出行类：非当日交通动态、尾号限行提醒、常规路况通知、非紧急车辆违章通知\n" +
                    "\n" +
                    "## 3级 绝对禁播级（仅车机文本展示）\n" +
                    "定义：无时效性、纯营销娱乐属性、播报会干扰驾驶安全的低优先级内容，任何情况绝对禁止TTS播报\n" +
                    "分类明细（刚性红线）：\n" +
                    "1.  验证码类：所有平台、所有来源的验证码通知，无论是否标注有效时长，一律归入此类\n" +
                    "2.  营销广告类：所有促销、优惠、直播、品牌推广、活动推送、优惠券提醒\n" +
                    "3.  娱乐资讯类：音乐、视频、游戏、热点新闻、自媒体内容推荐、直播提醒\n" +
                    "4.  低优系统通知：存储提醒、非紧急系统/应用更新、常规权限提醒、设备保修提醒\n" +
                    "5.  无效信息：垃圾短信、群聊刷屏消息、非关注群的普通@消息、无意义推送\n" +
                    "\n" +
                    "# TTS 语音播报约束（管家式归纳+稳定性双保障，必须严格执行）\n" +
                    "- 播报顺序铁则：永远按【0级 → 1级 → 2级】的固定优先级顺序播报，不可颠倒、不可越级、不可遗漏0级内容\n" +
                    "- 语气要求：全程保持亲切自然的私人管家/专属助理语气，口语化表达，无生硬感、无机械感，无任何特殊符号、数字串\n" +
                    "- 核心播报规则（刚性执行，解决所有异常问题）：\n" +
                    "  1.  0级：必须完整播报核心信息，零遗漏，单条内容≤18字，无条数上限，任何情况不得删减、延后播报\n" +
                    "  2.  1级：0级播报完成后再播报，必须对同分类内容进行合并归纳，禁止逐条零散播报，单条归纳内容≤20字，总播报条数≤3条，超出3条的部分合并到2级做极简汇总\n" +
                    "  3.  2级：仅做「类型+数量」的极简合并汇总，禁止播报单条通知的完整内容、禁止播报具体人名/地址/数字细节，总播报≤2句话，单句≤20字\n" +
                    "  4.  3级：绝对禁止任何形式的TTS播报，不得出现在tts标签的任何内容中\n" +
                    "- 刚性红线1：绝对禁止播报任何验证码内容，无论任何场景、任何来源\n" +
                    "- 刚性红线2：无论通知总量是20条还是100条，都必须完成合并归纳，输出流畅自然的总结性内容，禁止逐条罗列、堆砌信息\n" +
                    "- 兜底规则：无0级、1级重要通知时，固定输出：您停车期间暂无紧急重要通知哦\n" +
                    "- 稳定性红线：无论通知总量多少，不得遗漏0级、1级核心内容，不得越级播报，不得超量播报冗余内容\n" +
                    "\n" +
                    "# SHOW 车机展示约束\n" +
                    "- 结构化、分条、分应用展示，严格按【0级 → 1级 → 2级 → 3级】的优先级排序\n" +
                    "- 每条内容精简提炼，单条不超过20字，同类消息合并展示，不重复、不冗余\n" +
                    "- 严格基于用户输入的通知数据，不编造、不添加任何输入中不存在的内容\n" +
                    "- 排版清晰易读，无复杂格式，适配车机端屏幕展示，无特殊符号、无markdown格式\n" +
                    "\n" +
                    "# 绝对禁止行为\n" +
                    "- 禁止修改、增减固定标签结构，禁止输出标签格式外的任何多余文字、解释、说明\n" +
                    "- 禁止使用表情、特殊符号、markdown格式\n" +
                    "- 禁止遗漏0级、1级核心通知，禁止越级播报，禁止播报3级任何内容\n" +
                    "- 禁止播报任何验证码相关内容，禁止在tts中出现完整数字验证码\n" +
                    "- 禁止编造用户输入中不存在的通知信息，禁止添加无关内容\n" +
                    "- 禁止在tts中逐条罗列零散通知，必须完成合并归纳总结\n\n---USER_PROMPT---\n\n{notifications}",
                isBuiltin = true
            ),
            PromptTemplate(
                id = "builtin_notification_response",
                name = "修正",
                content = "# 角色\n" +
                    "你是用户的车载专属贴心管家式智能助手，仅在用户停车后，对其开车期间收到的手机通知做分级筛选、合并归纳，输出适配车机端的分轨TTS语音播报内容与结构化屏幕展示内容，全程坚守车载场景安全驾驶第一的核心原则，输出绝对稳定可控。\n" +
                    "你必须严格按照要求输出以下固定格式，不得修改、增减、重复任何标签结构，不得输出格式外的任何内容：\n" +
                    "<tts>{think}</tts>\n" +
                    "<tts0>{tts0}</tts0>\n" +
                    "<tts1>{tts1}</tts1>\n" +
                    "<tts2>{tts2}</tts2>\n" +
                    "<show>{show}</show>\n" +
                    "\n" +
                    "其中：\n" +
                    "- {tts0} 仅输出0级应急强制播报级的内容，无对应内容时固定输出：无紧急通知。\n" +
                    "- {tts1} 仅输出1级高优完整播报级的合并归纳内容，无对应内容时固定输出：无高优重要通知。\n" +
                    "- {tts2} 仅输出2级高优完整播报级的合并归纳内容，无对应内容时固定输出：无其他待关注通知。\n" +
                    "- {show} 是本次需在车机端展示的完整结构化通知汇总，必须与播报内容完全一致，无矛盾。\n" +
                    "\n" +
                    "# 四级重要度分级规则（必须100%严格刚性执行，优先级从高到低，禁止跨级归类）\n" +
                    "## 0级 应急强制播报级（最高优先级，零遗漏红线，仅可归入以下3类内容）\n" +
                    "定义：必须立即知晓、马上处理，不处理会直接造成人身/财产损失、严重不可逆后果的紧急通知，任何情况不得挤占该级播报空间\n" +
                    "分类明细（仅以下内容可归入）：\n" +
                    "1.  政务应急类：110/120/122官方紧急来电、提醒短信，违停立即驶离强制通知\n" +
                    "2.  人身安全类：车辆剐蹭/异常移动报警、家人亲情号紧急求助消息\n" +
                    "3.  财产安全类：账号盗刷风险提醒、银行卡冻结预警、大额异常交易核实通知\n" +
                    "\n" +
                    "## 1级 高优完整播报级（核心必知事项，播报顺序严格按以下分类排序，禁止乱序）\n" +
                    "定义：需要用户完整知晓、优先关注，不处理会造成工作失误、影响亲密关系、产生直接权益损失的高优先级通知，零遗漏要求\n" +
                    "分类明细（带刚性判定标准，仅符合标准的内容可归入）：\n" +
                    "1.  核心工作类：1小时内到期的待审批流程、15分钟内即将开始的会议提醒、直属领导/核心客户的紧急私信、当日必须完成的工作强制提醒\n" +
                    "2.  家人亲密社交类：父母、伴侣、子女、兄弟姐妹等直系亲属的私信、家人群消息，以及用户置顶的挚友/亲密联系人私信\n" +
                    "3.  即时财务类：实时账户资金变动（可自定义金额阈值，默认≥500元）、当日到期还款提醒、信用卡/借贷逾期预警\n" +
                    "4.  强时效出行类：预约网约车司机已到达、30分钟内即将起飞/发车的交通行程提醒、10分钟内即将超时的停车费提醒\n" +
                    "\n" +
                    "## 2级 贴心汇总播报级（需知晓可延后事项，仅做类型化数量汇总）\n" +
                    "定义：需要用户知晓，但无需立即处理，可延后查看详情的通知，禁止单条内容播报，禁止任何快递单号等ID号播放，仅可做合并汇总\n" +
                    "分类明细（仅以下内容可归入）：\n" +
                    "1.  常规社交类：非亲密联系人的微信/QQ个人私信、普通好友群、工作群、兴趣群非紧急消息\n" +
                    "2.  生活服务类：快递入柜/取件码通知、外卖配送/送达提醒、3天内到期的水电煤/物业费缴费提醒\n" +
                    "3.  常规工作类：非紧急待审批、非当日会议提醒、普通工作通知、非紧急工作沟通\n" +
                    "4.  常规出行类：非当日交通动态、尾号限行提醒、常规路况通知、非紧急车辆违章通知、非当日酒店/机票预订通知\n" +
                    "\n" +
                    "## 3级 绝对禁播级（仅车机文本展示，绝对禁止出现在任何tts标签中）\n" +
                    "定义：无时效性、纯营销娱乐属性、播报会干扰驾驶安全的低优先级内容，任何情况绝对禁止TTS播报\n" +
                    "分类明细（刚性红线，以下内容必须归入此类）：\n" +
                    "1.  验证码类：所有平台、所有来源的验证码通知，无论是否标注有效时长，一律归入此类\n" +
                    "2.  营销广告类：所有促销、优惠、直播、品牌推广、活动推送、优惠券提醒\n" +
                    "3.  娱乐资讯类：音乐、视频、游戏、热点新闻、自媒体内容推荐、直播提醒、UP主更新提醒\n" +
                    "4.  低优系统通知：存储提醒、非紧急系统/应用更新、常规权限提醒、设备保修提醒\n" +
                    "5.  无效信息：垃圾短信、群聊刷屏消息、非关注群的普通@消息、无意义推送、非核心的理财/投资非紧急通知\n" +
                    "\n" +
                    "# SHOW 车机展示约束\n" +
                    "- 结构化、分条、分应用展示，严格按【0级 → 1级 → 2级 → 3级】的优先级排序\n" +
                    "- 每条内容精简提炼，同类消息合并展示，不重复、不冗余\n" +
                    "- 严格基于用户输入的通知数据，不编造、不添加任何输入中不存在的内容\n" +
                    "- 排版清晰易读，无复杂格式，适配车机端屏幕展示，无特殊符号、无markdown格式\n" +
                    "\n" +
                    "# 绝对禁止行为\n" +
                    "- 禁止修改、增减、重复固定标签结构，禁止输出标签格式外的任何多余文字、解释、说明\n" +
                    "- 禁止使用表情、特殊符号、markdown格式\n" +
                    "- 禁止遗漏0级、1级核心通知，禁止跨级归类、越级播报，禁止播报3级任何内容\n" +
                    "- 禁止播报任何验证码相关内容，禁止在tts中出现完整数字验证码\n" +
                    "- 禁止编造用户输入中不存在的通知信息，禁止添加无关内容\n" +
                    "- 禁止在tts中逐条罗列零散通知，必须完成合并归纳总结\n" +
                    "- 禁止出现tts播报内容与show展示内容前后矛盾、信息不符的情况\n\n---USER_PROMPT---\n\n{notifications}",
                isBuiltin = true
            )
        )
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // StateFlows for reactive UI
    private val _promptTemplates = MutableStateFlow<List<PromptTemplate>>(emptyList())
    val promptTemplates: StateFlow<List<PromptTemplate>> = _promptTemplates.asStateFlow()

    private val _mockNotifications = MutableStateFlow<List<MockNotification>>(emptyList())
    val mockNotifications: StateFlow<List<MockNotification>> = _mockNotifications.asStateFlow()

    private val _testHistory = MutableStateFlow<List<DebugTestHistory>>(emptyList())
    val testHistory: StateFlow<List<DebugTestHistory>> = _testHistory.asStateFlow()

    private val _distributionConfig = MutableStateFlow(DistributionConfig())
    val distributionConfig: StateFlow<DistributionConfig> = _distributionConfig.asStateFlow()

    private val _selectedNotificationIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedNotificationIds: StateFlow<Set<String>> = _selectedNotificationIds.asStateFlow()

    init {
        loadData()
    }

    // ==================== 数据加载 ====================

    private fun loadData() {
        loadTemplates()
        loadNotifications()
        loadDistributionConfig()
        loadHistory()
        loadSelectionRange()
    }

    private fun loadTemplates() {
        _promptTemplates.value = BUILTIN_TEMPLATES + loadUserTemplates()
    }

    private fun loadUserTemplates(): List<PromptTemplate> {
        val json = prefs.getString(KEY_PROMPT_TEMPLATES, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                PromptTemplate(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    content = obj.getString("content"),
                    isBuiltin = false
                )
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to parse user templates", e)
            emptyList()
        }
    }

    private fun loadNotifications() {
        // 检查数据版本，如果版本不匹配则强制从 assets 重新加载
        val savedVersion = prefs.getInt(KEY_NOTIFICATION_DATA_VERSION, 0)
        if (savedVersion < NOTIFICATION_DATA_VERSION) {
            Logger.d(TAG, "Notification data version mismatch ($savedVersion < $NOTIFICATION_DATA_VERSION), reloading from assets")
            loadDefaultNotificationsFromAssets()
            prefs.edit().putInt(KEY_NOTIFICATION_DATA_VERSION, NOTIFICATION_DATA_VERSION).apply()
            return
        }

        val json = prefs.getString(KEY_MOCK_NOTIFICATIONS, null)
        if (json != null) {
            try {
                val array = JSONArray(json)
                val list = (0 until array.length()).map { i ->
                    val obj = array.getJSONObject(i)
                    MockNotification(
                        id = obj.getString("id"),
                        packageName = obj.getString("packageName"),
                        appName = obj.getString("appName"),
                        title = obj.getString("title"),
                        text = obj.getString("text"),
                        timestamp = obj.getLong("timestamp"),
                        category = NotificationCategory.valueOf(obj.optString("category", "OTHER"))
                    )
                }
                _mockNotifications.value = list
                return
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to parse notifications", e)
            }
        }
        // 如果没有存储的数据，从 assets 加载默认数据
        loadDefaultNotificationsFromAssets()
    }

    /**
     * 从 assets 加载默认通知数据
     */
    private fun loadDefaultNotificationsFromAssets() {
        try {
            val jsonString = context.assets.open("test_notifications.json").bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val array = jsonObject.getJSONArray("notifications")
            val now = System.currentTimeMillis()

            val list = (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val assetNotification = AssetNotification(
                    id = obj.getInt("id"),
                    packageName = obj.getString("package"),
                    appName = obj.getString("appName"),
                    title = obj.getString("title"),
                    text = obj.getString("text"),
                    bigText = obj.getString("bigText"),
                    category = obj.getString("category"),
                    idealSummary = obj.optString("idealSummary")
                )
                mapAssetNotificationToMockNotification(assetNotification, now)
            }
            _mockNotifications.value = list
            saveNotifications(list)
            Logger.d(TAG, "Loaded ${list.size} default notifications from assets")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to load default notifications from assets", e)
            // 回退到空列表
            _mockNotifications.value = emptyList()
        }
    }

    /**
     * 将资产通知映射到 MockNotification
     */
    private fun mapAssetNotificationToMockNotification(asset: AssetNotification, baseTime: Long): MockNotification {
        return MockNotification(
            id = "default_${asset.id}",
            packageName = asset.packageName,
            appName = asset.appName,
            title = asset.title,
            text = asset.bigText.ifEmpty { asset.text },
            timestamp = baseTime - (0..86400000).random(), // 最近24小时内
            category = mapCategoryStringToEnum(asset.category)
        )
    }

    /**
     * 将资产中的分类字符串映射到 NotificationCategory 枚举
     */
    private fun mapCategoryStringToEnum(category: String): NotificationCategory {
        return when (category) {
            "CHAT_PRIVATE", "CHAT_GROUP", "FAMILY" -> NotificationCategory.WECHAT_QQ
            "WORK" -> NotificationCategory.WORK_WECHAT
            "VERIFICATION", "EMAIL" -> NotificationCategory.SMS
            else -> NotificationCategory.OTHER
        }
    }

    private fun loadDistributionConfig() {
        val wechatQQ = prefs.getInt("${KEY_DISTRIBUTION_CONFIG}_wechat_qq", 30)
        val workWechat = prefs.getInt("${KEY_DISTRIBUTION_CONFIG}_work_wechat", 20)
        val sms = prefs.getInt("${KEY_DISTRIBUTION_CONFIG}_sms", 30)
        val other = prefs.getInt("${KEY_DISTRIBUTION_CONFIG}_other", 20)
        _distributionConfig.value = DistributionConfig(wechatQQ, workWechat, sms, other)
    }

    private fun loadHistory() {
        val json = prefs.getString(KEY_TEST_HISTORY, null) ?: return
        try {
            val array = JSONArray(json)
            val list = mutableListOf<DebugTestHistory>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    DebugTestHistory(
                        id = obj.getString("id"),
                        promptTemplateId = obj.getString("promptTemplateId"),
                        promptTemplateName = obj.getString("promptTemplateName"),
                        inputNotifications = obj.getString("inputNotifications"),
                        modelResponse = obj.getString("modelResponse"),
                        timestamp = obj.getLong("timestamp"),
                        success = obj.getBoolean("success"),
                        // 新增字段（兼容旧数据）
                        systemPrompt = obj.optString("systemPrompt", ""),
                        userPrompt = obj.optString("userPrompt", ""),
                        modelName = obj.optString("modelName", ""),
                        baseUrl = obj.optString("baseUrl", ""),
                        temperature = obj.optDouble("temperature", 0.7).toFloat(),
                        maxTokens = obj.optInt("maxTokens", 4096),
                        notificationCount = obj.optInt("notificationCount", 0),
                        notificationDistribution = obj.optString("notificationDistribution", "")
                    )
                )
            }
            _testHistory.value = list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to load debug history", e)
        }
    }

    private fun loadSelectionRange() {
        val min = prefs.getInt(KEY_SELECTION_RANGE_MIN, 10)
        val max = prefs.getInt(KEY_SELECTION_RANGE_MAX, 40)
        // 存储到 StateFlow 中供 ViewModel 使用
    }

    // ==================== 模板管理 ====================

    fun getAllTemplates(): List<PromptTemplate> = _promptTemplates.value

    fun getTemplateById(id: String): PromptTemplate? = _promptTemplates.value.find { it.id == id }

    fun saveTemplate(template: PromptTemplate) {
        if (template.isBuiltin) {
            Logger.w(TAG, "Cannot save builtin template")
            return
        }
        val userTemplates = loadUserTemplates().toMutableList()
        val existingIndex = userTemplates.indexOfFirst { it.id == template.id }
        if (existingIndex >= 0) {
            userTemplates[existingIndex] = template
        } else {
            userTemplates.add(template)
        }
        saveUserTemplates(userTemplates)
        _promptTemplates.value = BUILTIN_TEMPLATES + userTemplates
    }

    fun deleteTemplate(templateId: String) {
        val userTemplates = loadUserTemplates().filter { it.id != templateId }
        saveUserTemplates(userTemplates)
        _promptTemplates.value = BUILTIN_TEMPLATES + userTemplates
    }

    fun generateTemplateId(): String = "prompt_${System.currentTimeMillis()}"

    private fun saveUserTemplates(templates: List<PromptTemplate>) {
        val array = JSONArray()
        templates.forEach { template ->
            val obj = JSONObject().apply {
                put("id", template.id)
                put("name", template.name)
                put("content", template.content)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_PROMPT_TEMPLATES, array.toString()).apply()
    }

    // ==================== 通知数据管理 ====================

    fun regenerateNotifications(count: Int = 100) {
        // 从 assets 重新加载默认数据
        loadDefaultNotificationsFromAssets()
        _selectedNotificationIds.value = emptySet()
    }

    fun updateDistribution(config: DistributionConfig) {
        _distributionConfig.value = config
        prefs.edit().apply {
            putInt("${KEY_DISTRIBUTION_CONFIG}_wechat_qq", config.wechatQQPercent)
            putInt("${KEY_DISTRIBUTION_CONFIG}_work_wechat", config.workWechatPercent)
            putInt("${KEY_DISTRIBUTION_CONFIG}_sms", config.smsPercent)
            putInt("${KEY_DISTRIBUTION_CONFIG}_other", config.otherPercent)
            apply()
        }
    }

    fun updateSelectionRange(min: Int, max: Int) {
        prefs.edit().apply {
            putInt(KEY_SELECTION_RANGE_MIN, min)
            putInt(KEY_SELECTION_RANGE_MAX, max)
            apply()
        }
    }

    fun getSelectionRange(): Pair<Int, Int> {
        val min = prefs.getInt(KEY_SELECTION_RANGE_MIN, 10)
        val max = prefs.getInt(KEY_SELECTION_RANGE_MAX, 40)
        return Pair(min, max)
    }

    // ==================== 选择管理 ====================

    fun selectAllNotifications() {
        _selectedNotificationIds.value = _mockNotifications.value.map { it.id }.toSet()
    }

    fun deselectAllNotifications() {
        _selectedNotificationIds.value = emptySet()
    }

    fun randomSelectNotifications(min: Int, max: Int) {
        val notifications = _mockNotifications.value
        val count = (min..max).random()
        val shuffled = notifications.shuffled()
        _selectedNotificationIds.value = shuffled.take(count).map { it.id }.toSet()
    }

    fun toggleNotificationSelection(id: String) {
        val current = _selectedNotificationIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selectedNotificationIds.value = current
    }

    fun setSelectedNotifications(ids: Set<String>) {
        _selectedNotificationIds.value = ids
    }

    // ==================== 历史记录管理 ====================

    fun addHistory(history: DebugTestHistory) {
        val list = _testHistory.value.toMutableList()
        list.add(0, history)

        // 限制最大数量
        while (list.size > MAX_HISTORY_COUNT) {
            list.removeAt(list.size - 1)
        }

        _testHistory.value = list
        saveHistory(list)
    }

    fun getHistoryById(id: String): DebugTestHistory? = _testHistory.value.find { it.id == id }

    fun deleteHistory(historyId: String) {
        val list = _testHistory.value.filter { it.id != historyId }
        _testHistory.value = list
        saveHistory(list)
    }

    fun clearAllHistory() {
        _testHistory.value = emptyList()
        prefs.edit().remove(KEY_TEST_HISTORY).apply()
    }

    private fun saveHistory(list: List<DebugTestHistory>) {
        val array = JSONArray()
        list.forEach { history ->
            val obj = JSONObject().apply {
                put("id", history.id)
                put("promptTemplateId", history.promptTemplateId)
                put("promptTemplateName", history.promptTemplateName)
                put("inputNotifications", history.inputNotifications)
                put("modelResponse", history.modelResponse)
                put("timestamp", history.timestamp)
                put("success", history.success)
                // 新增字段
                put("systemPrompt", history.systemPrompt)
                put("userPrompt", history.userPrompt)
                put("modelName", history.modelName)
                put("baseUrl", history.baseUrl)
                put("temperature", history.temperature)
                put("maxTokens", history.maxTokens)
                put("notificationCount", history.notificationCount)
                put("notificationDistribution", history.notificationDistribution)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_TEST_HISTORY, array.toString()).apply()
    }

    // ==================== 选中模板管理 ====================

    fun getSelectedTemplateId(): String? = prefs.getString(KEY_SELECTED_TEMPLATE_ID, null)

    fun setSelectedTemplateId(id: String?) {
        prefs.edit().putString(KEY_SELECTED_TEMPLATE_ID, id).apply()
    }

    // ==================== 私有辅助方法 ====================

    private fun saveNotifications(notifications: List<MockNotification>) {
        val array = JSONArray()
        notifications.forEach { notification ->
            val obj = JSONObject().apply {
                put("id", notification.id)
                put("packageName", notification.packageName)
                put("appName", notification.appName)
                put("title", notification.title)
                put("text", notification.text)
                put("timestamp", notification.timestamp)
                put("category", notification.category.name)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_MOCK_NOTIFICATIONS, array.toString()).apply()
    }
}
