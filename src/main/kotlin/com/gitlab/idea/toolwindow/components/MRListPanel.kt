package com.gitlab.idea.toolwindow.components

import com.gitlab.idea.model.GitLabMergeRequest
import com.gitlab.idea.model.MergeRequestState
import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.RenderingHints
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*
import javax.swing.border.CompoundBorder

/**
 * 合并请求列表面板
 * 包含筛选器和MR列表
 */
class MRListPanel : JPanel() {

    private val listModel = DefaultListModel<GitLabMergeRequest>()
    private val mrList: JBList<GitLabMergeRequest>
    private val listRenderer: MRListCellRenderer  // 保存 renderer 引用
    val titleFilter: TextFieldWithIcon
    val stateFilter: JComboBox<String>
    val scopeFilter: JComboBox<String>
    private val filterPanel: JPanel
    private val scrollPane: JBScrollPane
    private val loadingLabel: JLabel

    var onFilterChanged: ((MergeRequestState?, String?, String?) -> Unit)? = null
    var onMRSelected: ((GitLabMergeRequest) -> Unit)? = null
    var onLoadMore: (() -> Unit)? = null

    private var hasMoreData: Boolean = false
    private var isLoadingData: Boolean = false

    init {
        layout = BorderLayout()

        // 创建筛选面板 - 使用 IDEA 原生样式
        filterPanel = JPanel()
        filterPanel.layout = BoxLayout(filterPanel, BoxLayout.X_AXIS)
        // 添加底部边框作为分割线
        filterPanel.border = CompoundBorder(
            JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
            JBUI.Borders.empty(2, 10)
        )
        filterPanel.background = UIUtil.getPanelBackground()

        // 标题搜索框（带放大镜图标）
        titleFilter = TextFieldWithIcon()
        titleFilter.setIcon(AllIcons.Actions.Search)
        titleFilter.placeholderText = "标题搜索"
        titleFilter.preferredSize = Dimension(180, 36)
        titleFilter.maximumSize = Dimension(180, 36)

        // 添加焦点监听器以重绘 placeholder
        titleFilter.addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusGained(e: java.awt.event.FocusEvent?) {
                titleFilter.repaint()
            }
            override fun focusLost(e: java.awt.event.FocusEvent?) {
                titleFilter.repaint()
            }
        })

        // 状态下拉筛选
        val stateLabel = JLabel("状态:")
        stateLabel.font = stateLabel.font.deriveFont(Font.PLAIN, 12f)
        stateFilter = JComboBox(arrayOf("全部", "OPENED", "CLOSED", "LOCKED", "MERGED"))
        stateFilter.preferredSize = Dimension(90, 36)
        stateFilter.maximumSize = Dimension(90, 36)
        stateFilter.addActionListener {
            applyFilters()
        }

        // 范围下拉筛选
        val scopeLabel = JLabel("范围:")
        scopeLabel.font = scopeLabel.font.deriveFont(Font.PLAIN, 12f)
        scopeFilter = JComboBox(arrayOf("ALL", "CREATEDBY_ME", "ASSIGNEDTO_ME"))
        scopeFilter.preferredSize = Dimension(130, 36)
        scopeFilter.maximumSize = Dimension(130, 36)
        scopeFilter.addActionListener {
            applyFilters()
        }

        // 添加筛选组件（搜索框在最左侧）
        filterPanel.add(titleFilter)
        filterPanel.add(Box.createHorizontalStrut(16))
        filterPanel.add(stateLabel)
        filterPanel.add(Box.createHorizontalStrut(6))
        filterPanel.add(stateFilter)
        filterPanel.add(Box.createHorizontalStrut(16))
        filterPanel.add(scopeLabel)
        filterPanel.add(Box.createHorizontalStrut(6))
        filterPanel.add(scopeFilter)
        filterPanel.add(Box.createHorizontalGlue())

        // 标题搜索框回车监听
        titleFilter.addActionListener {
            applyFilters()
        }

        // 创建列表
        listRenderer = MRListCellRenderer()
        mrList = JBList(listModel)
        mrList.cellRenderer = listRenderer
        mrList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        mrList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selected = mrList.selectedValue
                if (selected != null) {
                    onMRSelected?.invoke(selected)
                }
            }
        }

        // 添加鼠标移动监听器，实现悬停效果
        mrList.addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
            override fun mouseMoved(e: java.awt.event.MouseEvent) {
                val index = mrList.locationToIndex(e.point)
                // 检查鼠标点是否真正在列表项的边界内
                // JList.locationToIndex() 在超出范围时会返回最近的索引，所以需要额外检查
                val actualHoverIndex = if (index >= 0) {
                    val cellBounds = mrList.getCellBounds(index, index)
                    if (cellBounds != null && cellBounds.contains(e.point)) {
                        index  // 鼠标在列表项边界内
                    } else {
                        -1  // 鼠标在空白区域
                    }
                } else {
                    -1
                }

                if (listRenderer.hoveredIndex != actualHoverIndex) {
                    listRenderer.hoveredIndex = actualHoverIndex
                    mrList.repaint()
                }
            }
        })

        // 鼠标离开列表时清除悬停状态
        mrList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseExited(e: java.awt.event.MouseEvent) {
                if (listRenderer.hoveredIndex != -1) {
                    listRenderer.hoveredIndex = -1
                    mrList.repaint()
                }
            }

            override fun mousePressed(e: java.awt.event.MouseEvent) {
                // 检查点击位置是否真正在列表项的边界内
                val index = mrList.locationToIndex(e.point)
                val shouldSelect = if (index >= 0) {
                    val cellBounds = mrList.getCellBounds(index, index)
                    cellBounds != null && cellBounds.contains(e.point)
                } else {
                    false
                }

                // 如果点击在空白区域，清除选择
                if (!shouldSelect) {
                    mrList.clearSelection()
                }
            }
        })

        // 添加滚动面板
        scrollPane = JBScrollPane(mrList)
        scrollPane.border = null

        // 添加滚动监听，实现自动加载更多
        scrollPane.verticalScrollBar.addAdjustmentListener { e ->
            if (!e.valueIsAdjusting) {
                checkScrollToBottom()
            }
        }

        // 创建加载状态标签 - 使用 IDEA 原生样式
        loadingLabel = JLabel("正在加载更多...")
        loadingLabel.horizontalAlignment = SwingConstants.CENTER
        loadingLabel.font = loadingLabel.font.deriveFont(Font.ITALIC, 11f)
        loadingLabel.border = JBUI.Borders.empty(6)
        loadingLabel.isVisible = false

        // 使用包装面板来放置加载标签
        val listWrapper = JPanel(BorderLayout())
        listWrapper.add(scrollPane, BorderLayout.CENTER)
        listWrapper.add(loadingLabel, BorderLayout.SOUTH)

        // 添加组件
        add(filterPanel, BorderLayout.NORTH)
        add(listWrapper, BorderLayout.CENTER)

        // 使用 IDEA 原生分割线边框
        border = JBUI.Borders.customLine(
            JBColor.border().darker(),
            0, 0, 0, 1
        )
    }

    /**
     * 检查是否滚动到底部
     */
    private fun checkScrollToBottom() {
        val scrollbar = scrollPane.verticalScrollBar
        val value = scrollbar.value
        val maximum = scrollbar.maximum
        val visible = scrollbar.visibleAmount

        // 当滚动到距离底部50像素以内时触发加载
        if (hasMoreData && !isLoadingData && (value + visible >= maximum - 50)) {
            isLoadingData = true
            showLoading(true)
            onLoadMore?.invoke()
        }
    }

    /**
     * 设置合并请求列表
     */
    fun setMergeRequests(mrs: List<GitLabMergeRequest>, hasMore: Boolean = false) {
        listModel.clear()
        mrs.forEach { listModel.addElement(it) }

        // 不默认选中任何合并请求，让用户手动选择
        mrList.clearSelection()

        hasMoreData = hasMore
        isLoadingData = false
        showLoading(false)
    }

    /**
     * 添加更多合并请求到列表
     */
    fun addMoreMergeRequests(mrs: List<GitLabMergeRequest>) {
        val previousSelection = mrList.selectedValue
        mrs.forEach { listModel.addElement(it) }

        // 恢复之前的选择
        if (previousSelection != null) {
            mrList.setSelectedValue(previousSelection, true)
        }
    }

    /**
     * 更新列表中的单个合并请求
     * 返回是否成功找到并更新了该 MR
     */
    fun updateMergeRequest(updatedMR: GitLabMergeRequest): Boolean {
        // 在列表模型中查找对应的 MR
        for (i in 0 until listModel.size()) {
            val mr = listModel.getElementAt(i)
            if (mr.iid == updatedMR.iid) {
                // 保存当前选中的索引
                val selectedIndex = mrList.selectedIndex
                // 更新列表模型中的数据
                listModel.set(i, updatedMR)
                // 如果当前选中的是这个 MR，保持选中状态
                if (selectedIndex == i) {
                    mrList.selectedIndex = selectedIndex
                }
                return true
            }
        }
        return false
    }

    /**
     * 从列表中移除指定的合并请求
     */
    fun removeMergeRequest(mrIid: Long) {
        for (i in 0 until listModel.size()) {
            val mr = listModel.getElementAt(i)
            if (mr.iid == mrIid) {
                listModel.remove(i)
                break
            }
        }
    }

    /**
     * 更新hasMore状态并结束加载状态
     */
    fun updateLoadStatus(hasMore: Boolean) {
        hasMoreData = hasMore
        isLoadingData = false
        showLoading(false)
    }

    /**
     * 显示/隐藏加载状态
     */
    private fun showLoading(show: Boolean) {
        loadingLabel.isVisible = show
    }

    /**
     * 设置加载状态（从外部调用）
     */
    fun setLoadingState(loading: Boolean) {
        isLoadingData = loading
        showLoading(loading)
    }

    /**
     * 应用筛选条件
     */
    private fun applyFilters() {
        val stateText = stateFilter.selectedItem as String
        val state = when (stateText) {
            "OPENED" -> MergeRequestState.OPENED
            "CLOSED" -> MergeRequestState.CLOSED
            "LOCKED" -> MergeRequestState.LOCKED
            "MERGED" -> MergeRequestState.MERGED
            else -> null
        }

        val scopeText = scopeFilter.selectedItem as String
        val scope = when (scopeText) {
            "CREATEDBY_ME" -> "created_by_me"
            "ASSIGNEDTO_ME" -> "assigned_to_me"
            else -> null
        }

        val titleKeyword = titleFilter.text.trim().ifBlank { null }

        onFilterChanged?.invoke(state, scope, titleKeyword)
    }

    /**
     * 带圆角背景的状态标签
     */
    private class RoundedLabel : JLabel {
        private var arcWidth = 0
        private var arcHeight = 0

        constructor() : super()

        fun setRoundedCorners(arcWidth: Int, arcHeight: Int) {
            this.arcWidth = arcWidth
            this.arcHeight = arcHeight
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g as? Graphics2D ?: return
            val backgroundColor = background

            if (backgroundColor != null) {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = backgroundColor
                // 先绘制圆角背景
                g2.fillRoundRect(0, 0, width - 1, height - 1, arcWidth, arcHeight)
            }

            // 再绘制文字
            super.paintComponent(g)
        }
    }

    /**
     * 合并请求列表单元格渲染器
     */
    class MRListCellRenderer : ListCellRenderer<GitLabMergeRequest> {
        private val panel = JPanel()
        private val firstRowPanel = JPanel()
        private val titleBranchPanel = JPanel()
        private val titleLabel = JLabel()
        private val branchInfoLabel = JLabel()
        private val stateLabel = RoundedLabel()
        private val authorLabel = JLabel()
        private val timeLabel = JLabel()

        // 固定列宽配置（单位：像素）
        private val titleBranchColumnWidth = 520   // 标题+分支信息列（增加宽度，让右侧列往右移）
        private val authorColumnWidth = 80         // 创建人列（缩小宽度，缩短与时间的间距）
        private val timeColumnWidth = 130          // 时间列（略微缩小）

        // 列间距配置（单位：像素）
        private val stateAuthorSpacing = 15        // 状态和创建人间距（调大）
        private val authorTimeSpacing = 3          // 创建人和时间间距（调小）

        // 鼠标悬停的索引
        var hoveredIndex: Int = -1

        init {
            panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
            panel.border = JBUI.Borders.empty(2, 10)  // 进一步减小上下边距

            // 第一行：水平布局（标题+分支信息、弹性空白、状态、创建人、时间）
            firstRowPanel.layout = BoxLayout(firstRowPanel, BoxLayout.X_AXIS)
            firstRowPanel.alignmentX = LEFT_ALIGNMENT
            firstRowPanel.isOpaque = false

            // 字体设置
            val baseFont = titleLabel.font.deriveFont(Font.BOLD, 12f)

            titleLabel.font = baseFont
            branchInfoLabel.font = baseFont

            // 标题和分支信息面板（垂直布局）
            titleBranchPanel.layout = BoxLayout(titleBranchPanel, BoxLayout.Y_AXIS)
            titleBranchPanel.alignmentX = LEFT_ALIGNMENT
            titleBranchPanel.isOpaque = false

            // 设置标题+分支信息列的尺寸（可扩展，但有限制）
            titleBranchPanel.preferredSize = Dimension(titleBranchColumnWidth, 40)
            titleBranchPanel.maximumSize = Dimension(Int.MAX_VALUE, 40)  // 允许扩展但有高度限制
            titleBranchPanel.minimumSize = Dimension(200, 40)  // 最小宽度

            // 设置右侧各列固定宽度和高度
            // 状态标签不设置固定宽度，让它根据文字内容自动调整
            stateLabel.preferredSize = null  // 自动宽度
            stateLabel.maximumSize = null
            stateLabel.minimumSize = null

            authorLabel.preferredSize = Dimension(authorColumnWidth, 30)
            authorLabel.maximumSize = Dimension(authorColumnWidth, 30)
            authorLabel.minimumSize = Dimension(authorColumnWidth, 30)

            timeLabel.preferredSize = Dimension(timeColumnWidth, 30)
            timeLabel.maximumSize = Dimension(timeColumnWidth, 30)
            timeLabel.minimumSize = Dimension(timeColumnWidth, 30)

            // 设置字体样式和对齐方式
            stateLabel.font = baseFont.deriveFont(Font.PLAIN, 11f)  // 使用更细更小的字体
            stateLabel.isOpaque = false  // 不绘制默认背景
            stateLabel.setRoundedCorners(12, 12)  // 设置圆角弧度
            stateLabel.border = JBUI.Borders.empty(4, 10)  // 内边距
            stateLabel.horizontalAlignment = SwingConstants.CENTER

            authorLabel.font = baseFont
            authorLabel.isOpaque = false
            authorLabel.horizontalAlignment = SwingConstants.LEFT

            timeLabel.font = baseFont
            timeLabel.isOpaque = false
            timeLabel.horizontalAlignment = SwingConstants.LEFT  // 改为左对齐

            // 分支信息标签样式（显示在标题下方）
            branchInfoLabel.foreground = JBColor.GRAY
            branchInfoLabel.font = branchInfoLabel.font.deriveFont(Font.PLAIN, 11f)

            // 将标题和分支信息添加到面板中
            titleBranchPanel.add(titleLabel)
            titleBranchPanel.add(Box.createVerticalStrut(2))
            titleBranchPanel.add(branchInfoLabel)

            // 布局：标题列 + 弹性空白 + 右侧固定列
            // 这样内容短时保持宽松间距，内容长时弹性空白收缩
            firstRowPanel.add(titleBranchPanel)
            firstRowPanel.add(Box.createHorizontalGlue())  // 弹性空白，实现宽松默认间距

            // 右侧固定列（状态、创建人、时间），使用不同的间距
            firstRowPanel.add(stateLabel)
            firstRowPanel.add(Box.createHorizontalStrut(stateAuthorSpacing))  // 状态和创建人间距调大
            firstRowPanel.add(authorLabel)
            firstRowPanel.add(Box.createHorizontalStrut(authorTimeSpacing))   // 创建人和时间间距调小
            firstRowPanel.add(timeLabel)

            panel.add(firstRowPanel)
        }

        /**
         * 截断文本并添加省略号，同时设置 tooltip
         * @param label 目标标签
         * @param text 要显示的文本
         * @param maxWidth 最大可用宽度（像素）
         * @param margin 保留边距（像素），默认8
         */
        private fun truncateAndSetTooltip(
            label: JLabel,
            text: String,
            maxWidth: Int,
            margin: Int = 8
        ) {
            val fontMetrics = label.getFontMetrics(label.font)
            val textWidth = fontMetrics.stringWidth(text)
            val availableWidth = maxWidth - margin

            if (textWidth > availableWidth) {
                // 计算可以显示的字符数
                val ellipsis = "..."
                val ellipsisWidth = fontMetrics.stringWidth(ellipsis)
                val targetWidth = availableWidth - ellipsisWidth

                if (targetWidth > 0) {
                    // 使用二分查找快速定位截断位置
                    var left = 0
                    var right = text.length
                    var bestLength = 0

                    while (left <= right) {
                        val mid = (left + right) / 2
                        val midWidth = fontMetrics.stringWidth(text.substring(0, mid))

                        when {
                            midWidth < targetWidth -> {
                                bestLength = mid
                                left = mid + 1
                            }
                            midWidth > targetWidth -> {
                                right = mid - 1
                            }
                            else -> {
                                bestLength = mid
                                break
                            }
                        }
                    }

                    // 确保至少显示一个字符
                    if (bestLength == 0 && text.isNotEmpty()) {
                        bestLength = 1
                    }

                    val truncatedText = if (bestLength < text.length) {
                        text.substring(0, bestLength) + ellipsis
                    } else {
                        text
                    }

                    label.text = truncatedText
                    label.toolTipText = text
                } else {
                    // 宽度太小，只显示省略号
                    label.text = ellipsis
                    label.toolTipText = text
                }
            } else {
                label.text = text
                label.toolTipText = null
            }
        }

        /**
         * 为标题和分支信息面板设置文本（带截断和tooltip）
         * 标题和分支信息在同一列内垂直排列，需要分别截断
         * @param title MR 标题
         * @param sourceBranch 源分支
         * @param targetBranch 目标分支
         * @param panelWidth 面板实际宽度（如果为 null 则使用配置的宽度）
         */
        private fun setTitleAndBranchText(
            title: String,
            sourceBranch: String,
            targetBranch: String,
            panelWidth: Int? = null
        ) {
            // 使用面板实际宽度（如果可用），否则使用配置的宽度
            val actualWidth = panelWidth ?: titleBranchColumnWidth

            // 标题单独截断（考虑面板内边距）
            truncateAndSetTooltip(titleLabel, title, actualWidth, margin = 10)

            // 分支信息单独截断（考虑面板内边距）
            val branchText = "($sourceBranch → $targetBranch)"
            truncateAndSetTooltip(branchInfoLabel, branchText, actualWidth, margin = 10)
        }

        /**
         * 为不需要截断的标签设置 tooltip
         * 用于固定宽度且不需要添加省略号的列
         */
        private fun setTooltipIfNeeded(label: JLabel, text: String, maxWidth: Int, margin: Int = 8) {
            val fontMetrics = label.getFontMetrics(label.font)
            val textWidth = fontMetrics.stringWidth(text)
            val availableWidth = maxWidth - margin

            if (textWidth > availableWidth) {
                label.toolTipText = text
            } else {
                label.toolTipText = null
            }

            label.text = text
        }

        override fun getListCellRendererComponent(
            list: JList<out GitLabMergeRequest>,
            value: GitLabMergeRequest,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            // 获取列表的宽度，用于计算标题列的实际可用宽度
            val listWidth = list.width
            // 状态标签现在是自动宽度，预估一个较小的值（约50px包含文字和padding）
            val estimatedStateWidth = 50
            val rightPanelWidth = estimatedStateWidth + stateAuthorSpacing +
                                authorColumnWidth + authorTimeSpacing +
                                timeColumnWidth + 20  // 额外的边距

            // 计算标题列的实际可用宽度
            val availableTitleWidth = if (listWidth > 0) {
                (listWidth - rightPanelWidth).coerceAtLeast(titleBranchColumnWidth)
            } else {
                titleBranchColumnWidth
            }

            // 设置标题和分支信息（使用实际宽度进行截断）
            setTitleAndBranchText(
                value.title,
                value.sourceBranch,
                value.targetBranch,
                availableTitleWidth
            )

            // 设置状态标签（自动宽度，不需要截断）
            stateLabel.text = value.state.displayName
            stateLabel.background = getColorForState(value.state)
            stateLabel.toolTipText = null  // 状态文字短，不需要tooltip

            // 设置创建人（带截断和tooltip）
            truncateAndSetTooltip(authorLabel, value.author.name, authorColumnWidth, margin = 8)

            // 设置时间（带截断和tooltip）
            val timeText = formatTime(value.createdAt)
            setTooltipIfNeeded(timeLabel, timeText, timeColumnWidth, margin = 8)

            // 设置背景色（选中、悬停、普通三种状态）- 使用 IDEA 原生选中颜色
            val backgroundColor = when {
                isSelected -> UIUtil.getListSelectionBackground(cellHasFocus)
                index == hoveredIndex -> UIUtil.getPanelBackground().brighter()
                else -> UIUtil.getPanelBackground()
            }

            panel.background = backgroundColor
            panel.isOpaque = true

            // 设置前景色 - 选中时使用白色，普通时使用默认前景色
            val foregroundColor = if (isSelected) {
                UIUtil.getListSelectionForeground(cellHasFocus)
            } else {
                UIUtil.getLabelForeground()
            }

            titleLabel.foreground = foregroundColor
            branchInfoLabel.foreground = if (isSelected) {
                foregroundColor.darker()
            } else {
                JBColor.GRAY
            }
            authorLabel.foreground = foregroundColor
            timeLabel.foreground = foregroundColor

            // 状态标签的文字颜色 - 始终使用白色，因为背景色较深
            stateLabel.foreground = JBColor(Color.WHITE, Color.WHITE)

            return panel
        }

        private fun getColorForState(state: MergeRequestState): Color {
            return when (state) {
                MergeRequestState.OPENED -> JBColor(Color(0x7058a3), Color(0x7058a3))
                MergeRequestState.CLOSED -> JBColor(Color(0x7f8c8d), Color(0x7f8c8d))
                MergeRequestState.LOCKED -> JBColor(Color(0x9f9627), Color(0x9f9627))
                MergeRequestState.MERGED -> JBColor(Color(0x108548), Color(0x108548))
            }
        }

        private fun formatTime(dateString: String): String {
            return try {
                // 解析 UTC 时间
                val utcFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                utcFormat.timeZone = TimeZone.getTimeZone("UTC")
                val utcDate = utcFormat.parse(dateString) ?: return dateString

                // 转换为北京时间（UTC+8）
                val beijingTimeZone = TimeZone.getTimeZone("GMT+8")
                val beijingCalendar = Calendar.getInstance(beijingTimeZone)
                beijingCalendar.time = utcDate

                val beijingHour = beijingCalendar.get(Calendar.HOUR_OF_DAY)
                val beijingMinute = beijingCalendar.get(Calendar.MINUTE)
                val beijingYear = beijingCalendar.get(Calendar.YEAR)
                val beijingMonth = beijingCalendar.get(Calendar.MONTH) + 1  // Calendar.MONTH 从 0 开始
                val beijingDay = beijingCalendar.get(Calendar.DAY_OF_MONTH)

                // 获取当前北京时间
                val nowBeijingCalendar = Calendar.getInstance(beijingTimeZone)
                val nowYear = nowBeijingCalendar.get(Calendar.YEAR)
                val nowMonth = nowBeijingCalendar.get(Calendar.MONTH) + 1
                val nowDay = nowBeijingCalendar.get(Calendar.DAY_OF_MONTH)

                // 判断是否为当天（基于北京时间）
                val isSameDay = beijingYear == nowYear && beijingMonth == nowMonth && beijingDay == nowDay

                if (isSameDay) {
                    // 当天显示"今天 HH:mm"
                    "今天 ${String.format("%02d:%02d", beijingHour, beijingMinute)}"
                } else {
                    // 非当天显示"yyyy-MM-dd HH:mm"（北京时间）
                    String.format("%d-%02d-%02d %02d:%02d",
                        beijingYear, beijingMonth, beijingDay, beijingHour, beijingMinute)
                }
            } catch (e: Exception) {
                dateString
            }
        }
    }

    /**
     * 带图标的文本框
     */
    class TextFieldWithIcon : JTextField() {
        private var icon: Icon? = null
        private val iconSize = 16
        var placeholderText: String? = null

        fun setIcon(icon: Icon?) {
            this.icon = icon
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)

            // 绘制图标
            if (icon != null) {
                val iconY = (height - iconSize) / 2 + 1
                icon!!.paintIcon(this, g, 8, iconY)
            }

            // 绘制 placeholder 文本
            if (text.isEmpty() && placeholderText != null && !hasFocus()) {
                val g2 = g.create() as Graphics
                g2.color = UIUtil.getInactiveTextColor()
                g2.font = font
                val metrics = g2.getFontMetrics(font)
                val iconWidth = icon?.iconWidth ?: 0
                val x = iconWidth + 14
                val y = (height - metrics.height) / 2 + metrics.ascent
                g2.drawString(placeholderText!!, x, y)
                g2.dispose()
            }
        }

        override fun getInsets(): Insets {
            val insets = super.getInsets()
            // 为左侧图标留出空间
            val iconWidth = icon?.iconWidth ?: 0
            return JBUI.insets(insets.top, iconWidth + 12, insets.bottom, insets.right)
        }
    }
}
