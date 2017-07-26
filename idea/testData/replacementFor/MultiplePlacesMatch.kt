fun Int.foo(p1: Int, p2: Int){}

@ReplacementFor("p.foo(p, p)")
fun bar(p: Int){}

fun Int.f(a: Int, b: Int) {
    1.foo(1, 1)
    1.foo(2, 2)
    foo(this, this)
    (a + b).foo(a + b, a+b)
}
