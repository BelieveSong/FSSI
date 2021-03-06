package fssi.wallet.pro.ch01

import scalafx._
import animation._
import application._
import geometry._
import scene._
import util._
import text._
import paint._
import image._
import shape._
import scalafx.application.JFXApp.PrimaryStage

object HelloEarthRiseMain extends JFXApp {
  val message = """Earthrise at Christmas:
[Forty] years ago this Christmas, a turbulent world
looked to the heavens for a unique view of our home
planet. This photo of Earthrise over the lunar horizon
was taken by the Apollo 8 crew in December 1968, showing
Earth for the first time as it appears from deep space.
Astronauts Frank Borman, Jim Lovell and William Anders
had become the first humans to leave Earth orbit,
entering lunar orbit on Christmas Eve. In a historic live
broadcast that night, the crew took turns reading from
the Book of Genesis, closing with a holiday wish from
Commander Borman: "We close with good night, good luck,
a Merry Christmas, and God bless all of you -- all of
you on the good Earth." """.replace("\n", "")

  val textRef = new Text {
    layoutY = 100
    textOrigin = VPos.Top
    textAlignment = TextAlignment.Justify
    wrappingWidth = 400
    text = message
    fill = Color.rgb(187, 195, 107)
    font = Font.font("SanSerif", FontWeight.Bold, 24.0)
  }

  stage = new PrimaryStage {
    title = "Hello, EarthRise"
    scene = new Scene(516, 387) {
      content = List(
        new ImageView(image = new Image("http://projavafx.com/images/earthrise.jpg")),
        new Group {
          layoutX = 50
          layoutY = 180
          children = List(textRef)
          clip = Rectangle(430, 85)
        }
      )
    }
  }

  new TranslateTransition {
    cycleCount = Timeline.Indefinite
    duration = Duration(75000)
    node = textRef
    toY = -820
    interpolator = Interpolator.Linear
  }.play()
}
