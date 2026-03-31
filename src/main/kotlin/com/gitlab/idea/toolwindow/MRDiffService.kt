package com.gitlab.idea.toolwindow

import com.gitlab.idea.model.MergeRequestDiffPayload
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project

class MRDiffService(
    private val project: Project
) {

    fun showDiff(payload: MergeRequestDiffPayload) {
        DiffManager.getInstance().showDiff(project, createDiffRequest(payload))
    }

    private fun createDiffRequest(payload: MergeRequestDiffPayload): SimpleDiffRequest {
        val fileName = payload.afterContent?.path ?: payload.beforeContent?.path ?: payload.changeFile.path
        val resolvedFileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName)
        val fileType = if (resolvedFileType.isBinary) PlainTextFileType.INSTANCE else resolvedFileType

        val contentFactory = DiffContentFactory.getInstance()
        val beforeContent = contentFactory.create(project, payload.beforeContent?.content.orEmpty(), fileType)
        val afterContent = contentFactory.create(project, payload.afterContent?.content.orEmpty(), fileType)

        return SimpleDiffRequest(
            payload.title,
            beforeContent,
            afterContent,
            payload.beforeTitle,
            payload.afterTitle
        )
    }
}
