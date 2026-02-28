package com.gitlab.idea.toolwindow.components

import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.border.Border

/**
 * 错误状态面板
 * 居中显示错误信息和编辑/刷新图标按钮
 */
class ErrorStatePanel : JPanel() {

    private val messageLabel: JBLabel = JBLabel("无法检测项目路径")

    var onEditClicked: (() -> Unit)? = null
    var onRefreshClicked: (() -> Unit)? = null

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = UIUtil.getPanelBackground()

        // 消息文字
        messageLabel.font = messageLabel.font.deriveFont(Font.BOLD, 18f)
        messageLabel.alignmentX = CENTER_ALIGNMENT
        messageLabel.foreground = JBColor.GRAY
        messageLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        messageLabel.toolTipText = "鼠标悬浮查看详细信息"

        // 创建图标按钮面板
        val buttonPanel = JPanel()
        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)
        buttonPanel.background = background
        buttonPanel.alignmentX = CENTER_ALIGNMENT
        buttonPanel.border = JBUI.Borders.emptyTop(3)

        // 编辑图标按钮
        val editButton = createIconButton(
            icon = AllIcons.Actions.Edit,
            tooltip = "编辑服务器配置",
            onClick = { onEditClicked?.invoke() }
        )

        // 刷新图标按钮
        val refreshButton = createIconButton(
            icon = AllIcons.Actions.Refresh,
            tooltip = "重新加载",
            onClick = { onRefreshClicked?.invoke() }
        )

        buttonPanel.add(editButton)
        buttonPanel.add(Box.createHorizontalStrut(20))
        buttonPanel.add(refreshButton)

        // 使用垂直居中布局
        add(Box.createVerticalGlue())
        add(messageLabel)
        add(buttonPanel)
        add(Box.createVerticalGlue())
    }

    /**
     * 创建图标按钮
     */
    private fun createIconButton(icon: Icon, tooltip: String, onClick: () -> Unit): JButton {
        val button = JButton(icon)
        button.isFocusPainted = false
        button.isContentAreaFilled = false
        button.border = RoundedBorder(JBColor.GRAY, 1, 8)
        button.toolTipText = tooltip
        button.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        button.isOpaque = false

        // 设置图标大小
        button.preferredSize = Dimension(40, 40)
        button.maximumSize = Dimension(40, 40)

        // 鼠标悬浮效果 - 改变背景色和边框
        button.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                button.isContentAreaFilled = true
                button.background = JBColor(
                    Color(220, 230, 255),
                    Color(80, 100, 140)
                )
            }

            override fun mouseExited(e: MouseEvent) {
                button.isContentAreaFilled = false
            }
        })

        button.addActionListener {
            onClick()
        }

        return button
    }

    /**
     * 圆角边框类
     */
    private class RoundedBorder(
        private val color: Color,
        private val thickness: Int = 1,
        private val radius: Int = 8
    ) : Border {

        override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
            val g2 = g as Graphics2D
            g2.color = color
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
            g2.draw(RoundRectangle2D.Double(
                x + thickness / 2.0,
                y + thickness / 2.0,
                width - thickness.toDouble(),
                height - thickness.toDouble(),
                radius.toDouble(),
                radius.toDouble()
            ))
        }

        override fun getBorderInsets(c: Component): Insets {
            return JBUI.insets(thickness + 4, thickness + 4, thickness + 4, thickness + 4)
        }

        override fun isBorderOpaque(): Boolean {
            return false
        }
    }

    /**
     * 设置错误信息
     */
    fun setError(title: String, detail: String?) {
        messageLabel.text = title
        // 将详情信息设置到 toolTipText，鼠标悬浮时显示
        if (detail != null) {
            messageLabel.toolTipText = detail
        }
    }
}
