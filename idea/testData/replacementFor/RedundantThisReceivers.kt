// WITH_RUNTIME
class A
class B

fun A.foo1(b: B){}
fun A.foo2(b: B){}

@ReplacementFor("a.foo1(this)")
fun B.bar1(a: A){}

@ReplacementFor("a.foo2(this)")
fun B.bar2(a: A){}


fun B.f(a: A, b: B) {
    a.foo1(this)

    fun bar2(a: A) {
    }

    a.foo2(this)

    with(b) {
        a.foo1(this@f)
        a.foo1(this)
    }
}
