// "Replace with 'newFun(p)'" "true"

@Deprecated("", ReplaceWith("newFun(p)"))
infix fun String.oldFun(p: Int) {
    newFun(p)
}

fun String.newFun(p: Int) {
}

fun foo() {
    "" <caret>oldFun 1
}
