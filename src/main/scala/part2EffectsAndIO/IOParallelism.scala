package part2EffectsAndIO

import cats.effect.IOApp
import cats.effect.IO
import cats.Parallel
import utils._

object IOParallelism extends IOApp.Simple {
 
  // IOs are usually sequential
  val anisIO = IO(s"[${Thread.currentThread().getName()}] Ani")
  val kamranIO = IO(s"[${Thread.currentThread().getName()}] Kamran")
  // these 2 IOs will be executed in a same thread
  val composedIO = for {
    ani <- anisIO
    kamran <- kamranIO
  } yield s"$ani and $kamran love Rock the JVM"

  // debug extension method
  import cats.syntax.apply._
  // mapN extension method
  val meaningOfLife = IO(42).debugExt
  val favLang = IO("Scala").debugExt
  val goalInLife = (meaningOfLife, favLang).mapN((num, string) => s"My goal in life is $num and $string")

  // Parallelism on IOs
  // convert a sequential IO to parallel IO
  val parIO1: IO.Par[Int] = Parallel[IO].parallel(meaningOfLife.debugExt)
  val parIO2: IO.Par[String] = Parallel[IO].parallel(favLang.debugExt)
  import cats.effect.implicits._
  val goalInLifeParallel: IO.Par[String] = (parIO1, parIO2).mapN((num, string) => s"My goal in life is $num and $string")
  // turn back to sequential
  val goalInLife_v2: IO[String] = Parallel[IO].sequential(goalInLifeParallel)

  // short hand
  import cats.syntax.parallel._
  val goalInLife_v3: IO[String] =  (meaningOfLife.debugExt, favLang.debugExt).parMapN((num, string) => s"My goal in life is $num and $string")

  // regarding failure:
  val aFailure: IO[String] = IO.raiseError(new RuntimeException("I can't do this")) // how can we compose 1 suc, 1 failed comp. in paralell?
  // compose success + failure
  val parallelWithFailure = (meaningOfLife.debugExt, aFailure.debugExt).parMapN(_ + _)
  // compose failure + failure
  val anotherFailure: IO[String] = IO.raiseError(new RuntimeException("Second failure"))
  val twoFailures = (aFailure.debugExt, anotherFailure.debugExt).parMapN(_+_)
  // the first effect to fail gives the result
  val twoFailuresDelayed = (IO(Thread.sleep(1000)) >> aFailure.debugExt, anotherFailure.debugExt).parMapN(_+_)
  override def run: IO[Unit] = 
    // sequential
    // composedIO.map(println) //[io-compute-11] Ani and [io-compute-11] Kamran love Rock the JVM
    // goalInLife.map(println) // [io-compute-8] 42
    //                         // [io-compute-8] Scala
    //                         // My goal in life is 42 and Scala

    // goalInLife_v2.debugExt.void // result of computation is in 3 different threads
    // 1 parallel program that gathers multiple threads into 1 after those 2 computations were performed

    // goalInLife_v3.debugExt.void

    twoFailures.debugExt.void
}
