package fssi
package scp
package ast
package uc
import bigknife.sop._
import bigknife.sop.implicits._
import fssi.scp.types.{NodeID, QuorumSet, SlotIndex, Value}

trait InitializeProgram[F[_]] extends SCP[F] with BaseProgram[F] {

  import model._

  def initialize(nodeId: NodeID,
                 quorumSet: QuorumSet,
                 currentHeight: BigInt,
                 fakeValue: Value): SP[F, Unit] = {
    for {
      _ <- nodeService.cacheNodeQuorumSet(nodeId, quorumSet)
      _ <- nominateFakeValue(nodeId, SlotIndex(currentHeight + 1), fakeValue)
      _ <- broadcastMessageRegularly(SlotIndex(currentHeight + 1))
    } yield ()
  }

  def nominateFakeValue(nodeId: NodeID, slotIndex: SlotIndex, fakeValue: Value): SP[F, Unit] = {
    for {
      _ <- handleAppRequest(nodeId, slotIndex, fakeValue, fakeValue)
    } yield ()
  }
}
