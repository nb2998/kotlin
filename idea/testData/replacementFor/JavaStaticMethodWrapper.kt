// WITH_RUNTIME
import javax.swing.SwingUtilities

@ReplacementFor("SwingUtilities.invokeLater(this)", "javax.swing.SwingUtilities")
fun Runnable.invokeItLater() {
    SwingUtilities.invokeLater(this)
}

fun foo() {
    SwingUtilities.invokeLater(Runnable {
        print(1)
    })

    SwingUtilities.invokeLater({
        print(2)
    })
    
    SwingUtilities.invokeLater {
        print(2)
    }
}