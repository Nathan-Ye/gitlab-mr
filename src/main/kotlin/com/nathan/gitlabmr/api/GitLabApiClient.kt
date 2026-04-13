package com.nathan.gitlabmr.api

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.nathan.gitlabmr.model.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

private val CHECKING_MERGE_STATUSES = setOf("checking", "preparing", "unchecked", "approvals_syncing")

/**
 * GitLab API客户端
 * 提供与GitLab REST API交互的所有功能
 */
class GitLabApiClient(
    server: GitLabServer,
    private val project: Project? = null
) : Disposable {
    // 从GitLabServer对象中提取配置
    private val serverUrl: String = server.url
    private val privateToken: String = server.token

    // 保留旧构造函数的兼容性
    constructor(serverUrl: String, privateToken: String, project: Project? = null) : this(
        GitLabServer(
            id = "",
            name = "",
            url = serverUrl,
            token = privateToken,
            isDefault = false
        ),
        project
    )

    companion object {
        private const val DEFAULT_PAGE_SIZE = 20
        private const val API_VERSION = "v4"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /**
         * 创建API客户端实例
         */
        fun create(server: GitLabServer, project: Project? = null): GitLabApiClient {
            return GitLabApiClient(server, project)
        }

        /**
         * 标准化URL格式
         */
        private fun normalizeUrl(url: String): String {
            var normalized = url.trim()
            if (normalized.endsWith("/")) {
                normalized = normalized.dropLast(1)
            }
            return normalized
        }

        /**
         * 从URL中提取项目路径
         * 例如: https://gitlab.com/group/project -> group/project
         */
        fun extractProjectPath(url: String): String? {
            val parsedUrl = try {
                java.net.URI(url).toURL()
            } catch (e: Exception) {
                return null
            }

            val path = parsedUrl.path.removePrefix("/")
            val gitSuffix = ".git"

            return when {
                path.endsWith(gitSuffix) -> path.dropLast(gitSuffix.length)
                path.isNotEmpty() -> path
                else -> null
            }
        }
    }

    private val gson = Gson()
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val apiBaseUrl: String = "${normalizeUrl(serverUrl)}/api/$API_VERSION"
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * 添加认证参数到URL（使用URL参数方式，与浏览器一致）
     */
    private fun String.withAuthToken(): String {
        return if (this.contains("?")) {
            "$this&private_token=${java.net.URLEncoder.encode(privateToken, "UTF-8")}"
        } else {
            "$this?private_token=${java.net.URLEncoder.encode(privateToken, "UTF-8")}"
        }
    }

    private fun encodeProjectId(projectId: String): String {
        return if (projectId.all { it.isDigit() }) {
            projectId
        } else {
            java.net.URLEncoder.encode(projectId, "UTF-8")
        }
    }

    private fun decodeUtf8(bytes: ByteArray): String? {
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }

    private fun isLikelyText(contentType: String?, bytes: ByteArray): Boolean {
        val normalizedType = contentType?.lowercase()
        if (normalizedType != null) {
            if (normalizedType.startsWith("text/")) return true
            if ("json" in normalizedType || "xml" in normalizedType || "javascript" in normalizedType || "yaml" in normalizedType) {
                return true
            }
            if ("octet-stream" in normalizedType) return false
        }

        return bytes.none { it == 0.toByte() } && decodeUtf8(bytes) != null
    }

    // ==================== 连接测试 ====================

    /**
     * 测试连接
     */
    suspend fun testConnection(): GitLabApiResponse<Map<String, Any>> = withContext(Dispatchers.IO) {
        try {
            // 先尝试使用URL参数方式（浏览器方式）
            val urlWithToken = "$apiBaseUrl/user?private_token=${java.net.URLEncoder.encode(privateToken, "UTF-8")}"

            val request1 = Request.Builder()
                .url(urlWithToken)
                .header("Accept", "application/json")
                .get()
                .build()

            val response1 = client.newCall(request1).execute()
            val responseBody1 = response1.body?.string()

            if (response1.isSuccessful) {
                try {
                    val userJson = JsonParser.parseString(responseBody1).asJsonObject
                    val userInfo = mapOf(
                        "username" to (userJson.get("username")?.asString ?: ""),
                        "name" to (userJson.get("name")?.asString ?: ""),
                        "id" to (userJson.get("id")?.asLong ?: 0)
                    )
                    return@withContext GitLabApiResponse(userInfo, true, null, response1.code)
                } catch (jsonException: Exception) {
                    return@withContext GitLabApiResponse(null, false, "Invalid JSON: ${jsonException.message}", response1.code)
                }
            }

            // 如果URL参数方式失败，尝试Header方式
            val request2 = Request.Builder()
                .url("$apiBaseUrl/user")
                .header("PRIVATE-TOKEN", privateToken.trim())
                .header("Accept", "application/json")
                .get()
                .build()

            val response2 = client.newCall(request2).execute()
            val responseBody2 = response2.body?.string()

            if (response2.isSuccessful) {
                try {
                    val userJson = JsonParser.parseString(responseBody2).asJsonObject
                    val userInfo = mapOf(
                        "username" to (userJson.get("username")?.asString ?: ""),
                        "name" to (userJson.get("name")?.asString ?: ""),
                        "id" to (userJson.get("id")?.asLong ?: 0)
                    )
                    GitLabApiResponse(userInfo, true, null, response2.code)
                } catch (jsonException: Exception) {
                    GitLabApiResponse(null, false, "Invalid JSON: ${jsonException.message}", response2.code)
                }
            } else {
                val errorDetails = buildString {
                    appendLine("HTTP ${response2.code} - Authentication failed")
                    appendLine()
                    appendLine("Both authentication methods failed:")
                    appendLine("  Method 1 (URL parameter): ${response1.code}")
                    appendLine("  Method 2 (Header): ${response2.code}")
                    appendLine()
                    appendLine("Response: ${responseBody2?.take(200) ?: "No response body"}")
                }
                GitLabApiResponse(null, false, errorDetails, response2.code)
            }
        } catch (e: Exception) {
            val errorMsg = "Connection failed: ${e.message}\n" +
                    "Please check:\n" +
                    "1. Server URL is correct\n" +
                    "2. Access Token is valid and has 'api' scope\n" +
                    "3. Network connection is working\n" +
                    "4. SSL certificate is trusted (for self-hosted GitLab)"
            GitLabApiResponse(null, false, errorMsg, -1)
        }
    }

    /**
     * 获取当前认证用户信息
     */
    suspend fun getCurrentUser(): GitLabApiResponse<GitLabUser> = withContext(Dispatchers.IO) {
        try {
            // 先尝试使用URL参数方式（浏览器方式）
            val urlWithToken = "$apiBaseUrl/user?private_token=${java.net.URLEncoder.encode(privateToken, "UTF-8")}"

            val request1 = Request.Builder()
                .url(urlWithToken)
                .header("Accept", "application/json")
                .get()
                .build()

            val response1 = client.newCall(request1).execute()
            val responseBody1 = response1.body?.string()

            if (response1.isSuccessful) {
                try {
                    val user = gson.fromJson(responseBody1, GitLabUser::class.java)
                    return@withContext GitLabApiResponse(user, true, null, response1.code)
                } catch (jsonException: Exception) {
                    return@withContext GitLabApiResponse(null, false, "Invalid JSON: ${jsonException.message}", response1.code)
                }
            }

            // 如果URL参数方式失败，尝试Header方式
            val request2 = Request.Builder()
                .url("$apiBaseUrl/user")
                .header("PRIVATE-TOKEN", privateToken.trim())
                .header("Accept", "application/json")
                .get()
                .build()

            val response2 = client.newCall(request2).execute()
            val responseBody2 = response2.body?.string()

            if (response2.isSuccessful) {
                try {
                    val user = gson.fromJson(responseBody2, GitLabUser::class.java)
                    return@withContext GitLabApiResponse(user, true, null, response2.code)
                } catch (jsonException: Exception) {
                    return@withContext GitLabApiResponse(null, false, "Invalid JSON: ${jsonException.message}", response2.code)
                }
            } else {
                GitLabApiResponse(null, false, "Authentication failed: HTTP ${response2.code}", response2.code)
            }
        } catch (e: Exception) {
            GitLabApiResponse(null, false, "Failed to get current user: ${e.message}", -1)
        }
    }

    /**
     * 同步测试连接（在后台任务中运行）
     */
    fun testConnectionSync(callback: (GitLabApiResponse<Map<String, Any>>) -> Unit) {
        project?.let {
            ProgressManager.getInstance().run(object : Task.Backgroundable(it, "Testing GitLab Connection", true) {
                override fun run(indicator: ProgressIndicator) {
                    val result = runBlocking { testConnection() }
                    callback(result)
                }
            })
        } ?: run {
            // 如果没有project，直接在协程中运行
            coroutineScope.launch {
                val result = testConnection()
                withContext(Dispatchers.Main) {
                    callback(result)
                }
            }
        }
    }

    // ==================== 项目相关API ====================

    /**
     * 获取项目信息
     * @param projectPath 项目路径，例如: group/project
     */
    suspend fun getProject(projectPath: String): GitLabApiResponse<GitLabProject> = withContext(Dispatchers.IO) {
        try {
            // GitLab API要求对嵌套路径进行URL编码
            // 例如: group/subgroup/project -> group%2Fsubgroup%2Fproject
            val encodedPath = java.net.URLEncoder.encode(projectPath, "UTF-8")
            val url = "$apiBaseUrl/projects/$encodedPath".withAuthToken()

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string()
                val project = gson.fromJson(body, GitLabProject::class.java)
                GitLabApiResponse(project, true, null, response.code)
            } else {
                val error = parseError(response.body?.string())
                GitLabApiResponse(null, false, error, response.code)
            }
        } catch (e: Exception) {
            GitLabApiResponse(null, false, e.message ?: "Unknown error", -1)
        }
    }

    /**
     * 根据URL匹配项目
     * 自动从远程仓库URL中提取项目路径并查询
     */
    suspend fun matchProjectByUrl(repositoryUrl: String): GitLabApiResponse<GitLabProject> {
        val projectPath = extractProjectPath(repositoryUrl)
            ?: return GitLabApiResponse(null, false, "无法从URL中提取项目路径", -1)

        return getProject(projectPath)
    }

    /**
     * 获取用户的所有项目
     */
    suspend fun getUserProjects(
        page: Int = 1,
        perPage: Int = DEFAULT_PAGE_SIZE
    ): GitLabApiResponse<List<GitLabProject>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$apiBaseUrl/projects?page=$page&per_page=$perPage&membership=true&order_by=last_activity_at&sort=desc")
                .header("PRIVATE-TOKEN", privateToken)
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string()
                val type = object : TypeToken<List<GitLabProject>>() {}.type
                val projects = gson.fromJson<List<GitLabProject>>(body, type)
                GitLabApiResponse(projects, true, null, response.code)
            } else {
                val error = parseError(response.body?.string())
                GitLabApiResponse(null, false, error, response.code)
            }
        } catch (e: Exception) {
            GitLabApiResponse(null, false, e.message ?: "Unknown error", -1)
        }
    }

    // ==================== 合并请求相关API ====================

    /**
     * 获取项目的所有合并请求
     * @param projectId 项目ID或路径
     * @param state MR状态: opened, closed, locked, merged, all
     * @param page 页码
     * @param perPage 每页数量
     * @param search 搜索关键词（在标题和描述中搜索）
     * @param scope 范围筛选: created_by_me, assigned_to_me, all
     */
    suspend fun getMergeRequests(
        projectId: String,
        state: String = "all",
        page: Int = 1,
        perPage: Int = DEFAULT_PAGE_SIZE,
        search: String? = null,
        scope: String? = null
    ): GitLabApiResponse<List<GitLabMergeRequest>> = withContext(Dispatchers.IO) {
        try {
            // 如果projectId是数字ID，不需要编码；如果是路径，需要编码
            val encodedProjectId = if (projectId.all { it.isDigit() }) {
                projectId
            } else {
                java.net.URLEncoder.encode(projectId, "UTF-8")
            }

            // 构建查询参数
            val queryParams = mutableListOf(
                "state=$state",
                "page=$page",
                "per_page=$perPage"
            )

            // 添加搜索参数
            if (!search.isNullOrBlank()) {
                queryParams.add("search=${java.net.URLEncoder.encode(search, "UTF-8")}")
            }

            // 添加范围筛选参数
            if (!scope.isNullOrBlank() && scope != "all") {
                queryParams.add("scope=$scope")
            }

            // 修复：正确构造URL，避免重复的?
            val baseUrl = "$apiBaseUrl/projects/$encodedProjectId/merge_requests"
            val params = queryParams.joinToString("&")
            val url = "$baseUrl?$params".withAuthToken()

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string()
                val type = object : TypeToken<List<MergeResponseDto>>() {}.type
                val mrDtos = gson.fromJson<List<MergeResponseDto>>(body, type)

                // 转换为业务模型
                val mrs = mrDtos.map { it.toGitLabMergeRequest() }
                GitLabApiResponse(mrs, true, null, response.code)
            } else {
                val error = parseError(response.body?.string())
                GitLabApiResponse(null, false, error, response.code)
            }
        } catch (e: Exception) {
            GitLabApiResponse(null, false, e.message ?: "Unknown error", -1)
        }
    }

    /**
     * 获取单个合并请求的详细信息
     */
    suspend fun getMergeRequest(
        projectId: String,
        mergeRequestIid: Long
    ): GitLabApiResponse<GitLabMergeRequest> = withContext(Dispatchers.IO) {
        try {
            // 如果projectId是数字ID，不需要编码；如果是路径，需要编码
            val encodedProjectId = if (projectId.all { it.isDigit() }) {
                projectId
            } else {
                java.net.URLEncoder.encode(projectId, "UTF-8")
            }
            val url = "$apiBaseUrl/projects/$encodedProjectId/merge_requests/$mergeRequestIid".withAuthToken()

            val request = Request.Builder()
                .url(url)
                .header("PRIVATE-TOKEN", privateToken)
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string()
                val mrDto = gson.fromJson(body, MergeResponseDto::class.java)
                GitLabApiResponse(mrDto.toGitLabMergeRequest(), true, null, response.code)
            } else {
                val error = parseError(response.body?.string())
                GitLabApiResponse(null, false, error, response.code)
            }
        } catch (e: Exception) {
            GitLabApiResponse(null, false, e.message ?: "Unknown error", -1)
        }
    }

    suspend fun getRepositoryFileContent(
        projectId: String,
        filePath: String,
        ref: String
    ): GitLabApiResponse<GitLabMergeRequestFileContent> = withContext(Dispatchers.IO) {
        try {
            val encodedProjectId = encodeProjectId(projectId)
            val encodedFilePath = java.net.URLEncoder.encode(filePath, "UTF-8")
            val encodedRef = java.net.URLEncoder.encode(ref, "UTF-8")
            val url = "$apiBaseUrl/projects/$encodedProjectId/repository/files/$encodedFilePath/raw?ref=$encodedRef".withAuthToken()

            val request = Request.Builder()
                .url(url)
                .header("Accept", "*/*")
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val error = parseError(response.body?.string())
                return@withContext GitLabApiResponse(null, false, error, response.code)
            }

            val bodyBytes = response.body?.bytes() ?: ByteArray(0)
            val isText = isLikelyText(response.header("Content-Type"), bodyBytes)
            val content = if (isText) {
                decodeUtf8(bodyBytes) ?: bodyBytes.toString(StandardCharsets.UTF_8)
            } else {
                ""
            }

            GitLabApiResponse(
                GitLabMergeRequestFileContent(
                    path = filePath,
                    content = content,
                    revision = ref,
                    isText = isText
                ),
                true,
                null,
                response.code
            )
        } catch (e: Exception) {
            GitLabApiResponse(null, false, e.message ?: "Unknown error", -1)
        }
    }

    /**
     * 获取单个合并请求的变更文件列表
     */
    suspend fun getMergeRequestChanges(
        projectId: String,
        mergeRequestIid: Long
    ): GitLabApiResponse<List<GitLabMergeRequestChangeFile>> = withContext(Dispatchers.IO) {
        try {
            val encodedProjectId = if (projectId.all { it.isDigit() }) {
                projectId
            } else {
                java.net.URLEncoder.encode(projectId, "UTF-8")
            }
            val url = "$apiBaseUrl/projects/$encodedProjectId/merge_requests/$mergeRequestIid/changes".withAuthToken()

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string()
                val changesResponse = gson.fromJson(body, MergeRequestChangesResponseDto::class.java)
                val changedFiles = changesResponse.changes.orEmpty().map { it.toGitLabChangeFile() }
                GitLabApiResponse(changedFiles, true, null, response.code)
            } else {
                val error = parseError(response.body?.string())
                GitLabApiResponse(null, false, error, response.code)
            }
        } catch (e: Exception) {
            GitLabApiResponse(null, false, e.message ?: "Unknown error", -1)
        }
    }

    /**
     * 获取项目的所有合并请求（自动处理分页）
     */
    suspend fun getAllMergeRequests(
        projectId: String,
        state: String = "all",
        indicator: ProgressIndicator? = null
    ): GitLabApiResponse<List<GitLabMergeRequest>> = withContext(Dispatchers.IO) {
        try {
            val allMrs = mutableListOf<GitLabMergeRequest>()
            var page = 1
            var hasMore = true

            while (hasMore) {
                indicator?.checkCanceled()
                indicator?.text2 = "Loading page $page..."

                val response = getMergeRequests(projectId, state, page, perPage = 100)

                if (response.success && response.data != null) {
                    allMrs.addAll(response.data)
                    hasMore = response.data.size >= 100
                    page++
                } else {
                    hasMore = false
                }
            }

            GitLabApiResponse(allMrs, true, null, 200)
        } catch (e: Exception) {
            GitLabApiResponse(null, false, e.message ?: "Unknown error", -1)
        }
    }

    // ==================== 分支相关API ====================

    /**
     * 获取项目的所有分支（自动处理分页）
     * @param projectId 项目ID或路径
     * @param search 搜索关键词（可选）
     * @param indicator 进度指示器（可选）
     */
    suspend fun getProjectBranches(
        projectId: String,
        search: String? = null,
        indicator: ProgressIndicator? = null
    ): GitLabApiResponse<List<GitLabBranch>> = withContext(Dispatchers.IO) {
        try {
            val encodedProjectId = if (projectId.all { it.isDigit() }) {
                projectId
            } else {
                java.net.URLEncoder.encode(projectId, "UTF-8")
            }

            val allBranches = mutableListOf<GitLabBranch>()
            var page = 1
            var hasMore = true

            while (hasMore) {
                indicator?.checkCanceled()

                val baseUrl = "$apiBaseUrl/projects/$encodedProjectId/repository/branches"
                val url = buildString {
                    append(baseUrl)
                    append("?page=$page&per_page=100")
                    if (search != null) {
                        append("&search=${java.net.URLEncoder.encode(search, "UTF-8")}")
                    }
                }.withAuthToken()

                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .get()
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val type = object : TypeToken<List<GitLabBranch>>() {}.type
                    val branches = gson.fromJson<List<GitLabBranch>>(body, type)
                    allBranches.addAll(branches)
                    hasMore = branches.size >= 100
                    page++
                } else {
                    hasMore = false
                    if (allBranches.isEmpty()) {
                        val error = parseError(response.body?.string())
                        return@withContext GitLabApiResponse(null, false, error, response.code)
                    }
                }
            }

            GitLabApiResponse(allBranches, true, null, 200)
        } catch (e: Exception) {
            GitLabApiResponse(null, false, e.message ?: "Unknown error", -1)
        }
    }

    /**
     * 获取单个分支的提交信息
     * @param projectId 项目ID或路径
     * @param branchName 分支名称
     */
    suspend fun getBranchCommit(
        projectId: String,
        branchName: String
    ): GitLabApiResponse<GitLabCommit> = withContext(Dispatchers.IO) {
        try {
            val encodedProjectId = if (projectId.all { it.isDigit() }) {
                projectId
            } else {
                java.net.URLEncoder.encode(projectId, "UTF-8")
            }

            val encodedBranchName = java.net.URLEncoder.encode(branchName, "UTF-8")
            val url = "$apiBaseUrl/projects/$encodedProjectId/repository/branches/$encodedBranchName".withAuthToken()

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string()
                val json = JsonParser.parseString(body).asJsonObject
                val commitJson = json.getAsJsonObject("commit")
                val commit = gson.fromJson(commitJson, GitLabCommit::class.java)
                GitLabApiResponse(commit, true, null, response.code)
            } else {
                val error = parseError(response.body?.string())
                GitLabApiResponse(null, false, error, response.code)
            }
        } catch (e: Exception) {
            GitLabApiResponse(null, false, e.message ?: "Unknown error", -1)
        }
    }

    // ==================== 项目成员API ====================

    /**
     * 获取项目成员列表
     * 获取项目的所有成员（包括直接成员和通过组继承的成员）
     * @param projectId 项目ID或路径
     */
    suspend fun getProjectMembers(
        projectId: String
    ): GitLabApiResponse<List<GitLabMember>> = withContext(Dispatchers.IO) {
        try {
            val encodedProjectId = if (projectId.all { it.isDigit() }) {
                projectId
            } else {
                java.net.URLEncoder.encode(projectId, "UTF-8")
            }

            // 使用 /members/all 端点获取所有成员，包括通过组继承的成员
            val allMembers = mutableListOf<GitLabMember>()
            var page = 1
            val perPage = 100

            do {
                val url = "$apiBaseUrl/projects/$encodedProjectId/members/all?page=$page&per_page=$perPage".withAuthToken()

                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .get()
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val type = object : TypeToken<List<GitLabMember>>() {}.type
                    val members = gson.fromJson<List<GitLabMember>>(body, type)
                    allMembers.addAll(members)

                    // 检查是否还有更多数据
                    val linkHeader = response.header("Link")
                    if (linkHeader == null || !linkHeader.contains("rel=\"next\"")) {
                        break
                    }
                    page++
                } else {
                    val error = parseError(response.body?.string())
                    return@withContext GitLabApiResponse(null, false, error, response.code)
                }
            } while (true)

            GitLabApiResponse(allMembers, true, null, 200)
        } catch (e: Exception) {
            GitLabApiResponse(null, false, e.message ?: "Unknown error", -1)
        }
    }

    // ==================== 创建合并请求API ====================

    /**
     * 创建合并请求
     * @param projectId 项目ID或路径
     * @param request 创建MR的请求参数
     */
    suspend fun createMergeRequest(
        projectId: String,
        request: CreateMergeRequestRequest
    ): GitLabApiResponse<CreateMergeRequestResponse> = withContext(Dispatchers.IO) {
        try {
            val encodedProjectId = if (projectId.all { it.isDigit() }) {
                projectId
            } else {
                java.net.URLEncoder.encode(projectId, "UTF-8")
            }

            val url = "$apiBaseUrl/projects/$encodedProjectId/merge_requests".withAuthToken()
            val requestBodyJson = gson.toJson(request)
            val requestBody = requestBodyJson.toRequestBody(JSON_MEDIA_TYPE)

            val httpRequest = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = client.newCall(httpRequest).execute()

            if (response.isSuccessful) {
                val body = response.body?.string()
                val mrResponse = gson.fromJson(body, CreateMergeRequestResponse::class.java)
                GitLabApiResponse(mrResponse, true, null, response.code)
            } else {
                val error = parseError(response.body?.string())
                GitLabApiResponse(null, false, error, response.code)
            }
        } catch (e: Exception) {
            GitLabApiResponse(null, false, e.message ?: "Unknown error", -1)
        }
    }

    // ==================== MR操作API ====================

    /**
     * 关闭MR
     * @param projectId 项目ID或路径
     * @param mergeRequestIid MR的iid
     */
    suspend fun closeMergeRequest(
        projectId: String,
        mergeRequestIid: Long
    ): GitLabApiResponse<GitLabMergeRequest> = withContext(Dispatchers.IO) {
        try {
            val encodedProjectId = if (projectId.all { it.isDigit() }) {
                projectId
            } else {
                java.net.URLEncoder.encode(projectId, "UTF-8")
            }
            val url = "$apiBaseUrl/projects/$encodedProjectId/merge_requests/$mergeRequestIid".withAuthToken()

            // 使用 state_event: "close" 来关闭 MR
            val requestBody = """{"state_event": "close"}""".toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .put(requestBody)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string()
                val mrDto = gson.fromJson(body, MergeResponseDto::class.java)
                GitLabApiResponse(mrDto.toGitLabMergeRequest(), true, null, response.code)
            } else {
                val error = parseError(response.body?.string())
                GitLabApiResponse(null, false, error, response.code)
            }
        } catch (e: Exception) {
            GitLabApiResponse(null, false, e.message ?: "Unknown error", -1)
        }
    }

    /**
     * 合并MR
     * @param projectId 项目ID或路径
     * @param mergeRequestIid MR的iid
     * @param shouldRemoveSourceBranch 是否在合并后删除源分支
     */
    suspend fun mergeMergeRequest(
        projectId: String,
        mergeRequestIid: Long,
        shouldRemoveSourceBranch: Boolean = false
    ): GitLabApiResponse<GitLabMergeRequest> = withContext(Dispatchers.IO) {
        try {
            val encodedProjectId = if (projectId.all { it.isDigit() }) {
                projectId
            } else {
                java.net.URLEncoder.encode(projectId, "UTF-8")
            }
            val url = "$apiBaseUrl/projects/$encodedProjectId/merge_requests/$mergeRequestIid/merge".withAuthToken()

            // 构建请求体，根据参数决定是否删除源分支
            val requestBodyJson = if (shouldRemoveSourceBranch) {
                """{"should_remove_source_branch": true}"""
            } else {
                "{}"
            }
            val requestBody = requestBodyJson.toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .put(requestBody)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string()
                val mrDto = gson.fromJson(body, MergeResponseDto::class.java)
                GitLabApiResponse(mrDto.toGitLabMergeRequest(), true, null, response.code)
            } else {
                val error = parseError(response.body?.string())
                GitLabApiResponse(null, false, error, response.code)
            }
        } catch (e: Exception) {
            GitLabApiResponse(null, false, e.message ?: "Unknown error", -1)
        }
    }

    /**
     * 删除MR
     * @param projectId 项目ID或路径
     * @param mergeRequestIid MR的iid
     */
    suspend fun deleteMergeRequest(
        projectId: String,
        mergeRequestIid: Long
    ): GitLabApiResponse<Unit> = withContext(Dispatchers.IO) {
        try {
            val encodedProjectId = if (projectId.all { it.isDigit() }) {
                projectId
            } else {
                java.net.URLEncoder.encode(projectId, "UTF-8")
            }
            val url = "$apiBaseUrl/projects/$encodedProjectId/merge_requests/$mergeRequestIid".withAuthToken()

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .delete()
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                GitLabApiResponse(Unit, true, null, response.code)
            } else {
                val error = parseError(response.body?.string())
                GitLabApiResponse(null, false, error, response.code)
            }
        } catch (e: Exception) {
            GitLabApiResponse(null, false, e.message ?: "Unknown error", -1)
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 解析API错误信息
     */
    private fun parseError(body: String?): String {
        if (body == null) return "Unknown error"

        return try {
            val json = JsonParser.parseString(body).asJsonObject
            json.get("message")?.asString
                ?: json.get("error")?.asString
                ?: json.get("error_description")?.asString
                ?: body
        } catch (e: Exception) {
            // 如果不是JSON，返回原始内容的前200个字符
            body.take(200)
        }
    }

    override fun dispose() {
        coroutineScope.cancel()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    // ==================== 内部DTO类 ====================

    /**
     * 合并请求响应DTO
     */
    private data class MergeResponseDto(
        val id: Long,
        val iid: Long,
        val project_id: Long,
        val title: String,
        val description: String?,
        val state: String,
        val source_branch: String,
        val target_branch: String,
        val author: AuthorDto,
        val assignees: List<AuthorDto>?,
        val reviewers: List<AuthorDto>?,
        val merged_by: AuthorDto?,  // 修复：GitLab API返回单个对象，不是数组
        val created_at: String,
        val updated_at: String,
        val merged_at: String?,
        val closed_at: String?,
        val web_url: String,
        val draft: Boolean?,
        val work_in_progress: Boolean?,
        val has_conflicts: Boolean?,
        val merge_status: String?,
        val detailed_merge_status: String?,
        val labels: List<String>?,
        val upvotes: Int?,
        val downvotes: Int?,
        val user_notes_count: Int?,
        val force_remove_source_branch: Boolean? = null,
        val diff_refs: MergeRequestDiffRefsDto? = null
    ) {
        fun toGitLabMergeRequest(): GitLabMergeRequest {
            val hasConflict = has_conflicts ?: false
            val apiState = state.lowercase()
            val detailedMergeStatus = detailed_merge_status?.lowercase()
            val mergeStatus = merge_status?.lowercase()
            val finalState = when (apiState) {
                "merged" -> MergeRequestState.MERGED
                "closed" -> MergeRequestState.CLOSED
                "opened" -> when {
                    detailedMergeStatus in CHECKING_MERGE_STATUSES || mergeStatus in CHECKING_MERGE_STATUSES -> MergeRequestState.CHECKING
                    detailedMergeStatus == "conflict" || hasConflict -> MergeRequestState.LOCKED
                    else -> MergeRequestState.OPENED
                }
                else -> MergeRequestState.fromString(state)
            }

            return GitLabMergeRequest(
                id = id,
                iid = iid,
                projectId = project_id,
                title = title,
                description = description,
                state = finalState,
                mergeStatusRaw = merge_status,
                detailedMergeStatusRaw = detailed_merge_status,
                sourceBranch = source_branch,
                targetBranch = target_branch,
                author = author.toGitLabUser(),
                assignees = assignees?.map { it.toGitLabUser() } ?: emptyList(),
                reviewers = reviewers?.map { it.toGitLabUser() } ?: emptyList(),
                mergedBy = if (merged_by != null) listOf(merged_by.toGitLabUser()) else emptyList(),
                createdAt = created_at,
                updatedAt = updated_at,
                mergedAt = merged_at,
                closedAt = closed_at,
                webUrl = web_url,
                draft = draft ?: false,
                workInProgress = work_in_progress ?: false,
                hasConflicts = hasConflict,
                labels = labels ?: emptyList(),
                upvotes = upvotes ?: 0,
                downvotes = downvotes ?: 0,
                userNotesCount = user_notes_count ?: 0,
                forceRemoveSourceBranch = force_remove_source_branch ?: false,
                diffRefs = diff_refs?.toGitLabDiffRefs()
            )
        }
    }

    private data class MergeRequestChangesResponseDto(
        val changes: List<MergeRequestChangeDto>?
    )

    private data class MergeRequestChangeDto(
        val old_path: String?,
        val new_path: String,
        val new_file: Boolean?,
        val renamed_file: Boolean?,
        val deleted_file: Boolean?,
        val diff: String?,
        val old_mode: String?,
        val new_mode: String?
    ) {
        fun toGitLabChangeFile(): GitLabMergeRequestChangeFile {
            val changeType = when {
                renamed_file == true -> GitLabMergeRequestChangeType.RENAMED
                deleted_file == true -> GitLabMergeRequestChangeType.DELETED
                new_file == true -> GitLabMergeRequestChangeType.ADDED
                else -> GitLabMergeRequestChangeType.MODIFIED
            }

            return GitLabMergeRequestChangeFile(
                path = new_path,
                oldPath = old_path?.takeIf { it != new_path },
                changeType = changeType,
                diff = diff,
                oldMode = old_mode,
                newMode = new_mode,
                isBinary = false
            )
        }
    }

    private data class MergeRequestDiffRefsDto(
        val base_sha: String?,
        val head_sha: String?,
        val start_sha: String?
    ) {
        fun toGitLabDiffRefs(): GitLabMergeRequestDiffRefs {
            return GitLabMergeRequestDiffRefs(
                baseSha = base_sha,
                headSha = head_sha,
                startSha = start_sha
            )
        }
    }

    private data class AuthorDto(
        val id: Long,
        val username: String,
        val name: String,
        val state: String?,
        val avatar_url: String?,
        val web_url: String?
    ) {
        fun toGitLabUser(): GitLabUser {
            return GitLabUser(
                id = id,
                username = username,
                name = name,
                state = state,
                avatarUrl = avatar_url,
                webUrl = web_url
            )
        }
    }
}
