// "class org.jetbrains.kotlin.idea.quickfix.replacement.replaceWith.DeprecatedSymbolUsageFix" "false"

@Deprecated("", ReplaceWith("="))
fun oldFun() {
}

fun foo() {
    <caret>oldFun()
}
