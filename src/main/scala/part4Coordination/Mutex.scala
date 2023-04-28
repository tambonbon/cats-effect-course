package part4Coordination

import cats.effect.IO
import utils.debugExt
import scala.concurrent.duration._
import cats.effect.IOApp
import scala.util.Random
import cats.syntax.parallel._
import scala.collection.immutable.Queue
import cats.effect.kernel.Deferred
import cats.effect.kernel.Ref

abstract class Mutex {
  def acquire: IO[Unit]
  def release: IO[Unit]
}


object Mutex {

  type Signal = Deferred[IO, Unit]
  case class State(locked: Boolean, waiting: Queue[Signal]) // Queue of all the threads that are waiting
  
  def createSignal(): IO[Signal] = Deferred[IO, Unit]

  def create: IO[Mutex] = 
    Ref[IO].of(State(locked = false, Queue())) // IO[Ref[IO, State]]
      .map { state =>
        new Mutex {
            /*
            Change the state of the Ref:
            - if the mutex is currently unlocked, state becomes (true, [])
            - if the mutex is locked, state becomes (true, queue + new signal) AND WAIT ON THAT SIGNAL.
          */
          override def acquire: IO[Unit] = createSignal().flatMap { signal =>
            state.modify { // modify will result in the 2nd one in tuple
              case State(false, queue) => (State(locked = true, Queue()), IO.unit)
              case State(true, queue) => (State(locked = true, waiting = queue.enqueue(signal)), signal.get)
            }.flatten // modify -> IO[B], our B is IO[Unit], so modify -> IO[IO[Unit]], we need to flatten
          }
            /*
            Change the state of the Ref:
            - if the mutex is unlocked, leave the state unchanged
            - if the mutex is locked,
              - if the queue is empty, unlock the mutex, i.e. state becomes (false, [])
              - if the queue is not empty, take a signal out of the queue and complete it (thereby unblocking a fiber waiting on it)
          */
          override def release: IO[Unit] = state.modify { // dont need to create a new signal
            case State(false, queue) => (State(locked = false, Queue()), IO.unit)
            case State(true, queue) => 
              if (queue.isEmpty) State(locked = false, Queue()) -> IO.unit // same with (unlocked, IO.unit)
              else {
                val (signal, rest) = queue.dequeue
                State(locked = true, rest) -> signal.complete(()).void
              }
          }.flatten
        }
      }
}

object MutexPlayground extends IOApp.Simple {
  def criticalTask(): IO[Int] = IO.sleep(1.second) >> IO(Random.nextInt(100))

  def createNonLockingTask(id: Int): IO[Int] = for {
    _ <- IO(s"[task $id] waiting for permission").debugExt
    _ <- IO(s"[task $id] working").debugExt
    res <- criticalTask()
    _ <- IO(s"[task $id] got result: $res").debugExt
  } yield res

  def demoNonLockingTasks() = (1 to 5).toList.parTraverse(id => createNonLockingTask(id)) //TODO: what parTraverse

  // now imagine, the tasks start at same time, but they need to wait for another until 1 task is completed by acquiring a mutex
  def createLockingTask(id: Int, mutex: Mutex): IO[Int] = for {
    _ <- IO(s"[task $id] waiting for permission").debugExt //this will start at the same time
    _ <- mutex.acquire // blocks if the mutext has been acquired by some other fiber
    // critical section
    _ <- IO(s"[task $id] working").debugExt // 1 per 1 second
    res <- criticalTask()
    _ <- IO(s"[task $id] got result: $res").debugExt //1 per 1 sec
    // critical section end
    _ <- mutex.release
    _ <- IO(s"[task $id] lock removed").debugExt // 1 per 1 sec
  } yield res

  def demoLockingTasks() = for {
    mutex <- Mutex.create
    tasks <- (1 to 5).toList.parTraverse(id => createLockingTask(id, mutex))
  } yield tasks
  // only one task will proceed one at a time




  override def run: IO[Unit] = 
    demoNonLockingTasks().debugExt.void // working in parallel at the same time
    // [io-compute-1] [task 1] waiting for permission
    // [io-compute-5] [task 3] waiting for permission
    // [io-compute-3] [task 2] waiting for permission
    // [io-compute-1] [task 1] working
    // [io-compute-3] [task 2] working
    // [io-compute-5] [task 3] working
    // [io-compute-7] [task 4] waiting for permission
    // [io-compute-7] [task 4] working
    // [io-compute-2] [task 5] waiting for permission
    // [io-compute-2] [task 5] working ###### it stops here for a while
    // [io-compute-3] [task 5] got result: 35
    // [io-compute-12] [task 1] got result: 63
    // [io-compute-11] [task 2] got result: 72
    // [io-compute-5] [task 4] got result: 26
    // [io-compute-6] [task 3] got result: 42
    // [io-compute-1] List(63, 72, 42, 26, 35)

    demoLockingTasks().debugExt.void
}
