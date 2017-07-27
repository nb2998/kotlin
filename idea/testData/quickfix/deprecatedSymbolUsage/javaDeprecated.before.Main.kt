// "class org.jetbrains.kotlin.idea.quickfix.replacement.replaceWith.DeprecatedSymbolUsageFix" "false"

fun foo() {
    val c = JavaClass()
    c.<caret>oldFun()
}
