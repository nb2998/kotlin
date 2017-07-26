// WITH_RUNTIME
class A

fun A.oldWay(){}

val A.oldProperty: Int
    get() = 0

@ReplacementFor("oldWay()")
fun A.betterWay1(){}

@ReplacementFor("oldProperty")
fun A.betterWay2() = 0

fun A.foo(a: A) {
    a.oldWay()
    oldWay()

    print(a.oldProperty)
    print(oldProperty)
}
