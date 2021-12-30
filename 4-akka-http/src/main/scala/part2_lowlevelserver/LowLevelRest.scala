package part2_lowlevelserver

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import part2_lowlevelserver.GuitarDB.{CreateGuitar, FindAllGuitars, FindGuitar, FindGuitarsStock, GuitarCreated, GuitarStockUpdated, AddQuantity}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

// step 1
import spray.json._

case class Guitar(make: String, model: String, quantity: Int = 0)

object GuitarDB {
  case class CreateGuitar(guitar: Guitar)

  case class GuitarCreated(id: Int)

  case class FindGuitar(id: Int)

  case object FindAllGuitars

  case class FindGuitarsStock(filter: Boolean)

  case class AddQuantity(id: Int, qty: Int)

  case class GuitarStockUpdated(id: Int)
}

class GuitarDB extends Actor with ActorLogging {

  import GuitarDB._

  var guitars: Map[Int, Guitar] = Map()
  var currentGuitarId: Int = 0

  override def receive: Receive = {
    case FindAllGuitars =>
      log.info("Searching for all guitars")
      sender() ! guitars.values.toList
    case FindGuitar(id) =>
      log.info(s"Searching guitar by id: $id")
      sender() ! guitars.get(id)
    case CreateGuitar(guitar) =>
      log.info(s"Adding guitar $guitar with id $currentGuitarId")
      guitars = guitars + (currentGuitarId -> guitar)
      sender() ! GuitarCreated(currentGuitarId)
      currentGuitarId += 1
    case FindGuitarsStock(filter) =>
      log.info(s"Searching guitars for stock: $filter")
      if (filter) {
        sender() ! guitars.filter(_._2.quantity > 0).values.toList
      } else {
        sender() ! guitars.filter(_._2.quantity == 0).values.toList
      }
    case AddQuantity(id, qty) =>
      log.info(s"Trying to add $qty items for guitar $id")

      val guitar: Option[Guitar] = guitars.get(id)

      val newGuitar: Option[Guitar] = guitar map {
        case Guitar(make, model, q) => Guitar(make, model, q + qty)
      }

      // if newGuitar contains NONE, following does nothing
      newGuitar.foreach(guitar => guitars = guitars + (id -> guitar))

      sender() ! newGuitar
  }

}

// step 2
trait GuitarStoreJsonProtocol extends DefaultJsonProtocol {
  // step 3
  implicit val guitarFormat = jsonFormat3(Guitar)
}

object LowLevelRest extends App with GuitarStoreJsonProtocol {

  implicit val system = ActorSystem("LowLevelRest")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  /*
    GET on localhost:8080/api/guitar => ALL the guitars in the store
    GET on localhost:8080/api/guitar?id=X => fetches the guitar associated with id X
    POST on localhost:8080/api/guitar => insert the guitar into the store
   */

  // JSON ~> marshalling
  val simpleGuitar = Guitar("Fender", "Stratocaster")
  println(simpleGuitar.toJson.prettyPrint)

  // unmarshalling
  val simpleGuitarJsonString =
    """
      |{
      |  "make": "Fender",
      |  "model": "Stratocaster",
      |  "quantity": 0
      |}
      |""".stripMargin

  println(simpleGuitarJsonString.parseJson.convertTo[Guitar])

  /*
    setup
   */
  val guitarDB = system.actorOf(Props[GuitarDB], "LowLevelGuitarDB")
  val guitarList = List(
    Guitar("Fender", "Stratocaster", 2),
    Guitar("Gibson", "Les Paul", 10),
    Guitar("Martin", "LX1")
  )
  guitarList.foreach { guitar =>
    guitarDB ! CreateGuitar(guitar)
  }

  /*
    server code
   */
  implicit val defaultTimeout: Timeout = Timeout(2 seconds)

  def getGuitar(query: Query): Future[HttpResponse] = {
    val guitarId = query.get("id").map(_.toInt) // Option[Int]

    guitarId match {
      case None => Future(HttpResponse(StatusCodes.NotFound)) // /api/guitar?id=
      case Some(id: Int) =>
        val guitarFuture: Future[Option[Guitar]] = (guitarDB ? FindGuitar(id)).mapTo[Option[Guitar]]
        guitarFuture.map {
          case None => HttpResponse(StatusCodes.NotFound) // /api/guitar?id=9000
          case Some(guitar) =>
            HttpResponse(
              entity = HttpEntity(
                ContentTypes.`application/json`,
                guitar.toJson.prettyPrint
              )
            )
        }
    }
  }

  def getInventory(query: Uri.Query): Future[HttpResponse] = {
    val inStock = query.get("inStock").map(_.toBoolean) // Option[Boolean]

    inStock match {
      case None => Future(HttpResponse(StatusCodes.NotFound))
      case Some(filter: Boolean) =>
        val guitarsFuture = (guitarDB ? FindGuitarsStock(filter)).mapTo[List[Guitar]]
        guitarsFuture map { guitars =>
          HttpResponse(
            entity = HttpEntity(
              ContentTypes.`application/json`,
              guitars.toJson.prettyPrint
            )
          )
        }
    }

  }

  val requestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, uri@Uri.Path("/api/guitar"), _, _, _) =>
      /*
         query parameter handling code
        */
      val query = uri.query() // query object <=> Map[String, String]
      if (query.isEmpty) {
        val guitarsFuture: Future[List[Guitar]] = (guitarDB ? FindAllGuitars).mapTo[List[Guitar]]
        guitarsFuture.map { guitars =>
          HttpResponse(
            entity = HttpEntity(
              ContentTypes.`application/json`,
              guitars.toJson.prettyPrint
            )
          )
        }
      } else {
        // fetch guitar associate to the guitar id
        getGuitar(query)
      }

    case HttpRequest(HttpMethods.POST, Uri.Path("/api/guitar"), _, entity, _) =>
      // entities are a Source[ByteString]
      val strictEntityFuture = entity.toStrict(3 seconds)
      strictEntityFuture.flatMap { strictEntity =>
        val guitarJsonString = strictEntity.data.utf8String
        val guitar = guitarJsonString.parseJson.convertTo[Guitar]

        val guitarCreatedFuture: Future[GuitarCreated] = (guitarDB ? CreateGuitar(guitar)).mapTo[GuitarCreated]
        guitarCreatedFuture.map { _ =>
          HttpResponse(StatusCodes.OK)
        }
      }

    case HttpRequest(HttpMethods.GET, uri@Uri.Path("/api/guitar/inventory"), _, _, _) =>
      /*
         query parameter handling code
        */
      val query = uri.query() // query object <=> Map[String, String]
      if (query.isEmpty) {
        val guitarsFuture: Future[List[Guitar]] = (guitarDB ? FindAllGuitars).mapTo[List[Guitar]]
        guitarsFuture.map { guitars =>
          HttpResponse(
            entity = HttpEntity(
              ContentTypes.`application/json`,
              guitars.toJson.prettyPrint
            )
          )
        }
      } else {
        getInventory(query)
      }

    case HttpRequest(HttpMethods.POST, uri@Uri.Path("/api/guitar/inventory"), _, _, _) =>
      val query = uri.query()
      val guitarId: Option[Int] = query.get("id").map(_.toInt)
      val guitarQuantity: Option[Int] = query.get("quantity").map(_.toInt)

      val validGuitarResponseFuture: Option[Future[HttpResponse]] = for {
        id <- guitarId
        quantity <- guitarQuantity
      } yield {
        val newGuitarFuture = (guitarDB ? AddQuantity(id, quantity)).mapTo[Option[Guitar]]
        newGuitarFuture.map(_ => HttpResponse(StatusCodes.OK))
      }

      validGuitarResponseFuture.getOrElse(Future(HttpResponse(StatusCodes.BadRequest)))

    case request: HttpRequest =>
      request.discardEntityBytes()
      Future {
        HttpResponse(status = StatusCodes.NotFound)
      }
  }

  Http().bindAndHandleAsync(requestHandler, "localhost", 8080)

  /**
   * Exercise: enhance the Guitar case class with a quantity field, by default 0
   * - GET to /api/guitar/inventory?inStock=true/false which returns the guitars in stock as a JSON
   * - POST to /api/guitar/inventory?id=X&quantity=Y which adds Y guitars to the stock for guitar with id X
   */


}
