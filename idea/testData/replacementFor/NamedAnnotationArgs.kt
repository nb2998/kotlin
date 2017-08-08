// WITH_RUNTIME
import javax.swing.SwingUtilities

@ReplacementFor(imports = "javax.swing.SwingUtilities", expression = "SwingUtilities.invokeLater(this)")
fun Runnable.invokeItLater() {
    SwingUtilities.invokeLater(this)
}

fun foo() {
    SwingUtilities.invokeLater(Runnable {
        print(1)
    })
}