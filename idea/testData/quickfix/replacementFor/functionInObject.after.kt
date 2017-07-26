// "Replace by 'doIt' call" "true"
// WITH_RUNTIME
import new.NewObject
import old.doIt

fun foo() {
    NewObject.doIt("a")
}