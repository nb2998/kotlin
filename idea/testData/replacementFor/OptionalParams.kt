fun f(p: Int){}
fun g(p: String){}
fun h(p: String?){}

@ReplacementFor("f(p1)", "g(p2)", "h(p3)")
fun new(p1: Int = 0, p2: String = "", p3: String? = null){}

fun foo() {
    f(1)
    g("a")
    h("b")
}