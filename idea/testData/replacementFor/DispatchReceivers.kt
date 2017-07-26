// WITH_RUNTIME
package x

class A {
    fun oldWay(b: B){}

    val oldProperty: Int = 0
}

class B {
    @ReplacementFor("a.oldWay(this)")
    fun betterWay(a: A){}
}

@ReplacementFor("a.oldProperty")
fun betterWay(a: A) = 0

fun A.foo(a: A, b: B) {
    a.oldWay(b)
    oldWay(b)

    print(a.oldProperty)
    print(oldProperty)
}

fun B.foo(a: A, b: B) {
    a.oldWay(this)

    with(a) {
        oldWay(this@foo)
        print(oldProperty)
    }
}