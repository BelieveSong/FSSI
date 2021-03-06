package fssi
package interpreter

import java.io._
import java.nio.file.{Path, Paths}

import fssi.interpreter.Configuration.CoreNodeConfig
import fssi.interpreter.Configuration._

sealed trait Setting

object Setting {

  /** default setting
    */
  case object DefaultSetting extends Setting

  /** Setting for command line tool
    */
  case class ToolSetting() extends Setting {
    def contractTempDir: Path = Paths.get(System.getProperty("user.home"), ".fssi")
  }

  /** P2P node setting
    */
  sealed trait P2PNodeSetting extends Setting {
    def workingDir: File
  }

  /** setting for running core node
    */
  case class CoreNodeSetting(workingDir: File) extends P2PNodeSetting {
    private lazy val configFile: File = new File(workingDir, "fssi.conf")
    lazy val config: CoreNodeConfig   = configFile.asCoreNodeConfig

    def isFullFunctioning: Boolean = config.mode.equalsIgnoreCase("full")
  }

  case class EdgeNodeSetting(workingDir: File) extends P2PNodeSetting {
    private lazy val configFile: File = new File(workingDir, "fssi.conf")
    lazy val config: EdgeNodeConfig   = configFile.asEdgeNodeConfig
  }

  def defaultInstance: Setting = DefaultSetting

}
