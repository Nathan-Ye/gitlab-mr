package com.gitlab.idea.config

import com.gitlab.idea.model.GitLabServer
import com.gitlab.idea.model.GitLabServerList
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * GitLab配置持久化服务（应用级别）
 * 用于存储全局GitLab服务器配置
 */
@Service(Service.Level.APP)
@State(
    name = "GitLabConfigService",
    storages = [Storage("GitLabConfig.xml")]
)
class GitLabConfigService : PersistentStateComponent<GitLabConfigService.State> {

    private var state = State()

    companion object {
        fun getInstance(): GitLabConfigService {
            return com.intellij.openapi.application.ApplicationManager.getApplication().getService(GitLabConfigService::class.java)
        }
    }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    /**
     * 配置状态数据类
     */
    data class State(
        var serverList: GitLabServerList = GitLabServerList(),
        var selectedServerId: String? = null
    )

    // ==================== 公共API方法 ====================

    /**
     * 获取所有服务器配置
     */
    fun getAllServers(): List<GitLabServer> {
        return state.serverList.servers
    }

    /**
     * 添加服务器配置
     * 如果设为默认服务器，则先将其他默认服务器取消，确保只有一个默认服务器
     * 如果已存在相同URL的服务器，则更新而非重复添加
     */
    fun addServer(server: GitLabServer) {
        // 仅处理应用级（默认）服务器
        if (server.isDefault) {
            // 1. 如果新服务器设为默认，先取消其他所有服务器的默认状态
            state.serverList.servers.forEach { it.isDefault = false }

            // 2. 检查是否存在相同URL的服务器，有则更新，无则添加
            val existingIndex = state.serverList.servers.indexOfFirst { it.url == server.url }
            if (existingIndex >= 0) {
                state.serverList.servers[existingIndex] = server
            } else {
                state.serverList.servers.add(server)
            }
        }
    }

    /**
     * 更新服务器配置
     */
    fun updateServer(server: GitLabServer) {
        val index = state.serverList.servers.indexOfFirst { it.id == server.id }
        if (index >= 0) {
            state.serverList.servers[index] = server
        }
    }

    /**
     * 删除服务器配置
     */
    fun removeServer(serverId: String) {
        state.serverList.servers.removeIf { it.id == serverId }
        if (state.selectedServerId == serverId) {
            state.selectedServerId = null
        }
    }

    /**
     * 获取选中的服务器
     */
    fun getSelectedServer(): GitLabServer? {
        val selectedId = state.selectedServerId
        return state.serverList.servers.find { it.id == selectedId }
    }

    /**
     * 设置选中的服务器
     */
    fun setSelectedServer(serverId: String?) {
        state.selectedServerId = serverId
    }

    /**
     * 根据ID获取服务器
     */
    fun getServerById(serverId: String): GitLabServer? {
        return state.serverList.servers.find { it.id == serverId }
    }

    /**
     * 清空所有配置
     */
    fun clearAll() {
        state.serverList.servers.clear()
        state.selectedServerId = null
    }

    /**
     * 清除所有服务器的默认状态
     */
    fun clearAllDefaultServers() {
        state.serverList.servers.forEach { it.isDefault = false }
    }

    /**
     * 获取所有默认服务器（应用级）
     */
    fun getDefaultServers(): List<GitLabServer> {
        return state.serverList.servers.filter { it.isDefault }
    }
}
