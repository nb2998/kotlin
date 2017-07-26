// WITH_RUNTIME

@ReplacementFor("SwingUtilities.invokeLater(this)", "javax.swing.SwingUtilities")
fun Runnable.invokeItLater() {
    javax.swing.SwingUtilities.invokeLater(this)
}

fun foo() {
    javax.swing.SwingUtilities.invokeLater(Runnable {
        print(1)
    })
}