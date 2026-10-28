package com.nathan.gitlabmr.util

import com.nathan.gitlabmr.model.GitLabBranch
import java.time.Instant

/**
 * 分支筛选与排序纯函数，无 IntelliJ 依赖，便于单元测试。
 */
object BranchFilters {

    /**
     * 按关键字筛选分支：关键字 trim 后为空返回全部；
     * 否则返回分支名忽略大小写“包含”关键字的分支，保持原有相对顺序。
     */
    fun filterBranches(all: List<GitLabBranch>, keyword: String): List<GitLabBranch> {
        val normalized = keyword.trim().lowercase()
        if (normalized.isEmpty()) {
            return all
        }
        return all.filter { it.name.lowercase().contains(normalized) }
    }

    /**
     * 分支排序：main/master 优先 → 名称含 test 次之 → 提交时间 committedDate 降序。
     * committedDate 无法解析时按 0 处理，不抛异常。
     */
    fun rankBranches(branches: List<GitLabBranch>): List<GitLabBranch> {
        return branches.sortedWith(
            compareBy<GitLabBranch>(
                { branch ->
                    when (branch.name.lowercase()) {
                        "master", "main" -> 0
                        else -> 1
                    }
                },
                { branch ->
                    if ("test" in branch.name.lowercase()) 0 else 1
                }
            ).thenByDescending { branch ->
                try {
                    Instant.parse(branch.commit.committedDate).toEpochMilli()
                } catch (e: Exception) {
                    0L
                }
            }
        )
    }
}
