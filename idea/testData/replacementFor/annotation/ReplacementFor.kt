package kotlin

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
public annotation class ReplacementFor(vararg val expressions: String, val imports: Array<String> = arrayOf())
