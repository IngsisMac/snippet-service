package ingsis.snippet.service

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SnippetServiceApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<SnippetServiceApplication>(*args)
}
