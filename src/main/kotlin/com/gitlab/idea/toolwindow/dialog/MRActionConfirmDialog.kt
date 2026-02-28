package com.gitlab.idea.toolwindow.dialog

import com.gitlab.idea.model.GitLabMergeRequest
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

/**
 * MR操作确认对话框
 */
object MRActionConfirmDialog {

    /**
     * 确认删除MR
     */
    fun confirmDelete(project: Project, mr: GitLabMergeRequest): Boolean {
        val message = """
            确定要删除合并请求 "${mr.title}" 吗？

            ⚠️ 警告：此操作不可撤销！
        """.trimIndent()

        return Messages.showYesNoDialog(
            project,
            message,
            "删除合并请求",
            Messages.getWarningIcon()
        ) == Messages.YES
    }
}
