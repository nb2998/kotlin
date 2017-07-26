// WITH_RUNTIME

@ReplacementFor("kotlin.collections.listOf(this)")
fun String.singletonList(): List<String> = listOf(this)

fun foo() {
    listOf("a")
    kotlin.collections.listOf("a")
    listOf(1)
}