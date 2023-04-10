package part2EffectsAndIO

import cats.effect.IO
import scala.io.StdIn

object IOIntro {

  // IO
  val ourFirstIO: IO[Int] =
    IO.pure(42) // pure -> arg should not have any side effects
  val aDelayIO: IO[Int] = IO.delay({
    println("I'm producing an integer with delay")
    54
  })

  val shouldNotDoThis: IO[Int] =
    IO.pure { // don't use IO.pure unless the value inside is producing side effect
      println("I'm producing an integer wrongly")
      54
    }

  val aDelayIO_v2: IO[Int] = IO { // same as IO.delay
    println("I'm producing an integer with delay")
    54
  }

  // map, flatMap (most used APIs for IO)
  val improvedMeaningOfLife = ourFirstIO.map(_ * 2)
  val printedMeaningOfLife = ourFirstIO.flatMap(mol => IO(println(mol)))

  def smallProgram(): IO[Unit] = for {
    line1 <- IO(StdIn.readLine())
    line2 <- IO(StdIn.readLine())
    _ <- IO(println(line1 + line2))
  } yield ()

  // mapN - combine IO effects as tuples
  import cats.syntax.apply._
  val combinedMeaningOfLife: IO[Int] =
    (ourFirstIO, improvedMeaningOfLife).mapN(_ + _)
  def smallProgram_v2(): IO[Unit] =
    (IO(StdIn.readLine()), IO(StdIn.readLine())).mapN(_ + _).map(println)

  def main(args: Array[String]): Unit = {
    import cats.effect.unsafe.implicits.global // platform to run IO structure (an IO runtime)

    // end of the app, run exactly one
    // println(aDelayIO.unsafeRunSync()) // unsafeRunSync: to execute all those effects described by IOs
    println(smallProgram().unsafeRunSync())
    println(smallProgram_v2().unsafeRunSync())
  }
}

  object Exercise2 {

    /** Exercises
      */

    // 1 - sequence two IOs and take the result of the LAST one
    def sequenceTakeLast[A, B](ioa: IO[A], iob: IO[B]): IO[B] = ioa.flatMap(sth => iob)
    def sequenceTakeLast_v2[A, B](ioa: IO[A], iob: IO[B]): IO[B] = ioa *> iob // andThen by value (eagerly)
    def sequenceTakeLast_v3[A, B](ioa: IO[A], iob: IO[B]): IO[B] = ioa >> iob // andThen by name (lazy)

    // 2 - sequence two IOs and take the result of the FIRST one
    def sequenceTakeFirst[A, B](ioa: IO[A], iob: IO[B]): IO[A] = ioa.flatMap(a => iob.map(sth => a)) // a bcuz we care of ioa
    def sequenceTakeFirst_v2[A, B](ioa: IO[A], iob: IO[B]): IO[A] = ioa <* iob

    // 3 - repeat an IO effect forever
    def forever[A](io: IO[A]): IO[A] = io.flatMap(sth => forever(IO(sth)))
    def forever_sln[A](io: IO[A]): IO[A] = io.flatMap(sth => forever(io))
    def forever_sln_v2[A](io: IO[A]): IO[A] = io >> forever_sln_v2(io)
    def forever_sln_v3[A](io: IO[A]): IO[A] = io *> forever_sln_v3(io)
    def forever_sln_v4[A](io: IO[A]): IO[A] = io.foreverM // same with sln and sln_v2, with tail recursion

    // 4 - convert an IO to a different type
    def convert[A, B](ioa: IO[A], value: B): IO[B] = ioa.map(sth => value)
    def convert_sln[A, B](ioa: IO[A], value: B): IO[B] = ioa.map(_ => value)
    def convert_sln_v2[A, B](ioa: IO[A], value: B): IO[B] = ioa.as(value)
 
    // 5 - discard value inside an IO, just return unit
    def asUnit[A](ioa: IO[A]): IO[Unit] = convert(ioa, ())
    def asUnit_sln[A](ioa: IO[A]): IO[Unit] = ioa.map(_ => ())
    def asUnit_sln_v2[A](ioa: IO[A]): IO[Unit] = ioa.as(()) // discourage
    def asUnit_sln_v3[A](ioa: IO[A]): IO[Unit] = ioa.void

    // 6 - fix stack recursion
    def sum(n: Int): Int = 
      if (n <= 0) 0 else n + sum(n - 1)
    def sumIO(n: Int): IO[Int] = 
      if (n <= 0) IO(0)
      else for {
        lastNumber <- IO(n)
        prevNumber <- sumIO(n - 1)
      } yield prevNumber + lastNumber

    // 7 (hard) - write a fibonacci IO that does NOT crash on recursion
    def fibonacci(n: Int): IO[BigInt] = 
      if (n < 2) IO(1)
      else for {
        last <- IO(fibonacci(n-1)).flatMap(x => x)
        prev <- IO(fibonacci(n-2)).flatten //same with above
        // prev <- IO.defer(fibonacci(n-2)) //same with above
        // defer: constructing an IO, based on another IO (an IO inside another IO, with the same type)
      } yield last + prev
    
    def main(args: Array[String]): Unit = {
      import cats.effect.unsafe.implicits.global

      // exercise 3
      // this prints out normally, recommended to use lazy
      // forever_sln_v2(IO{
      //   println("forever!")
      //   Thread.sleep(1000)
      // }).unsafeRunSync()

      // this gives a stack overflow
      // the chain of IOs will be evaluated eagerly, and cascading to a stackoverflow
      // before the final datatype IO has the chance to run the unsafeRun method
      // forever_sln_v3(IO{
      //   println("forever!")
      //   Thread.sleep(1000)
      // }).unsafeRunSync()

      // exercise 6
      println(sumIO(20000).unsafeRunSync()) // print out normally
      // sum(20000) // stack overflow
      // ---> IO is really useful, not only to compose effect, but also to prevent stack overflow

      // exercise 7
      (1 to 100).foreach(i => println(fibonacci(i).unsafeRunSync()))
    }
  }
