package fssi.scp.types

case class Statement[M <: Message](
    from: NodeID,
    slotIndex: SlotIndex,
    timestamp: Timestamp,
    quorumSet: QuorumSet,
    message: M
) {
  def to[N <: Message]: Statement[N] = this.asInstanceOf[Statement[N]]

  def withMessage[N <: Message](n: N): Statement[N] =
    Statement(from, slotIndex, timestamp, quorumSet, n)
}

object Statement {
  trait Implicits {
    import fssi.base.implicits._
    import fssi.scp.types.implicits._

    implicit def statementToBytes[M <: Message](statement: Statement[M]): Array[Byte] = {
      val from      = statement.from.asBytesValue.any
      val slotIndex = statement.slotIndex.asBytesValue.any
      val quorum    = statement.quorumSet.asBytesValue.any
      val message   = statement.message.asBytesValue.any
      val bytes     = (from ++ slotIndex ++ quorum ++ message).bytes
      bytes
    }
  }
}
