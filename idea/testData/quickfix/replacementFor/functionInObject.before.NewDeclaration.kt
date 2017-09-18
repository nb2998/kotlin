package new

object NewObject {
    @ReplacementFor("s.doIt()", imports = arrayOf("old.doIt"))
    fun doIt(s: String){}
}
