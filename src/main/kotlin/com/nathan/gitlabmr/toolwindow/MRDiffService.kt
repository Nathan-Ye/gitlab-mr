package com.nathan.gitlabmr.toolwindow

import com.nathan.gitlabmr.api.GitLabApiClient
import com.nathan.gitlabmr.api.MRDiffContentLoader
import com.nathan.gitlabmr.model.GitLabMergeRequest
import com.nathan.gitlabmr.model.GitLabMergeRequestChangeFile
import com.nathan.gitlabmr.model.MergeRequestDiffPayload
import com.nathan.gitlabmr.util.GitLabNotifications
import com.nathan.gitlabmr.util.GitUtil
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManagerEx
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.chains.DiffRequestProducerException
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.runBlocking

class MRDiffService(
    private val project: Project
) {

    /**
     * 按“变更”树自上而下的顺序打开差异链，使前后差异按钮可以跨文件连续跳转。
     */
    fun showDiff(
        apiClient: GitLabApiClient,
        projectId: String,
        mergeRequest: GitLabMergeRequest,
        changeFiles: List<GitLabMergeRequestChangeFile>,
        selectedFile: GitLabMergeRequestChangeFile
    ) {
        val diffFiles = changeFiles.filterNot { it.isBinary }
        val selectedIndex = diffFiles.indexOfFirst { isSameChange(it, selectedFile) }
        if (selectedIndex < 0) {
            val message = if (selectedFile.isBinary) "二进制文件暂不支持差异预览" else "未找到该文件对应的变更"
            GitLabNotifications.showError(project, "打开差异失败", message)
            return
        }

        val producers = diffFiles.map { changeFile ->
            MergeRequestFileDiffProducer(project, apiClient, projectId, mergeRequest, changeFile)
        }
        val chain = SimpleDiffRequestChain.fromProducers(producers, selectedIndex)
        DiffManagerEx.getInstance().showDiffBuiltin(project, chain, DiffDialogHints.DEFAULT)
    }

    private fun isSameChange(a: GitLabMergeRequestChangeFile, b: GitLabMergeRequestChangeFile): Boolean {
        return a.path == b.path && a.oldPath == b.oldPath && a.changeType == b.changeType
    }

    private class MergeRequestFileDiffProducer(
        private val project: Project,
        private val apiClient: GitLabApiClient,
        private val projectId: String,
        private val mergeRequest: GitLabMergeRequest,
        private val changeFile: GitLabMergeRequestChangeFile
    ) : DiffRequestProducer {

        override fun getName(): String = changeFile.path

        override fun process(context: UserDataHolder, indicator: ProgressIndicator): DiffRequest {
            indicator.checkCanceled()

            val response = runBlocking {
                MRDiffContentLoader(apiClient).loadDiffPayload(projectId, mergeRequest, changeFile)
            }
            val payload = response.data
            if (!response.success || payload == null) {
                throw DiffRequestProducerException(response.error ?: "无法构建差异内容")
            }

            indicator.checkCanceled()
            return buildDiffRequest(project, payload)
        }
    }

    companion object {
        private fun buildDiffRequest(project: Project, payload: MergeRequestDiffPayload): SimpleDiffRequest {
            val fileName = payload.afterContent?.path ?: payload.beforeContent?.path ?: payload.changeFile.path
            val resolvedFileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName)
            val fileType = if (resolvedFileType.isBinary) PlainTextFileType.INSTANCE else resolvedFileType

            // 关联项目中的本地文件后，diff 查看器的 "Jump to Source" 才能定位到真实文件
            val localFile = GitUtil.findLocalFile(project, payload.changeFile.path)
                ?.takeIf { !it.fileType.isBinary }

            val contentFactory = DiffContentFactory.getInstance()
            val beforeContent = createContent(contentFactory, project, payload.beforeContent?.content.orEmpty(), fileType, localFile)
            val afterContent = createContent(contentFactory, project, payload.afterContent?.content.orEmpty(), fileType, localFile)

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
            project: Project,
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
}
