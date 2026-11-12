package com.nathan.gitlabmr.model

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
    val lastActivityAt: String,

    @SerializedName("only_allow_merge_if_pipeline_succeeds")
    val onlyAllowMergeIfPipelineSucceeds: Boolean? = null
)

/**
 * GitLab合并请求状态
 */
enum class MergeRequestState(val displayName: String) {
    OPENED("待合并"),
    CHECKING("检查中"),
    CLOSED("已关闭"),
    LOCKED("有冲突"),
    MERGED("已合并");

    companion object {
        fun fromString(value: String?): MergeRequestState {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: OPENED
        }
    }
}

/** 流水线仍在进行的 GitLab 状态。 */
internal val PIPELINE_RUNNING_STATUSES =
    setOf("created", "waiting_for_resource", "preparing", "pending", "running", "scheduled")

/** 流水线已失败或被取消的状态。 */
internal val PIPELINE_FAILED_STATUSES = setOf("failed", "canceled")

/**
 * 合并阻塞原因：说明"为什么现在不能合并"，不影响 [MergeRequestState] 的生命周期语义。
 */enum class MergeBlockReason(val displayName: String) {
    NONE(""),
    CI_RUNNING("流水线中"),
    CI_FAILED("流水线未通过"),
    CONFLICT("有冲突"),
    NEED_REBASE("需变基"),
    APPROVAL("待审批"),
    REQUESTED_CHANGES("有待处理的修改"),
    DISCUSSIONS("有未解决讨论"),
    DRAFT("草稿"),
    EXTERNAL_CHECK("等待外部检查"),
    BLOCKED("被其他合并请求阻塞"),
    UNKNOWN("暂不可合并")
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
    val mergeStatusRaw: String? = null,
    val detailedMergeStatusRaw: String? = null,
    val blockReason: MergeBlockReason = MergeBlockReason.NONE,
    val headPipelineStatus: String? = null,
    val autoMergeEnabled: Boolean = false,
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
    val forceRemoveSourceBranch: Boolean = false,
    val diffRefs: GitLabMergeRequestDiffRefs? = null
) {
    /**
     * 状态展示文案：待合并叠加阻塞原因，其他状态保持原样。
     */
    fun displayStateText(): String {
        if (state != MergeRequestState.OPENED || blockReason == MergeBlockReason.NONE) {
            return state.displayName
        }
        return "${state.displayName} · ${blockReason.displayName}"
    }

    /**
     * 是否允许点击合并：流水线运行中允许点击以设置自动合并，原因未知时交给接口兜底。
     * 已设置自动合并时不再允许重复合并。
     */
    fun canAttemptMerge(): Boolean {
        if (autoMergeEnabled) return false
        return state == MergeRequestState.OPENED &&
            (blockReason == MergeBlockReason.NONE ||
                blockReason == MergeBlockReason.CI_RUNNING ||
                blockReason == MergeBlockReason.UNKNOWN)
    }

    /**
     * 自动合并标记文案：仅对待合并且已设置的合并请求展示。
     */
    fun autoMergeText(): String? {
        if (!autoMergeEnabled || state != MergeRequestState.OPENED) return null
        return "已设置，流水线通过后自动合并"
    }

    /**
     * 合并状态是否还在变化中（需要轮询刷新）。
     */
    fun isMergeStatusPending(): Boolean {
        return state == MergeRequestState.CHECKING ||
            blockReason == MergeBlockReason.CI_RUNNING ||
            (autoMergeEnabled && state == MergeRequestState.OPENED)
    }

    /**
     * 流水线是否仍在进行。
     */
    fun isPipelineRunning(): Boolean {
        return headPipelineStatus != null && headPipelineStatus in PIPELINE_RUNNING_STATUSES
    }
}

data class GitLabMergeRequestDiffRefs(
    val baseSha: String?,
    val headSha: String?,
    val startSha: String?
)

enum class GitLabMergeRequestChangeType {
    ADDED,
    MODIFIED,
    DELETED,
    RENAMED
}

data class GitLabMergeRequestChangeFile(
    val path: String,
    val oldPath: String? = null,
    val changeType: GitLabMergeRequestChangeType = GitLabMergeRequestChangeType.MODIFIED,
    val diff: String? = null,
    val oldMode: String? = null,
    val newMode: String? = null,
    val isBinary: Boolean = false
)

data class GitLabMergeRequestFileContent(
    val path: String,
    val content: String,
    val revision: String?,
    val isText: Boolean = true
)

data class MergeRequestDiffPayload(
    val changeFile: GitLabMergeRequestChangeFile,
    val beforeContent: GitLabMergeRequestFileContent?,
    val afterContent: GitLabMergeRequestFileContent?,
    val title: String,
    val beforeTitle: String,
    val afterTitle: String
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
