package new

object NewObject {
    @ReplacementFor("s.doIt()", "old.doIt")
    fun doIt(s: String){}
}
