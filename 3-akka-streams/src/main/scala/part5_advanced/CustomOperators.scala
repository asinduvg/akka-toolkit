package part5_advanced

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, Attributes, FlowShape, Inlet, Outlet, SinkShape, SourceShape}
import akka.stream.stage.{GraphStage, GraphStageLogic, GraphStageWithMaterializedValue, InHandler, OutHandler}

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Random, Success}

object CustomOperators extends App {

  implicit val system: ActorSystem = ActorSystem("CustomOperators")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  // 1 - a custom source which emits random numbers until cancel

  class RandomNumberGenerator(max: Int) extends GraphStage[ /*step 0: define the shape*/ SourceShape[Int]] {

    // step 1: define the ports and the component-specific members
    val outPort: Outlet[Int] = Outlet[Int]("randomGenerator")
    val random = new Random()

    // step 2: construct a new shape
    override def shape: SourceShape[Int] = SourceShape(outPort)

    // step 3: create the logic
    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      // step 4:
      // define mutable state
      // implement my logic here

      setHandler(outPort, new OutHandler {
        // when there is demand from downstream
        override def onPull(): Unit = {
          // emit a new element
          val nextNumber = random.nextInt(max)
          // push it out of the outport
          push(outPort, nextNumber)
        }
      })
    }

  }

  val randomNumberGeneratorSource = Source.fromGraph(new RandomNumberGenerator(100))
  //  randomNumberGeneratorSource.runWith(Sink.foreach(println))

  // 2 - a custom sink that prints elements in batches of a given size

  class Batcher(batchSize: Int) extends GraphStage[SinkShape[Int]] {
    val inPort: Inlet[Int] = Inlet[Int]("batcher")

    override def shape: SinkShape[Int] = SinkShape[Int](inPort)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

      override def preStart(): Unit = {
        pull(inPort)
      }

      // mutable state
      val batch = new mutable.Queue[Int]

      setHandler(inPort, new InHandler {
        // when the upstream wants to send me an element
        override def onPush(): Unit = {
          val nextElement = grab(inPort)
          batch.enqueue(nextElement)

          // assume some complex computation
          Thread.sleep(100)

          if (batch.size >= batchSize) {
            println("New batch: " + batch.dequeueAll(_ => true).mkString("[", ", ", "]"))
          }

          pull(inPort) // send demand upstream
        }

        override def onUpstreamFinish(): Unit = {
          if (batch.nonEmpty) {
            println("New batch: " + batch.dequeueAll(_ => true).mkString("[", ", ", "]"))
            println("Stream finished.")
          }
        }
      })
    }
  }

  val batcherSink = Sink.fromGraph(new Batcher(10))
  //  randomNumberGeneratorSource.to(batcherSink).run()

  /**
   * Exercise: a custom flow - a simple filter flow
   * - 2 ports: an input port and an output port
   */

  class SimpleFilter[T](predicate: T => Boolean) extends GraphStage[FlowShape[T, T]] {
    val inPort: Inlet[T] = Inlet[T]("filterIn")
    val outPort: Outlet[T] = Outlet[T]("filterOut")

    override def shape: FlowShape[T, T] = FlowShape[T, T](inPort, outPort)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      setHandler(inPort, new InHandler {
        // when the upstream wants to send me an element
        override def onPush(): Unit = {
          try {
            val nextElement = grab(inPort)

            if (predicate(nextElement)) {
              push(outPort, nextElement) // pass it on
            } else {
              pull(inPort) // ask for another element
            }
          } catch {
            case e: Throwable => failStage(e)
          }
        }

        override def onUpstreamFinish(): Unit = {
          complete(outPort)
        }
      })

      setHandler(outPort, new OutHandler {
        // when there is demand from downstream
        override def onPull(): Unit = pull(inPort)
      })

    }
  }

  val myFilter = Flow.fromGraph(new SimpleFilter[Int](_ > 50))
  //  randomNumberGeneratorSource.via(myFilter).to(batcherSink).run()
  // backpressure Out of the box!!!

  /**
   * Materialized values in graph stages
   */

  // 3 - a flow that counts the number of elements that go through it
  class CounterFlow[T] extends GraphStageWithMaterializedValue[FlowShape[T, T], Future[Int]] {
    val inPort: Inlet[T] = Inlet[T]("counterIn")
    val outPort: Outlet[T] = Outlet[T]("counterOut")

    override val shape: FlowShape[T, T] = FlowShape(inPort, outPort)

    override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Int]) = {
      val promise = Promise[Int]
      val logic = new GraphStageLogic(shape) {
        // setting mutable state
        var counter = 0

        setHandler(outPort, new OutHandler {
          override def onPull(): Unit = pull(inPort)

          override def onDownstreamFinish(): Unit = {
            promise.success(counter)
            super.onDownstreamFinish()
          }

        })

        setHandler(inPort, new InHandler {
          override def onPush(): Unit = {
            // extract the element
            val nextElement = grab(inPort)
            counter += 1
            // pass it on
            push(outPort, nextElement)
          }

          override def onUpstreamFinish(): Unit = {
            promise.success(counter)
            super.onUpstreamFinish()
          }

          override def onUpstreamFailure(ex: Throwable): Unit = {
            promise.failure(ex)
            super.onUpstreamFailure(ex)
          }

        })

      }
      (logic, promise.future)
    }
  }

  val counterFlow = Flow.fromGraph(new CounterFlow[Int])
  val countFuture = Source(1 to 10)
    //    .map(x => if (x == 7) throw new RuntimeException("gotcha!") else x)
    .viaMat(counterFlow)(Keep.right)
    .to(Sink.foreach(x => if (x == 7) throw new RuntimeException("gotcha, sink!") else println(x)))
    //    .to(Sink.foreach[Int](println))
    .run()

  import system.dispatcher

  countFuture.onComplete {
    case Success(count) => println(s"The number of elements passed: $count")
    case Failure(ex) => println(s"Counting the elements failed: $ex")
  }

}
