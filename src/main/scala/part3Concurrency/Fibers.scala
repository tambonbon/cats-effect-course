package part3Concurrency

import cats.effect.IOApp
import cats.effect.IO
import utils._
import cats.effect.kernel.Fiber
import cats.effect.kernel.Outcome.{Canceled, Errored, Succeeded}
import cats.effect.kernel.Outcome
import scala.concurrent.duration._

/**
  * 1. CE was built, so that Scala devs can write high-performance, concurrent app on top of JVM
  * * Parallelism --> multiple computations running AT THE SAME TIME
  * * Concurrency --> multiple computations OVERLAP
  * Parallel prog may not be concurrent, e.g. the tasks are independent
  * Concurrent prog may not be parallel, e.g. multi-tasking on the same CPU
  * CE focus on Concurrency
  * 
  * 2. CE uses Fiber --> description of an effect being executed on some other thread
  * [Effect Type, Failure Type, Result Type]
  * Creating a fiber is an EFFECTFUL operation
  * --> whenever we call `start` on an effect, ie. making it run on some other threads managed by CE
  * --> that is an effectful operation --> the fiber must be wrapped in an IO
  * Managing a fiber is an EFFECTFUL operation
  * --> the result is wrapped in another IO
  * 
  * 3. How Fiber works
  * Fiber is a passive data structure, managed by CE RT
  * CE has a thread pool that manages the execution of effects
  * 10 of millions of fibers per GB heap, because they are stored in memory
  * CE schedules fibers for execution
  * 
  * 4. Motivation for Fibers
  * - No more locks, threads
  * - Delegate thread management to CE
  * - Avoid async code with callbacks (callback hell)
  * - Keep low-level primitives e.g blocking, waiting, joining, interrupting, cancelling
  * If we run blocking effects in a fiber --> 'descheduling' --> no real threads are blocked and wasted
  * The same fiber can run on multiple JVM threads
  */
object Fibers extends IOApp.Simple {
  
  val meaningOfLife = IO.pure(42)
  val favLang = IO.pure("Scala")

  def sameThread() = for {
    mol <- meaningOfLife.debugExt // bc mol is pure & wrapped in for --> has same thread with lang
    lang <- favLang.debugExt
  } yield ()

  // introduce the Fiber
  def createFiber: Fiber[IO, Throwable, String] = ??? // almost impossible to create fibers manually

  // the fiber is not actually started, but the fiber allocation is wrapped in another effect
  val aFiber: IO[Fiber[IO, Throwable, Int]] = meaningOfLife.debugExt.start // every single thing that we do,
  // must be wrapped in an IO

  def differentThreadIO() = for {
    _ <- aFiber // create a fiber in this way
    _ <- favLang.debugExt
  } yield ()

  // joining a fiber
  def runOnSomeOtherThread[A](io: IO[A]): IO[Outcome[cats.effect.IO, Throwable, A]] = for {
    fib <- io.start // fib is a handle of a fiber 
    result <- fib.join // an effect which waits for the fiber to terminate
  } yield result
  /**
   * When we run `yield result` --> IO[ResultType of fib.join]
   *  fib.join = A
   *  but fib wraps an IO effect --> fib.join = IO[A]
   *  but fib might also fail --> actually wraps an outcome --> Outcome[IO, Throwable, A]
   * 
   * Much like Fiber has 3 types args, we have the same return status of temination of a Fiber, which also an IO
   *  - Possible outcomes:
    * 1. successfully, with a value (wrapped in IO, examples above)
    * 2. as a failure, wrapping in exception
    * 3. cancelled, neither
   */
  
  def thrownOnAnotherThread() = for {
    fib <- IO.raiseError[Int](new RuntimeException("no number for you ")).start
    result <- fib.join
  } yield result
  
  def testCancel() = {
    val task = IO("starting").debugExt >> IO.sleep(1.second) >> IO("done").debugExt // "done" will never be exec
    val taskWithCancellationHandler = task.onCancel(IO("I'm being cancelled").debugExt.void)
    for {
      fib <- taskWithCancellationHandler.start // on a separate thread
      _ <- IO.sleep(500.millis) >> IO("cancelling").debugExt
      _ <- fib.cancel // just like join, cancel sends signal to the RT to stop the exec of this fiber
      result <- fib.join
    } yield result
  }
  override def run: IO[Unit] = 
    // sameThread()
    differentThreadIO() // [io-compute-1] Scala
                        // [io-compute-4] 42

    runOnSomeOtherThread(meaningOfLife) // data structure: IO(Succeeded(IO(42)))
     .debugExt.void // [io-compute-12] Succeeded(IO(42))
     
    thrownOnAnotherThread().debugExt.void // [E] java.lang.RuntimeException: no number for you 
                                          // [E]     at part3Concurrency.Fibers$.thrownOnAnotherThread(Fibers.scala:51)
                                          // [E]     at part3Concurrency.Fibers$.run(Fibers.scala:62)
                                          // [io-compute-1] Errored(java.lang.RuntimeException: no number for you )
    
    testCancel().debugExt.void // [io-compute-8] starting
                               // [io-compute-10] cancelling
                               // [io-compute-11] I'm being cancelled
                               // [io-compute-5] Canceled()                              
}

  /**
   * Exercises:
   *  1. Write a function that runs an IO on another thread, and, depending on the result of the fiber
   *    - return the result in an IO
   *    - if errored or cancelled, return a failed IO
   *
   *  2. Write a function that takes two IOs, runs them on different fibers and returns an IO with a tuple containing both results.
   *    - if both IOs complete successfully, tuple their results
   *    - if the first IO returns an error, raise that error (ignoring the second IO's result/error)
   *    - if the first IO doesn't error but second IO returns an error, raise that error
   *    - if one (or both) canceled, raise a RuntimeException
   *
   *  3. Write a function that adds a timeout to an IO:
   *    - IO runs on a fiber
   *    - if the timeout duration passes, then the fiber is canceled
   *    - the method returns an IO[A] which contains
   *      - the original value if the computation is successful before the timeout signal
   *      - the exception if the computation is failed before the timeout signal
   *      - a RuntimeException if it times out (i.e. cancelled by the timeout)
   */
object FibersExercise extends IOApp.Simple {

  // exercise 1
  def processResultsFromFiber[A](io: IO[A]): IO[A] = {
    val ioresult = for {
      fib <- io.debugExt.start
      result <- fib.join
    } yield result
    ioresult.flatMap{
      case Succeeded(value) => value
      case Errored(e) => IO.raiseError(e)
      case Canceled() => IO.raiseError(new RuntimeException("Computation canceled"))
    }
  }
  def testExercise1() = {
    val aComputation = IO("starting").debugExt >> IO.sleep(1.second) >> IO.canceled.debugExt >> IO("done!").debugExt >> IO(42)
    processResultsFromFiber(aComputation).void
  }

  // exercise 2
  def tupleIOs[A, B](ioa: IO[A], iob: IO[B]): IO[(A, B)] = {
    val result = for {
      // start 2 separate fibers
      fiba <- ioa.start
      fibb <- iob.start
      resulta <- fiba.join //.join returns an `Outcome`
      resultb <- fibb.join
    } yield (resulta, resultb)
    result.flatMap{
      case (Succeeded(fa), Succeeded(fb)) => for {
        a <- fa // fa is IO[A]
        b <- fb
      } yield (a, b)
      case (Errored(e), _) => IO.raiseError(e)
      case (_, Errored(e)) => IO.raiseError(e)
      case _ => IO.raiseError(new RuntimeException("Some computations canceled"))
    }
  }
  def testExercise2() = {
    val firstIO = IO.sleep(1.second) >> IO(1).debugExt
    val secondIO = IO.sleep(2.second) >> IO.raiseError(new RuntimeException()).debugExt
    tupleIOs(firstIO, secondIO).debugExt.void
  }

  // exercise 3
  def timeout[A](io: IO[A], duration: FiniteDuration): IO[A] = {
    val computation = for {
      fib <- io.start
      _ <- IO.sleep(duration) >> fib.cancel // can replace to fib.start, but be careful as fiber can leak
      result <- fib.join
    } yield result

    computation.flatMap{
      case Succeeded(fa) => fa
      case Errored(e) => IO.raiseError(e)
      case Canceled() => IO.raiseError(new RuntimeException("Computation canceled"))
    }
  }
  def testExercise3() = {
    val aComputation = IO("starting").debugExt >> IO.sleep(1.second) >> IO("almost done").debugExt >> IO("done!").debugExt >> IO(42)
    // 1. it prints starting
    // 2. it waits 1 sec
    // 3. it prints almost done and done at the same time
    // 4. it waits 2.5 sec
    // 5. it prints 42
    timeout(aComputation, 3500.millis).debugExt.void
  }


  override def run: IO[Unit] = {
    testExercise1()
      // [io-compute-2] starting
      // [io-compute-8] done!
      // [io-compute-8] 42

    testExercise2()

    testExercise3()
  }
}
