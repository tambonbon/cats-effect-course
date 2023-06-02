package part3Concurrency

import cats.effect.IOApp
import cats.effect.IO
import utils.debugExt
import scala.concurrent.duration._
import part3Concurrency.Cancelling.authFlow

object Cancelling extends IOApp.Simple {
  /*
   To cancel IOs
   - fib.cancel
   - IO.race & other APIs
   - manual cancellation
   */

  val chainOfIOs: IO[Int] = IO("waiting").debugExt >> IO.canceled >> IO(42).debugExt // what after IO.canceled is not evaluated
  // IO[Int] bc it's the last thing

  // uncancellable
  // example: online store, payment processor
  // never takes more than 1 sec to process, should never be cancelled
  // payment process must NOT be canceled
  val specialPaymentSystem: IO[String] = 
    (
      IO("Payment running, don't cancel me").debugExt >>
      IO.sleep(1.second) >>
      IO("Payment completed").debugExt
    ).onCancel(IO("MEGA CANCEL OF DOOM").debugExt.void) // we don't want cancel, but still want a mechanism to prevent cancelation

  val cancellationOfDoom = for {
    fiber <- specialPaymentSystem.start
    _ <- IO.sleep(500.millis) >> fiber.cancel
    _ <- fiber.join
  } yield ()

  val atomicPayment = IO.uncancelable(_ => specialPaymentSystem) // masking
  val atomicPayment_v2 = specialPaymentSystem.uncancelable // same

  val noCancellationOfDoom = for {
    fiber <- atomicPayment_v2.start
    _ <- IO.sleep(500.millis) >> IO("attempting cancellation..").debugExt >> fiber.cancel
    _ <- fiber.join
  } yield ()

  /*
    The uncancelable API is more complex and more general.
    It takes a function from Poll[IO] to IO. In the example above, we aren't using that Poll instance.
    The Poll object can be used to mark sections within the returned effect which CAN BE CANCELED.
   */

  /*
    Example: authentication service. Has two parts:
    - input password, can be cancelled, because otherwise we might block indefinitely on user input
    - verify password, CANNOT be cancelled once it's started
   */
  val inputPassword = IO("Input password:").debugExt >> IO("(typing password)").debugExt >> IO.sleep(3.seconds) >> IO("tambonbon")
  val verifyPassword = (pw: String) => IO("verifying...").debugExt >> IO.sleep(5.seconds) >> IO(pw == "tambonbon")

  val authFlow: IO[Unit] = IO.uncancelable { poll =>
    for {
      pw <- inputPassword.onCancel(IO("Authentication timed out.. Try again later").debugExt.void)
      verified <- verifyPassword(pw)
      _ <- if (verified) IO("Authentication successful.").debugExt else IO("Authentication failed").debugExt
    } yield ()  
  }

  // now we'll deliberately cancel
  val authProgram = for {
    authFiber <- authFlow.start
    _ <- IO.sleep(3.seconds) >> IO("Authencitation timeout, attempting cancel...").debugExt >> authFiber.cancel
    _ <- authFiber.join
  } yield ()

  val authFlowUsePoll: IO[Unit] = IO.uncancelable { poll =>
    for {
      pw <- poll(inputPassword).onCancel(IO("Authentication timed out.. Try again later").debugExt.void) // becomes cancelable
      // what is in poll --> cancellable
      // poll is opposite of uncancelable
      verified <- verifyPassword(pw) // not cancellable
      _ <- if (verified) IO("Authentication successful.").debugExt else IO("Authentication failed").debugExt // not cancellable
    } yield ()  
  }

  override def run: IO[Unit] = 
    cancellationOfDoom
    // [io-compute-9] Payment running, don't cancel me
    // [io-compute-4] MEGA CANCEL OF DOOM
    noCancellationOfDoom
    // [io-compute-10] Payment running, don't cancel me
    // [io-compute-8] attempting cancellation..
    // [io-compute-8] Payment completed

    authFlow
    // [io-compute-2] Input password:
    // [io-compute-2] (typing password)
    // [io-compute-5] verifying...  
    // [io-compute-5] Authentication successful.

    authProgram
    // [io-compute-6] Input password:
    // [io-compute-6] (typing password)
    // [io-compute-4] Authencitation timeout, attempting cancel...
    // [io-compute-8] verifying...
    // [io-compute-10] Authentication successful.

    authFlowUsePoll
    // [io-compute-9] Input password:
    // [io-compute-9] (typing password)
    // [io-compute-8] verifying...
    // [io-compute-2] Authentication successful.
}

object CancellingExercise extends IOApp.Simple {

  // 1
  val cancelBeforeMol = IO.canceled >> IO(42).debugExt
  val uncancelableMol = IO.uncancelable(_ => IO.canceled >> IO(42).debugExt)
  // uncancelable eliminates all cancel points, except poll


  // 2
  val invincibleAuthProgram = for {
    authFiber <- IO.uncancelable(_ => authFlow.start)
    _ <- IO.sleep(3.seconds) >> IO("Authencitation timeout, attempting cancel...").debugExt >> authFiber.cancel
    _ <- authFiber.join
  } yield ()
  override def run: IO[Unit] =
    uncancelableMol.void // IO(42)
}
