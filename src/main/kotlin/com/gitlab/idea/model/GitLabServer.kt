package com.gitlab.idea.model

import com.google.gson.annotations.SerializedName
import com.intellij.util.xmlb.annotations.Tag

/**
 * GitLab服务器配置数据类
 */
@Tag("GitLabServer")
data class GitLabServer(
    @SerializedName("id")
    var id: String = "",

    @SerializedName("name")
    var name: String = "",

    @SerializedName("url")
    var url: String = "",

    @SerializedName("token")
    var token: String = "",

    @SerializedName("isDefault")
    var isDefault: Boolean = false  // true=应用级(所有项目默认使用), false=项目级
) {
    companion object {
        fun generateId(): String = "gitlab_server_${System.currentTimeMillis()}"
    }
}

/**
 * GitLab服务器配置列表
 */
@Tag("GitLabServerList")
data class GitLabServerList(
    @SerializedName("servers")
    var servers: MutableList<GitLabServer> = mutableListOf()
)

/**
 * GitLab项目信息
 */
data class GitLabProject(
    @SerializedName("id")
    val id: Long,

    @SerializedName("name")
    val name: String,

    @SerializedName("name_with_namespace")
    val nameWithNamespace: String,

    @SerializedName("path")
    val path: String,

    @SerializedName("path_with_namespace")
    val pathWithNamespace: String,

    @SerializedName("web_url")
    val webUrl: String,

    @SerializedName("default_branch")
    val defaultBranch: String?,

    @SerializedName("ssh_url_to_repo")
    val sshUrlToRepo: String?,

    @SerializedName("http_url_to_repo")
    val httpUrlToRepo: String?,

    @SerializedName("created_at")
    val createdAt: String,

    @SerializedName("last_activity_at")
    val lastActivityAt: String
)

/**
 * GitLab合并请求状态
 */
enum class MergeRequestState(val displayName: String) {
    OPENED("OPENED"),
    CLOSED("CLOSED"),
    LOCKED("LOCKED"),
    MERGED("MERGED");

    companion object {
        fun fromString(value: String?): MergeRequestState {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: OPENED
        }
    }
}

/**
 * GitLab合并请求
 */
data class GitLabMergeRequest(
    val id: Long,
    val iid: Long,
    val projectId: Long,
    val title: String,
    val description: String?,
    val state: MergeRequestState,
    val sourceBranch: String,
    val targetBranch: String,
    val author: GitLabUser,
    val assignees: List<GitLabUser>,
    val reviewers: List<GitLabUser>,
    val mergedBy: List<GitLabUser>,
    val createdAt: String,
    val updatedAt: String,
    val mergedAt: String?,
    val closedAt: String?,
    val webUrl: String,
    val draft: Boolean = false,
    val workInProgress: Boolean = false,
    val hasConflicts: Boolean = false,
    val labels: List<String> = emptyList(),
    val upvotes: Int = 0,
    val downvotes: Int = 0,
    val userNotesCount: Int = 0,
    val forceRemoveSourceBranch: Boolean = false
)

/**
 * GitLab用户信息
 */
data class GitLabUser(
    @SerializedName("id")
    val id: Long,

    @SerializedName("username")
    val username: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("state")
    val state: String? = null,

    @SerializedName("avatar_url")
    val avatarUrl: String? = null,

    @SerializedName("web_url")
    val webUrl: String? = null
)

/**
 * GitLab API响应包装类
 */
data class GitLabApiResponse<T>(
    val data: T?,
    val success: Boolean,
    val error: String? = null,
    val statusCode: Int = -1
)

/**
 * GitLab分支信息
 */
data class GitLabBranch(
    @SerializedName("name")
    val name: String,

    @SerializedName("merged")
    val merged: Boolean,

    @SerializedName("protected")
    val protected: Boolean,

    @SerializedName("default")
    val default: Boolean,

    @SerializedName("developers_can_push")
    val developersCanPush: Boolean,

    @SerializedName("developers_can_merge")
    val developersCanMerge: Boolean,

    @SerializedName("can_push")
    val canPush: Boolean,

    @SerializedName("web_url")
    val webUrl: String,

    @SerializedName("commit")
    val commit: GitLabCommit
)

/**
 * GitLab提交信息
 */
data class GitLabCommit(
    @SerializedName("id")
    val id: String,

    @SerializedName("short_id")
    val shortId: String,

    @SerializedName("title")
    val title: String,

    @SerializedName("message")
    val message: String,

    @SerializedName("author_name")
    val authorName: String,

    @SerializedName("author_email")
    val authorEmail: String,

    @SerializedName("authored_date")
    val authoredDate: String,

    @SerializedName("committer_name")
    val committerName: String,

    @SerializedName("committer_email")
    val committerEmail: String,

    @SerializedName("committed_date")
    val committedDate: String,

    @SerializedName("web_url")
    val webUrl: String
)

/**
 * GitLab项目成员信息
 */
data class GitLabMember(
    @SerializedName("id")
    val id: Long,

    @SerializedName("username")
    val username: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("state")
    val state: String,

    @SerializedName("avatar_url")
    val avatarUrl: String?,

    @SerializedName("web_url")
    val webUrl: String?,

    @SerializedName("access_level")
    val accessLevel: Int,

    @SerializedName("expires_at")
    val expiresAt: String?
)

/**
 * 创建合并请求请求体
 */
data class CreateMergeRequestRequest(
    @SerializedName("source_branch")
    val sourceBranch: String,

    @SerializedName("target_branch")
    val targetBranch: String,

    @SerializedName("title")
    val title: String,

    @SerializedName("description")
    val description: String? = null,

    @SerializedName("assignee_id")
    val assigneeId: Long? = null,

    @SerializedName("remove_source_branch")
    val removeSourceBranch: Boolean? = null
)

/**
 * 创建合并请求响应
 */
data class CreateMergeRequestResponse(
    @SerializedName("id")
    val id: Long,

    @SerializedName("iid")
    val iid: Long,

    @SerializedName("project_id")
    val projectId: Long,

    @SerializedName("title")
    val title: String,

    @SerializedName("description")
    val description: String?,

    @SerializedName("state")
    val state: String,

    @SerializedName("source_branch")
    val sourceBranch: String,

    @SerializedName("target_branch")
    val targetBranch: String,

    @SerializedName("author")
    val author: GitLabUser,

    @SerializedName("assignees")
    val assignees: List<GitLabUser>,

    @SerializedName("web_url")
    val webUrl: String,

    @SerializedName("created_at")
    val createdAt: String,

    @SerializedName("updated_at")
    val updatedAt: String,

    @SerializedName("has_conflicts")
    val hasConflicts: Boolean,

    @SerializedName("draft")
    val draft: Boolean,

    @SerializedName("work_in_progress")
    val workInProgress: Boolean
)

/**
 * 合并MR请求体（预留，当前使用默认选项）
 */
data class MergeMRRequest(
    @SerializedName("merge_commit_message")
    val mergeCommitMessage: String? = null,

    @SerializedName("should_remove_source_branch")
    val shouldRemoveSourceBranch: Boolean? = null,

    @SerializedName("merge_when_pipeline_succeeds")
    val mergeWhenPipelineSucceeds: Boolean? = null
)
