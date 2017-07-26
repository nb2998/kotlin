// "Replace by 'invokeLater' call" "true"
// WITH_RUNTIME
import javax.swing.SwingUtilities

@ReplacementFor("SwingUtilities.invokeLater(this)", "javax.swing.SwingUtilities")
fun Runnable.invokeItLater() {
    SwingUtilities.invokeLater(this)
}

@ReplacementFor("SwingUtilities.invokeLater(runnable)", "javax.swing.SwingUtilities")
fun invokeLater(runnable: Runnable) {
    SwingUtilities.invokeLater(runnable)
}

fun foo() {
    <caret>SwingUtilities.invokeLater(Runnable {
        print(1)
    })
}