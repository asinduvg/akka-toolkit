package part5infra

import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}
import akka.dispatch.{ControlMessage, PriorityGenerator, UnboundedPriorityMailbox}
import com.typesafe.config.{Config, ConfigFactory}

object Mailboxes extends App {

  val system = ActorSystem("MailboxDemo", ConfigFactory.load().getConfig("mailboxesDemo"))

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(message.toString)
    }
  }

  /**
   * Interesting case #1 - custom priority mailbox
   * p0 -> most important
   * p1
   * p2
   * p3
   */

  class SupportTicketPriorityMailbox(settings: ActorSystem.Settings, config: Config)
    extends UnboundedPriorityMailbox(
      PriorityGenerator {
        case message: String if message.startsWith("[P0]") => 0
        case message: String if message.startsWith("[P1]") => 1
        case message: String if message.startsWith("[P2]") => 2
        case message: String if message.startsWith("[P3]") => 3
        case _ => 4
      }
    )

  // step 2 - make it known in the config
  // step 3 - attach the dispatcher to an actor

  val supportTicketLogger = system.actorOf(Props[SimpleActor].withDispatcher("support-ticket-dispatcher"))
  //  supportTicketLogger ! PoisonPill
  //  supportTicketLogger ! "[P3] this thing would be nice to have"
  //  supportTicketLogger ! "[P0] this thing would be solved NOW!"
  //  supportTicketLogger ! "[P1] do this when you have time"

  // after which time can I send another message and be prioritized accordingly?

  /**
   * Interesting case #2 - control-aware mailbox
   * we'll use UnboundedControlAwareMailbox
   */

  // step 1 - mark important messages as control messages
  case object ManagementTicket extends ControlMessage

  /*
    step 2 - configure who gets the mailbox
      - make the actor attach to the mailbox
   */

  val controlAwareActor = system.actorOf(Props[SimpleActor].withMailbox("control-mailbox"))
  //  controlAwareActor ! "[P0] this thing would be nice to have"
  //  controlAwareActor ! "[P1] this thing would be solved NOW!"
  //  controlAwareActor ! ManagementTicket

  // method #2 - using deployment config
  val altControlAwareActor = system.actorOf(Props[SimpleActor], "altControlAwareActor")
  altControlAwareActor ! "[P0] this thing would be nice to have"
  altControlAwareActor ! "[P1] this thing would be solved NOW!"
  altControlAwareActor ! ManagementTicket

}
