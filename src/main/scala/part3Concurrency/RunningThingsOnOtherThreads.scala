package part3Concurrency

import cats.effect.IO
import cats.effect.IOApp
import cats.effect.ExitCode
import cats.effect.kernel.Fiber

object RunningThingsOnOtherThreads extends IOApp {
  /**
   * Cats Effect's core data structure: its main effect type, IO
   * IO[A] instances describe computations that, if finished,
   * evaluate to a value of type A,
   * and can perform arbitrary side effect
   */

    val meaningOfLife: IO[Int] = IO(42)
    val favLang: IO[String] = IO("Scala")

   /**
    * For demonstrating asynchronicity, we'll decorate the IO type
    * with an extension method, which also prints the current thread, and the value
    * it's about to compute
    */
    extension [A] (io: IO[A])
      def debugExt: IO[A] = io.map { value =>
        println(s"[${Thread.currentThread().getName()}] $value")
        value
      }
  
    def sameThread() = for {
      _ <- meaningOfLife.debugExt
      // if use .debug --> error
      // "value flatMap is not a member of String => cats.effect.IO[Int], 
      // but could be made available as an extension method."
      _ <- favLang.debugExt
    } yield ()

    def run(args: List[String]): IO[ExitCode] = 
      sameThread().as(ExitCode.Success)
      // [io-compute-11] 42
      // [io-compute-11] Scala
      //  --> these IOs disclosing their same thread
}

object IntroFiber extends IOApp {
  import RunningThingsOnOtherThreads.*
  /**
    * Fibers are "lightweight threads"
    * They are semantic abstraction, similar to threads,
    * But unlike threads (which can be spawned in the thousands per JVM),
    * fibers can be spawned in the millions per GB of heap
    * *** it is Threads vs CPU >< Fibers vs GB of heap ***
    * Fibers are passive data structures which contains IOs (themselves data structure)
    * The Cats Effect scheduler takes care to schedule these IOs
    */

    // The shape of a fiber
    def createFiber: Fiber[IO, Throwable, String] = ??? // ??? bcuz fibers are almost impossible to create manually as a user
    // ^^ effect type (itself generic, usually IO), the type of error it might fail, type of result it might return

    // Fibers can be created through the `start` method of IOs
    val aFiber: IO[Fiber[IO, Throwable, Int]] = meaningOfLife.debugExt.start
    // `start` spawns a Fiber, but since creating the fiber itself, and running IO on a separate thread, is an EFFECT..
    // .. the returned fiber is wrapped in another IO instance

    def differentThreads() = for {
      _ <- aFiber
      _ <- favLang.debugExt
    } yield ()

    def run(args: List[String]): IO[ExitCode] = differentThreads().as(ExitCode.Success)
    // [io-compute-12] 42
    // [io-compute-1] Scala
    // --> different threads
}

object gatheringResultsFromFibers extends IOApp {
  import RunningThingsOnOtherThreads.* 

  // Much like we wait for a thread, and we Await a Future to compute a result..
  // We also have the concept of joining a Fiber, but in purely functional way
  def runOnAnotherThread[A](io: IO[A]) = for {
    fib <- io.start
    result <- fib.join
  } yield result

  def run(args: List[String]): IO[ExitCode] = runOnAnotherThread(meaningOfLife.debugExt).as(ExitCode.Success)
  // [io-compute-1] 42
}

object EndStatesOfAFiber extends  IOApp{
  import RunningThingsOnOtherThreads._
  import concurrent.duration.DurationInt
  /**
    * A fiber can termniate in 1 of 3 states
    * 1. successfully, with a value (wrapped in IO, examples above)
    * 2. as a failure, wrapping in exception
    * 3. cancelled, neither
    */

  def throwOnAnotherThread() = for {
    fib <- IO.raiseError[Int](new RuntimeException("no number for you")).start
    result <- fib.join
  } yield result

  def run(args: List[String]): IO[ExitCode] = 
    throwOnAnotherThread().debugExt.as(ExitCode.Success)
    // [io-compute-4] Errored(java.lang.RuntimeException: no number for you)
    testCancel().debugExt.as(ExitCode.Success)
    //     [io-compute-3] starting
    // [io-compute-1] cancelling
    // [io-compute-8] Canceled()
    //  seems like run is measured upside down.. testCancel runs first
  // New thing is Cancellation, it's a big thing
  def testCancel() = {
    val task = IO("starting").debugExt *> IO.sleep(1.second) *> IO("done").debugExt
    // *> : sequence op for IOs

    for {
      fib <- task.start
      _ <- IO.sleep(500.millis) *> IO("cancelling").debugExt
      _ <- fib.cancel
      result <- fib.join
    } yield result
  }
}
