package part3_clustering

import akka.actor.{Actor, ActorLogging, ActorSystem, Address, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{InitialStateAsEvents, MemberEvent, MemberJoined, MemberRemoved, MemberUp, UnreachableMember}
import com.typesafe.config.ConfigFactory

class ClusterSubscriber extends Actor with ActorLogging {
  val cluster = Cluster(context.system)

  override def preStart(): Unit = {
    cluster.subscribe(
      self,
      initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent],
      classOf[UnreachableMember]
    )
  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  override def receive: Receive = {
    case MemberJoined(member) =>
      log.info(s"New member in town: ${member.address}")
    case MemberUp(member) if member.hasRole("numberCruncher") =>
      log.info(s"HELLO BROTHER: ${member.address}")
    case MemberUp(member) =>
      log.info(s"Let's say welcome to the newest member: ${member.address}")
    case MemberRemoved(member, previousStatus) =>
      log.info(s"Poor ${member.address}, it was removed from $previousStatus")
    case UnreachableMember(member) =>
      log.info(s"Uh oh, member ${member.address} is unreachable")
    case m: MemberEvent =>
      log.info(s"Another member event: $m")
  }

}

object ClusteringBasics extends App {

  def startCluster(ports: List[Int]): Unit = ports foreach { port =>
    val config = ConfigFactory.parseString(
      s"""
         |akka.remote.artery.canonical.port = $port
         |""".stripMargin)
      .withFallback(ConfigFactory.load("part3_clustering/clusteringBasics.conf"))

    val system = ActorSystem("RTJVMCluster", config) // all the actor system in a cluster must have the same name
    system.actorOf(Props[ClusterSubscriber], "clusterSubscriber")
  }

  startCluster(List(2551, 2552, 0))

}

object ClusteringBasics_ManualRegistration extends App {
  val system = ActorSystem(
    "RTJVMCluster",
    ConfigFactory
      .load("part3_clustering/clusteringBasics.conf")
      .getConfig("manualRegistration")
  )

  val cluster = Cluster(system) // Cluster.get(system)

  def joinExistingCluster =
    cluster.joinSeedNodes(List(
      Address("akka", "RTJVMCluster", "localhost", 2551), // akka:/RTJVMCluster@2551
      Address("akka", "RTJVMCluster", "localhost", 2552) // equivalent with AddressFromURIString("akka:/RTJVMCluster@2552")
    ))

  def joinExistingNode =
    cluster.join(Address("akka", "RTJVMCluster", "localhost", 58972))

  def joinMyself =
    cluster.join(Address("akka", "RTJVMCluster", "localhost", 2555))

  joinExistingCluster

  system.actorOf(Props[ClusterSubscriber], "clusterSubscriber")

}
