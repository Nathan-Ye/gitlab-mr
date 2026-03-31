package com.gitlab.idea.toolwindow.components

import com.gitlab.idea.model.GitLabMergeRequestChangeFile
import com.gitlab.idea.model.GitLabMergeRequestChangeType
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.vcs.FileStatus
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

class MRChangesTreePanel : JPanel(BorderLayout()) {

    private val cardLayout = CardLayout()
    private val cardPanel = JPanel(cardLayout)
    private val treeRoot = DefaultMutableTreeNode(RootNode)
    private val treeModel = DefaultTreeModel(treeRoot)
    private val changesTree = Tree(treeModel)
    private val loadingLabel = createStatusLabel("正在加载改动文件...")
    private val emptyLabel = createStatusLabel("当前合并请求没有改动文件")
    private val errorLabel = createStatusLabel("改动文件加载失败")

    var onFileSelected: ((GitLabMergeRequestChangeFile) -> Unit)? = null
    var onFileDoubleClicked: ((GitLabMergeRequestChangeFile) -> Unit)? = null

    init {
        background = UIUtil.getPanelBackground()

        changesTree.isRootVisible = false
        changesTree.showsRootHandles = true
        changesTree.background = UIUtil.getPanelBackground()
        changesTree.border = JBUI.Borders.empty(8)
        changesTree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        changesTree.cellRenderer = ChangesTreeRenderer()
        changesTree.addTreeSelectionListener {
            val node = changesTree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return@addTreeSelectionListener
            val fileNode = node.userObject as? FileNode ?: return@addTreeSelectionListener
            onFileSelected?.invoke(fileNode.changeFile)
        }
        changesTree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount != 2 || e.button != MouseEvent.BUTTON1) return

                val path = changesTree.getPathForLocation(e.x, e.y) ?: return
                val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                val fileNode = node.userObject as? FileNode ?: return
                onFileDoubleClicked?.invoke(fileNode.changeFile)
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

    fun showLoading() {
        cardLayout.show(cardPanel, "LOADING")
    }

    fun showEmpty(message: String = "当前合并请求没有改动文件") {
        emptyLabel.text = message
        cardLayout.show(cardPanel, "EMPTY")
    }

    fun showError(message: String) {
        errorLabel.text = message
        cardLayout.show(cardPanel, "ERROR")
    }

    fun setChanges(changes: List<GitLabMergeRequestChangeFile>) {
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

        val moduleRoots = linkedMapOf<String, ModuleTreeNode>()
        for (change in changes.sortedBy { it.path.lowercase() }) {
            val segments = change.path.split('/').filter { it.isNotBlank() }
            if (segments.isEmpty()) continue

            val moduleName = segments.first()
            val moduleNode = moduleRoots.getOrPut(moduleName) { ModuleTreeNode(moduleName, isModule = true) }
            insertChange(moduleNode, segments.drop(1), change)
        }

        moduleRoots.values.forEach { moduleNode ->
            compressDirectoryChain(moduleNode)
            treeRoot.add(toSwingNode(moduleNode))
        }

        treeModel.reload()
    }

    private fun insertChange(moduleNode: ModuleTreeNode, relativeSegments: List<String>, change: GitLabMergeRequestChangeFile) {
        if (relativeSegments.isEmpty()) {
            moduleNode.files.add(change)
            return
        }

        var currentNode = moduleNode
        val directorySegments = relativeSegments.dropLast(1)
        for (segment in directorySegments) {
            currentNode = currentNode.directories.getOrPut(segment) { ModuleTreeNode(segment) }
        }
        currentNode.files.add(change)
    }

    private fun compressDirectoryChain(node: ModuleTreeNode) {
        val compressedDirectories = linkedMapOf<String, ModuleTreeNode>()

        for ((name, child) in node.directories.toSortedMap(String.CASE_INSENSITIVE_ORDER)) {
            compressDirectoryChain(child)
            val mergedChild = mergeSingleDirectoryChain(name, child)
            compressedDirectories[mergedChild.name] = mergedChild
        }

        node.directories.clear()
        node.directories.putAll(compressedDirectories)
        node.files.sortBy { it.path.substringAfterLast('/').lowercase() }
    }

    private fun mergeSingleDirectoryChain(name: String, node: ModuleTreeNode): ModuleTreeNode {
        var mergedName = name
        var currentNode = node

        while (currentNode.files.isEmpty() && currentNode.directories.size == 1) {
            val nextEntry = currentNode.directories.entries.first()
            mergedName += "/${nextEntry.key}"
            currentNode = nextEntry.value
        }

        if (mergedName == currentNode.name) return currentNode

        return ModuleTreeNode(mergedName).also { mergedNode ->
            mergedNode.directories.putAll(currentNode.directories)
            mergedNode.files.addAll(currentNode.files)
        }
    }

    private fun toSwingNode(node: ModuleTreeNode): DefaultMutableTreeNode {
        val swingNode = DefaultMutableTreeNode(DirectoryNode(node.name, node.isModule))

        node.directories.values.forEach { child ->
            swingNode.add(toSwingNode(child))
        }

        node.files.forEach { change ->
            swingNode.add(DefaultMutableTreeNode(FileNode(change)))
        }

        return swingNode
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

    private class ModuleTreeNode(var name: String, val isModule: Boolean = false) {
        val directories = linkedMapOf<String, ModuleTreeNode>()
        val files = mutableListOf<GitLabMergeRequestChangeFile>()
    }

    private data class DirectoryNode(val displayName: String, val isModule: Boolean)

    private data class FileNode(val changeFile: GitLabMergeRequestChangeFile) {
        val fileName: String
            get() = changeFile.path.substringAfterLast('/')
    }

    private class ChangesTreeRenderer : ColoredTreeCellRenderer() {
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
                is DirectoryNode -> renderDirectory(userObject)
                is FileNode -> renderFile(userObject)
            }
        }

        private fun renderDirectory(node: DirectoryNode) {
            icon = if (node.isModule) AllIcons.Nodes.Module else AllIcons.Nodes.Folder
            append(node.displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        }

        private fun renderFile(node: FileNode) {
            val fileType = FileTypeManager.getInstance().getFileTypeByFileName(node.fileName)
            icon = fileType.icon
            append(
                node.fileName,
                SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, getFileStatus(node.changeFile.changeType).color)
            )
        }

        private fun getFileStatus(changeType: GitLabMergeRequestChangeType): FileStatus {
            return when (changeType) {
                GitLabMergeRequestChangeType.ADDED -> FileStatus.ADDED
                GitLabMergeRequestChangeType.MODIFIED -> FileStatus.MODIFIED
                GitLabMergeRequestChangeType.DELETED -> FileStatus.DELETED
                GitLabMergeRequestChangeType.RENAMED -> FileStatus.MODIFIED
            }
        }
    }
}
