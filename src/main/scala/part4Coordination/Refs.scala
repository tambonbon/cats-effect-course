package part4Coordination

import cats.effect.IOApp
import cats.effect.IO
import cats.effect.kernel.Ref
import utils.debugExt
import cats.syntax.parallel._
import scala.concurrent.duration._

object Refs extends IOApp.Simple {

  // Ref: a purely functional atomic reference
  val atomicMol: IO[Ref[IO, Int]] = Ref[IO].of(42)
  val atomicMol_v2: IO[Ref[IO, Int]] = IO.ref(42)
  // a Ref is simply a wrapper of a value, in a FP way, thus return an effect
  // Interacting with a Ref is an effect

  // modifying is an effect
  val increasedMol: IO[IO[Unit]] = atomicMol.map { ref =>
    ref.set(43) // this is an effect --> return an IO[Unit]
  }
  val increasedMol_v2: IO[Unit] = atomicMol.flatMap { ref =>
    ref.set(43) // always thread-safe
  }

  // obtain a value
  val mol = atomicMol.flatMap { ref =>
    ref.get // also thread-safe
  }

  val getAndSetMol = atomicMol.flatMap { ref =>
    ref.getAndSet(43)
  } // gets the old value, sets the new one

  // updating with a function
  val fMol: IO[Unit] = atomicMol.flatMap { ref => // noted the type
    ref.update(value => value * 10) // update takes a function
  }

  val updatedMol: IO[Int] = atomicMol.flatMap { ref =>
    ref.updateAndGet(value => value * 10) // get the new value
  }

  // modifying with a funciton, returning a diffrent type
  val modifiedMol: IO[String] = atomicMol.flatMap { ref =>
    ref.modify(value => (value * 10, s"my current value is $value"))
  } // this is very POWERFUL

  // Why we need this: concurrent + thread-safe read/write shared value in a purely Fp way

  def demoConcurrentWorkImpure(): IO[Unit] = {
    import cats.syntax.parallel._
    var count = 0

    def task(workload: String): IO[Unit] = {
      val wordCount = workload.split(" ").length
      for {
        _ <- IO(s"Counting words for '$workload': $wordCount").debugExt
        newCount = count + wordCount // not safe here
        _ <- IO(s"New total: $newCount").debugExt
        _ = count = newCount
      } yield ()
    }

    List(
      "I love Cats Effect",
      "This ref thing is useless",
      "Tam writes lots of code"
    )
      .map(task)
      .parSequence
      .void // not in order because not thread safe
  }

  def demoConcurrentWorkPureNotThreadSafe(): IO[Unit] = {
    var count =
      0 // this is very difficult to trace, and though pure, results in different outputs everytime

    def task(workload: String): IO[Unit] = {
      val wordCount = workload.split(" ").length
      for {
        _ <- IO(s"Counting words for '$workload': $wordCount").debugExt
        newCount = IO(count + wordCount)
        _ <- IO(s"New total: $newCount").debugExt
        _ <- IO(count += wordCount)
      } yield ()
    }

    List(
      "I love Cats Effect",
      "This ref thing is useless",
      "Tam writes lots of code"
    )
      .map(task)
      .parSequence
      .void //
  }

  def demoConcurrentWorkPureThreadSafe(): IO[Unit] = {
    def task(workload: String, total: Ref[IO, Int]): IO[Unit] = {
      val wordCount = workload.split(" ").length
      for {
        _ <- IO(s"Counting words for '$workload': $wordCount").debugExt
        newCount <- total.updateAndGet(currentCount => currentCount + wordCount)
        _ <- IO(s"New total: $newCount").debugExt
      } yield ()
    }
    
    for {
      initialCount <- Ref[IO].of(0)
      _ <- List(
      "I love Cats Effect",
      "This ref thing is useless",
      "Tam writes lots of code"
      ).map(string => task(string, initialCount))
      .parSequence
    } yield ()
  }

  override def run: IO[Unit] =
    demoConcurrentWorkImpure()
    // [io-compute-1] Counting words for 'I love Cats Effect': 4
    // [io-compute-8] Counting words for 'This ref thing is useless': 5
    // [io-compute-6] Counting words for 'Tam writes lots of code': 5
    // [io-compute-8] New total: 5
    // [io-compute-6] New total: 5
    // [io-compute-1] New total: 4

    demoConcurrentWorkPureThreadSafe()
}

object RefsExercise extends IOApp.Simple {
  
  def tickingClockImpure(): IO[Unit] = {
    var ticks: Long = 0L
    def tickingClock: IO[Unit] = for {
      _ <- IO.sleep(1.second)
      _ <- IO(System.currentTimeMillis()).debugExt
      _ <- IO(ticks +=1) // whenever we have a mutated var -> thread-unsafe, because 1 is getting it (l144), here it's mutating
      _ <- tickingClock // recursively
    } yield ()

    def printTicks: IO[Unit] = for {
      _ <- IO.sleep(5.seconds)
      _ <- IO(s"TICKS: $ticks").debugExt
      _ <- printTicks
    } yield ()

    for {
      _ <- (tickingClock, printTicks).parTupled
    } yield ()
  }

  def tickingClockPure(): IO[Unit] = {
    def tickingClock(ticks: Ref[IO, Int]): IO[Unit] = for {
      // use method with the exact same Reference to Ref, not Reference to IO
      // because otherwise, you get a new evaluation every time, and value is not updated
      // e.g., NOT val ticks = Ref[IO].of(0), but put in the signature
      _ <- IO.sleep(1.second)
      _ <- IO(System.currentTimeMillis()).debugExt
      _ <- IO(ticks.update(_ + 1)) // thread-safe here
      _ <- tickingClock(ticks)
    } yield ()

    def printTicks(ticks: Ref[IO, Int]): IO[Unit] = for {
      _ <- IO.sleep(5.seconds)
      t <- ticks.get
      _ <- IO(s"TICKS: $t").debugExt
      _ <- printTicks(ticks)
    } yield ()

    for {
      tickRef <- Ref[IO].of(0)
      _ <- (tickingClock(tickRef), printTicks(tickRef)).parTupled
    } yield ()
  }


  override def run: IO[Unit] = ???
}