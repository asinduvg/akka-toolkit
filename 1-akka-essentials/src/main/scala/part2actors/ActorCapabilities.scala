package part2actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import part2actors.ActorCapabilities.BankAccount.{Deposit, Withdraw}
import part2actors.ActorCapabilities.Person.LiveTheLife

import scala.util.{Failure, Success}

object ActorCapabilities extends App {

  class SimpleActor extends Actor {
    override def receive: Receive = {
      case "Hi!" => context.sender() ! "Hello there!" // replying to a message
      case message: String => println(s"[$self] I have received $message")
      case number: Int => println(s"[Simple Actor] I have received a NUMBER: $number")
      case SpecialMessage(contents) => println(s"[Simple Actor] I have received something special: $contents")
      case SendMessageToYourself(content) => self ! content
      case SayHiTo(ref) => ref ! "Hi!"
      case WirelessPhoneMessage(content, ref) => ref forward (content + "s") // keep the original sender of the Wireless Phone Message
    }
  }

  val system = ActorSystem("actorCapabilitiesDemo")
  val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")

  //  simpleActor ! "hello actor"

  // 1 - messages can be of any type
  // a) messages must be IMMUTABLE
  // b) messages must be SERIALIZABLE
  // in practice use case classes and case objects


  //  simpleActor ! 42

  case class SpecialMessage(contents: String)

  //  simpleActor ! SpecialMessage("Some special message")

  // 2 - actors have information about their context and about themselves
  // context.self === `this` in OOP

  case class SendMessageToYourself(content: String)

  //  simpleActor ! SendMessageToYourself("I am an actor and I am proud of it")

  // 3 - actors can REPLY to messages
  val alice = system.actorOf(Props[SimpleActor], "alice")
  val bob = system.actorOf(Props[SimpleActor], "bob")

  case class SayHiTo(ref: ActorRef)

  //  alice ! SayHiTo(bob)

  // 4 - dead letters
  //  alice ! "Hi!" // reply to 'me'

  // 5 - forwarding messages
  // D -> A -> B
  // forwarding = sending a message with the original sender

  case class WirelessPhoneMessage(content: String, ref: ActorRef)

  //  alice ! WirelessPhoneMessage("Hi", bob) // noSender

  /*
    Exercises
      1. a Counter actor
        - Increment
        - Decrement
        - Print

      2. a Bank account as an actor
         receives
          - Deposit an amount
          - Withdraw an amount
          - Statement
         replies with
          - Success
          - Failure

         interact with some other kind of actor

   */


  // DOMAIN of the CounterActor
  object CounterActor {
    case object Increment

    case object Decrement

    case object Print
  }

  class CounterActor extends Actor {

    import CounterActor._

    var amount = 0

    override def receive: Receive = {
      case Increment => amount += 1
      case Decrement => amount -= 1
      case Print => println(amount)
    }
  }

  val counterActor = system.actorOf(Props[CounterActor], "counterActor")

  //  counterActor ! CounterActor.Increment
  //  counterActor ! CounterActor.Print


  object BankAccount {
    case class Deposit(amount: Int)

    case class Withdraw(amount: Int)

    case class Statement()

    case class TransactionSuccess(message: String)

    case class TransactionFailure(reason: String)
  }


  class BankAccount extends Actor {

    import BankAccount._

    var accountBalance = 1000

    override def receive: Receive = {
      case Deposit(amount) =>
        if (amount < 0) sender() ! TransactionFailure("Invalid deposit amount")
        else {
          accountBalance += amount
          sender() ! TransactionSuccess("Successfully deposited amount")
        }
      case Withdraw(amount) =>
        if (amount < 0) sender() ! TransactionFailure("Invalid withdraw amount")
        else if (amount > accountBalance) sender() ! TransactionFailure("Account balance not enough")
        else {
          accountBalance -= amount
          sender() ! TransactionSuccess("Successfully withdraw amount")
        }
      case Statement() => sender() ! s"Your balance is $accountBalance"
    }
  }

  object Person {
    case class LiveTheLife(account: ActorRef)
  }

  class Person extends Actor {

    import Person._
    import BankAccount._

    override def receive: Receive = {
      case LiveTheLife(account) =>
        account ! Deposit(10000)
        account ! Withdraw(19000)
        account ! Withdraw(500)
        account ! Statement()
      case message => println(message.toString)
    }
  }

  val account = system.actorOf(Props[BankAccount], "bankAccount")
  val person = system.actorOf(Props[Person], "billionaire")

  person ! LiveTheLife(account)

}
