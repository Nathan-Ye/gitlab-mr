package com.gitlab.idea.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitLocalBranch
import git4idea.config.GitConfigUtil
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Git工具类
 * 用于从IDEA项目中提取Git相关信息
 */
object GitUtil {

    /**
     * 获取当前项目的所有Git仓库
     */
    fun getRepositories(project: Project): List<GitRepository> {
        return GitRepositoryManager.getInstance(project).repositories
    }

    /**
     * 获取主仓库（通常项目只有一个仓库）
     */
    fun getMainRepository(project: Project): GitRepository? {
        val repositories = getRepositories(project)
        return if (repositories.size == 1) repositories[0] else null
    }

    /**
     * 获取远程仓库URL
     * @param repository Git仓库
     * @param remoteName 远程名称，默认为origin
     */
    fun getRemoteUrl(repository: GitRepository?, remoteName: String = "origin"): String? {
        if (repository == null) return null

        val remote = repository.remotes.find { it.name == remoteName }
        return remote?.firstUrl
    }

    /**
     * 获取所有远程仓库URL
     */
    fun getAllRemoteUrls(repository: GitRepository?): List<String> {
        if (repository == null) return emptyList()

        return repository.remotes.flatMap { remote ->
            remote.urls
        }
    }

    /**
     * 获取当前分支
     */
    fun getCurrentBranch(repository: GitRepository?): GitLocalBranch? {
        return repository?.currentBranch
    }

    /**
     * 检查是否有未提交的更改
     */
    fun hasUncommittedChanges(repository: GitRepository?): Boolean {
        // Simple check - if repository exists, it might have changes
        // For a more accurate check, would need to use ChangeListManager
        return repository != null
    }

    /**
     * 从Git URL中提取项目路径
     * 支持HTTP和SSH两种URL格式
     */
    fun extractProjectPathFromUrl(gitUrl: String): String? {
        if (gitUrl.isBlank()) return null

        return when {
            // HTTP/HTTPS URL: https://gitlab.com/group/project.git
            gitUrl.startsWith("http://") || gitUrl.startsWith("https://") -> {
                val uri = java.net.URI(gitUrl)
                var path = uri.path.removePrefix("/").removeSuffix(".git")
                path.ifEmpty { null }
            }
            // SSH URL: git@gitlab.com:group/project.git
            gitUrl.startsWith("git@") || gitUrl.contains(":") -> {
                val colonIndex = gitUrl.indexOf(":")
                if (colonIndex > 0) {
                    var path = gitUrl.substring(colonIndex + 1).removeSuffix(".git")
                    path.ifEmpty { null }
                } else {
                    null
                }
            }
            else -> null
        }
    }

    /**
     * 检测GitLab服务器URL
     */
    fun detectGitLabServerUrl(gitUrl: String): String? {
        if (gitUrl.isBlank()) return null

        return when {
            gitUrl.startsWith("http://") || gitUrl.startsWith("https://") -> {
                val uri = java.net.URI(gitUrl)
                "${uri.scheme}://${uri.host}"
            }
            gitUrl.startsWith("git@") -> {
                val atIndex = gitUrl.indexOf("@")
                val colonIndex = gitUrl.indexOf(":")
                if (atIndex > 0 && colonIndex > atIndex) {
                    gitUrl.substring(atIndex + 1, colonIndex)
                } else {
                    null
                }
            }
            else -> null
        }
    }

    /**
     * 判断远程仓库是否是GitLab仓库
     */
    fun isGitLabRepository(repository: GitRepository?): Boolean {
        val urls = getAllRemoteUrls(repository)
        return urls.any { url ->
            url.contains("gitlab.com", ignoreCase = true) ||
                    url.contains("gitlab", ignoreCase = true)
        }
    }

    /**
     * 异步获取Git配置值
     */
    suspend fun getGitConfigValue(
        project: Project,
        repository: GitRepository,
        key: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val root = repository.root
            GitConfigUtil.getValue(project, root, key)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取.git目录
     */
    fun getGitDir(repository: GitRepository?): VirtualFile? {
        return repository?.root?.findChild(".git")
    }
}
