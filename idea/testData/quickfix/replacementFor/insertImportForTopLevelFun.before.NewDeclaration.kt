package new

@ReplacementFor("s.doIt()", imports = arrayOf("old.doIt"))
fun doIt(s: String){}
