// "Replace by 'doIt' call" "true"
// WITH_LIBRARY_AND_RUNTIME: replacementFor/annotation
import old.doIt

fun foo() {
    <caret>"a".doIt()
}