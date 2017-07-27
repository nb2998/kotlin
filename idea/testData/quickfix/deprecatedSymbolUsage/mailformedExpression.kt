// "class org.jetbrains.kotlin.idea.quickfix.replacement.replaceWith.DeprecatedSymbolUsageFix" "false"

@Deprecated("", ReplaceWith("order.safeAddItem(...)"))
fun oldFun() {
}

fun foo() {
    <caret>oldFun()
}
