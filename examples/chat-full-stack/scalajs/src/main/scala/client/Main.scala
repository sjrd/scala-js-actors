package client

import scala.collection.mutable

import akka.actor._
import akka.scalajs.wsclient._

import models._

import scala.scalajs.js
import org.scalajs.jquery.{jQuery => jQ, _}

object Main {
  RegisterPicklers.registerPicklers()

  val system = ActorSystem("chat-client")
  val manager = system.actorOf(Props(new Manager))

  val usersContainer = jQ(".conversation-wrap")
  val messagesContainer = jQ(".msg-wrap")

  private[this] var myFocusedTab: TabInfo = null
  def focusedTab: TabInfo = myFocusedTab
  def focusedTab_=(v: TabInfo): Unit = {
    if (myFocusedTab ne v) {
      myFocusedTab = v
      v.activate()
    }
  }

  val roomsTab = new RoomsTabInfo
  val roomTabInfos = mutable.Map.empty[Room, DiscussionTabInfo]

  def getDiscussionTabInfoOrCreate(room: Room): DiscussionTabInfo = {
    roomTabInfos.getOrElseUpdate(room, new DiscussionTabInfo(room))
  }

  def startup(): Unit = {
    jQ("#connect-button") click { (event: JQueryEventObject) => connect }
    jQ("#disconnect-button") click { (event: JQueryEventObject) => disconnect }
    jQ("#send-button") click { (event: JQueryEventObject) => send }

    roomsTab.focusTab()
  }

  def connect(): Unit = {
    val nickname = jQ("#nickname-edit").value().toString()
    val email = jQ("#email-edit").value().toString()
    val gravatarHash = computeGravatarHash(email)
    val user = User(nickname, gravatarHash)
    manager ! ConnectAs(user)
  }

  def disconnect(): Unit = {
    manager ! Disconnect
  }

  def send(): Unit = {
    val text = jQ("#msg-to-send").value().toString()
    manager ! Send(text)
    jQ("#msg-to-send").value("")
  }

  def joinRoom(room: Room): Unit = {
    roomTabInfos.get(room).fold {
      manager ! Join(room)
    } {
      _.focusTab()
    }
  }

  def computeGravatarHash(email: String): String =
    js.Dynamic.global.hex_md5(email.trim.toLowerCase).asInstanceOf[js.String]

  def gravatarURL(user: User, size: Int): String =
    s"http://www.gravatar.com/avatar/${user.gravatarHash}?s=$size"
}

abstract class TabInfo(val name: String) {
  val tabButton = jQ("""<a href="#" class="btn col-lg-6 send-message-btn" role="button">""").text(name)
  tabButton click { (e: JQueryEventObject) => focusTab(); false }
  tabButton.appendTo(jQ("#room-tabs"))

  def isFocused: Boolean = Main.focusedTab eq this

  def focusTab(): Unit = {
    Main.focusedTab = this
  }

  def activate(): Unit = {
    render()
  }

  def invalidate(): Unit = {
    if (isFocused)
      render()
  }

  def render(): Unit = {
    import Main._
    usersContainer.empty()
    messagesContainer.empty()
  }
}

class RoomsTabInfo extends TabInfo("Rooms") {
  import Main._

  private[this] var myRooms: List[Room] = Nil
  def rooms: List[Room] = myRooms
  def rooms_=(v: List[Room]): Unit = {
    myRooms = v
    invalidate()
  }

  override def render(): Unit = {
    super.render()
    val roomsContainer = usersContainer // "abuse" the users container
    for (room <- Room("Test") :: rooms) {
      roomsContainer.append(
        jQ("""<div class="media conversation">""").append(
          jQ("""<div class="media-body">""").append(
            jQ("""<h5 class="media-heading">""").text(room.name),
            jQ("""<small>""").append(
              jQ("""<a href="#" role="button"><i class="fa fa-plus"></i> Join</a>""").click {
                (e: JQueryEventObject) => joinRoom(room); false
              }
            )
          )
        )
      )
    }
    roomsContainer.append(
      jQ("""<div class="media conversion">""").append(
        jQ("""<div class="media-body">""").append(
          jQ("""<h5 class="media-heading">""").append(
            jQ("""<input type="text" class="form-control new-room-name">""")
          ),
          jQ("""<small>""").append(
            jQ("""<a href="#" role="button"><i class="fa fa-plus"></i> Join</a>""").click {
              (e: JQueryEventObject) => joinRoom(Room(jQ(".new-room-name").value().toString())); false
            }
          )
        )
      )
    )
  }
}

class DiscussionTabInfo(room: Room) extends TabInfo(room.name) {

  var manager: ActorRef = Main.system.deadLetters
  val users = new mutable.ListBuffer[User]
  val messages = new mutable.ListBuffer[Message]

  override def render(): Unit = {
    super.render()
    renderUsers()
    renderMessages()
  }

  def renderUsers(): Unit = {
    import Main._
    for (user <- users) {
      usersContainer.append(
        jQ("""<div class="media conversation">""").append(
          jQ("""<div class="pull-left">""").append(
            jQ("""<img class="media-object" alt="gravatar" style="width: 50px; height: 50px"""").attr(
              "src", gravatarURL(user, 50)
            )
          ),
          jQ("""<div class="media-body">""").append(
            jQ("""<h5 class="media-heading">""").text(room.name),
            jQ("""<small>""").append(
              jQ("""<a href="#" role="button"><i class="fa fa-plus"></i> Join</a>""").click {
                (e: JQueryEventObject) => joinRoom(room); false
              }
            )
          )
        )
      )
    }
  }

  def renderMessages(): Unit = {
    import Main._

    for (message <- messages) {
      val timeStampStr = new js.Date(message.timestamp).toString()
      messagesContainer.append(
        jQ("""<div class="media msg">""").append(
          jQ("""<a class="pull-left" href="#">""").append(
            jQ("""<img class="media-object" alt="gravatar" style="width: 32px; height: 32px">""").attr(
              "src", gravatarURL(message.user, 32)
            )
          ),
          jQ("""<div class="media-body">""").append(
            jQ(s"""<small class="pull-right time"><i class="fa fa-clock-o"></i> $timeStampStr</small>"""),
            jQ("""<h5 class="media-heading">""").text(message.user.nick),
            jQ("""<small class="col-lg-10">""").text(message.text)
          )
        )
      )
    }

    // scroll to latest message
    jQ(".msg-wrap").scrollTop(jQ(".msg-wrap")(0).scrollHeight)
  }
}

case class ConnectAs(user: User)
case class Send(text: String)
case object Disconnect
case object Disconnected

class Manager extends Actor {
  val proxyManager = context.actorOf(Props(new ProxyManager))
  var user: User = User.Nobody
  var service: ActorRef = context.system.deadLetters

  def receive = {
    case m @ ConnectAs(user) =>
      this.user = user
      jQ("#connect-button").text("Connecting ...").prop("disabled", true)
      jQ("#nickname-edit").prop("disabled", true)
      proxyManager ! m

    case m @ WebSocketConnected(entryPoint) =>
      service = entryPoint
      service ! Connect(user)
      jQ("#status-disconnected").addClass("status-hidden")
      jQ("#status-connected").removeClass("status-hidden")
      jQ("#nickname").text(user.nick)
      jQ("#send-button").prop("disabled", false)
      jQ("#disconnect-button").text("Disconnect").prop("disabled", false)

    case Send(text) =>
      val message = Message(user, text, System.currentTimeMillis())
      service ! SendMessage(message)

    case ReceiveMessage(message) =>
      Console.err.println(s"receiving message $message")
      addMessage(message)

    case m @ Disconnect =>
      jQ("#disconnect-button").text("Disconnecting ...").prop("disabled", true)
      proxyManager ! m

    case Disconnected =>
      service = context.system.deadLetters
      jQ("#connect-button").text("Connect").prop("disabled", false)
      jQ("#nickname-edit").prop("disabled", false)
      jQ("#send-button").prop("disabled", true)
      jQ("#status-disconnected").removeClass("status-hidden")
      jQ("#status-connected").addClass("status-hidden")
  }

  def addMessage(message: Message) = {
    val timeStampStr = new js.Date(message.timestamp).toString()
    jQ(".msg-wrap").append(
      jQ("""<div class="media msg">""").append(
        jQ("""<a class="pull-left" href="#">""").append(
          jQ("""<img class="media-object" alt="gravatar" style="width: 32px; height: 32px">""").attr(
              "src", s"http://www.gravatar.com/avatar/${message.user.gravatarHash}?s=32")
        ),
        jQ("""<div class="media-body">""").append(
          jQ(s"""<small class="pull-right time"><i class="fa fa-clock-o"></i> $timeStampStr</small>"""),
          jQ("""<h5 class="media-heading">""").text(message.user.nick),
          jQ("""<small class="col-lg-10">""").text(message.text)
        )
      )
    )
    // scroll to new message
    jQ(".msg-wrap").scrollTop(jQ(".msg-wrap")(0).scrollHeight)
  }
}

class ProxyManager extends Actor {
  def receive = {
    case ConnectAs(user) =>
      context.watch(context.actorOf(
          Props(new ClientProxy("ws://localhost:9000/chat-ws-entry", context.parent))))

    case Disconnect =>
      context.children.foreach(context.stop(_))

    case Terminated(proxy) =>
      context.parent ! Disconnected
  }
}
