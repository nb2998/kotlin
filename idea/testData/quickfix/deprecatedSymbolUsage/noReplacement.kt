// "class org.jetbrains.kotlin.idea.quickfix.replacement.replaceWith.DeprecatedSymbolUsageFix" "false"

@Deprecated("")
fun oldFun() {
}

fun foo() {
    <caret>oldFun()
}
