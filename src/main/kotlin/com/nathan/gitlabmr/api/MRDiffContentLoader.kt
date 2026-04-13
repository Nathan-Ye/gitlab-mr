package com.nathan.gitlabmr.api

import com.nathan.gitlabmr.model.GitLabApiResponse
import com.nathan.gitlabmr.model.GitLabMergeRequest
import com.nathan.gitlabmr.model.GitLabMergeRequestChangeFile
import com.nathan.gitlabmr.model.GitLabMergeRequestChangeType
import com.nathan.gitlabmr.model.GitLabMergeRequestFileContent
import com.nathan.gitlabmr.model.MergeRequestDiffPayload

class MRDiffContentLoader(
    private val apiClient: GitLabApiClient
) {

    suspend fun loadDiffPayload(
        projectId: String,
        mergeRequest: GitLabMergeRequest,
        changeFile: GitLabMergeRequestChangeFile
    ): GitLabApiResponse<MergeRequestDiffPayload> {
        if (changeFile.isBinary) {
            return GitLabApiResponse(null, false, "Binary files are not supported in diff preview")
        }

        val detailedMergeRequest = if (mergeRequest.diffRefs == null) {
            val response = apiClient.getMergeRequest(projectId, mergeRequest.iid)
            if (!response.success || response.data == null) {
                return GitLabApiResponse(
                    null,
                    false,
                    response.error ?: "Unable to load merge request details",
                    response.statusCode
                )
            }
            response.data
        } else {
            mergeRequest
        }

        val diffRefs = detailedMergeRequest.diffRefs
            ?: return GitLabApiResponse(null, false, "Unable to load merge request diff refs")

        val beforeRef = diffRefs.baseSha ?: diffRefs.startSha
        val afterRef = diffRefs.headSha

        val beforeContent = when (changeFile.changeType) {
            GitLabMergeRequestChangeType.ADDED -> null
            GitLabMergeRequestChangeType.MODIFIED,
            GitLabMergeRequestChangeType.DELETED,
            GitLabMergeRequestChangeType.RENAMED -> {
                if (beforeRef.isNullOrBlank()) {
                    return GitLabApiResponse(null, false, "Missing base revision for diff")
                }
                val beforePath = changeFile.oldPath ?: changeFile.path
                val response = loadTextContent(projectId, beforePath, beforeRef)
                if (!response.success || response.data == null) {
                    return GitLabApiResponse(
                        null,
                        false,
                        response.error ?: "Unable to load previous file content",
                        response.statusCode
                    )
                }
                response.data
            }
        }

        val afterContent = when (changeFile.changeType) {
            GitLabMergeRequestChangeType.DELETED -> null
            GitLabMergeRequestChangeType.ADDED,
            GitLabMergeRequestChangeType.MODIFIED,
            GitLabMergeRequestChangeType.RENAMED -> {
                if (afterRef.isNullOrBlank()) {
                    return GitLabApiResponse(null, false, "Missing head revision for diff")
                }
                val response = loadTextContent(projectId, changeFile.path, afterRef)
                if (!response.success || response.data == null) {
                    return GitLabApiResponse(
                        null,
                        false,
                        response.error ?: "Unable to load updated file content",
                        response.statusCode
                    )
                }
                response.data
            }
        }

        return GitLabApiResponse(
            MergeRequestDiffPayload(
                changeFile = changeFile,
                beforeContent = beforeContent,
                afterContent = afterContent,
                title = buildWindowTitle(changeFile),
                beforeTitle = buildSideTitle("Before", beforeContent?.path ?: changeFile.oldPath ?: changeFile.path),
                afterTitle = buildSideTitle("After", afterContent?.path ?: changeFile.path)
            ),
            true
        )
    }

    private suspend fun loadTextContent(
        projectId: String,
        filePath: String,
        ref: String
    ): GitLabApiResponse<GitLabMergeRequestFileContent> {
        val response = apiClient.getRepositoryFileContent(projectId, filePath, ref)
        if (!response.success || response.data == null) {
            return response
        }

        if (!response.data.isText) {
            return GitLabApiResponse(null, false, "Binary files are not supported in diff preview", response.statusCode)
        }

        return response
    }

    private fun buildWindowTitle(changeFile: GitLabMergeRequestChangeFile): String {
        return when (changeFile.changeType) {
            GitLabMergeRequestChangeType.ADDED -> "Diff: ${changeFile.path} [Added]"
            GitLabMergeRequestChangeType.DELETED -> "Diff: ${changeFile.oldPath ?: changeFile.path} [Deleted]"
            GitLabMergeRequestChangeType.RENAMED -> {
                val oldPath = changeFile.oldPath ?: changeFile.path
                "Diff: $oldPath -> ${changeFile.path} [Renamed]"
            }
            GitLabMergeRequestChangeType.MODIFIED -> "Diff: ${changeFile.path}"
        }
    }

    private fun buildSideTitle(prefix: String, path: String): String {
        return "$prefix: $path"
    }
}
