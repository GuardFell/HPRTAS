package uk.ac.uwe.hprtas.workers;

/**
 * The console the workers and the engine tests write their narrative to.
 *
 * The JVM's own streams are used rather than the file descriptors behind them: a test runner and a
 * shell both substitute their own streams to capture what a run said, and writing past them loses
 * the output the evidence is made of. When the streams are redirected to a file or a pipe they are
 * UTF-8, which is what the evidence files need.
 *
 * Every write ends with a newline and is flushed. `PrintStream` only flushes itself on its own
 * line-ending, which on Windows would put a carriage return into a file that has to be comparable
 * with a run made anywhere else, so the newline is written explicitly and the flush is asked for.
 */
final class Console {

  private Console() {}

  static void out(String format, Object... arguments) {
    System.out.print(String.format(format, arguments) + "\n");
    System.out.flush();
  }

  static void error(String format, Object... arguments) {
    System.err.print(String.format(format, arguments) + "\n");
    System.err.flush();
  }
}
