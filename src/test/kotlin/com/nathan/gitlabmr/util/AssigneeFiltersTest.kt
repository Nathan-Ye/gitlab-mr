package com.nathan.gitlabmr.util

import com.nathan.gitlabmr.model.GitLabMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssigneeFiltersTest {

    private fun member(id: Long, name: String, username: String): GitLabMember =
        GitLabMember(
            id = id,
            username = username,
            name = name,
            state = "active",
            avatarUrl = null,
            webUrl = "https://example.com/$username",
            accessLevel = 30,
            expiresAt = null
        )

    private val members = listOf(
        member(1, "张三", "zhangsan"),
        member(2, "李四", "lisi"),
        member(3, "Alice Wang", "alice.w")
    )

    private fun names(list: List<GitLabMember>): List<String> = list.map { it.name }

    @Test
    fun `filterMembers returns all when keyword is empty`() {
        assertEquals(names(members), names(AssigneeFilters.filterMembers(members, "")))
    }

    @Test
    fun `filterMembers returns all when keyword is blank`() {
        assertEquals(names(members), names(AssigneeFilters.filterMembers(members, "   ")))
    }

    @Test
    fun `filterMembers matches display name case-insensitively by contains`() {
        assertEquals(
            listOf("Alice Wang"),
            names(AssigneeFilters.filterMembers(members, "alice w"))
        )
        assertEquals(
            listOf("张三"),
            names(AssigneeFilters.filterMembers(members, "张"))
        )
    }

    @Test
    fun `filterMembers matches username case-insensitively`() {
        assertEquals(
            listOf("张三"),
            names(AssigneeFilters.filterMembers(members, "ZHANGSAN"))
        )
        assertEquals(
            listOf("Alice Wang"),
            names(AssigneeFilters.filterMembers(members, "alice.w"))
        )
    }

    @Test
    fun `filterMembers keeps original relative order`() {
        assertEquals(
            listOf("李四", "Alice Wang"),
            names(AssigneeFilters.filterMembers(members, "i"))
        )
    }

    @Test
    fun `filterMembers matches a full display label`() {
        assertEquals(
            listOf("张三"),
            names(AssigneeFilters.filterMembers(members, "张三 (@zhangsan)"))
        )
    }

    @Test
    fun `filterMembers returns empty when nothing matches`() {
        assertTrue(AssigneeFilters.filterMembers(members, "nobody").isEmpty())
    }

    @Test
    fun `buildItems starts with no-assignee and all members when keyword is blank`() {
        assertEquals(
            listOf("无", "张三 (@zhangsan)", "李四 (@lisi)", "Alice Wang (@alice.w)"),
            AssigneeFilters.buildItems(members, "  ")
        )
    }

    @Test
    fun `buildItems filters members when keyword is not blank`() {
        assertEquals(
            listOf("李四 (@lisi)"),
            AssigneeFilters.buildItems(members, "lisi")
        )
    }

    @Test
    fun `buildItems returns placeholder when nothing matches`() {
        assertEquals(
            listOf("(无匹配成员)"),
            AssigneeFilters.buildItems(members, "nobody")
        )
    }

    @Test
    fun `findMember resolves a member from its display label`() {
        assertEquals(2L, AssigneeFilters.findMember(members, "李四 (@lisi)")?.id)
    }

    @Test
    fun `findMember returns null for blank, no-assignee, placeholder or typed keyword`() {
        assertNull(AssigneeFilters.findMember(members, null))
        assertNull(AssigneeFilters.findMember(members, "   "))
        assertNull(AssigneeFilters.findMember(members, "无"))
        assertNull(AssigneeFilters.findMember(members, "(无匹配成员)"))
        assertNull(AssigneeFilters.findMember(members, "li"))
        assertNull(AssigneeFilters.findMember(members, "李四"))
    }
}
