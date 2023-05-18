package part4Coordination

import utils.debugExt
import scala.concurrent.duration._
import cats.effect.IOApp
import cats.effect.IO

/**
 * Cyclic barrier is a coordination primitive that
 * - is initialized with a count
 * - has a single method: await
 * 
 * A Cyclic Barrier will block all the fibers calling that `await` method, until we have exactly N fibers waiting
 * then the barrier will unblock all fibers and reset to original state.
 * Any further fibers will again block until we have exactly N fibers waiting.
 */
object CyclicBarriers extends IOApp.Simple {
  
  
  override def run: IO[Unit] = ???
}
