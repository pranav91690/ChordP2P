import akka.actor._
import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.Random
import java.security.MessageDigest

/**
 * Created by pranav on 10/19/15.
 */
object project3 {
  // Message Definitions
  // Core Functionality
  case object findKey
  case object printStatus
  case class getClosestFinger(Sender: ActorRef, key:Int, id:Int, mode:Int, hops:Int)
  case class closestFinger(Sender: ActorRef, key:Int, id:Int, mode:Int, hops:Int)
//  case class returnSuccessor(key:Int, mode:Int, hops:Int)
  case class returnSuccessor(id:Int, node:finger, mode:Int, hops:Int)
//  case class Key(node : nodeReference)
  case class Successor(finger : finger)

  // Joining Algorithm
  case class join(bootStrap:ActorRef)
  case class getSuccessor(nodeId:Int)

  // Fix Fingers
  case object stabilize
  case object fix_fingers
  case class inform(id:Int)
  case object returnPredecessor
  case class Predecessor(finger : finger)

  // Message sent by the Master to the Listener
  case class done(avgHops : Float)

  // HeartBeat for the Periodic Refreshing
  val heartBeat : FiniteDuration = 5 milliseconds

  // Max Request
  var maxRequests = 0

  // Number of NOdes
  var numNodes = 0

  // Mac Value
  val max = Math.pow(2,31).toInt - 1


  class Node(identifier : Int, m : Int, listener: ActorRef) extends Actor{
    // Instance Variables
    // Predecessor - Initially it's null
    var predecessor : finger  = new finger
    // Finger Table - Initialize to m members
    var fingers : Array[finger] = new Array(m)
    // Hash Table to Store Actor Ref and ID's
    var keys: mutable.HashMap[Int, ActorRef] = new mutable.HashMap()
    // Present Key Value
    var key = 0
    // Counter to Check the Requests Processed
    var reqProcessed = 0
    // Avg Hops Value
    var hops = 0
    // Schedulers
    var stabilizeC  : Cancellable = null
    var fixFingersC : Cancellable = null
    var requests    : Cancellable = null
    // COunters for Stabilizers
    var sCounter = numNodes * 10
    var fCounter = numNodes * m * 10


    // Define Receive Method
    def receive = {
      // Core Functionality Messages
      case `findKey`            =>
        // RandomLy Generate the ID
        val random = new Random()
        val input = random.nextString(20)
        val id = returnKey(input)
        val hopCount = 1

        // Send this message only if the the Successor is set
        key += 1
        keys.put(key, sender())

        // Send a Message to Itself to find the id in it's key
        self ! getClosestFinger(self, key, id, 0, hopCount)


      case getClosestFinger(req, k, id, mode, hopCount)   =>
        // Go Through the Fingers and return the Closest one
        var loop = false
        var found = false
        var i = m-1
        while(!loop){
          if(fingers(i) != null) {
            // Check if the Node is set or not
            if (fingers(i).node != null) {
              if (existsInRange(identifier, id, fingers(0).id)) {
                // Closest Finger Found
                fingers(i).node ! closestFinger(req, k, id, mode, hopCount)
                loop = true
                found = true
              }
            }
          }
          i-=1
          if(i<0){
            loop = true
          }
        }

        if(!found) {
          // If nothing found, return the Node itself
          self ! closestFinger(req, k, id, mode, hopCount)
        }

      case closestFinger(req, k, id, mode, hopCount)=>
        // Check if the Node id falls between the node id and it's successor's id
        if (existsInRange(identifier, fingers(0).id, id)) {
          // Return the Correct Finger
          req ! returnSuccessor(k, fingers(0), mode, hopCount)
        } else {
          val newHopCount = hopCount + 1
          // Go Through it's finger Table Now
          self ! getClosestFinger(req, k, id, mode, newHopCount)
        }


        
      case returnSuccessor(k, finger, mode, hopCount) =>
        mode match {
          // Incase it's a key
          case 0 =>
            // Get the Actor Reference
            val result = keys.get(k)

            result match {
              case None     => 
              case Some(x)  =>
                reqProcessed+=1
                hops += hopCount
                // If the Requested Keys are matched
                if (reqProcessed == maxRequests){
                  // Cancel the Scheduler
                  requests.cancel()
                  // Send a Message to the Listener
                  val avg = hops/maxRequests
                  // How to get the Listener
                  listener ! done(avg)
                }
            }

          case 3 =>
            // Get the Actor Reference
            val result = keys.get(k)

            result match {
              case None     =>
              case Some(x)  =>
                // Send a Message to the Actor
                x ! Successor(finger)
            }

          // InCase it's a finger
          case 1 =>
            // Put the Node in the correct Finger
            fingers(k).id   = finger.id
            fingers(k).node = finger.node
        }

      // Join Functionality
      case join(bootStrap)        =>
        // Send a Message to Get the Identifier
        bootStrap ! getSuccessor(identifier)

      case Successor(finger) =>
        // Initiate the Successor
        fingers(0)        = new finger
        fingers(0).id     = finger.id
        fingers(0).node   = finger.node

        // Send a Message to the Node that this might be a Predecessor
        fingers(0).node ! inform(identifier)

        import context.dispatcher

        stabilizeC  = context.system.scheduler.schedule(0 milliseconds, heartBeat, self, stabilize)
//        val tmp2 = context.system.scheduler.schedule(3 milliseconds, 1 second, self, printStatus)
        fixFingersC = context.system.scheduler.schedule(1 seconds, heartBeat, self, fix_fingers)
        requests    =context.system.scheduler.schedule(5 seconds, 1 seconds, self, findKey)

      case `printStatus` =>
        println("ID : " + identifier + " Suc : " + fingers(0).id)

      case getSuccessor(id)   =>
        // Return the Successor to the Requesting ID
        val hopCount = 1
        key+=1
        keys.put(key,sender())
        self ! getClosestFinger(self, key, id, 3, hopCount)

      case `stabilize` =>
        if(sCounter == 0) {
          if (!stabilizeC.isCancelled) {
            stabilizeC.cancel()
          }
        }
        // If it's successor is set
        fingers(0).node ! returnPredecessor
        sCounter-=1


      case `fix_fingers` =>
        if(fCounter == 0) {
          if (!fixFingersC.isCancelled) {
            fixFingersC.cancel()
          }
        }
        val random = new Random()
        val fIndex = 1 + random.nextInt(m-1)
        val start = (identifier + Math.pow(2, fIndex).toInt) % max
        val hopCount = 0
        // If it is the First Time, instantiate the finger
        if(fingers(fIndex) == null){
          fingers(fIndex)       = new finger
        }

        fingers(fIndex).id = start

        self ! getClosestFinger(self, fIndex, start, 1, hopCount)
        fCounter-=1

      case inform(id) =>
        if(predecessor == null){
          predecessor.id   = id
          predecessor.node = sender()
        }else{
          if(existsInRange(predecessor.id, identifier, id)){
            predecessor.id   = id
            predecessor.node = sender()
          }
        }

      case `returnPredecessor` =>
          sender() ! Predecessor(predecessor)


      case Predecessor(finger) =>
        if(finger.node != null) {
          if (existsInRange(identifier, fingers(0).id, finger.id)) {
            // Update the Successor
            fingers(0).id = finger.id
            fingers(0).node = finger.node
            fingers(0).node ! inform(identifier)
          }
        }
    }
  }

  // Class for Finger Table Entry
  class finger{
    var id  :Int      = 0
    var node:ActorRef = null
  }

  // The Listener Actor
  class Listener(totalNodes : Int) extends Actor {
    var total = 0.0
    var counter = 0
    def receive = {
      case done(avg) ⇒
        total += avg
        counter+=1
        println(counter)
        if(counter == totalNodes){
          val avg = total/counter
          println("Average Hops : " + avg)
          context.system.shutdown()
        }

    }
  }

  def existsInRange(s:Int, end:Int, id:Int) : Boolean = {
    val start = s + 1
    if(start < end) {
      if (id <= end && id >= start) {
        return true
      }
    }else{
      if((id >= start && id <= max) || (id >= 0 && id <= end)){
        return true
      }
    }

    false
  }

  def main (args: Array[String]) {
    // Main Method
    println("Start of Chord")
    // Let's write the Main Function
    numNodes    = args(0).toInt
    maxRequests = args(1).toInt

    // Create an Actor System
    val system = ActorSystem("Chord")

    // Create the result listener, which will print the result and shutdown the system
    val listener = system.actorOf(Props(new Listener(numNodes)), name = "listener")

    // Create and Instantiate the BootStrap Node
    val random = new Random()
    val m = 31

    // BootStrap Node initialization
    val txt = random.nextString(10)
    val id = returnKey(txt)
    val bootStrap = system.actorOf(Props(new Node(id, m, listener)),"bootstrap")
    val finger = new finger
    finger.id = id
    finger.node = bootStrap

    // Make it the Successor and Predecessor
    bootStrap ! Successor(finger)

    for(i <- 1 until numNodes){
      // Create an Actor Node
      val name = "Node" + i
      val txt = random.nextString(10)
      val id = returnKey(txt)
      val newNode = system.actorOf(Props(new Node(id, m, listener)), name)
      newNode ! join(bootStrap)
    }
  }

  def returnKey(input : String) : Int = {
    val m = MessageDigest.getInstance("SHA-1").digest(input.getBytes("UTF-8"))
    val l =  m.length - 1
    var key = 0
    for(i <- 0 until 4){
      key = key | m(i)<<(i*8)
    }

    val res = key & 0x7FFFFFFF
    res
  }
}
