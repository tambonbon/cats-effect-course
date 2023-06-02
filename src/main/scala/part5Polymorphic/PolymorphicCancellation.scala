package part5Polymorphic

import cats.effect.IOApp
import utils.debugExt
import cats.effect.IO
import cats.Applicative
import cats.Monad
import cats.effect.kernel.Poll
import cats.effect.kernel.MonadCancel
import cats.effect.kernel.Outcome.Succeeded
import cats.effect.kernel.Outcome.Errored
import cats.effect.kernel.Outcome.Canceled

object PolymorphicCancellation extends IOApp.Simple {
  
  // Remember that IO is a Monad
  trait MyApplicativeError[F[_], E] extends Applicative[F] {
    def raiseError[A](error: E): F[A] // F is an effect; to construct a side effect
    def handleErrorWith[A](fa: F[A])(f: E => F[A]): F[A]
  }

  trait MyMonadError[F[_], E] extends MyApplicativeError[F, E] with Monad[F]

  // MonadCancel

  trait MyPoll[F[_]] { // example for Poll
    def apply[A](fa: F[A]): F[A]
  }
  trait MyMonadCancel[F[_], E] extends MyMonadError[F, E] {
    def canceled: F[Unit]
    def uncancelable[A](poll: Poll[F] => F[A]): F[A] // think of Poll as a higher-kinded function type
  }

  // monadCancel for IO
  val monadCancelIO: MonadCancel[IO, Throwable] = MonadCancel[IO]

  // because MonadCancel is a MonadError, which is also a Monad..
  // .. MonadCancel allows us to create values
  val meaningOfLifeIO: IO[Int] = monadCancelIO.pure(42)
  val mapMeaningOfLifeIO: IO[Int] = monadCancelIO.map(meaningOfLifeIO)(_ * 10)

  // more important functionalities of MonadCancel
  val mustCompute = monadCancelIO.uncancelable { _ =>
    for {
      _ <- monadCancelIO.pure("once started, I can't go back")
      result <- monadCancelIO.pure(56)
    } yield result  
  }

  import cats.syntax.flatMap._
  import cats.syntax.functor._ // does not go for free
  def mustComputePolymorphism[F[_], E](using mc: MonadCancel[F, E]): F[Int] = mc.uncancelable { _ =>
    for {
      _ <- mc.pure("once started, I can't go back")
      result <- mc.pure(56)
    } yield result   
  }

  // we can type them with whatever effect that has MonadCancel in scope
  // --> we can generalize code
  val mustCompute_v2 = mustComputePolymorphism[IO, Throwable]


  // use case: allow cancellation listeners
  val mustComputeWithListener = mustCompute.onCancel(IO("I'm being cancelled!").void) // onCancel from IO API
  val mustComputeWithListener_v2 = monadCancelIO.onCancel(mustCompute, IO("I'm being cancelled!").void) // onCancel from monadCanCel API

  // alow finalizers: guarantee, guaranteeCase
  val aComputationWithFinalizers = monadCancelIO.guaranteeCase(IO(42)) {
    case Succeeded(fa) => fa.flatMap(a => IO(s"successful: $a").void)
    case Errored(e) => IO(s"failed: $e").void
    case Canceled() => IO("canceled").void
  }

  // bracket pattern is specific to MonadCancel
  val aComputationWithUsage = monadCancelIO.bracket(IO(42)){ value
     => IO(s"Using the meaning of Life: $value")
  } { value
     => IO("releasing the meaning of life...").void 
  }


  /**
   * Exercise: Generalize a piece of code
   */
  import utils.general.debugExt
  import scala.concurrent.duration._

  def unsafeSleep[F[_], E](duration: FiniteDuration)(using mc: MonadCancel[F, E]): F[Unit] = 
    mc.pure(Thread.sleep(duration.toMillis)) // not semantic blocking, not advisable

  def inputPassword[F[_], E](using mc: MonadCancel[F,E]): F[String] = for {
     _ <- mc.pure("Input password:").debugExt // mc is for any kinds of effects
     _ <- mc.pure("(typing password)").debugExt
     _ <- unsafeSleep[F, E](3.seconds) 
     password <- mc.pure("tambonbon")
  } yield password

  def verifyPassword[F[_], E](pw: String)(using mc: MonadCancel[F,E]): F[Boolean] = for {
    _ <- mc.pure("verifying...").debugExt
    _ <- unsafeSleep[F, E](5.seconds)
    result <- mc.pure(pw == "tambonbon")
  } yield result

  import cats.effect.syntax.all._
  def authFlow[F[_], E](using mc: MonadCancel[F,E]): F[Unit] = mc.uncancelable { poll =>
    for {
      pw <- poll(inputPassword).onCancel(mc.pure("Authentication timed out.. Try again later").debugExt.void)
      verified <- verifyPassword(pw)
      _ <- if (verified) mc.pure("Authentication successful.").debugExt else mc.pure("Authentication failed").debugExt
    } yield ()  
  }

  // now we'll deliberately cancel
  val authProgram: IO[Unit] = for {
    authFiber <- authFlow[IO, Throwable].start
    _ <- IO.sleep(3.seconds) >> IO("Authencitation timeout, attempting cancel...").debugExt >> authFiber.cancel
    _ <- authFiber.join
  } yield ()
  
  override def run: IO[Unit] = authProgram
}
