package fssi.scp.ballot5.start1x.quorumA2
import org.scalatest.Suites

class QuorumPreparedA3Suites
    extends Suites(
      new AcceptMoreCommitA3Suite(),
      new VBlockingAcceptMoreA3Suite()
    ) {
  override def suiteName: String = "Quorum prepared A3"
}
