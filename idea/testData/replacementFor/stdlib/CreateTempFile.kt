// WITH_RUNTIME
import java.io.File

fun foo(dir: File) {
    val file1 = File.createTempFile("prefix", "suffix", dir)
    val file2 = File.createTempFile("prefix", "suffix", null)
    val file3 = File.createTempFile("prefix", "suffix")
    val file4 = File.createTempFile("prefix", null) //TODO: no last argument
    val file5 = File.createTempFile("tmp", null) //TODO: no arguments
}
