package com.gitlab.idea

import com.gitlab.idea.config.GitLabConfigService
import com.intellij.ide.BrowserUtil
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepositoryManager

/**
 * GitLab插件主类
 * 管理插件生命周期和核心功能
 */
class GitLabPlugin {

    companion object {
        private const val PLUGIN_ID = "com.gitlab.idea.integration"

        /**
         * 获取插件版本
         */
        fun getVersion(): String {
            val plugin = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))
            return plugin?.version ?: "unknown"
        }

        /**
         * 在浏览器中打开GitLab文档
         */
        fun openDocumentation() {
            BrowserUtil.browse("https://docs.gitlab.com/")
        }

        /**
         * 获取配置服务
         */
        fun getConfigService(): GitLabConfigService {
            return GitLabConfigService.getInstance()
        }
    }
}

/**
 * 插件应用服务
 */
class GitLabApplicationService {

    init {
        // 插件初始化
        println("GitLab Integration Plugin loaded")
    }
}

/**
 * 插件项目服务
 */
class GitLabProjectService(val project: Project) {

    init {
        println("GitLab Integration Plugin loaded for project: ${project.name}")
    }

    /**
     * 检查项目是否有Git仓库
     */
    fun hasGitRepository(): Boolean {
        return GitRepositoryManager.getInstance(project).repositories.isNotEmpty()
    }
}
