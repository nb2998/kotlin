// WITH_RUNTIME

interface I
class A : I
class B

fun foo(p: Any){}

@ReplacementFor("foo(p)")
fun <T : I> bar(p: T){}

fun f(o: Any) {
    foo(A())
    foo(B())
}