package com.gitlab.idea.config

import com.gitlab.idea.model.GitLabServer
import com.gitlab.idea.model.GitLabServerList
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

/**
 * GitLab配置持久化服务（项目级别）
 * 用于存储项目特定的GitLab服务器配置
 */
@Service(Service.Level.PROJECT)
@State(
    name = "GitLabProjectConfigService",
    storages = [Storage("GitLabProjectConfig.xml")]
)
class GitLabProjectConfigService : PersistentStateComponent<GitLabProjectConfigService.State> {

    private var state = State()

    companion object {
        fun getInstance(project: Project): GitLabProjectConfigService {
            return project.getService(GitLabProjectConfigService::class.java)
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
     */
    fun addServer(server: GitLabServer) {
        state.serverList.servers.add(server)
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
}
