package part3_graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.stream.scaladsl.{Balance, Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, Zip}

object GraphBasics extends App {

  implicit val system = ActorSystem("GraphBasics")
  implicit val materializer = ActorMaterializer()

  val input = Source(1 to 1000)
  val incrementer = Flow[Int].map(_ + 1) // hard computation
  val multiplier = Flow[Int].map(_ * 10) // hard computation
  val output = Sink.foreach[(Int, Int)](println)

  // step 1 - setting up the fundamentals for the graph
  val graph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] => // builder = MUTABLE data structure
      import GraphDSL.Implicits._ // bring some nice operators into scope

      // step 2 - add the necessary components on this graph
      val broadcast = builder.add(Broadcast[Int](2)) // fan-out operator
      val zip = builder.add(Zip[Int, Int]) // fan-in operator

      // step 3 - tying up the components
      input ~> broadcast

      broadcast.out(0) ~> incrementer ~> zip.in0
      broadcast.out(1) ~> multiplier ~> zip.in1

      zip.out ~> output

      // step 4 - return a closed shape
      ClosedShape // FREEZE the builder's shape
      // shape
    } // graph
  ) // runnable graph

  //  graph.run() // run the graph and materialize it

  /**
   * exercise 1: feed a source into 2 sinks at the same time (hint: use a broadcast)
   */

  val firstSink = Sink.foreach[Int](x => println(s"First sink $x"))
  val secondSink = Sink.foreach[Int](x => println(s"Second sink $x"))

  val graph2 = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[Int](2))

      //      input ~> broadcast
      //      broadcast.out(0) ~> firstSink
      //      broadcast.out(1) ~> secondSink

      // alternative way
      input ~> // implicit port numbering
        broadcast ~> firstSink
      broadcast ~> secondSink

      ClosedShape
    }
  )

  //  graph2.run()
  /**
   * exercise 2: balance
   */

  import scala.concurrent.duration._

  val fastSource = Source(1 to 500).throttle(5, 1 second)
  val slowSource = Source(501 to 1000).throttle(1, 1 second)

  val sink1 = Sink.fold[Int, Int](0)((count, _) => {
    println(s"Sink1 number of elements: $count")
    count + 1
  })

  val sink2 = Sink.fold[Int, Int](0)((count, _) => {
    println(s"Sink2 number of elements: $count")
    count + 1
  })

  val balanceGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      // fan-in shape
      val merge = builder.add(Merge[Int](2))
      // fan-out shape
      val balance = builder.add(Balance[Int](2))

      fastSource ~> merge.in(0)
      slowSource ~> merge.in(1)

      merge ~> balance

      balance.out(0) ~> sink1
      balance.out(1) ~> sink2

      ClosedShape

    }
  )

  balanceGraph.run()

}
