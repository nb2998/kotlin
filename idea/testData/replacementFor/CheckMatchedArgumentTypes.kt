// WITH_RUNTIME

fun Any.foo(p: Any){}

@ReplacementFor("p1.foo(p2)")
fun bar(p1: String, p2: Int){}

fun f(o: Any) {
    "a".foo(1)
    o.foo(1)
    "a".foo(o)
}