package part1recap

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object ThreadModelLimitations extends App {

  /*
  Daniel's rants
   */

  /**
   * DR #1: OOP encapsulation only valid in the SINGLE THREAD MODEL
   */

  class BankAccount(private var amount: Int) {
    override def toString: String = "" + amount

    def withdraw(money: Int): Unit = this.synchronized {
      this.amount -= money
    }

    def deposit(money: Int): Unit = this.synchronized {
      this.amount += money
    }

    def getAmount: Int = amount

  }

  val account = new BankAccount(2000)
  for (_ <- 1 to 1000) {
    new Thread(() => account.withdraw(1)).start()
  }

  for (_ <- 1 to 1000) {
    new Thread(() => account.deposit(1)).start()
  }

  for (_ <- 1 to 1000) {
    // wait until the processes end to print
  }

  println(account.getAmount)

  // OOP encapsulation broken in multithreaded env
  // synchronization! Locks the rescue

  // deadlocks, livelocks

  /**
   * DR #2: delegating something to a thread is a pain
   */

  // you have a running thread and you want to pass a runnable to that thread

  var task: Runnable = null

  var runningThread: Thread = new Thread(() => {
    while (true) {
      while (task == null) {
        runningThread.synchronized {
          println("[background] waiting for a task")
          runningThread.wait()
        }
      }

      task.synchronized {
        println("[background] I have a task")
        task.run()
        task = null
      }

    }
  })

  def delegateToBackgroundThread(r: Runnable): Unit = {
    if (task == null) task = r

    runningThread.synchronized {
      runningThread.notify()
    }
  }

  runningThread.start()
  Thread.sleep(1000)
  delegateToBackgroundThread(() => println(42))
  Thread.sleep(1000)
  delegateToBackgroundThread(() => println("this should run in the background"))

  /**
   * DR #3: tracing and dealing with errors in a multi threaded env is a Pain in neck
   */
  // 1M numbers between 10 threads
  val futures = (0 to 9)
    .map(i => 100000 * i until 100000 * (i + 1)) // 0 - 99999, 100000 - 199999, 200000 - 299999 etc
    .map(range => Future {
      if (range.contains(546735)) throw new RuntimeException("Invalid number!")
      range.sum
    })

  val sumFuture = Future.reduceLeft(futures)(_ + _) // Future with the sum of all numbers
  sumFuture.onComplete(println)

}