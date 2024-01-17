package part3_clustering

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{InitialStateAsEvents, MemberEvent, MemberRemoved, MemberUp}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object ChatActor {
  def props(nickname: String, port: Int) = Props(new ChatActor(nickname, port))
}

object ChatDomain {
  case class ChatMessage(nickname: String, contents: String)

  case class UserMessage(contents: String)

  case class EnterRoom(fullAddress: String, nickname: String)
}

class ChatActor(nickname: String, port: Int) extends Actor with ActorLogging {

  import ChatDomain._

  implicit val timeout = Timeout(5 seconds)

  var chatters = Map[ActorRef, String]()

  // TODO 1: initialize the cluster object
  val cluster = Cluster(context.system)

  // TODO 2: subscribe to cluster events in preStart()
  override def preStart(): Unit = {
    cluster.subscribe(
      self,
      initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent]
    )
  }

  // TODO 3: unsubscribe self in postStop()
  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

  override def receive: Receive = online(Map())

  def online(chatRoom: Map[String, String]): Receive = {
    case MemberUp(member) =>
      // TODO 4: send a special EnterRoom message to the chatActor deployed on the new node (hint: use actorSelection)
      val remoteChatActorSelection = getChatActor(member.address.toString)
      remoteChatActorSelection ! EnterRoom(s"${self.path.address}@localhost:$port", nickname)

    case MemberRemoved(member, _) =>
      // TODO 5: remove the member from your data structures
      val remoteNickname = chatRoom(member.address.toString)
      log.info(s"$remoteNickname left the room")
      context.become(online(chatRoom - member.address.toString))

    case EnterRoom(remoteAddress, remoteNickname) =>
      // TODO 6: add the member to your data structures
      if (remoteNickname != nickname) {
        log.info(s"$remoteNickname entered the room.")
        context.become(online(chatRoom + (remoteAddress -> remoteNickname)))
      }

    case UserMessage(contents) =>
      // TODO 7: broadcast the content (as ChatMessages) to the rest of the cluster members
      chatRoom.keys.foreach { remoteAddressAsString =>
        getChatActor(remoteAddressAsString) ! ChatMessage(nickname, contents)
      }

    case ChatMessage(remoteNickname, contents) =>
      log.info(s"[$remoteNickname] $contents")
  }

  def getChatActor(memberAddress: String) =
    context.actorSelection(s"$memberAddress/user/chatActor")

}

class ChatApp(nickname: String, port: Int) extends App {

  import ChatDomain._

  val config = ConfigFactory.parseString(
    s"""
       |akka.remote.artery.canonical.port = $port
       |""".stripMargin
  ).withFallback(ConfigFactory.load("part3_clustering/clusterChat.conf"))

  val system = ActorSystem("RTJVMCluster", config)
  val chatActor = system.actorOf(ChatActor.props(nickname, port), "chatActor")

  scala.io.Source.stdin.getLines().foreach { line =>
    chatActor ! UserMessage(line)
  }

}

object Alice extends ChatApp("Alice", 2551)

object Bob extends ChatApp("Bob", 2552)

object Charlie extends ChatApp("Charlie", 2553)