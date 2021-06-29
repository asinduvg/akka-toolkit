package part2actors

import akka.actor.{Actor, ActorSystem, Props}
import com.sun.org.apache.xml.internal.utils.StringToIntTable

object ActorsIntro extends App {

  // part1 - actor systems
  val actorSystem = ActorSystem("firstActorSystem")
  println(actorSystem.name)

  /*
    - actors are uniquely identified
    - messages are asynchronous
    - each actor may respond differently
    - actors are(really) encapsulated
   */

  // part2 - create actors
  // word count actor

  class WordCountActor extends Actor {
    // internal data
    var totalWords = 0

    // behavior
    def receive: PartialFunction[Any, Unit] = {
      case message: String => {
        println(s"[word counter] I have received: $message")
        totalWords += message.split(" ").length
      }
      case msg => println(s"[word counter] I cannot understand ${msg.toString}")
    }

  }

  // part3 - instantiate the actor
  val wordCounter = actorSystem.actorOf(Props[WordCountActor], "wordCounter")
  val anotherWordCounter = actorSystem.actorOf(Props[WordCountActor], "anotherWordCounter")

  // part4 - communicate
  wordCounter ! "I'm learning Akka and it's pretty damn cool!" // ! aka "tell"
  anotherWordCounter ! "A different message"
  // asynchronous

  object Person {
    def props(name: String): Props = Props(new Person(name))
  }

  class Person(name: String) extends Actor {
    override def receive: Receive = {
      case "hi" => println(s"Hi, my name is $name")
      case _ =>
    }
  }

  val person = actorSystem.actorOf(Person.props("Bob")) // legal but discouraged

  person ! "hi"


}
