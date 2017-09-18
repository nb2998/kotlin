// WITH_RUNTIME

@ReplacementFor("SwingUtilities.invokeLater(this)", imports = arrayOf("javax.swing.SwingUtilities"))
fun Runnable.invokeItLater() {
    javax.swing.SwingUtilities.invokeLater(this)
}

fun foo() {
    javax.swing.SwingUtilities.invokeLater(Runnable {
        print(1)
    })
}