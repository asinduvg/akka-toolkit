package part2actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}


object ChangingActorBehavior extends App {

  object FussyKid {
    case object KidAccept

    case object KidReject

    val HAPPY = "happy"
    val SAD = "sad"
  }

  class FussyKid extends Actor {

    import FussyKid._
    import Mom._

    // internal state of the kid
    var state: String = HAPPY

    override def receive: Receive = {
      case Food(VEGETABLE) => state = SAD
      case Food(CHOCOLATE) => state = HAPPY
      case Ask(_) =>
        if (state == HAPPY) sender() ! KidAccept
        else sender() ! KidReject
    }
  }

  class StatelessFussyKid extends Actor {

    import FussyKid._
    import Mom._

    override def receive: Receive = happyReceive

    def happyReceive: Receive = {
      case Food(VEGETABLE) => context.become(sadReceive, false) // change my receive handler to sadReceive
      case Food(CHOCOLATE) =>
      case Ask(_) => sender() ! KidAccept
    }

    def sadReceive: Receive = {
      case Food(VEGETABLE) => context.become(sadReceive, false)
      case Food(CHOCOLATE) => context.unbecome()
      case Ask(_) => sender() ! KidReject
    }
  }

  object Mom {
    case class MomStart(kidRef: ActorRef)

    case class Food(food: String)

    case class Ask(message: String) // do you want to play?

    val VEGETABLE = "veggies"
    val CHOCOLATE = "chocolate"
  }

  class Mom extends Actor {

    import Mom._
    import FussyKid._

    override def receive: Receive = {
      case MomStart(kidRef) =>
        // test our interaction
        kidRef ! Food(VEGETABLE)
        kidRef ! Food(VEGETABLE)
        kidRef ! Food(CHOCOLATE)
        kidRef ! Food(CHOCOLATE)
        kidRef ! Ask("do you want to play?")
      case KidAccept => println("Yay, my kid is happy")
      case KidReject => println("My kid is sad, but he's healthy")
    }
  }

  val system = ActorSystem("changingActorBehaviorDemo")
  val fussyKid = system.actorOf(Props[FussyKid], "fussyKid")
  val statelessFussyKid = system.actorOf(Props[StatelessFussyKid], "statelessFussyKid")
  val mom = system.actorOf(Props[Mom], "mom")

  import Mom.MomStart

  mom ! MomStart(statelessFussyKid)

  /*
    with false parameter
    Food(veg) -> stack.push(sadReceive)
    Food(choc) -> stack.push(happyReceive)

    Stack:
      1. happyReceive
      2. sadReceive
      3. happyReceive
   */

  object CounterActor {
    case object Increment

    case object Decrement

    case object Print
  }

  class CounterActor extends Actor {

    import CounterActor._

    //    val basicAmount = 0

    //    override def receive: Receive = incrementer(basicAmount)
    //
    //    def incrementer(amount: Int): Receive = {
    //      case Increment => context.become(incrementer(amount + 1))
    //      case Decrement => context.become(decrementer(amount - 1))
    //      case Print => println(s"[Counter Actor] amount is $amount")
    //    }
    //
    //    def decrementer(amount: Int = 0): Receive = {
    //      case Increment => context.become(incrementer(amount + 1))
    //      case Decrement => context.become(decrementer(amount - 1))
    //      case Print => println(s"[Counter Actor] amount is $amount")
    //    }

    override def receive: Receive = countReceive(0)

    def countReceive(currentCount: Int): Receive = {
      case Increment => context.become(countReceive(currentCount + 1))
      case Decrement => context.become(countReceive(currentCount - 1))
      case Print => println(s"[Counter] My current count is $currentCount")
    }

  }

  val counterActor = system.actorOf(Props[CounterActor], "counterActor")

  import CounterActor._

  counterActor ! Increment
  counterActor ! Increment
  counterActor ! Decrement
  counterActor ! Print

  /*
    EXERCISE - a simplified voting system

   */

  case class Vote(candidate: String)

  case object VoteStatusRequest

  case class VoteStatusReply(candidate: Option[String])

  class Citizen extends Actor {

    override def receive: Receive = notVoted

    def notVoted: Receive = {
      case Vote(candidate) => {
        context.become(voted(candidate), true)
      }
      case VoteStatusRequest => sender() ! VoteStatusReply(None)
    }

    def voted(candidate: String): Receive = {
      case VoteStatusRequest => sender() ! VoteStatusReply(Some(candidate))
      case _ =>
    }

  }

  case class AggregateVotes(citizens: Set[ActorRef])

  class VoteAggregator extends Actor {
    override def receive: Receive = aggregateVotes(Map(), Set())

    def aggregateVotes(votingState: Map[String, Int], remainCitizens: Set[ActorRef]): Receive = {
      case AggregateVotes(citizens) =>
        citizens.foreach(citizen => citizen ! VoteStatusRequest)
        context.become(aggregateVotes(votingState, citizens), true)
      case VoteStatusReply(None) => sender() ! VoteStatusRequest // requesting until vote. Might lead to an infinite loop
      case VoteStatusReply(Some(candidate)) =>
        val newRemainingCitizens = remainCitizens - sender()
        val currentVotesOfCandidate = votingState getOrElse(candidate, 0)
        val newVotingState = votingState + (candidate -> (currentVotesOfCandidate + 1))
        context.become(aggregateVotes(newVotingState, newRemainingCitizens), true)
        if (newRemainingCitizens.isEmpty) {
          println(newVotingState)
        }
    }
  }

  val alice = system.actorOf(Props[Citizen])
  val bob = system.actorOf(Props[Citizen])
  val charlie = system.actorOf(Props[Citizen])
  val daniel = system.actorOf(Props[Citizen])

  alice ! Vote("Martin")
  //  alice ! Vote("James")
  bob ! Vote("Jonas")
  charlie ! Vote("Roland")
  daniel ! Vote("Roland")

  val voteAggregator = system.actorOf(Props[VoteAggregator])

  voteAggregator ! AggregateVotes(Set(alice, bob, charlie, daniel))

  /*
    Print status of votes
    Martin -> 1
    Jonas -> 1
    Roland -> 2

   */

}
