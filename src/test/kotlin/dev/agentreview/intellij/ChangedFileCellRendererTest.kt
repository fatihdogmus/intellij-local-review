package dev.agentreview.intellij

import com.intellij.testFramework.junit5.TestApplication
import dev.fatihdogmus.agenticreview.vcs.ChangedFile
import dev.fatihdogmus.agenticreview.vcs.ChangedFileStatus
import dev.fatihdogmus.agenticreview.vcs.ReviewContent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JLabel

@TestApplication
class ChangedFileCellRendererTest {
    @Test
    fun rendererBuildsSubtitleStatsAndUnseenFormatting() {
        val renderer = newRenderer { true }
        val file = ChangedFile(
            filePath = "src/new/Foo.kt",
            status = ChangedFileStatus.RENAMED,
            beforeContent = ReviewContent("one\ntwo", "before", "src/old/Foo.kt"),
            afterContent = ReviewContent("one\nthree\nfour", "after", "src/new/Foo.kt"),
            previousFilePath = "src/old/Foo.kt",
        )

        val component = invokeComponent(renderer, file, false)
        val title = field<JLabel>(renderer, "title")
        val subtitle = field<JLabel>(renderer, "subtitle")
        val stats = field<JLabel>(renderer, "stats")
        val badge = field<JLabel>(renderer, "statusBadge")

        assertThat(component).isInstanceOf(JComponent::class.java)
        assertThat(title.text).startsWith("* Foo.kt")
        assertThat(subtitle.text).isEqualTo("src/old/Foo.kt -> src/new/Foo.kt")
        assertThat(stats.text).contains("+2").contains("-1")
        assertThat(badge.text).isEqualTo("RENAMED")
    }

    @Test
    fun rendererHandlesAddedDeletedAndSelectedStates() {
        val renderer = newRenderer { false }
        val added = ChangedFile("Added.kt", ChangedFileStatus.ADDED, null, ReviewContent("one\ntwo", "after", "Added.kt"))
        val deleted = ChangedFile("Deleted.kt", ChangedFileStatus.DELETED, ReviewContent("one", "before", "Deleted.kt"), null)

        invokeComponent(renderer, added, true)
        val addedStats = field<JLabel>(renderer, "stats").text
        val selectedBadgeColor = field<JLabel>(renderer, "statusBadge").foreground

        invokeComponent(renderer, deleted, false)
        val deletedSubtitle = field<JLabel>(renderer, "subtitle").text
        val deletedBadgeColor = field<JLabel>(renderer, "statusBadge").foreground

        assertThat(addedStats).isEqualTo("+2  -0")
        assertThat(selectedBadgeColor).isNotNull()
        assertThat(deletedSubtitle).isEqualTo("Deleted.kt")
        assertThat(deletedBadgeColor).isNotEqualTo(Color.BLACK)
    }

    private fun newRenderer(unseen: (ChangedFile) -> Boolean?): Any {
        val clazz = Class.forName("dev.fatihdogmus.agenticreview.ui.ChangedFileCellRenderer")
        val ctor = clazz.declaredConstructors.single()
        ctor.isAccessible = true
        return ctor.newInstance(unseen)
    }

    private fun invokeComponent(renderer: Any, file: ChangedFile, selected: Boolean): JComponent {
        val method = renderer.javaClass.getDeclaredMethod(
            "getListCellRendererComponent",
            JList::class.java,
            ChangedFile::class.java,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true
        return method.invoke(renderer, JList<ChangedFile>(), file, 0, selected, false) as JComponent
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(target: Any, name: String): T {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(target) as T
    }
}
