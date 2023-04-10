package part2EffectsAndIO

import cats.effect.IOApp
import cats.effect.IO
import scala.concurrent.Future
import scala.util.Random
import cats.Traverse

object IOTraversal extends IOApp.Simple{
  import concurrent.ExecutionContext.Implicits.global

  def heavyComputation(string: String): Future[Int] = Future {
    Thread.sleep(Random.nextInt(1000))
    string.split(" ").length
  }

  val workload: List[String] = List("I quite like CE", "Scala is greate", "looking forward to some awesone stuff")
  val futures: List[Future[Int]] = workload.map(heavyComputation)
  // Future[List[Int]] would be hard to obtain

  // traverse
  import cats.instances.list._
  val listTraverse = Traverse[List]
  val singleeFuture: Future[List[Int]] = listTraverse.traverse(workload)(heavyComputation) // take a function that turns string into a future int
  
  override def run: IO[Unit] = ???
}
