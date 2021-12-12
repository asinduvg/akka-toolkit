package part3_graphs

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, OverflowStrategy, UniformFanInShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, MergePreferred, RunnableGraph, Sink, Source, Zip, ZipWith}

object GraphCycles extends App {

  implicit val system = ActorSystem("GraphCycles")
  implicit val materializer = ActorMaterializer()

  val accelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(Merge[Int](2))
    val incrementerShape = builder.add(Flow[Int].map { x =>
      println(s"Accelerating $x")
      x + 1
    })

    sourceShape ~> mergeShape ~> incrementerShape
    mergeShape <~ incrementerShape

    ClosedShape
  }

  //  RunnableGraph.fromGraph(accelerator).run()
  // graph cycle deadlock!

  /*
    Solution 1: MergePreferred
   */

  val actualAccelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(MergePreferred[Int](1))
    val incrementerShape = builder.add(Flow[Int].map { x =>
      println(s"Accelerating $x")
      x + 1
    })

    sourceShape ~> mergeShape ~> incrementerShape
    mergeShape.preferred <~ incrementerShape

    ClosedShape
  }

  //  RunnableGraph.fromGraph(actualAccelerator).run()

  /*
    Solution 2: buffers
   */

  val bufferedRepeater = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(Merge[Int](2))
    val repeaterShape = builder.add(Flow[Int].buffer(10, OverflowStrategy.dropHead).map { x =>
      println(s"Accelerating $x")
      Thread.sleep(100)
      x
    })

    sourceShape ~> mergeShape ~> repeaterShape
    mergeShape <~ repeaterShape

    ClosedShape
  }

  //  RunnableGraph.fromGraph(bufferedRepeater).run()

  /*
    cycles risk deadlocking
      - add bounds to the number of elements in the cycle

      boundedness vs liveness
   */

  /**
   * Challenge: create a fan-in shape
   *  - two inputs which will be fed with EXACTLY ONE number
   *  - output will emit an INFINITE FIBONACCI SEQUENCE based off those 2 numbers
   *
   * Hint: Use ZipWith and cycles, MergePreferred
   */

  val fibonacciShape = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val broadcast = builder.add(Broadcast[(BigInt, BigInt)](2))
    val mergeShape = builder.add(MergePreferred[(BigInt, BigInt)](1))
    val adderFlowShape = builder.add(Flow[(BigInt, BigInt)].map(tup => {
      Thread.sleep(100)
      (tup._1 + tup._2, tup._1)
    }))
    val zipShape = builder.add(Zip[BigInt, BigInt])
    val extractLast = builder.add(Flow[(BigInt, BigInt)].map(_._1))

    zipShape.out ~> mergeShape ~> adderFlowShape ~> broadcast ~> extractLast
    broadcast ~> mergeShape.preferred

    UniformFanInShape(extractLast.out, zipShape.in0, zipShape.in1)

  }

  val fiboGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val source1 = builder.add(Source.single[BigInt](1))
      val source2 = builder.add(Source.single[BigInt](1))

      val sink = builder.add(Sink.foreach[BigInt](println))
      val fibo = builder.add(fibonacciShape)

      source1 ~> fibo.in(0)
      source2 ~> fibo.in(1)
      fibo.out ~> sink

      ClosedShape

    }
  )

  fiboGraph.run()


}
