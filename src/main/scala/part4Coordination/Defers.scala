package part4Coordination

import cats.effect.IOApp
import cats.effect.IO
import cats.effect.kernel.Deferred
import utils.debugExt
import scala.concurrent.duration._
import cats.effect.kernel.Ref
import cats.syntax.traverse._

/**
  * Defer: A concurenncy primitive, for WAITING for an IO (an effect), while some other effects
  * are completed by other fibers.
  * it's like Promises in FP way
  */
object Defers extends IOApp.Simple{

  val aDeferred: IO[Deferred[IO, Int]] = Deferred[IO, Int]
  val aDeferred_v2: IO[Deferred[IO, Int]] = IO.deferred[Int]

  // get will block the other fiber semantically (managed by CE),
  // until some other fiber completes the Deferred with a value
  val reader: IO[Int] = aDeferred.flatMap { deferred => 
    deferred.get // this effect that will be eval. in another fiber, will block the fiber
    // block is semantic, so they're rescheduled by CE Runtime
    // the block is until a `deferred` instance will actually be completed by some other fiber with some value
    // and then `deferred` is completed, this blocked fiber `deferred.get` will fetch anything in the fiber
  }
  val writer = aDeferred.flatMap { signal =>
    signal.complete(42)  
  } // if we run these 2 effects (reader, writer) on different threads/fibers..
  // then the 1st fiber on reader will wait, and 2nd fiber when it completes with value 42
  // then it'll unblock the 1st fiber, and that fiber will be free to continue with value injected there

  // example: 1 time producer-consumer problem
  def demoDeferred(): IO[Unit] = { 
    def consumer(signal: Deferred[IO, Int]) = for {
      _ <- IO(s"[consumer] waiting for result...").debugExt
      meaningOfLife <- signal.get // blocker; blocks until meaningOflife is obtained and injected by some other fibers
      _ <- IO(s"[consumer] got the result: $meaningOfLife").debugExt
    } yield ()

    def producer(signal: Deferred[IO, Int]) = for {
      _ <- IO(s"[producer] crunching numbers...").debugExt
      _ <- IO.sleep(1.seconds)
      _ <- IO("[producer] complete: 42").debugExt
      meaningOfLife <- IO(42)
      _ <- signal.complete(meaningOfLife)
    } yield ()

    for {
      signal <- Deferred[IO,Int] // signal is a deferred instance
      fibConsumer <- consumer(signal).start
      fibProducer <- producer(signal).start
      _ <- fibProducer.join
      _ <- fibConsumer.join
    } yield ()
  }

  // example 2: simulate downloading some content
  val fileParts = List("I", "love S", "cala", " with Cat", "s Effect!<EOF>")

  // we want to have 2 IO on different fibers
  // 1 will wait for `fileParts` to piece together, until it gets to <EOF>, then it signals the download is complete
  // 1 will take each piece of content, and download every second
  def fileNotifierWithRef(): IO[Unit] = {

    def downloadFile(contentRef: Ref[IO, String]): IO[Unit] = 
      fileParts.map { parts => 
        IO(s"[downloader] got '$parts'").debugExt >> IO.sleep(1.second) >> contentRef.update(_ + parts)
      } // we have a sequence of IOs: List[IO[Unit]]
      .sequence // switch to IO[List[Unit]]
      .void // IO[Unit]
    
    def notifyFileComplete(contentRef: Ref[IO, String]): IO[Unit] = for {
      file <- contentRef.get
      _ <- if (file.endsWith("<EOF>")) IO("[notifer] File download complete").debugExt
            else IO("[notifier] downloading...").debugExt >> IO.sleep(500.millis) >> notifyFileComplete(contentRef) // a busy wait! (flooded with downloading streams if no sleep)
    } yield ()

    for {
      contentRef <- Ref[IO].of("")
      fibDownloader <- downloadFile(contentRef).start
      notifier <- notifyFileComplete(contentRef).start
      _ <- fibDownloader.join
      _ <- notifier.join
    } yield ()
  }

  // deferred works miracles for waiting
  def fileNotofiferWithRefAndDeferred(): IO[Unit] = {

    def notifyFileComplete(signal: Deferred[IO, String]): IO[Unit] = for {
      _ <- IO("[notifier] downloading...").debugExt
      _ <- signal.get // blocks until the signal is complete
      _ <- IO("[notifer] File download complete").debugExt
    } yield ()

    def fileDownloader(parts: String, contentRef: Ref[IO, String], signal: Deferred[IO, String]): IO[Unit] = for {
      _ <- IO(s"[downloader] got '$parts'").debugExt // >> IO.sleep(1.second) >> contentRef.update(_ + parts) [equivalent]
      _ <- IO.sleep(1.second)
      latest <- contentRef.updateAndGet(_ + parts)
      _ <- if (latest.contains("<EOF>")) // move the checking to download
        signal.complete(latest) // send the signal to the waiting thread
          else IO.unit
    } yield ()

    for {
      content <- Ref[IO].of("")
      signal <- Deferred[IO, String]
      notifierFib <- notifyFileComplete(signal = signal).start
      fileTasksFib <- fileParts.map(part => fileDownloader(part, content, signal)).sequence.start
      _ <- notifierFib.join
      _ <- fileTasksFib.join
    } yield ()
  }
  override def run: IO[Unit] =
    demoDeferred()
    // [io-compute-7] [consumer] waiting for result...
    // [io-compute-6] [producer] crunching numbers...
    // [io-compute-3] [producer] complete: 42
    // [io-compute-9] [consumer] got the result: 42 // the consumer is blocked until producer completes with a signal

    fileNotifierWithRef()

    fileNotofiferWithRefAndDeferred()
}

object DeferredExerices extends IOApp.Simple {

  /**
   *  Exercises:
   *  - (medium) write a small alarm notification with two simultaneous IOs
   *    - one that increments a counter every second (a clock)
   *    - one that waits for the counter to become 10, then prints a message "time's up!"
  */
  def alarmNotification(): IO[Unit] = {

    def notification(signal: Deferred[IO, Unit]): IO[Unit] = for {
      _ <- IO(s"[notification] start to wait...").debugExt
      _ <- signal.get
      _ <- IO(s"[notification] time's up")
    } yield ()

    def counter(counterRef: Ref[IO, Int], signal: Deferred[IO, Unit]): IO[Unit] = for {
      _ <- IO.sleep(1.second)
      result <- counterRef.updateAndGet(_ + 1)
      _ <- IO(result).debugExt
      _ <- if (result >= 10) signal.complete(()) else counter(counterRef = counterRef, signal = signal )
    } yield ()

    for {
      alarm <- Ref[IO].of(0)
      signal <- Deferred[IO, Unit]
      notificationFib <- notification(signal).start
      counterFib <- counter(alarm, signal).start
      _ <- notificationFib.join
      _ <- counterFib.join
    } yield ()
  }

  override def run: IO[Unit] = ???
}
