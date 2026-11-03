package com.nathan.gitlabmr.util

import com.nathan.gitlabmr.model.GitLabMember

/**
 * 指派人筛选与展示纯函数，无 IntelliJ 依赖，便于单元测试。
 */
object AssigneeFilters {

    /** “不指派”在下拉框中的展示文本。 */
    const val NO_ASSIGNEE_LABEL = "无"

    /** 关键字筛选无匹配时的占位项。 */
    const val NO_MATCH_PLACEHOLDER = "(无匹配成员)"

    /**
     * 成员在下拉框中的展示文本：姓名 (@用户名)。
     */
    fun displayLabel(member: GitLabMember): String = "${member.name} (@${member.username})"

    /**
     * 按关键字筛选成员：关键字 trim 后为空返回全部；
     * 否则返回展示文本（姓名 (@用户名)）忽略大小写“包含”关键字的成员，保持原有相对顺序。
     * 展示文本同时包含姓名和用户名，因此输入其中任意一部分都能命中。
     */
    fun filterMembers(all: List<GitLabMember>, keyword: String): List<GitLabMember> {
        val normalized = keyword.trim().lowercase()
        if (normalized.isEmpty()) {
            return all
        }
        return all.filter { member ->
            displayLabel(member).lowercase().contains(normalized)
        }
    }

    /**
     * 生成下拉项：
     * - 关键字为空：首项为“无”，后面是全部成员；
     * - 关键字非空：只展示匹配的成员，无匹配时返回单个占位项。
     */
    fun buildItems(all: List<GitLabMember>, keyword: String): List<String> {
        if (keyword.trim().isEmpty()) {
            return listOf(NO_ASSIGNEE_LABEL) + all.map(::displayLabel)
        }
        val matched = filterMembers(all, keyword)
        return if (matched.isEmpty()) {
            listOf(NO_MATCH_PLACEHOLDER)
        } else {
            matched.map(::displayLabel)
        }
    }

    /**
     * 从下拉框展示文本反查成员；空文本、“无”以及手输文字/占位项均返回 null。
     */
    fun findMember(all: List<GitLabMember>, label: String?): GitLabMember? {
        val normalized = label?.trim() ?: return null
        if (normalized.isEmpty()) {
            return null
        }
        return all.firstOrNull { displayLabel(it) == normalized }
    }
}
