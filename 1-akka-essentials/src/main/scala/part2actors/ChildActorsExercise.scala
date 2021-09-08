package part2actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ChildActorsExercise extends App {
  // Distributed Word counting

  object WordCounterMaster {
    case class Initialize(nChildren: Int)

    case class WordCountTask(text: String, taskId: Int)

    case class WordCountReply(text: String, taskId: Int, count: Int)
  }

  class WordCounterMaster extends Actor {

    import WordCounterMaster._

    override def receive: Receive = {
      case Initialize(nChildren) =>
        val childrenList: List[ActorRef] =
          (for (i <- 1 to nChildren)
            yield context.actorOf(Props[WordCounterWorker], s"worker$i")).toList
        context.become(workerRouter(0, 0, childrenList, Map()), true)

    }

    def workerRouter(nextAvailWorker: Int, nextTaskId: Int, childrenList: List[ActorRef], requestMap: Map[Int, ActorRef]): Receive = {
      case message: String =>
        val originalSender = sender()
        childrenList(nextAvailWorker) ! WordCountTask(message, nextTaskId)
        val nextWorker = (nextAvailWorker + 1) % childrenList.length
        val newRequestMap = requestMap + (nextTaskId -> originalSender)
        context.become(workerRouter(nextWorker, nextTaskId + 1, childrenList, newRequestMap), true)
      case WordCountReply(text, taskId, count) =>
        val originalSender = requestMap(taskId)
        originalSender ! s"count for the $text: $taskId is $count"
        context.become(workerRouter(nextAvailWorker, nextTaskId, childrenList, requestMap - taskId))

    }

  }

  class WordCounterWorker extends Actor {

    import WordCounterMaster._

    override def receive: Receive = {
      case WordCountTask(text, taskId) =>
        sender() ! WordCountReply(text, taskId, text.split(" ").length)
      case _ =>
    }
  }

  /*
    create WordCounterMaster
    send Initialize(10) to WordCounterMaster
    send "Akka is awesome" to WordCounterMaster
      wcm will send a WordCountTask("...") to one of its children
        child replies with a WordCountReply(3) to the master
        master replies with 3 to the sender.

      requester -> wcm -> wcw
      requester <- wcm <-

      round robin logic
      1, 2, 3, 4, 5, 6 and 7 tasks
      1, 2, 3, 4, 5, 1, 2


   */

  class TestActor extends Actor {

    import WordCounterMaster._

    override def receive: Receive = {
      case "go" =>
        val master = context.actorOf(Props[WordCounterMaster], "master")
        master ! Initialize(3)
        val texts = List("I love Akka", "Scala is super dope", "yes", "me too")
        texts.foreach(text => master ! text)
      case reply: String =>
        println(s"[test actor] I received a reply: $reply")
    }
  }

  val system: ActorSystem = ActorSystem("roundRobinWordCountExercise")
  val testActor: ActorRef = system.actorOf(Props[TestActor], "testActor")

  testActor ! "go"
}
