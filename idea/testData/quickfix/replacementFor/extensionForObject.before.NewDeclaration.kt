package new

object NewObject

@ReplacementFor("s.doIt()", "old.doIt")
fun NewObject.doIt(s: String){}
