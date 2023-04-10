package part2EffectsAndIO

import scala.concurrent.Future
import scala.io.StdIn

object Effects {

  /** Effect = data type which
    *   1. embodies a computational concept (eg side effects, absence of value)
    *      2. is RT
    */
  /*
    Effect types
    Properties:
    - type signature describes the kind of calculation that will be performed
    - type signature describes the VALUE that will be calculated
    - when side effects are needed, effect construction is separate from effect execution
   */

  /*
    example: Option is an effect type
    - describes a possibly absent value
    - computes a value of type A, if it exists
    - side effects are not needed
   */
  val anOption: Option[Int] = Option(42)

  /*
    example: Future is NOT an effect type
    - describes an asynchronous computation
    - computes a value of type A, if it's successful
    - side effect is required (allocating/scheduling a thread), execution is NOT separate from construction
   */
  import scala.concurrent.ExecutionContext.Implicits.global
  val aFuture: Future[Int] = Future(42)

  /*
    example: MyIO data type from the Monads lesson - it IS an effect type
    - describes any computation that might produce side effects
    - calculates a value of type A, if it's successful
    - side effects are required for the evaluation of () => A
      - YES, the creation of MyIO does NOT produce the side effects on construction
   */
  case class MyIO[A](unsafeRun: () => A) {
    def map[B](f: A => B): MyIO[B] =
      MyIO(() => f(unsafeRun()))

    def flatMap[B](f: A => MyIO[B]): MyIO[B] =
      MyIO(() => f(unsafeRun()).unsafeRun())
  }

  val anIO: MyIO[Int] = MyIO(() => {
    println(
      "I'm writing something..."
    ) // can only be printed when deliberately called
    42
  })

  def main(args: Array[String]): Unit = {
    anIO.unsafeRun()
  }
}

object Exercise {
  import Effects._

  /** Exercises
    *   1. An IO which returns the current time of the system 
    *   2. An IO which measures the duration of a computation (hint: use ex 1) 
    *   3. An IO which prints something to the console 
    *   4. An IO which reads a line (a string) from the std input
    */

  val exercise1: MyIO[Long] = MyIO(() => System.currentTimeMillis())

  def exercise2[A](computation: MyIO[A]): MyIO[Long] = for {
    currentTime <- exercise1
    _ <- computation
    finishTime <- exercise1
  } yield finishTime - currentTime

  /**
    Deconstruction:
    exercise1.flatMap(currentTime => computation.flatMap(_ => exercise1.map(finishTime => finishTime - currentTime)))
    Part 3:
    exercise1.map(finishTime => finishTime - currentTime) = MyIO(() => exercise1.unsafeRun() - currentTime // def of map
                                                          = MyIO(() => System.currentTimeMillis() - currentTime) 
    -> exercise1.flatMap(currentTime => computation.flatMap(_ => MyIO(() => System.currentTimeMillis() - currentTime)))
    Part 2:
    computation.flatMap(lambda) = MyIO(() => lambda((computation.unsafeRun()).unsafeRun())
                                = MyIO(() => lambda(___COMP___).unsafeRun())
                                = MyIO(() => MyIO(() => System.currentTimeMillis() - currentTime)).unsafeRun())
                                = MyIO(() => System.currentTimeMillis() - currentTime)
                                = MyIO(() => System.currentTimeMillis_after_computation() - currentTime)
    Part 1:
    exercise1.flatMap(currentTime => MyIO(() => System.currentTimeMillis_after_computation() - currentTime))
    = MyIO(() => MyIO(() => System.currentTimeMillis_after_computation() - System.currentTimeMillis()).unsafeRun())
    = MyIO(() => System.currentTimeMillis_after_computation() - System.currentTimeMillis_at_start())
   */

  def testTimeIO(): Unit = {
    val test = exercise2(MyIO(() => Thread.sleep(100)))
    println(test.unsafeRun.toString())
  }

  def exercise3(line: String): MyIO[Unit] = MyIO(() => println(line))

  val exercise4: MyIO[String] = MyIO(() => StdIn.readLine())

  def testConsole(): Unit = {
    val program = for {
      line1 <- exercise4
      line2 <- exercise4
      _ <- exercise3(line1 + line2)
    } yield ()
    program.unsafeRun()
  }

  def main(args: Array[String]): Unit = {
    println(exercise1.toString())
    testTimeIO()
    testConsole()
  }
}
