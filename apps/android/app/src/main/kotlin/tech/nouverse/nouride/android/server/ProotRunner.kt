package tech.nouverse.nouride.android.server

import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread

/**
 * Starts a command inside the rootfs under proot. Its output, stdout and stderr together, is
 * appended to [log] line by line and handed to [onLine].
 */
class ProotRunner(private val args: ProotArgs, private val tmp: File) {
  fun start(command: List<String>, log: File, onLine: (String) -> Unit = {}): Process {
    tmp.mkdirs()
    val prootCommand = args.build(command)
    val builder = ProcessBuilder(prootCommand.argv).redirectErrorStream(true)
    builder.environment().clear()
    builder.environment().putAll(prootCommand.env)
    val process = builder.start()
    process.outputStream.close()
    log.parentFile?.mkdirs()
    thread(name = "proot-output", isDaemon = true) {
      FileOutputStream(log, true).bufferedWriter().use { out ->
        process.inputStream.bufferedReader().forEachLine { line ->
          out.write(line)
          out.newLine()
          out.flush()
          onLine(line)
        }
      }
    }
    return process
  }

  /** Runs [command] to the end; its exit code. */
  fun run(command: List<String>, log: File, onLine: (String) -> Unit = {}): Int =
    start(command, log, onLine).waitFor()
}
