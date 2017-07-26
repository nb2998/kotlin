// WITH_RUNTIME
package x

class A
class B

fun A.oldWay(b: B){}

val A.oldProperty: Int
    get() = 0

@ReplacementFor("a.oldWay(this)")
fun B.betterWay(a: A){}

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