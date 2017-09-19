// "Replace by 'doIt' call" "true"
// WITH_LIBRARY_AND_RUNTIME: replacementFor/annotation
import new.doIt
import old.doIt

fun foo() {
    "a".doIt()
}