package part2EffectsAndIO

import cats.effect.IO
import scala.io.StdIn
import cats.effect.IOApp
import cats.effect.ExitCode
import part2EffectsAndIO.IOApps.program

object IOApps {
  val program = for {
    line <- IO(StdIn.readLine())
    _ <- IO(println("You've just written: $line"))
  } yield ()  
}

object TestApp {
  def main(args: Array[String]): Unit = {
    import cats.effect.unsafe.implicits.global
    program.unsafeRunSync() // old way to main
  }
}

object FirstCEApp extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = 
    program.map(_ => ExitCode.Success)
    // program.as(ExitCode.Success) // this is the Cats Effect main
}

object MySimpleApp extends IOApp.Simple {
  override def run: IO[Unit] = program // Most of the time we'll just return IO of Unit
}
