package upmc.akka.leader

import akka.actor._

abstract class NodeStatus
case class Passive () extends NodeStatus
case class Candidate () extends NodeStatus
case class Dummy () extends NodeStatus
case class Waiting () extends NodeStatus
case class Leader () extends NodeStatus

abstract class LeaderAlgoMessage
case class Initiate () extends LeaderAlgoMessage
case class ALG (list:List[Int], nodeId:Int) extends LeaderAlgoMessage
case class AVS (list:List[Int], nodeId:Int) extends LeaderAlgoMessage
case class AVSRSP (list:List[Int], nodeId:Int) extends LeaderAlgoMessage

case class StartWithNodeList (list:List[Int])

class ElectionActor (val id:Int, val terminaux:List[Terminal]) extends Actor {

     val father = context.parent
     var nodesAlive:List[Int] = List(id)

     var candSucc:Int = -1
     var candPred:Int = -1
     var status:NodeStatus = new Passive ()

     def receive = {

          // Initialisation
          case Start => {
               self ! Initiate
          }

          case StartWithNodeList (list) => {
             
               if (list.isEmpty) {
                    this.nodesAlive = this.nodesAlive:::List(id)
               }
               else {
                    this.nodesAlive = list
               }

               // Debut de l'algorithme d'election
               self ! Initiate
          }

          case Initiate => {
               
               this.status = new Candidate()  
               val actorIndex = this.nodesAlive.indexOf(id) 
               val neighboor = this.neighboor(actorIndex)
               getNode(neighboor) ! ALG (this.nodesAlive, id)
               
          }

          case ALG (list, init) => {
               if(this.status.equals(Passive()))
               {
                    this.status = new Dummy()
                    val actorIndex = this.nodesAlive.indexOf(id) 
                    val neighboor = this.neighboor(actorIndex)
                    getNode(neighboor) ! ALG (this.nodesAlive, id)
               }
               else
               if(this.status.equals(Candidate()))
               {
                
                    this.candPred = init
                    if(id > init)
                    {
                         if(this.candSucc == -1)
                         {
                              this.status = new Waiting()
                              val nodeInit = getNode(init)
                              nodeInit ! AVS (this.nodesAlive, id)

                         }
                         else
                         {
                              val nodeSucc = getNode(this.candSucc)
                              nodeSucc ! AVSRSP (this.nodesAlive, this.candPred)
                              this.status = new Dummy()
                         }
                    }

                    if(init == id)
                    {
                         this.status = new Leader()
                         father ! LeaderChanged (id)
                        
                    }
               }
          }

          case AVS (list, j) => {
               if(this.status.equals(Candidate()))
               {
                    if(this.candPred == -1)
                         this.candSucc = j
                    else
                    {
                         val nodeJ = getNode(j)
                         nodeJ ! AVSRSP(this.nodesAlive, this.candPred)
                         this.status = new Dummy()
                    }
               }
               else
                    if(this.status.equals(Waiting()))
                         this.candSucc = j
          }

          case AVSRSP (list, k) => {

               if(this.status.equals(Waiting()))
               {
                    if(this.id == k)
                         this.status = new Leader()
                    else
                    {
                         this.candPred = k
                         if (this.candSucc == -1)
                         {
                              if(k < this.id)
                              {
                                   this.status = new Waiting()
                                   getNode(k) ! AVS(this.nodesAlive, this.id)
                              }
                         }
                         else
                         {
                              this.status = new Dummy()
                              getNode(this.candSucc) ! AVSRSP(nodesAlive, k)
                         } 
                    }
               }
          }

     }

     def getNode(nodeId:Int):ActorSelection = 
     {
          val node : ActorSelection = null
          terminaux.foreach(n => 
                              {
                                   if (n.id == nodeId)
                                   {
                                      return context.actorSelection("akka.tcp://LeaderSystem" + n.id + "@" + n.ip + ":" + n.port + "/user/Node")
                                        
                                   }
                              })
         return node
     }

     def neighboor(nodeId:Int):Int = 
     {
           val ngb = (nodeId + 1) % terminaux.length
           if(this.nodesAlive.contains(ngb)) 
               return ngb
           else
               return neighboor(ngb)
     }

}
