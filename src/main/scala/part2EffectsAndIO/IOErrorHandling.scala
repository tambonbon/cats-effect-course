package part2EffectsAndIO

import cats.effect.IO
import scala.util.Try
import scala.util.Failure
import scala.util.Success

object IOErrorHandling {
  
  // IO: pure, delay, defer
  // create failed effects
  val aFailedCompute: IO[Int] = IO(throw new RuntimeException("A failure"))
  // because of IO, the throw clause is not thrown directly
  // but only be thrown when unsafeRun is called

  val aFailure: IO[Int] = IO.raiseError( new RuntimeException("A proper fail")) // recommended

  val dealWithIt = aFailure.handleErrorWith{
    case _: Throwable => IO(println("I'm still here"))
    // add more case
  }

  // turn into an Either
  val effectsAsEither: IO[Either[Throwable, Int]] = aFailure.attempt // attempt transform this effect to another effect
  // redeem: transform the failure  and the sucess in one go
  val resultAsString: IO[String] = aFailure.redeem(ex => s"Fail: $ex", value => s"Success: $value")
  // redeemWith (like a flatmap)
  val resultAsEffect: IO[Unit] = aFailure.redeemWith(ex => IO(println(s"Fail: $ex")), value => IO(println(s"Success: $value")))
  
  def main(args: Array[String]): Unit = {
    import cats.effect.unsafe.implicits.global

    aFailedCompute // throw nothing
    // aFailedCompute.unsafeRunSync()

    // aFailure.unsafeRunSync()

    dealWithIt.unsafeRunSync() // no exception will be thrown, only the effect println(I'm still here) is executed

    println(resultAsString.unsafeRunSync())
    resultAsEffect.unsafeRunSync()
  }
}

object Exercise3 {
  /**
   * Exercises
   */
  // 1 - construct potentially failed IOs from standard data types (Option, Try, Either)
  def option2IO[A](option: Option[A])(ifEmpty: Throwable): IO[A] = // fromOption
    option match
      case None => IO.raiseError(ifEmpty)
      case Some(value) => IO(value) // or IO.pure(value)
    
  def try2IO[A](aTry: Try[A]): IO[A] = // fromTry
    aTry match
      case Failure(exception) => IO.raiseError(exception)
      case Success(value) => IO(value)
    
  def either2IO[A](anEither: Either[Throwable, A]): IO[A] = // fromEither
    anEither match
      case Left(value) => IO.raiseError(value)
      case Right(value) => IO(value)
    
  // 2 - handleError, handleErrorWith
  def handleIOError[A](io: IO[A])(handler: Throwable => A): IO[A] =
    io.redeem(exc => handler(exc), suc => suc)
  // io.redeem(handler, identity)

  def handleIOErrorWith[A](io: IO[A])(handler: Throwable => IO[A]): IO[A] =
    io.redeemWith(exc => handler(exc), suc => IO.pure(suc))
  // io.redeemWith(handler, IO.pure)
}
