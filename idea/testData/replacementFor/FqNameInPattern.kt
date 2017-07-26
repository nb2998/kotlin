// WITH_RUNTIME
import javax.swing.SwingUtilities

@ReplacementFor("javax.swing.SwingUtilities.invokeLater(this)")
fun Runnable.invokeItLater() {
    SwingUtilities.invokeLater(this)
}

fun foo() {
    SwingUtilities.invokeLater(Runnable {
        print(1)
    })

    javax.swing.SwingUtilities.invokeLater(Runnable {
        print(1)
    })
}