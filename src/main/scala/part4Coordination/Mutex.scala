package part4Coordination

import cats.effect.IO

abstract class Mutex {
  def acquire: IO[Unit]
  def release: IO[Unit]
}


object Mutex {
  def create: IO[Mutex] = ???
}
