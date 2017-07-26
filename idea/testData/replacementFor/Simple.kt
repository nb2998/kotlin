fun oldWay(){}

@ReplacementFor("oldWay()")
fun betterWay(){}

fun foo() {
    oldWay()
}