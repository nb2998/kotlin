// "Replace by 'doIt' call" "true"
// WITH_RUNTIME
import new.NewObject
import new.doIt
import old.doIt

fun foo() {
    NewObject.doIt("a")
}