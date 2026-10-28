package com.nathan.gitlabmr.util

import com.nathan.gitlabmr.model.GitLabBranch
import com.nathan.gitlabmr.model.GitLabCommit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BranchFiltersTest {

    private fun branch(name: String, committedDate: String = "2026-01-01T00:00:00Z"): GitLabBranch =
        GitLabBranch(
            name = name,
            merged = false,
            protected = false,
            default = false,
            developersCanPush = false,
            developersCanMerge = false,
            canPush = true,
            webUrl = "https://example.com/$name",
            commit = GitLabCommit(
                id = "id-$name",
                shortId = "sid",
                title = "title",
                message = "message",
                authorName = "author",
                authorEmail = "author@example.com",
                authoredDate = committedDate,
                committerName = "committer",
                committerEmail = "committer@example.com",
                committedDate = committedDate,
                webUrl = "https://example.com/commit"
            )
        )

    private fun names(list: List<GitLabBranch>): List<String> = list.map { it.name }

    @Test
    fun `filterBranches returns all when keyword is empty`() {
        val all = listOf(branch("main"), branch("dev"))
        assertEquals(names(all), names(BranchFilters.filterBranches(all, "")))
    }

    @Test
    fun `filterBranches returns all when keyword is blank`() {
        val all = listOf(branch("main"), branch("dev"))
        assertEquals(names(all), names(BranchFilters.filterBranches(all, "   ")))
    }

    @Test
    fun `filterBranches matches case-insensitively by contains`() {
        val all = listOf(branch("feature/Login"), branch("bugfix/login-fix"), branch("main"))
        assertEquals(
            listOf("feature/Login", "bugfix/login-fix"),
            names(BranchFilters.filterBranches(all, "LOGIN"))
        )
    }

    @Test
    fun `filterBranches keeps original relative order`() {
        val all = listOf(branch("zeta-test"), branch("alpha-test"), branch("main"))
        assertEquals(
            listOf("zeta-test", "alpha-test"),
            names(BranchFilters.filterBranches(all, "test"))
        )
    }

    @Test
    fun `filterBranches returns empty when nothing matches`() {
        val all = listOf(branch("main"), branch("dev"))
        assertTrue(BranchFilters.filterBranches(all, "nope").isEmpty())
    }

    @Test
    fun `rankBranches puts main and master first`() {
        val ranked = BranchFilters.rankBranches(
            listOf(branch("feature/a"), branch("master"), branch("main"))
        )
        assertEquals(setOf("main", "master"), names(ranked).take(2).toSet())
    }

    @Test
    fun `rankBranches puts test branches second`() {
        val ranked = BranchFilters.rankBranches(
            listOf(branch("feature/a"), branch("test-env"), branch("main"))
        )
        assertEquals("main", ranked[0].name)
        assertEquals("test-env", ranked[1].name)
        assertEquals("feature/a", ranked[2].name)
    }

    @Test
    fun `rankBranches sorts by committedDate descending within same priority`() {
        val ranked = BranchFilters.rankBranches(
            listOf(
                branch("feature/old", "2025-01-01T00:00:00Z"),
                branch("feature/new", "2026-05-01T00:00:00Z"),
                branch("feature/mid", "2025-09-01T00:00:00Z")
            )
        )
        assertEquals(listOf("feature/new", "feature/mid", "feature/old"), names(ranked))
    }

    @Test
    fun `rankBranches treats unparsable committedDate as zero without throwing`() {
        val ranked = BranchFilters.rankBranches(
            listOf(branch("feature/bad", "not-a-date"), branch("feature/good", "2025-01-01T00:00:00Z"))
        )
        assertEquals(listOf("feature/good", "feature/bad"), names(ranked))
    }
}
