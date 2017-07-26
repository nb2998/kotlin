// "Replace by 'doIt' call" "true"
// WITH_RUNTIME
import old.doIt

fun foo() {
    <caret>"a".doIt()
}