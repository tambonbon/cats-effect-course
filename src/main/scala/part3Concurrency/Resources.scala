package part3Concurrency

import cats.effect.IOApp
import cats.effect.IO
import part3Concurrency.RunningThingsOnOtherThreads.debugExt
import scala.concurrent.duration._
import java.util.Scanner
import java.io.FileReader
import java.io.File
import cats.effect.kernel.Resource
import cats.effect.kernel.Outcome.Succeeded
import cats.effect.kernel.Outcome.Errored
import cats.effect.kernel.Outcome.Canceled

/**
  * Problem: leaking resources, when fiber stops, but resource is open
  *   1st sln: check for cancellation/handle errors (onCancel)
  *   - error prone
  *   - tedious with complex resources
  *   2nd sln: bracket 
  * Bracket pattern: someIO.bracket(useResouceCb)(releaseResourceCb)
  *   the 2nd arg is run no matter what
  * Bracket is equivalent to try-catches (pure FP)
  */
object Resources extends IOApp.Simple {
  
  // imagine an use-case: manage a connection lifecycle, like a server
  class Connection(url: String) {
    def open(): IO[String] = IO(s"Opening connection to $url").debugExt
    def close(): IO[String] = IO(s"Closing connection to $url").debugExt
  }
  
  val asyncFetchUrl = for {
    fib <- (new Connection("tambonbon.io").open() *> IO.sleep((Int.MaxValue).seconds)).start
    // the fiber above starts, even it cancels, but leaks resources, because it never closes
    _ <- IO.sleep(1.second) *> fib.cancel
  } yield ()

  val correctAsyncUrl = for {
    conn <- IO(new Connection("tambonbon.io"))
    fib <- (conn.open() *> IO.sleep((Int.MaxValue).seconds)).onCancel(conn.close().void).start
    _ <- IO.sleep(1.second) *> fib.cancel
  } yield ()

  /**
    *  CE invents bracket pattern
    */
  val bracketFetchUrl = IO(new Connection("tambonbon.io"))
    .bracket(conn => conn.`open`() *> IO.sleep(Int.MaxValue.seconds))(conn => conn.close().void)
  // ^^      return the result of that connection                     what you can do when you 
  //         create an effect, what you can do with that resource     release that connection

  val bracketProgram = for {
    fib <- bracketFetchUrl.start
    _ <- IO.sleep(1.second) *> fib.cancel
  } yield ()

  def openFileScanner(path: String): IO[Scanner] = 
    IO(new Scanner(new FileReader(new File(path))))

  def bracketReadFile(path: String): IO[Unit] = openFileScanner(path)
    .bracket(scanner => IO(scanner.nextLine()).debugExt *> IO.sleep(100.millis))(scanner => IO(scanner.close()))

  def bracketReadFile_sln(path: String): IO[Unit] =
  IO(s"opening file at $path") >>
    openFileScanner(path)
    .bracket { scanner => 
      def readLineByLine(): IO[Unit] = 
        if (scanner.hasNextLine) IO(scanner.nextLine()).debugExt >> IO.sleep(100.millis) >> readLineByLine()
        else IO.unit
      readLineByLine()
    }{ scanner => 
      IO(s"closing file at $path").debugExt >> IO(scanner.close())
    }

  /**
   * Resources
   */
  def connFromConfig(path: String): IO[Unit] = // now imagine we have 2 resources to open/close
    openFileScanner(path)
      .bracket{ scanner => // 1st resource, acquire function
        // acquaires a connection based on the file
        IO(new Connection(scanner.nextLine())).bracket{ conn => // 2nd resource, usage function
          conn.open().debugExt >> IO.never // IO.never, never returns
        }(conn => conn.close().debugExt.void)
      }(scanner => IO("closing file").debugExt >> IO(scanner.close))
  // Not so readable code anymore....

  val connectionResource = Resource.make(IO(new Connection("tambonbon.io")))(conn => conn.close().debugExt.void)
  // only acquired function and release function, usage function can be called later
  val resourceFetchUrl = for { // THIS IS NICE
    fib <- connectionResource.use(conn => conn.open() >> IO.never).start
    _ <- IO.sleep(1.second) >> fib.cancel
  } yield ()

  // resources are equivalent to brackets
  val simpleResource = IO("some resource")
  val usingResource: String => IO[String] = string => IO(s"Using thr string: $string").debugExt
  val releaseResource: String => IO[Unit] = string => IO(s"Finalise thr string: $string").debugExt.void

  val usingResourceWithBracket = simpleResource.bracket(usingResource)(releaseResource)
  val usingResourceWithResource = Resource.make(simpleResource)(releaseResource)//.use(usingResource)

  def getResourceFromFile(path: String): Resource[IO, Scanner] = Resource.make(openFileScanner(path)) { scanner =>
    IO(s"Closing file at $path").debugExt >> IO(scanner.close)
  }

  def resourceReadFile(path: String) = 
    IO(s"Opening file at $path") >> getResourceFromFile(path).use{ scanner => 
    def readLineByLine(): IO[Unit] = 
      if (scanner.hasNextLine) IO(scanner.nextLine()).debugExt >> IO.sleep(100.millis) >> readLineByLine()
      else IO.unit
    readLineByLine()
    }
  
  def cancelReadFile(path: String) = for {
    fiber <- resourceReadFile(path).start
    _ <- IO.sleep(2.seconds) >> fiber.cancel
  } yield ()

  // nested resources
  def connFromConfResource(path: String) = {
    Resource.make(openFileScanner(path))(scanner => IO("Closing file").debugExt >> IO(scanner.close()))
      .flatMap(scanner => Resource.make(IO(new Connection(scanner.nextLine())))(conn => conn.close().void))
      // will return the inner-most type
  }

  val openConnection = connFromConfResource("src/main/resources/connection.txt").use(conn => conn.open() >> IO.never)
  val canceledConnection = for {
    fiber <- openConnection.start
    _ <- IO.sleep(1.second) >> IO("cancelling").debugExt >> fiber.cancel
  } yield()

  // equivalent to connFromConfResource
  def connFromConfResourceClean(path: String) = for {
    scanner <- Resource.make(openFileScanner(path))(scanner => IO("Closing file").debugExt >> IO(scanner.close()))
    conn <-  Resource.make(IO(new Connection(scanner.nextLine())))(conn => conn.close().void)
  } yield ()

  // finalizers to regular IOs
  val ioWithFinalizer = IO("some resource").debugExt.guarantee((IO("freeing resource").debugExt.void))
  val ioWithFinalizer_v2 = IO("some resource").debugExt.guaranteeCase{
    case Succeeded(fa) => fa.flatMap(result => IO(s"releasing resource: $result").debugExt).void
    case Errored(e) => IO("nothing to release").debugExt.void
    case Canceled() => IO("resource got cancelled, releasing what's left").debugExt.void
  }

  override def run: IO[Unit] = 
    correctAsyncUrl

    bracketReadFile_sln("./src/main/scala/part1Recap/ContextualAbstraction.scala")
    // [io-compute-7] package part1Recap
    // [io-compute-10] 
    // [io-compute-9] object ContextualAbstraction {
    // [io-compute-10]   case class Person (name: String, age: Int)
    // [io-compute-3] 
    // [io-compute-1]   trait JSONSerializer[T] {
    // [io-compute-11]     def toJSON(value: T): String

    resourceReadFile("./src/main/scala/part1Recap/ContextualAbstraction.scala")
    // [io-compute-6] package part1Recap
    // [io-compute-11] 
    // [io-compute-4] object ContextualAbstraction {
    // [io-compute-9]   case class Person (name: String, age: Int)
    // [io-compute-9] 
    // [io-compute-4]   trait JSONSerializer[T] {
    // [io-compute-1]     def toJSON(value: T): String
    
    cancelReadFile("./src/main/scala/part1Recap/ContextualAbstraction.scala")
    // [io-compute-10] package part1Recap
    // [io-compute-4] 
    // [io-compute-3] object ContextualAbstraction {
    // [io-compute-4]   case class Person (name: String, age: Int)
    // [io-compute-6] 
    // [io-compute-6]   trait JSONSerializer[T] {
    // [io-compute-7]     def toJSON(value: T): String
    // ..... wait for 2 seconds .....
    // [io-compute-5] Closing file at ./src/main/scala/part1Recap/ContextualAbstraction.scala

    openConnection.void
    // [io-compute-2] Opening connection to tambonbon.io
    // [io-compute-2] Closing connection to tambonbon.io
    // [io-compute-2] Closing file
 
    canceledConnection
    // [io-compute-8] Opening connection to tambonbon.io
    // [io-compute-5] cancelling
    // [io-compute-9] Closing connection to tambonbon.io
    // [io-compute-9] Closing file
}
