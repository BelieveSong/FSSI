package fssi
package interpreter

import jsonCodecs._
import io.circe._
import io.circe.syntax._
import io.circe.parser._

import utils._, trie._, Trie.ops._
import types._, implicits._
import ast._
import java.io._
import org.slf4j._

class BlockStoreHandler extends BlockStore.Handler[Stack] {
  private val log = LoggerFactory.getLogger(getClass)

  private val blockFileDirName      = "block"
  private val blockTrie: Once[Trie] = Once.empty

  // current block height key in block trie
  private val KEY_CURRENT_HEIGHT: Array[Byte] = Array(0)

  // current undetermined block
  private val undeterminedBlockRef: java.util.concurrent.atomic.AtomicReference[Option[Block]] =
    new java.util.concurrent.atomic.AtomicReference(None)

  /** initialize a data directory to be a block store
    * @param dataDir directory to save block.
    */
  override def initializeBlockStore(dataDir: File): Stack[Unit] = Stack {
    val path = new File(dataDir, blockFileDirName)
    path.mkdirs()
    blockTrie := Trie.empty(levelDBStore(path))
  }

  /** save block, before saving, invoker should guarantee that the block is legal
    * which means, block has correct hashes, correct height etc.
    * @param block a legal block.
    */
  override def saveBlock(block: Block): Stack[Unit] = Stack {
    // save block in blockTrie
    // block will saved as json
    // key is the height of the block
    blockTrie foreach { trie =>
      // update current height
      trie.store.save(KEY_CURRENT_HEIGHT, block.height.toByteArray)
      log.info(s"saved current height: ${block.height}")

      // update height -> block
      val key   = block.height.toByteArray
      val value = block.asJson.noSpaces.getBytes("utf-8")
      trie.store.save(key, value)
      log.info(s"saved block for ${block.height}")
    }
  }

  /** get latest saved block, which block must have been gone through consensus
    */
  override def getLatestDeterminedBlock(): Stack[Block] = Stack { setting =>
    blockTrie.map { trie =>
      val height = trie.getValue(KEY_CURRENT_HEIGHT).get
      val blockEither = for {
        valueJson <- parse(new String(trie.getValue(height).get, "utf-8"))
        block     <- valueJson.as[Block]
      } yield block
      blockEither.right.get
    }.unsafe
  }

  /** get current undetermined block, and append a transaction into it, which is on consensus
    * @param determinedBlock the latest determined block which current undetermined block is based on.
    */
  override def appendTransactionToUnDeterminedBlock(determinedBlock: Block,
                                                    transaction: Transaction): Stack[Block] =
    Stack { setting =>
      /*
      if (undetermindedBlockRef.get.isEmpty) {
        
      }
       */

      ???
    }
}

object BlockStoreHandler {
  private val instance = new BlockStoreHandler()

  trait Implicits {
    implicit val blockStoreHandlerInstance: BlockStoreHandler = instance
  }
}