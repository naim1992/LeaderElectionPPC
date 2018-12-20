package upmc.akka.leader

import java.util
import java.util.Date

import akka.actor._

import akka.util.Timeout

import akka.pattern.{ ask, pipe }
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.util._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

abstract class Tick
case class CheckerTick () extends Tick

class CheckerActor (val id:Int, val terminaux:List[Terminal], electionActor:ActorRef) extends Actor {

     var time : Int = 200
     val father = context.parent

     var nodesAlive:List[Int] = List()
     var datesForChecking:List[Date] = List()
     var lastDate:Date = null

     var leader : Int = -1
     val scheduler = context.system.scheduler

    def receive = {

         // Initialisation
        case Start => {
             implicit val timeout = Timeout(5 seconds)
             terminaux.foreach(n => {
                         context.system.actorSelection("akka.tcp://LeaderSystem" + n.id + "@" + n.ip + ":" + n.port + "/user/Node").resolveOne().onComplete {
                              
                              case Success(actor) => 
                              {
                                   if(!this.nodesAlive.contains(n.id))
                                        this.nodesAlive = this.nodesAlive:::List(n.id)
                              }

                              case Failure(ex) => this.nodesAlive =  this.nodesAlive.filter(_ != n.id)
                         }
               })
             self ! CheckerTick
        }

        // A chaque fois qu'on recoit un Beat : on met a jour la liste des nodes
        case IsAlive (nodeId) => {
             if (!this.nodesAlive.contains(nodeId))
             {
                  this.nodesAlive = this.nodesAlive:::List(nodeId)
                  father ! Message ("Nodes Alive : (" + this.nodesAlive.mkString(", ") + ")")
             }
        }

        case IsAliveLeader (nodeId) => this.leader = nodeId

        // A chaque fois qu'on recoit un CheckerTick : on verifie qui est mort ou pas
        // Objectif : lancer l'election si le leader est mort
        case CheckerTick => {           
             terminaux.foreach(n => {
                    if (n.id != id && this.nodesAlive.contains(n.id)) {
                         implicit val timeout = Timeout(5 seconds)
                          context.system.actorSelection("akka.tcp://LeaderSystem" + n.id + "@" + n.ip + ":" + n.port + "/user/Node").resolveOne().onComplete {
                              
                              case Success(actor) => 

                              case Failure(ex) => {
                                   this.nodesAlive =  this.nodesAlive.filter(_ != n.id)
                                   father ! Message ("Nodes Alive : (" + this.nodesAlive.mkString(", ") + ")")
                                   
                                   if(n.id == leader)
                                   {
                                        
                                        electionActor ! StartWithNodeList (this.nodesAlive)
                                        
                                   }

                                   }
                             
                         }

                        
                    }
               })

               scheduler.scheduleOnce(time milliseconds , self, CheckerTick) 
        }

    }


}
