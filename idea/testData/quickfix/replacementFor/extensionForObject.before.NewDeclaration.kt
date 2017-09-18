package new

object NewObject

@ReplacementFor("s.doIt()", imports = arrayOf("old.doIt"))
fun NewObject.doIt(s: String){}
