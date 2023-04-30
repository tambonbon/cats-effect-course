package part4Coordination

import cats.effect.IOApp
import cats.effect.IO
import cats.effect.std.CountDownLatch
import utils.debugExt
import scala.concurrent.duration._
import cats.syntax.parallel._
import cats.effect.kernel.Resource
import java.io.FileWriter
import java.io.File
import scala.io.Source
import cats.syntax.traverse._
import scala.util.Random

/**
  * Coundown Latch: a coordination primitive that is initialized with a Count, and 2 methods: await, and release
  * All threads call `await` on the CDLatch are blocked
  * When the internal count of the latch reaches 0 via `release` from other threads, all waiting threads are unblocked
  */
object CountdownLatches extends IOApp.Simple{
  // use-case: when want to block a number of fibers, until some other fibers leave the `go` signal

  // announcer is the announcement of the race
  def announcer(latch: CountDownLatch[IO]): IO[Unit] = for {
    _ <- IO("Starting race shortly...").debugExt >> IO.sleep(2.seconds)
    _ <- IO("5...").debugExt >> IO.sleep(1.second)
    _ <- latch.release
    _ <- IO("4...").debugExt >> IO.sleep(1.second)
    _ <- latch.release
    _ <- IO("3...").debugExt >> IO.sleep(1.second)
    _ <- latch.release
    _ <- IO("2...").debugExt >> IO.sleep(1.second)
    _ <- latch.release
    _ <- IO("1...").debugExt >> IO.sleep(1.second)
    _ <- latch.release
    _ <- IO("GO GO GO!").debugExt
  } yield ()
  
  def createRunner(id: Int, latch: CountDownLatch[IO]): IO[Unit] = for {
    _ <- IO(s"[runner $id] waiting for signal...").debugExt
    _ <- latch.await // block this fiber until the count reaches 0
    _ <- IO(s"[runner $id] RUNNING!...").debugExt
  } yield ()

  def sprint(): IO[Unit] = for {
    latch <- CountDownLatch[IO](5)
    announcerFiber <- announcer(latch).start
    _ <- (1 to 3).toList.parTraverse(id => createRunner(id, latch))
    _ <- announcerFiber.join
  } yield ()

  override def run: IO[Unit] = 
    sprint()
    // [io-compute-11] Starting race shortly...
    // ### all the runners line up at the same time
    // [io-compute-6] [runner 1] waiting for signal...
    // [io-compute-1] [runner 2] waiting for signal...
    // [io-compute-5] [runner 3] waiting for signal...
    // [io-compute-11] 5...
    // [io-compute-4] 4...
    // [io-compute-7] 3...
    // [io-compute-5] 2...
    // [io-compute-7] 1...
    // [io-compute-12] GO GO GO!
    // ### all the runners run at the same time
    // [io-compute-9] [runner 1] RUNNING!...
    // [io-compute-12] [runner 2] RUNNING!...
    // [io-compute-9] [runner 3] RUNNING!...
    // [cats-effect-course]                        
}

object CountdownLatchesExercise extends IOApp.Simple {

  // exercise 1: simulate a multi-threading file downloader on multiple threads (servers)
  // we want to fetch all the chunks in parallel (in different fibers)
  // when all chunks finish downloading, we'll create another effect that stick all chunks into 1 single file
  // so we need a countdownlatch to do it
  object FileServer {
    val fileChunksList = List(
      "I love scala.",
      "Cats Effect seems quite fun.",
      "Never would I have thought I would do low-level concurrency with pure FP."
    )

    def getNumChunks: IO[Int] = IO(fileChunksList.length)
    def getFileChunk(n: Int): IO[String] = IO(fileChunksList(n))
  }

  def writeToFile(path: String, contents: String): IO[Unit] = {
    val fileResource = Resource.make(IO(new FileWriter(new File(path))))(writer => IO(writer.close()))
    fileResource.use { writer =>
      IO(writer.write(contents))  
    }
  }

  def appendFileContents(frompath: String, toPath: String): IO[Unit] = {
    val compositeResource = for {
      reader <- Resource.make(IO(Source.fromFile(frompath)))(source => IO(source.close()))
      writer <- Resource.make(IO(new FileWriter(new File(toPath), true)))(writer => IO(writer.close()))
    } yield (reader, writer)

    compositeResource.use {
      case (reader, writer) => IO(reader.getLines().foreach(writer.write))
    }
  }

  def createFileDownloaderTask(id: Int, latch: CountDownLatch[IO], filename: String, destFolder: String): IO[Unit] = for {
    _ <- IO(s"[task $id] downloadijng chunk...").debugExt
    _ <- IO.sleep((Random.nextDouble * 1000).toInt.millis)
    chunk <- FileServer.getFileChunk(id) // fetch chunk to the server
    _ <- writeToFile(s"$destFolder/$filename.part$id", chunk)
    _ <- IO(s"[task $id] chunk download complete...").debugExt
    _ <- latch.release // FINALLY, after our little task finishes downloading, it will release the latch, decrease the count by 1
    //
  } yield ()
  def downloadFile(filename: String, destFolder: String): IO[Unit] = for {
    n <- FileServer.getNumChunks
    latch <- CountDownLatch[IO](n)
    _ <- IO(s"Download started on $n fibers").debugExt
    _ <- (0 until n).toList.parTraverse(id => createFileDownloaderTask(id, latch, filename, destFolder))
    _ <- latch.await // the core of the program
    _ <- (0 until n).toList.traverse(id => appendFileContents(s"$destFolder/$filename.part$id", s"$destFolder/$filename"))
  } yield ()
  override def run: IO[Unit] = ???
}
