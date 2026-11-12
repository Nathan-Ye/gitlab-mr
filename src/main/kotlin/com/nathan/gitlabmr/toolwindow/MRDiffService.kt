package com.nathan.gitlabmr.toolwindow

import com.nathan.gitlabmr.model.MergeRequestDiffPayload
import com.nathan.gitlabmr.util.GitUtil
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

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

        // 关联项目中的本地文件后，diff 查看器的 "Jump to Source" 才能定位到真实文件
        val localFile = GitUtil.findLocalFile(project, payload.changeFile.path)
            ?.takeIf { !it.fileType.isBinary }

        val contentFactory = DiffContentFactory.getInstance()
        val beforeContent = createContent(contentFactory, payload.beforeContent?.content.orEmpty(), fileType, localFile)
        val afterContent = createContent(contentFactory, payload.afterContent?.content.orEmpty(), fileType, localFile)

        return SimpleDiffRequest(
            payload.title,
            beforeContent,
            afterContent,
            payload.beforeTitle,
            payload.afterTitle
        )
    }

    private fun createContent(
        contentFactory: DiffContentFactory,
        text: String,
        fileType: FileType,
        localFile: VirtualFile?
    ): DocumentContent {
        return if (localFile != null) {
            contentFactory.create(project, text, localFile)
        } else {
            contentFactory.create(project, text, fileType)
        }
    }
}
