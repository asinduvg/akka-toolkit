package part1recap

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util._

object MultiThreadingRecap extends App {

  // creating threads on JVM!

  val aThread = new Thread(() => println("I'm running in parallel"))
  aThread.start()
  aThread.join()

  val threadHello = new Thread(() => (1 to 1000).foreach(_ => println("Hello")))
  val threadGoodBye = new Thread(() => (1 to 1000).foreach(_ => println("good bye")))

  threadHello.start()
  threadGoodBye.start()

  // different runs produce different results

  class BankAccount(@volatile private var amount: Int) {
    override def toString: String = "" + amount

    def withdraw(money: Int): Unit = this.amount -= money

    def safeWithdraw(money: Int): Unit = this.synchronized {
      this.amount -= money
    }

  }

  // inter-thread communication on JVM
  // wait - notify mechanism

  val future = Future {
    // long computation on a different thread
    42
  }

  // callbacks
  future.onComplete {
    case Success(42) => println("I found the meaning of life")
    case Failure(_) => println("Something happened with the meaning of life")
  }

  val aProcessedFuture = future.map(_ + 1) // Future with 43
  val aFlatFuture = future.flatMap { value =>
    Future(value + 2)
  } // Future with 44
  val filteredFuture = future.filter(_ % 2 == 0)

  // for comprehensions
  val aNonsenseFuture = for {
    meaningOfLife <- future
    filteredMeaning <- filteredFuture
  } yield meaningOfLife + filteredMeaning

  // andThen, recover/recoverWith



}
