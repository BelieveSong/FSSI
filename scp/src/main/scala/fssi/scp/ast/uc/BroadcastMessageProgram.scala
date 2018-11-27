package fssi
package scp
package ast
package uc
import types._
import bigknife.sop._
import bigknife.sop.implicits._

trait BroadcastMessageProgram[F[_]] extends SCP[F] with BaseProgram[F] {
  import model.nodeStore._
  import model.nodeService._
  import model.applicationService._
  import model.logService._

  /** broadcast nominate and ballot message until externalized
    */
  def broadcastMessageRegularly(slotIndex: SlotIndex): SP[F, Unit] = {
    for {
      timeout         <- broadcastTimeout()
      _               <- debug(s"broadcast $slotIndex message regularly,timeout: $timeout millis")
      nominateMessage <- nominateEnvelope(slotIndex)
      _ <- ifThen(nominateMessage.nonEmpty) {
        for {
          _ <- broadcastEnvelope(slotIndex, nominateMessage.get)
          _ <- infoSentEnvelope(nominateMessage.get)
        } yield ()
      }

      ballotMessage <- ballotEnvelope(slotIndex)
      _ <- ifThen(ballotMessage.nonEmpty) {
        for {
          _ <- broadcastEnvelope(slotIndex, ballotMessage.get)
          _ <- infoSentEnvelope(nominateMessage.get)
        } yield ()
      }
      _ <- delayExecuteProgram(BROADCAST_TIMER, broadcastMessageRegularly(slotIndex), timeout)
    } yield ()
  }

  def stopBroadcastMessage(): SP[F, Unit] = {
    for {
      _ <- stopDelayTimer(BROADCAST_TIMER)
    } yield ()
  }
}
