package fssi
package interpreter
import java.io.File

import fssi.interpreter.Configuration._
import fssi.scp.types.QuorumSet
import fssi.types.biz.Account.SecretKey
import fssi.types.biz.{Account, Node}

case class Configuration(
    coreNodeConfig: CoreNodeConfig,
    edgeNodeConfig: EdgeNodeConfig
)

object Configuration {
  sealed trait NetWorkConfig {
    def host: String
    def port: Int
  }

  trait P2PConfig extends NetWorkConfig {
    def seeds: Vector[Node.Addr]
    def account: (Account, SecretKey)
  }

  case class ConsensusConfig(host: String,
                             port: Int,
                             seeds: Vector[Node.Addr],
                             account: (Account, SecretKey),
                             quorumSet: QuorumSet,
                             maxTimeoutSeconds: Long,
                             maxNominatingTimes: Int,
                             broadcastTimeout: Long,
                             maxTransactionSizeInBlock: Int,
                             maxConsensusWaitTimeout: Long)
      extends P2PConfig

  case class ApplicationConfig(host: String,
                               port: Int,
                               seeds: Vector[Node.Addr],
                               account: (Account, SecretKey))
      extends P2PConfig

  case class CoreNodeConfig(chainId: String,
                            mode: String,
                            consensusConfig: ConsensusConfig,
                            applicationConfig: ApplicationConfig)

  case class JsonRPCConfig(host: String, port: Int) extends NetWorkConfig

  case class EdgeNodeConfig(chainId: String,
                            jsonRPCConfig: JsonRPCConfig,
                            applicationConfig: ApplicationConfig)

  final case class ConfigurationWrapper(file: File) {
    def asConfiguration: Configuration   = configFileToConfiguration(file)
    def asCoreNodeConfig: CoreNodeConfig = configFileToCoreNodeConfig(file)
    def asEdgeNodeConfig: EdgeNodeConfig = configFileToEdgeNodeConfig(file)
  }

  implicit def toWrapper(file: File): ConfigurationWrapper = ConfigurationWrapper(file)

  implicit def configFileToCoreNodeConfig(configFile: File): CoreNodeConfig = {
    val configReader = ConfigReader(configFile)
    import configReader._
    val mode = core.mode
    val coreConsensusConfig = ConsensusConfig(
      core.consensus.host,
      core.consensus.port,
      core.consensus.seeds,
      core.consensus.account,
      core.consensus.scp.quorums,
      core.consensus.scp.maxTimeoutSeconds,
      core.consensus.scp.maxNominatingTimes,
      core.consensus.scp.broadcastTimeout,
      core.consensus.scp.maxTransactionSizeInBlock,
      core.consensus.scp.maxConsensusWaitTimeout
    )
    val coreApplicationConfig = ApplicationConfig(core.application.host,
                                                  core.application.port,
                                                  core.application.seeds,
                                                  core.application.account)
    CoreNodeConfig(chainId, mode, coreConsensusConfig, coreApplicationConfig)
  }

  implicit def configFileToEdgeNodeConfig(configFile: File): EdgeNodeConfig = {
    val configReader = ConfigReader(configFile)
    import configReader._
    val edgeJsonRPCConfig = JsonRPCConfig(edge.client.jsonRPC.host, edge.client.jsonRPC.port)
    val edgeApplicationConfig = ApplicationConfig(edge.application.host,
                                                  edge.application.port,
                                                  edge.application.seeds,
                                                  edge.application.account)
    EdgeNodeConfig(chainId, edgeJsonRPCConfig, edgeApplicationConfig)
  }

  implicit def configFileToConfiguration(configFile: File): Configuration = {
    val coreNodeConfig = configFileToCoreNodeConfig(configFile)
    val edgeNodeConfig = configFileToEdgeNodeConfig(configFile)
    Configuration(coreNodeConfig, edgeNodeConfig)
  }
}
