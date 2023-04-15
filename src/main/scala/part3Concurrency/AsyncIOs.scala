package part3Concurrency

import cats.effect.IOApp
import cats.effect.IO
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.util.Try
import utils.debugExt
import scala.concurrent.Future
import scala.concurrent.duration._

// Async: you start a comp., and then receive some kind of callbacks, which will be invoked later when the comp. succeeds
object AsyncIOs extends IOApp.Simple {

  // IOs can run asynchronously on fibers, without having to manually manage the fiber lifecycle
  val threadPool = Executors.newFixedThreadPool(8)
  given ec: ExecutionContext = ExecutionContext.fromExecutorService(threadPool)
  type Callback[A] = Either[Throwable, A] => Unit
  // Callback[A] will be the alias of the RHS, a function that takes Either[A] and returns Unit
  // This Callback, is subject to Async principle, so Async comp. will try to execute an IO, on some other Threads managed by us or CE
  // and when this effect is completed, we will invoke one of these Callback

  def computeMeaningOfLife(): Int = {
    Thread.sleep(1000)
    println(s"[${Thread.currentThread().getName()}] computing the meaning of life on some other thread")
    42
  }
  def computeMeaningOfLifeEither(): Either[Throwable, Int] = Try {
    computeMeaningOfLife()
  }.toEither

  // now we want to invoke computeMeaningOfLife in one of the 'threadPool'
  def computeMolOnThreadPool(): Unit = { // returns Unit --> side effect
    threadPool.execute( () => computeMeaningOfLife())
  }
  // the problem is, scheduling computeMolOnThreadPool on threadPool is hard,
  // and because it's a side effect, we cannot create IO effect that will get a hole on this comp

  // solution -> lift comp. to an IO
  val asyncMolIO: IO[Int] = IO.async_ { (cb: Callback[Int]) => // very critical, note the IO[Int], not Either anymore
    // IO.async_ creates an effect, the execution of that effect will make the thread that runs that effect BLOCK semantically
    // until the callback is evaluated, or invoked in "some other threads"
    threadPool.execute { () => // notify the completion of a comp. not manages by CE
      val result = computeMeaningOfLifeEither()
      cb(result) // CE thread is notified with the result
      // by invoking callback the result
    }
  }

  // Exercise: Lift an async comp on ec, into an IO
  def asyncToIO[A](comp: () => A)(ec: ExecutionContext): IO[A] = 
    IO.async_ { (cb: Callback[A]) =>
      ec.execute { () => 
        val res = Try(comp()).toEither
        cb(res) // type Unit
      }
    }
  // val asyncMolIO_v2: IO[Int] = asyncToIO(computeMeaningOfLife())

  // Exercise: Lift an async comp. as a Future, into an IO
  lazy val molFuture = Future { 
    computeMeaningOfLife() 
  }

  def convertFuturetoIO[A](future: => Future[A]): IO[A] = 
    IO.async_{ (cb: Callback[A]) =>
      future.onComplete { tryResult =>
        val res = tryResult.toEither
        cb(res)
      }  
    }
  val asyncMolIO_v3: IO[Int] = convertFuturetoIO(molFuture)
  val asyncMolIO_v4: IO[Int] = IO.fromFuture(IO(molFuture))
  
  // Exercise: a never-ending IO?
  val neverEndingIO: IO[Int] = IO.async_[Int](_ => ()) // no callback, no finish
  val neverEndingIO_v2: IO[Int] = IO.never

  // FULL ASYNC Call
  def demoAsyncCancellation() = {
    val computeMeaningOfLife_v2: IO[Int] = IO.async { (cb: Callback[Int]) => // async relies on same mechanism as async_: it will block the CE thread until a callback is evaluate
      /*
        finalizer in case comp. gets cancelled
        finalizer are of type IO[Unit]
        not specifying finalizer => Option[IO[Unit]]
        creating option is an effect => IO[Option[IO[Unit]]]
      */
      IO {
        threadPool.execute { () =>
          val result = computeMeaningOfLifeEither()
          cb(result)
        }
      } // IO[Unit], now we need to map, to make it Option[IO[Unit]]
      .as(Some(IO("Cancelled!").debugExt.void)) // HARD!
      // return IO[Option[IO[Unit]]]  
    }

    for {
      fiber <- computeMeaningOfLife_v2.start
      _ <- IO.sleep(500.millis) >> IO("cancelling..").debugExt >> fiber.cancel
      _ <- fiber.join
    } yield ()
  }
  

  override def run: IO[Unit] = 
    asyncMolIO.debugExt >> IO(threadPool.shutdown())
    // [pool-1-thread-1] computing the meaning of life on some other thread (managed by thread pool)
    // [io-compute-8] 42 (managed by CE)

    neverEndingIO.void

    demoAsyncCancellation().debugExt >> IO(threadPool.shutdown())
}
