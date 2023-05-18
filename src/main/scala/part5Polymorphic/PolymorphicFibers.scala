package part5Polymorphic

import cats.effect.IOApp
import cats.effect.IO
import cats.effect.kernel.MonadCancel
import cats.effect.kernel.Fiber

object PolymorphicFibers extends IOApp.Simple {

  // Spawn: A Type class generalizes the concept of fiber, that creates fibers for any effect
  trait MySpawn[F[_]] extends MonadCancel[F, Throwable] {
    def start[A](fa: F[A]): F[Fiber[F, Throwable, A]] // create a fiber
    def never[A]: F[A] // a forever suspending effect
    def cede: F[Unit] // a "yield effect "

  }

  val mol = IO(42)
  val fiber: IO[Fiber[IO, Throwable, Int]] = mol.start 



  override def run: IO[Unit] = ???
}
