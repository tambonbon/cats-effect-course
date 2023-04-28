package part4Coordination

import cats.effect.IOApp
import cats.effect.IO
import utils.debugExt
import scala.concurrent.duration._
import cats.effect.std.Semaphore
import scala.util.Random

/**
  * Semaphore: concurrency primitive that will only allow a certain amount of threads in some critical regions
  * Semaphore has an internal counter called Permit
  */
object Semaphores extends IOApp.Simple{

  val semaphore: IO[Semaphore[IO]] = Semaphore[IO](2) // 2 total permits

  // example: limiting the number of concurrent sessions on a server
  def doWorkWhileLoggedIn(): IO[Int] = IO.sleep(1.second) >> IO(Random.nextInt(100))

  def login(id: Int, sem: Semaphore[IO]): IO[Int] = for {
    _ <- IO(s"[session $id] waiting to log in").debugExt
    //this effect will attempt to acquire a permit on the semaphore
    _ <- sem.acquire
    // critical section
    _ <- IO(s"[session $id] logged in, working...").debugExt
    result <- doWorkWhileLoggedIn()
    _ <- IO(s"[session $id] done: $result, logging out...").debugExt
    // end of critical seciton
    _ <- sem.release
  } yield result

  def demoSemaphore() = for {
    sem <- Semaphore[IO](2)
    // now imagine 3 users trying to login, but because of 2 Semaphores, only 2 users can log in at the same time
    user1Fib <- login(1, sem).start
    user2Fib <- login(2, sem).start
    user3Fib <- login(3, sem).start
    _ <- user1Fib.join
    _ <- user2Fib.join
    _ <- user3Fib.join
  } yield ()

  override def run: IO[Unit] =
    demoSemaphore()
    // [io-compute-7] [session 2] waiting to log in
    // [io-compute-10] [session 1] waiting to log in
    // [io-compute-3] [session 3] waiting to log in
    // [io-compute-3] [session 3] logged in, working...
    // [io-compute-7] [session 2] logged in, working...
    // [io-compute-6] [session 3] done: 26, logging out... #### session 3 has to logout then session 1 can log in
    // [io-compute-12] [session 2] done: 85, logging out...
    // [io-compute-4] [session 1] logged in, working...
    // [io-compute-2] [session 1] done: 4, logging out...
}
