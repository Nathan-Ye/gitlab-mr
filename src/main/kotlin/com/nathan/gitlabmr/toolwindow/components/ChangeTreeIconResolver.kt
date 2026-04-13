package com.nathan.gitlabmr.toolwindow.components

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.IconUtil
import javax.swing.Icon
import javax.swing.UIManager

interface ChangeTreeIconResolver {
    fun clearCache()
    fun resolveFileIcon(node: FileNodeContext): Icon
    fun resolveDirectoryIcon(node: DirectoryNodeContext, expanded: Boolean): Icon
}

data class FileNodeContext(
    val changeFile: com.nathan.gitlabmr.model.GitLabMergeRequestChangeFile,
    val displayName: String,
    val relativePath: String,
    val localVirtualFile: VirtualFile?
)

data class DirectoryNodeContext(
    val displayName: String,
    val relativePath: String?,
    val isModuleGroup: Boolean,
    val fileCount: Int,
    val localVirtualFile: VirtualFile?
)

class DefaultChangeTreeIconResolver(
    private val project: Project
) : ChangeTreeIconResolver {

    private val fileIconCache = mutableMapOf<String, Icon>()
    private val directoryIconCache = mutableMapOf<String, Icon>()

    override fun clearCache() {
        fileIconCache.clear()
        directoryIconCache.clear()
    }

    override fun resolveFileIcon(node: FileNodeContext): Icon {
        val key = "${node.relativePath}:${node.changeFile.changeType}"
        return fileIconCache.getOrPut(key) {
            resolveNativeFileIcon(node.localVirtualFile)
                ?: FileTypeManager.getInstance().getFileTypeByFileName(node.displayName).icon
                ?: AllIcons.FileTypes.Any_type
        }
    }

    override fun resolveDirectoryIcon(node: DirectoryNodeContext, expanded: Boolean): Icon {
        val key = "${node.relativePath ?: node.displayName}:$expanded"
        return directoryIconCache.getOrPut(key) {
            val nativeIcon = resolveNativeDirectoryIcon(node.localVirtualFile)
            if (expanded) {
                UIManager.getIcon("Tree.openIcon") ?: nativeIcon ?: AllIcons.Nodes.Folder
            } else {
                nativeIcon ?: UIManager.getIcon("Tree.closedIcon") ?: AllIcons.Nodes.Folder
            }
        }
    }

    private fun resolveNativeFileIcon(file: VirtualFile?): Icon? {
        if (file == null || !file.isValid) return null
        return runCatching {
            IconUtil.getIcon(file, Iconable.ICON_FLAG_READ_STATUS, project)
        }.getOrNull()
    }

    private fun resolveNativeDirectoryIcon(directory: VirtualFile?): Icon? {
        if (directory == null || !directory.isValid) return null
        return runCatching {
            IconUtil.getIcon(directory, Iconable.ICON_FLAG_READ_STATUS, project)
        }.getOrNull()
    }
}

fun fileStatusForChangeType(changeType: com.nathan.gitlabmr.model.GitLabMergeRequestChangeType): FileStatus {
    return when (changeType) {
        com.nathan.gitlabmr.model.GitLabMergeRequestChangeType.ADDED -> FileStatus.ADDED
        com.nathan.gitlabmr.model.GitLabMergeRequestChangeType.MODIFIED -> FileStatus.MODIFIED
        com.nathan.gitlabmr.model.GitLabMergeRequestChangeType.DELETED -> FileStatus.DELETED
        com.nathan.gitlabmr.model.GitLabMergeRequestChangeType.RENAMED -> FileStatus.MODIFIED
    }
}
