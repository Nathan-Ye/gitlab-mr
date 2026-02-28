package com.gitlab.idea.toolwindow.components

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.GridBagLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.border.LineBorder

/**
 * 空状态面板
 * 显示"添加GitLab服务"按钮
 */
class EmptyStatePanel : JPanel() {

    var onAddServerClicked: (() -> Unit)? = null

    init {
        layout = BorderLayout()
        background = UIUtil.getPanelBackground()

        // 使用 GridBagLayout 实现真正的居中
        val centerPanel = JPanel(GridBagLayout())
        centerPanel.background = background

        // 添加按钮
        val addButton = JButton("添加 GitLab 服务")

        // 字体大小
        addButton.font = addButton.font.deriveFont(java.awt.Font.BOLD, 15f)

        // 圆角边框
        addButton.border = JBUI.Borders.compound(
            LineBorder(JBColor.GRAY.darker(), 1, true),
            JBUI.Borders.empty(8, 20)
        )

        // 设置按钮最小和首选尺寸
        addButton.minimumSize = Dimension(180, 45)
        addButton.preferredSize = Dimension(200, 45)

        // 鼠标悬停效果
        addButton.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                addButton.background = JBColor(Color(0x3C84F7), Color(0x3C84F7))
                addButton.foreground = JBColor(Color.WHITE, Color.WHITE)
            }

            override fun mouseExited(e: MouseEvent) {
                addButton.background = UIUtil.getPanelBackground()
                addButton.foreground = JBColor.BLACK
            }
        })

        // 初始颜色
        addButton.background = UIUtil.getPanelBackground()
        addButton.isOpaque = true

        addButton.addActionListener {
            onAddServerClicked?.invoke()
        }

        // 使用 GridBagConstraints 居中
        val gbc = java.awt.GridBagConstraints()
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.anchor = java.awt.GridBagConstraints.CENTER
        gbc.weightx = 1.0
        gbc.weighty = 1.0
        gbc.fill = java.awt.GridBagConstraints.NONE

        centerPanel.add(addButton, gbc)
        add(centerPanel, BorderLayout.CENTER)
    }
}
