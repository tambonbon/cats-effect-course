package part3Concurrency

import cats.effect.IOApp
import cats.effect.IO
import scala.concurrent.duration._
import utils.debugExt
import scala.concurrent.ExecutionContext
import java.util.concurrent.Executors

object BlockingIOs extends IOApp.Simple {
  
  val someSleeps = for {
    _ <- IO.sleep(1.second).debugExt // in a different thread --> SEMANTIC BLOCKING
    _ <- IO.sleep(1.second).debugExt // in a different thread
  } yield ()
  // SEMANTIC BLOCKING: The thread itself that CE manages, is not actually blocked
  // --> No Thread actually does the sleeping after 1 second, but rather the Thread will be scheduled after 1 sec to continue the computation
  //  --> that's why debug prints 2 different threads
  // Thus, with Semantic Blocking, it looks like the Thread is actually sleeping, but no actual thread is blocked in the amount of time


  // really blocking IOs
  val aBlockingIO = IO.blocking { //IO.blocking will hold on to a thread, but on a thread from another thread pool for blocking calls
    Thread.sleep(1000)
    println(s"[${Thread.currentThread().getName()}] compute a blocking code")
    42
  }

  // yielding
  val iosOnManyThreads = for {
    _ <- IO("first").debugExt
    // _ <- IO.cede // a signal to yield control over the thread
    _ <- IO("second").debugExt  // the subsequent comp. after IO.cede can run on different thread
    // _ <- IO.cede // if there is no 'cede', all the comp. will be in the same thread
    _ <- IO("third").debugExt
  } yield ()

  // for few effects, threads may still be the same even with cede, bc of CE optimisation
  def testThousandEffectsSwitch() = {
    val ec: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(8))
    val thousandCedes = (1 to 1000).map(IO.pure).reduce(_.debugExt >> IO.cede >> _.debugExt).evalOn(ec)
  }
  
  /* 
    - blocking calls & IO.sleep (both semantic block) and yield control over thread happen autom
   */
  override def run: IO[Unit] = 
    someSleeps.void
    // [io-compute-3] ()
    // [io-compute-12] ()

    aBlockingIO.void
    // [io-compute-blocker-4] compute a blocking code

    iosOnManyThreads.void
}
