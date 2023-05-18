package part5Polymorphic

import cats.effect.IOApp
import utils.debugExt
import cats.effect.IO
import cats.Applicative
import cats.Monad

object PolymorphicCancellation extends IOApp.Simple {
  
  trait MyApplicativeError[F[_], E] extends Applicative[F] {
    def raiseError[A](error: E): F[A] // F is an effect
    def handleErrorWith[A](fa: F[A])(f: E => F[A]): F[A]
  }

  trait MyMonadError[F[_], E] extends MyApplicativeError[F, E] with Monad[F]


  override def run: IO[Unit] = ???
}
