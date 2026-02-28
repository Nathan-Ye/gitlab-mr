package com.gitlab.idea.toolwindow.components

import com.gitlab.idea.model.GitLabMergeRequest
import com.gitlab.idea.model.MergeRequestState
import com.intellij.ui.JBColor
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridLayout
import java.awt.RenderingHints
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*

/**
 * 合并请求详情面板
 * 显示单个MR的完整信息
 */
class MRDetailsPanel : JPanel() {

    private val mainPanel = JPanel()
    private val titleLabel = JBTextArea()
    private val stateLabel = RoundedLabel()
    private val stateTextLabel = JBLabel("状态：")
    private val removeSourceBranchLabel = JBLabel()
    private val removeSourceBranchTextLabel = JBLabel("合并后删除源分支：")
    private val authorLabel = JBLabel()
    private val branchLabel = BranchInfoLabel()
    private val createdTimeLabel = JBLabel()
    private val mergedTimeLabel = JBLabel()
    private val assigneeLabel = JBLabel()
    private val mergedByLabel = JBLabel()
    private val descriptionArea = JBTextArea()
    private val actionToolbar: MRActionToolbar = MRActionToolbar()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private var currentMR: GitLabMergeRequest? = null
    private var scrollPane: JScrollPane
    private val emptyStatePanel = JPanel()
    private val emptyStateLabel = JLabel()
    private val centerCardPanel: JPanel
    private val centerCardLayout: CardLayout

    init {
        layout = BorderLayout()

        // 创建空状态面板
        emptyStatePanel.layout = BorderLayout()
        emptyStatePanel.background = UIUtil.getPanelBackground()
        emptyStateLabel.text = "请选择一个合并请求查看详情"
        emptyStateLabel.font = emptyStateLabel.font.deriveFont(Font.PLAIN, 14f)
        emptyStateLabel.foreground = JBColor.GRAY
        emptyStateLabel.horizontalAlignment = SwingConstants.CENTER
        emptyStateLabel.verticalAlignment = SwingConstants.CENTER
        emptyStatePanel.add(emptyStateLabel, BorderLayout.CENTER)

        // 创建滚动面板
        scrollPane = JScrollPane(mainPanel)
        scrollPane.border = null
        scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        // 加快鼠标滚轮滚动速度（单位滚动增量）
        scrollPane.verticalScrollBar.unitIncrement = 16

        // 创建中心卡片面板，用于在空状态和详情之间切换
        centerCardLayout = CardLayout()
        centerCardPanel = JPanel(centerCardLayout)
        centerCardPanel.add(emptyStatePanel, "EMPTY")
        centerCardPanel.add(scrollPane, "CONTENT")

        add(actionToolbar, BorderLayout.NORTH)
        add(centerCardPanel, BorderLayout.CENTER)

        setupUI()
    }

    private fun setupUI() {
        mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)
        mainPanel.border = JBUI.Borders.empty(16)
        mainPanel.background = UIUtil.getPanelBackground()

        // 标题 - 使用 JTextArea 支持自动换行
        titleLabel.isEditable = false
        titleLabel.isFocusable = false
        titleLabel.lineWrap = true
        titleLabel.wrapStyleWord = true
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 15f)
        titleLabel.background = UIUtil.getPanelBackground()
        titleLabel.alignmentX = LEFT_ALIGNMENT
        // 设置最大宽度为无限制，让文本区填充可用宽度
        titleLabel.maximumSize = java.awt.Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE)

        // 状态标签 - 使用圆角设计
        stateLabel.font = stateLabel.font.deriveFont(Font.BOLD, 12f)
        stateLabel.alignmentX = LEFT_ALIGNMENT

        // 状态文本标签
        stateTextLabel.font = stateTextLabel.font.deriveFont(Font.BOLD, 13f)
        stateTextLabel.alignmentX = LEFT_ALIGNMENT

        // 删除源分支标签样式
        removeSourceBranchTextLabel.font = removeSourceBranchTextLabel.font.deriveFont(Font.BOLD, 13f)
        removeSourceBranchTextLabel.alignmentX = LEFT_ALIGNMENT
        removeSourceBranchLabel.font = removeSourceBranchLabel.font.deriveFont(Font.PLAIN, 13f)
        removeSourceBranchLabel.alignmentX = LEFT_ALIGNMENT

        // 信息标签样式
        authorLabel.font = authorLabel.font.deriveFont(Font.PLAIN, 13f)
        createdTimeLabel.font = createdTimeLabel.font.deriveFont(Font.PLAIN, 13f)
        mergedTimeLabel.font = mergedTimeLabel.font.deriveFont(Font.PLAIN, 13f)
        assigneeLabel.font = assigneeLabel.font.deriveFont(Font.PLAIN, 13f)
        mergedByLabel.font = mergedByLabel.font.deriveFont(Font.PLAIN, 13f)

        // 描述
        descriptionArea.isEditable = false
        descriptionArea.isFocusable = false
        descriptionArea.lineWrap = true
        descriptionArea.wrapStyleWord = true
        descriptionArea.background = UIUtil.getPanelBackground()
        descriptionArea.font = descriptionArea.font.deriveFont(Font.PLAIN, 13f)
        descriptionArea.alignmentX = LEFT_ALIGNMENT
        // 设置最大宽度为无限制，让文本区填充可用宽度
        descriptionArea.maximumSize = Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE)

        // 创建两列信息面板 - 使用 GridLayout 实现 5:5 等宽布局
        val infoPanel = JPanel()
        infoPanel.layout = GridLayout(1, 2, 12, 0)
        infoPanel.background = UIUtil.getPanelBackground()
        infoPanel.alignmentX = LEFT_ALIGNMENT

        // 左列面板
        val leftColumn = JPanel()
        leftColumn.layout = BoxLayout(leftColumn, BoxLayout.Y_AXIS)
        leftColumn.background = UIUtil.getPanelBackground()

        // 右列面板
        val rightColumn = JPanel()
        rightColumn.layout = BoxLayout(rightColumn, BoxLayout.Y_AXIS)
        rightColumn.background = UIUtil.getPanelBackground()

        // 设置所有标签左对齐
        authorLabel.alignmentX = LEFT_ALIGNMENT
        createdTimeLabel.alignmentX = LEFT_ALIGNMENT
        assigneeLabel.alignmentX = LEFT_ALIGNMENT
        mergedTimeLabel.alignmentX = LEFT_ALIGNMENT
        mergedByLabel.alignmentX = LEFT_ALIGNMENT

        // 添加左列内容 - 状态移到第一行，横向排列
        val stateRowPanel = JPanel()
        stateRowPanel.layout = BoxLayout(stateRowPanel, BoxLayout.X_AXIS)
        stateRowPanel.background = UIUtil.getPanelBackground()
        stateRowPanel.alignmentX = LEFT_ALIGNMENT
        stateRowPanel.add(stateTextLabel)
        stateRowPanel.add(Box.createHorizontalStrut(6))
        stateRowPanel.add(stateLabel)
        stateRowPanel.add(Box.createHorizontalGlue())

        leftColumn.add(stateRowPanel)
        leftColumn.add(Box.createVerticalStrut(10))
        leftColumn.add(authorLabel)
        leftColumn.add(Box.createVerticalStrut(10))
        leftColumn.add(createdTimeLabel)

        // 添加右列内容
        rightColumn.add(assigneeLabel)
        rightColumn.add(Box.createVerticalStrut(10))
        rightColumn.add(mergedTimeLabel)
        rightColumn.add(Box.createVerticalStrut(10))
        rightColumn.add(mergedByLabel)

        infoPanel.add(leftColumn)
        infoPanel.add(rightColumn)

        // 添加组件到主面板
        // 标题 - 使用包装面板实现宽度自适应
        val titleWrapper = createWrapperPanel(titleLabel)
        mainPanel.add(titleWrapper)
        mainPanel.add(Box.createVerticalStrut(10))

        // 分支行 - 两列布局：左边分支，右边删除源分支
        val branchRowPanel = JPanel()
        branchRowPanel.layout = GridLayout(1, 2, 12, 0)
        branchRowPanel.background = UIUtil.getPanelBackground()
        branchRowPanel.alignmentX = LEFT_ALIGNMENT

        // 左列：分支信息 - 横向排列
        val branchLeftPanel = JPanel()
        branchLeftPanel.layout = BoxLayout(branchLeftPanel, BoxLayout.X_AXIS)
        branchLeftPanel.background = UIUtil.getPanelBackground()
        branchLeftPanel.alignmentX = LEFT_ALIGNMENT

        val branchTextLabel = JBLabel("分支：")
        branchTextLabel.font = branchTextLabel.font.deriveFont(Font.BOLD, 13f)

        branchLeftPanel.add(branchTextLabel)
        branchLeftPanel.add(Box.createHorizontalStrut(6))
        branchLeftPanel.add(branchLabel)
        branchLeftPanel.add(Box.createHorizontalGlue())

        // 右列：删除源分支
        val removeSourceBranchRightPanel = JPanel()
        removeSourceBranchRightPanel.layout = BoxLayout(removeSourceBranchRightPanel, BoxLayout.X_AXIS)
        removeSourceBranchRightPanel.background = UIUtil.getPanelBackground()
        removeSourceBranchRightPanel.alignmentX = LEFT_ALIGNMENT
        removeSourceBranchRightPanel.add(removeSourceBranchTextLabel)
        removeSourceBranchRightPanel.add(Box.createHorizontalStrut(6))
        removeSourceBranchRightPanel.add(removeSourceBranchLabel)

        branchRowPanel.add(branchLeftPanel)
        branchRowPanel.add(removeSourceBranchRightPanel)

        // 包装分支行，使其自适应宽度
        val branchRowWrapper = createWrapperPanel(branchRowPanel)
        mainPanel.add(branchRowWrapper)
        mainPanel.add(Box.createVerticalStrut(16))
        mainPanel.add(createSeparator())
        mainPanel.add(Box.createVerticalStrut(10))
        mainPanel.add(infoPanel)
        mainPanel.add(Box.createVerticalStrut(16))
        mainPanel.add(createSeparator())
        mainPanel.add(Box.createVerticalStrut(10))
        val descriptionLabel = JLabel("描述")
        descriptionLabel.font = descriptionLabel.font.deriveFont(Font.BOLD, 13f)
        descriptionLabel.alignmentX = LEFT_ALIGNMENT
        mainPanel.add(descriptionLabel)
        mainPanel.add(Box.createVerticalStrut(6))
        // 描述区 - 使用包装面板实现宽度自适应
        val descriptionWrapper = createWrapperPanel(descriptionArea)
        mainPanel.add(descriptionWrapper)
        mainPanel.add(Box.createVerticalGlue())

        // 监听 mainPanel 的大小变化，实现实时自动换行
        mainPanel.addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentResized(e: java.awt.event.ComponentEvent) {
                // mainPanel 大小变化时，强制文本区重新计算布局
                forceTextAreasRelayout()
            }
        })

        // 同时监听滚动条视口大小变化
        scrollPane.viewport.addChangeListener { e ->
            forceTextAreasRelayout()
        }
    }

    /**
     * 强制文本区重新布局
     */
    private fun forceTextAreasRelayout() {
        // 重新设置文本会触发 JTextArea 重新计算换行
        // 先失效，然后重新验证
        titleLabel.invalidate()
        descriptionArea.invalidate()

        titleLabel.revalidate()
        descriptionArea.revalidate()

        titleLabel.repaint()
        descriptionArea.repaint()
    }

    override fun addNotify() {
        super.addNotify()
        // 同时也监听外层面板的大小变化
        addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentResized(e: java.awt.event.ComponentEvent) {
                // 延迟执行，确保布局更新完成
                SwingUtilities.invokeLater {
                    forceTextAreasRelayout()
                }
            }
        })
    }

    /**
     * 设置合并请求
     */
    fun setMergeRequest(mr: GitLabMergeRequest) {
        currentMR = mr

        // 显示工具栏和详情内容
        actionToolbar.isVisible = true
        centerCardLayout.show(centerCardPanel, "CONTENT")

        // 设置标题
        titleLabel.text = mr.title

        // 设置状态
        stateLabel.text = mr.state.displayName
        stateLabel.setStateColor(getStateColor(mr.state))

        // 根据状态设置"合并后删除源分支"字段（仅对待合并状态显示）
        when (mr.state) {
            MergeRequestState.OPENED -> {
                removeSourceBranchLabel.text = if (mr.forceRemoveSourceBranch) "是" else "否"
                removeSourceBranchTextLabel.isVisible = true
                removeSourceBranchLabel.isVisible = true
            }
            else -> {
                removeSourceBranchTextLabel.isVisible = false
                removeSourceBranchLabel.isVisible = false
            }
        }

        // 设置分支信息（使用带背景色的标签样式）
        branchLabel.setBranchInfo(mr.sourceBranch, mr.targetBranch)

        // 设置作者
        authorLabel.text = "创建人: ${mr.author.name} (@${mr.author.username})"

        // 设置创建时间
        createdTimeLabel.text = "创建时间: ${formatDate(mr.createdAt)}"

        // 设置分配者
        assigneeLabel.text = if (mr.assignees.isNotEmpty()) {
            "分配给: ${mr.assignees.joinToString(", ") { "${it.name} (@${it.username})" }}"
        } else {
            "分配给: 无"
        }

        // 根据状态设置合并/关闭信息和合并者/关闭人
        when (mr.state) {
            MergeRequestState.MERGED -> {
                // 已合并状态：显示合并时间和合并人
                mergedTimeLabel.text = if (mr.mergedAt != null) {
                    "合并时间: ${formatDate(mr.mergedAt)}"
                } else {
                    "合并时间: 未知"
                }
                mergedByLabel.text = if (mr.mergedBy.isNotEmpty()) {
                    "合并者: ${mr.mergedBy.joinToString(", ") { "${it.name} (@${it.username})" }}"
                } else {
                    "合并者: 未知"
                }
                mergedTimeLabel.isVisible = true
                mergedByLabel.isVisible = true
            }
            MergeRequestState.CLOSED -> {
                // 已关闭状态：只显示关闭时间（没有则取更新时间），不显示关闭人
                mergedTimeLabel.text = if (mr.closedAt != null) {
                    "关闭时间: ${formatDate(mr.closedAt)}"
                } else {
                    "关闭时间: ${formatDate(mr.updatedAt)}"
                }
                mergedTimeLabel.isVisible = true
                mergedByLabel.isVisible = false
            }
            else -> {
                // 待合并或LOCKED状态：不显示这两个信息
                mergedTimeLabel.isVisible = false
                mergedByLabel.isVisible = false
            }
        }

        // 设置描述
        descriptionArea.text = if (mr.description.isNullOrBlank()) {
            "无描述"
        } else {
            mr.description
        }

        mainPanel.revalidate()
        mainPanel.repaint()

        // 将滚动条设置到顶部，确保显示最顶部的内容
        SwingUtilities.invokeLater {
            scrollPane.verticalScrollBar.value = 0
        }

        // 更新工具栏按钮状态
        actionToolbar.updateButtonStates(mr)
    }

    /**
     * 清空详情
     */
    fun clear() {
        currentMR = null

        // 隐藏工具栏，显示空状态
        actionToolbar.isVisible = false
        centerCardLayout.show(centerCardPanel, "EMPTY")

        // 清空工具栏按钮状态
        actionToolbar.updateButtonStates(null)
    }

    fun setOnCloseMR(callback: (GitLabMergeRequest) -> Unit) {
        actionToolbar.onCloseMRClicked = callback
    }

    fun setOnMergeMR(callback: (GitLabMergeRequest) -> Unit) {
        actionToolbar.onMergeMRClicked = callback
    }

    fun setOnDeleteMR(callback: (GitLabMergeRequest) -> Unit) {
        actionToolbar.onDeleteMRClicked = callback
    }

    private fun formatDate(dateString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = inputFormat.parse(dateString)
            dateFormat.format(date)
        } catch (e: Exception) {
            dateString
        }
    }

    private fun getStateColor(state: MergeRequestState): Color {
        return when (state) {
            MergeRequestState.OPENED -> JBColor(Color(0x7058a3), Color(0x7058a3))
            MergeRequestState.CLOSED -> JBColor(Color(0x7f8c8d), Color(0x7f8c8d))
            MergeRequestState.LOCKED -> JBColor(Color(0x9f9627), Color(0x9f9627))
            MergeRequestState.MERGED -> JBColor(Color(0x108548), Color(0x108548))
        }
    }

    private fun createSeparator(): TitledSeparator {
        return TitledSeparator()
    }

    /**
     * 创建一个包装面板，确保内部组件能够自适应容器宽度
     */
    private fun createWrapperPanel(component: Component): JPanel {
        val panel = JPanel()
        panel.layout = BorderLayout()
        panel.background = UIUtil.getPanelBackground()
        panel.alignmentX = LEFT_ALIGNMENT
        // 设置最大尺寸为无限制，让包装面板填充可用宽度
        panel.maximumSize = java.awt.Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE)
        panel.add(component, BorderLayout.CENTER)
        return panel
    }

    init {
        clear()
    }
}

/**
 * 圆角标签组件
 * 用于显示带圆角背景和白色文字的状态标签
 */
class RoundedLabel : JLabel() {
    private var bgColor: Color = JBColor.GRAY
    private val cornerRadius = 12
    private val paddingH = 10
    private val paddingV = 4

    init {
        isOpaque = false
        foreground = JBColor(Color.WHITE, Color.WHITE)
        horizontalAlignment = CENTER
    }

    fun setStateColor(color: Color) {
        bgColor = color
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g as? Graphics2D ?: return
        val backgroundColor = bgColor

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = backgroundColor
        // 先绘制圆角背景
        g2.fillRoundRect(0, 0, width - 1, height - 1, cornerRadius, cornerRadius)

        // 再绘制文字（使用父类的绘制方法，会尊重 foreground 设置）
        super.paintComponent(g)
    }

    override fun getPreferredSize(): Dimension {
        val fm = getFontMetrics(font)
        val textWidth = fm.stringWidth(text)
        val textHeight = fm.height
        return Dimension(
            textWidth + paddingH * 2,
            textHeight + paddingV * 2
        )
    }

    override fun getMinimumSize(): Dimension = preferredSize

    override fun getMaximumSize(): Dimension = preferredSize
}

/**
 * 分支信息标签组件
 * 用于显示带圆角背景的分支信息（source → target）
 */
class BranchInfoLabel : JLabel() {
    private val cornerRadius = 8
    private val paddingH = 10
    private val paddingV = 4

    init {
        isOpaque = false
        horizontalAlignment = CENTER
        font = font.deriveFont(Font.BOLD, 13f)
    }

    /**
     * 设置分支信息
     * @param sourceBranch 源分支
     * @param targetBranch 目标分支
     */
    fun setBranchInfo(sourceBranch: String, targetBranch: String) {
        text = "$sourceBranch  →  $targetBranch"
        toolTipText = "从 $sourceBranch 合并到 $targetBranch"
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g as? Graphics2D ?: return

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // 绘制圆角背景 - 使用浅蓝色调
        val bgColor = JBColor(Color(0xE3F2FD), Color(0x2D3B4D))
        g2.color = bgColor
        g2.fillRoundRect(0, 0, width - 1, height - 1, cornerRadius, cornerRadius)

        // 绘制边框 - 使用稍深的蓝色
        val borderColor = JBColor(Color(0x90CAF9), Color(0x4A6B8A))
        g2.color = borderColor
        g2.drawRoundRect(0, 0, width - 1, height - 1, cornerRadius, cornerRadius)

        // 再绘制文字（使用父类的绘制方法，会尊重 foreground 设置）
        super.paintComponent(g)
    }

    override fun getPreferredSize(): Dimension {
        val fm = getFontMetrics(font)
        val textWidth = fm.stringWidth(text)
        val textHeight = fm.height
        return Dimension(
            textWidth + paddingH * 2,
            textHeight + paddingV * 2
        )
    }

    override fun getMinimumSize(): Dimension = preferredSize

    override fun getMaximumSize(): Dimension = preferredSize
}
