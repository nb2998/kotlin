// "Replace by 'doIt' call" "true"
// WITH_RUNTIME
import new.doIt
import old.doIt

fun foo() {
    <caret>doIt("a")
}