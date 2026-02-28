package com.gitlab.idea.toolwindow.components

import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import java.awt.Font
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JPanel

/**
 * 加载状态面板
 * 居中显示加载动画和文字
 */
class LoadingStatePanel : JPanel() {

    private val messageLabel: JBLabel = JBLabel("正在加载...")
    private val loadingIcon: JBLabel = JBLabel(getLoadingIcon())

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = UIUtil.getPanelBackground()

        // 加载图标
        loadingIcon.alignmentX = CENTER_ALIGNMENT

        // 消息文字
        messageLabel.font = messageLabel.font.deriveFont(Font.PLAIN, 14f)
        messageLabel.alignmentX = CENTER_ALIGNMENT
        messageLabel.foreground = JBColor.GRAY

        // 使用垂直居中布局
        add(Box.createVerticalGlue())
        add(loadingIcon)
        add(Box.createVerticalStrut(12))
        add(messageLabel)
        add(Box.createVerticalGlue())
    }

    /**
     * 获取加载图标
     */
    private fun getLoadingIcon(): Icon {
        return AllIcons.Actions.Refresh
    }

    /**
     * 设置加载消息
     */
    fun setLoadingMessage(message: String) {
        messageLabel.text = message
    }
}
