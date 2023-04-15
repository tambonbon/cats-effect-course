package part3Concurrency

import cats.effect.IOApp
import cats.effect.IO
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import cats.effect.kernel.Outcome
import cats.effect.kernel.Fiber
import cats.effect.kernel.Outcome.Succeeded
import cats.effect.kernel.Outcome.Errored
import cats.effect.kernel.Outcome.Canceled

object RacingIOs extends IOApp.Simple {
  import utils.debugExt

  def runWithSleep[A](value: A, duration: FiniteDuration): IO[A] = 
    (
      IO(s"Starting computation: $value").debugExt >>
      IO.sleep(duration) >>
      IO(s"computation for $value: done") >>
      IO(value)
    ).onCancel(IO(s"computation CANCELED for $value").debugExt.void)

  def testRace(): IO[String] = {
    val meaningOfLife = runWithSleep(42, 1.second)
    val favLang = runWithSleep("Scala", 2.seconds)
    val first: IO[Either[Int, String]] = IO.race(meaningOfLife, favLang) // VERY USEFUL
    /**
      * both IOs run on separate fibers
      * the first one to finish will complete the result
      * the loser will be canceled
      */
    first.flatMap{
      case Left(value) => IO(s"Meaning of life won: $value")
      case Right(value) => IO(s"Fav lang won: $value")
    }
  }

  def testRacePair() = {
    val meaningOfLife = runWithSleep(42, 1.second)
    val favLang = runWithSleep("Scala", 2.seconds)  
    val raceResult: IO[Either[
                    (Outcome[IO, Throwable, Int], Fiber[IO, Throwable, String]), // (winner result, loser fiber)
                    (Fiber[IO, Throwable, Int], Outcome[IO, Throwable, String]) // (loser fiber, winner result)
     ]] = IO.racePair(meaningOfLife, favLang)

     raceResult.flatMap{
      case Left((winnerRes, loserFib)) => loserFib.cancel >> IO("Meaning of life won").debugExt >> IO(winnerRes).debugExt
      case Right((loserFib, winnerRes)) => loserFib.cancel >> IO("Language won").debugExt >> IO(winnerRes).debugExt
     }
  }


  override def run: IO[Unit] = 
    testRace().debugExt.void

    testRacePair().void
    // [io-compute-12] Starting computation: 42
    // [io-compute-1] Starting computation: Scala
    // [io-compute-11] computation CANCELED for Scala
    // [io-compute-11] Meaning of life won
    // [io-compute-11] Succeeded(IO(42))
}

  /**
   * Exercises:
   * 1 - implement a timeout pattern with race
   * 2 - a method to return a LOSING effect from a race (hint: use racePair)
   * 3 - implement race in terms of racePair
   */
object RacingExercise extends IOApp.Simple {
  import utils.debugExt
  import RacingIOs._

  // exercise 1
  def timeout[A](io: IO[A], duration: FiniteDuration): IO[A] = {
    val durationIO = IO.sleep(duration)
    IO.racePair(io, durationIO).flatMap{
      case Left((winnerRes, loserFib)) => io
      case Right((loserFib, winnerRes)) => IO.raiseError(new RuntimeException("Computation timed out"))
    }
  }
  def timeout_sln[A](io: IO[A], duration: FiniteDuration): IO[A] = {
    val durationIO = IO.sleep(duration)
    IO.race(io, durationIO).flatMap{
      case Left(value) => IO(value)
      case Right(value) => IO.raiseError(new RuntimeException("Computation timed out"))
    }
  }
  val timeout_sln_v2 = 
    val importantTask = IO.unit
    importantTask.timeout(duration = 1.second)

  
  // exercise 2
  def unrace[A, B](ioa: IO[A], iob: IO[B]): IO[Either[A, B]] = 
    IO.racePair(ioa, iob).flatMap{
      case Left((winnerRes, loserFib)) => loserFib.join.flatMap{ // loserFib needs to be join,
                                                                //  bcuz we just discarded the result/effect of ioa
                                                                // .join results in Outcome, so we need to flatMap
        case Succeeded(fa) => fa.map(result => Right(result)) // bcuz the fiber B was the one to lose, so we need an IO wrapping a Right
        case Errored(e) => IO.raiseError(e)
        case Canceled() => IO.raiseError(new RuntimeException("Loser canceled"))
      }
      case Right((loserFib, winnerRes)) => loserFib.join.flatMap{
        case Succeeded(fa) => fa.map(result => Left(result))
        case Errored(e) => IO.raiseError(e)
        case Canceled() => IO.raiseError(new RuntimeException("Loser canceled"))
      }
    }
  def testUnRace(): IO[String] = {
    val meaningOfLife = runWithSleep(42, 1.second)
    val favLang = runWithSleep("Scala", 2.seconds)
    val first: IO[Either[Int, String]] = unrace(meaningOfLife, favLang) 
    first.flatMap{
      case Left(value) => IO(s"Meaning of life won: $value")
      case Right(value) => IO(s"Fav lang won: $value")
    }
  }

  // exercise 3
  def simpleRace[A, B](ioa: IO[A], iob: IO[B]): IO[Either[A, B]] = 
    IO.racePair(ioa, iob).flatMap{
      case Left((winnerA, loserB)) => winnerA match
        case Succeeded(fa) => loserB.cancel >> fa.map(res => Left(res))
        case Errored(e) => loserB.cancel >> IO.raiseError(e)
        case Canceled() => loserB.join.flatMap{
          case Succeeded(fb) => fb.map(res => Right(res))
          case Errored(e) => IO.raiseError(e)
          case Canceled() => IO.raiseError(new RuntimeException("Both computation cancel"))
        }
      case Right((loserA, winnerB)) => winnerB match
        case Succeeded(fb) => fb.map(res => Right(res))
        case Errored(e) => loserA.cancel >> IO.raiseError(e)
        case Canceled() => loserA.join.flatMap{
          case Succeeded(fa) => fa.map(res => Left(res))
          case Errored(e) => IO.raiseError(e)
          case Canceled() => IO.raiseError(new RuntimeException("Both computation cancel"))
        }
      
    }


  override def run: IO[Unit] = 
    timeout(IO.sleep(1.second) >> IO(42), 500.millis).void
    timeout(IO.sleep(1.second) >> IO(42), 2.second).debugExt.void
    // [io-compute-5] 42
    // [io-compute-10] 42

    testUnRace().debugExt.void
    // [io-compute-10] Starting computation: 42
    // [io-compute-2] Starting computation: Scala
    // [io-compute-2] Fav lang won: Scala

}
