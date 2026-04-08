package com.gitlab.idea.toolwindow.components

import com.gitlab.idea.model.GitLabMergeRequestChangeFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

class MRChangesTreePanel(
    project: Project
) : JPanel(BorderLayout()) {

    private val cardLayout = CardLayout()
    private val cardPanel = JPanel(cardLayout)
    private val treeRoot = DefaultMutableTreeNode(RootNode)
    private val treeModel = DefaultTreeModel(treeRoot)
    private val changesTree = Tree(treeModel)
    private val loadingLabel = createStatusLabel("正在加载变更文件...")
    private val emptyLabel = createStatusLabel("暂无可展示的变更文件")
    private val errorLabel = createStatusLabel("变更文件加载失败")
    private val iconResolver: ChangeTreeIconResolver = DefaultChangeTreeIconResolver(project)

    private var repositoryRoot: VirtualFile? = null

    var onFileSelected: ((GitLabMergeRequestChangeFile) -> Unit)? = null
    var onFileDoubleClicked: ((GitLabMergeRequestChangeFile) -> Unit)? = null

    init {
        background = UIUtil.getPanelBackground()

        changesTree.isRootVisible = false
        changesTree.showsRootHandles = true
        changesTree.background = UIUtil.getPanelBackground()
        changesTree.border = JBUI.Borders.empty(8)
        changesTree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        changesTree.cellRenderer = ChangesTreeRenderer(iconResolver)
        changesTree.addTreeSelectionListener {
            val node = changesTree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return@addTreeSelectionListener
            val fileNode = node.userObject as? FileNode ?: return@addTreeSelectionListener
            onFileSelected?.invoke(fileNode.context.changeFile)
        }
        changesTree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount != 2 || e.button != MouseEvent.BUTTON1) return

                val path = changesTree.getPathForLocation(e.x, e.y) ?: return
                val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                val fileNode = node.userObject as? FileNode ?: return
                onFileDoubleClicked?.invoke(fileNode.context.changeFile)
            }
        })

        val treePanel = JPanel(BorderLayout())
        treePanel.background = UIUtil.getPanelBackground()
        treePanel.add(ScrollPaneFactory.createScrollPane(changesTree, true), BorderLayout.CENTER)

        cardPanel.add(wrapStatusPanel(loadingLabel), "LOADING")
        cardPanel.add(wrapStatusPanel(emptyLabel), "EMPTY")
        cardPanel.add(wrapStatusPanel(errorLabel), "ERROR")
        cardPanel.add(treePanel, "TREE")

        add(cardPanel, BorderLayout.CENTER)
        showEmpty()
    }

    fun setRepositoryRoot(root: VirtualFile?) {
        repositoryRoot = root
        iconResolver.clearCache()
    }

    fun showLoading() {
        cardLayout.show(cardPanel, "LOADING")
    }

    fun showEmpty(message: String = "暂无可展示的变更文件") {
        emptyLabel.text = message
        cardLayout.show(cardPanel, "EMPTY")
    }

    fun showError(message: String) {
        errorLabel.text = message
        cardLayout.show(cardPanel, "ERROR")
    }

    fun setChanges(changes: List<GitLabMergeRequestChangeFile>) {
        iconResolver.clearCache()
        rebuildTree(changes)
        if (treeRoot.childCount == 0) {
            showEmpty()
            return
        }

        expandAll()
        cardLayout.show(cardPanel, "TREE")
    }

    fun expandAllChanges() {
        if (treeRoot.childCount == 0) return
        expandAll()
        cardLayout.show(cardPanel, "TREE")
    }

    fun collapseToModules() {
        if (treeRoot.childCount == 0) return

        for (row in changesTree.rowCount - 1 downTo 0) {
            changesTree.collapseRow(row)
        }

        cardLayout.show(cardPanel, "TREE")
    }

    private fun rebuildTree(changes: List<GitLabMergeRequestChangeFile>) {
        treeRoot.removeAllChildren()

        val moduleRoots = linkedMapOf<String, DirectoryTreeNode>()
        for (change in changes.sortedBy { it.path.lowercase() }) {
            val relativePath = normalizeRelativePath(change.path) ?: continue
            val segments = relativePath.split('/').filter { it.isNotBlank() }
            if (segments.isEmpty()) continue

            val moduleName = segments.first()
            val moduleNode = moduleRoots.getOrPut(moduleName) {
                DirectoryTreeNode(
                    displayName = moduleName,
                    relativePath = moduleName,
                    isModuleGroup = true,
                    localVirtualFile = resolveLocalFile(moduleName)
                )
            }
            insertChange(moduleNode, relativePath, segments.drop(1), change)
        }

        moduleRoots.values.forEach { moduleNode ->
            compressDirectoryChain(moduleNode)
            treeRoot.add(toSwingNode(moduleNode))
        }

        treeModel.reload()
    }

    private fun insertChange(
        moduleNode: DirectoryTreeNode,
        changeRelativePath: String,
        relativeSegments: List<String>,
        change: GitLabMergeRequestChangeFile
    ) {
        if (relativeSegments.isEmpty()) {
            moduleNode.files.add(createFileNodeContext(change, changeRelativePath))
            return
        }

        val fileName = changeRelativePath.substringAfterLast('/')
        val directorySegments = changeRelativePath.removeSuffix("/$fileName")
            .split('/')
            .drop(1)
            .filter { it.isNotBlank() }

        var currentNode = moduleNode
        val pathBuilder = mutableListOf(moduleNode.displayName)
        for (segment in directorySegments) {
            pathBuilder += segment
            val currentRelativePath = pathBuilder.joinToString("/")
            currentNode = currentNode.directories.getOrPut(segment) {
                DirectoryTreeNode(
                    displayName = segment,
                    relativePath = currentRelativePath,
                    isModuleGroup = false,
                    localVirtualFile = resolveLocalFile(currentRelativePath)
                )
            }
        }
        currentNode.files.add(createFileNodeContext(change, changeRelativePath))
    }

    private fun createFileNodeContext(
        change: GitLabMergeRequestChangeFile,
        relativePath: String
    ): FileNodeContext {
        val displayName = relativePath.substringAfterLast('/')
        return FileNodeContext(
            changeFile = change,
            displayName = displayName,
            relativePath = relativePath,
            localVirtualFile = resolveLocalFile(relativePath)
        )
    }

    private fun compressDirectoryChain(node: DirectoryTreeNode) {
        val compressedDirectories = linkedMapOf<String, DirectoryTreeNode>()

        for ((name, child) in node.directories.toSortedMap(String.CASE_INSENSITIVE_ORDER)) {
            compressDirectoryChain(child)
            val mergedChild = mergeSingleDirectoryChain(name, child)
            compressedDirectories[mergedChild.displayName] = mergedChild
        }

        node.directories.clear()
        node.directories.putAll(compressedDirectories)
        node.files.sortBy { it.displayName.lowercase() }
    }

    private fun mergeSingleDirectoryChain(name: String, node: DirectoryTreeNode): DirectoryTreeNode {
        var mergedName = name
        var currentNode = node

        while (currentNode.files.isEmpty() && currentNode.directories.size == 1) {
            val nextEntry = currentNode.directories.entries.first()
            mergedName += "/${nextEntry.key}"
            currentNode = nextEntry.value
        }

        if (mergedName == currentNode.displayName) return currentNode

        return DirectoryTreeNode(
            displayName = mergedName,
            relativePath = currentNode.relativePath,
            isModuleGroup = currentNode.isModuleGroup,
            localVirtualFile = currentNode.localVirtualFile
        ).also { mergedNode ->
            mergedNode.directories.putAll(currentNode.directories)
            mergedNode.files.addAll(currentNode.files)
        }
    }

    private fun toSwingNode(node: DirectoryTreeNode): DefaultMutableTreeNode {
        val swingNode = DefaultMutableTreeNode(
            DirectoryNodeContext(
                displayName = node.displayName,
                relativePath = node.relativePath,
                isModuleGroup = node.isModuleGroup,
                fileCount = node.totalFileCount,
                localVirtualFile = node.localVirtualFile
            )
        )

        node.directories.values.forEach { child ->
            swingNode.add(toSwingNode(child))
        }

        node.files.forEach { file ->
            swingNode.add(DefaultMutableTreeNode(FileNode(file)))
        }

        return swingNode
    }

    private fun resolveLocalFile(relativePath: String?): VirtualFile? {
        val normalizedPath = normalizeRelativePath(relativePath) ?: return null
        return repositoryRoot?.findFileByRelativePath(normalizedPath)
    }

    private fun normalizeRelativePath(path: String?): String? {
        return path
            ?.replace('\\', '/')
            ?.trim('/')
            ?.takeIf { it.isNotBlank() }
    }

    private fun expandAll() {
        var row = 0
        while (row < changesTree.rowCount) {
            changesTree.expandRow(row)
            row++
        }
    }

    private fun createStatusLabel(text: String): JLabel {
        return JLabel(text, SwingConstants.CENTER).apply {
            foreground = JBColor.GRAY
            font = font.deriveFont(13f)
        }
    }

    private fun wrapStatusPanel(label: JLabel): JPanel {
        return JPanel(BorderLayout()).apply {
            background = UIUtil.getPanelBackground()
            add(label, BorderLayout.CENTER)
        }
    }

    private object RootNode

    private class DirectoryTreeNode(
        var displayName: String,
        val relativePath: String?,
        val isModuleGroup: Boolean,
        val localVirtualFile: VirtualFile?
    ) {
        val directories = linkedMapOf<String, DirectoryTreeNode>()
        val files = mutableListOf<FileNodeContext>()

        val totalFileCount: Int
            get() = files.size + directories.values.sumOf { it.totalFileCount }
    }

    private data class FileNode(val context: FileNodeContext)

    private class ChangesTreeRenderer(
        private val iconResolver: ChangeTreeIconResolver
    ) : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: javax.swing.JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ) {
            val node = value as? DefaultMutableTreeNode ?: return
            when (val userObject = node.userObject) {
                is DirectoryNodeContext -> renderDirectory(userObject, expanded)
                is FileNode -> renderFile(userObject.context)
            }
        }

        private fun renderDirectory(node: DirectoryNodeContext, expanded: Boolean) {
            icon = iconResolver.resolveDirectoryIcon(node, expanded)
            append(node.displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            append(" (${node.fileCount}个文件)", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }

        private fun renderFile(node: FileNodeContext) {
            icon = iconResolver.resolveFileIcon(node)
            append(
                node.displayName,
                SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, fileStatusForChangeType(node.changeFile.changeType).color)
            )
        }
    }
}
