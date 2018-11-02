package fssi.store

import types._
import java.io._

import jetbrains.exodus.ArrayByteIterable
import jetbrains.exodus.env._
import org.slf4j.LoggerFactory

trait BlockChainStorage {
  type This = this.type
  private[store] def readOnlyStore(key: StoreKey): KVStore
  private[store] val transactor: Transactor

  def init(): Unit
  def close(): Unit
  def name: String

  def get(key: StoreKey): Option[StoreValue] = _get(readOnlyStore(key), key)

  def put(key: StoreKey, value: Array[Byte]): Either[Throwable, Unit] = {
    transactor.transact { store =>
      _put(store, key, value) match {
        case Left(t) => throw t
        case Right(x) => x
      }
    }
  }

  def puts(kvs: Map[StoreKey, Array[Byte]]): Either[Throwable, Unit] = {
    transactor.transact { store =>
      kvs.foldLeft(Right(()): Either[Throwable, Unit]) {
        case (acc, (k, v)) =>
          acc match {
            case x @ Left(_) => x
            case _           => _put(store, k, v)
          }
      }
      ()
    }
  }

  trait ReadWriter {
    def get(key: StoreKey): Option[StoreValue]
    def put(key: StoreKey, value: Array[Byte]): Either[Throwable, Unit]
  }

  def transact[A](f: ReadWriter => A): Either[Throwable, A] = transactor.transact { store =>
    val rw = new ReadWriter {
      override def get(key: StoreKey): Option[StoreValue] = _get(store, key)
      override def put(key: StoreKey, value: Array[Byte]): Either[Throwable, Unit] =
        _put(store, key, value)
    }
    f(rw)
  }

  /** combine another store into this store
    */
  def combine(other: BlockChainStorage): Either[Throwable, Unit]


  /** satisfy merkle tree
    * when put a key with value, there are some effects:
    * 0. self node, with bytes and hash, hash should be re-computed by children's hash and it's value
    * 1. previousLevel(parentLevel) add a children key
    * 2. parent node hash affected recursively
    */
  private def _put(store: KVStore, key: StoreKey, value: Array[Byte]): Either[Throwable, Unit] = {
    scala.util.Try {
      store.put(key.withTag("bytes").bytesValue, value)
      // update hash recursively to upside, satisfy merkle tree
      updateHash(store, key)
      // update parent's childrenKeys
      key.previousLevel.foreach {parent =>
        val parentChildrenKey = parent.withTag("children").bytesValue
        val children = store.get(parentChildrenKey)
          .map(StoreKeySet.fromBytes)
          .getOrElse(StoreKeySet.empty)
        val newKey = StoreKeySet.toBytes(children + key)
        store.put(parentChildrenKey, newKey)
      }
      ()
    }.toEither
  }

  private def sha256(source: Array[Byte]): Array[Byte] = {
    java.security.MessageDigest.getInstance("SHA-256").digest(source)
  }

  private def updateHash(store: KVStore, key: StoreKey): Unit = {
    // ensure value has been put
    val value = store.get(key.withTag("bytes").bytesValue).get
    val hash: Array[Byte] = {
        val source = store.get(key.withTag("children").bytesValue)
          .map(StoreKeySet.fromBytes)
          .map {keySet =>
            keySet.foldLeft(Array.emptyByteArray) {(acc, n) =>
              acc ++ store.get(n.withTag("hash").bytesValue).get
            }
          }
          .map(_ ++ value)
          .getOrElse(value)
        sha256(source)
    }
    store.put(key.withTag("hash").bytesValue, hash)
    val parent = key.previousLevel
    if (parent.nonEmpty) updateHash(store, parent.get)
    else ()
  }

  private def _get(store: KVStore, key: StoreKey): Option[StoreValue] = {
    val bytesKey        = key.withTag("bytes").bytesValue
    val childrenKeysKey = key.withTag("children").bytesValue
    val hashKey         = key.withTag("hash").bytesValue
    for {
      bytes        <- store.get(bytesKey)
      childrenKeys <- store.get(childrenKeysKey).orElse(Option(Array.emptyByteArray))
      hash         <- store.get(hashKey)
    } yield StoreValue(bytes, StoreKeySet.fromBytes(childrenKeys), hash)
  }

}

object BlockChainStorage {
  private val log = LoggerFactory.getLogger(getClass)

  def xodus(root: File, storeName: String): BlockChainStorage = new BlockChainStorage {
    private val env                                       = Environments.newInstance(root)

    override private[store] def readOnlyStore(key: StoreKey): XodusStore = {
      new XodusStore(name, None, env)
    }
    override def name: String = storeName
    override private[store] val transactor = new Transactor {

      /** `f` is a tranction, if `f` succeed, the transaction should be committed, or rollbacked.
        */
      override def transact[A](f: KVStore => A): Either[Throwable, A] = {
        val transaction = env.beginTransaction()
        val transStore  = new XodusStore(name, Some(transaction), env)
        try {
          def _loop(): A = {
            val r = f(transStore)
            if (transaction.flush()) r
            else {
              transaction.revert()
              _loop()
            }
          }
          val a = _loop()
          transaction.commit()
          Right(a)
        } catch {
          case x: Throwable =>
            transaction.abort()
            Left(x)
        }

      }
    }

    override def init(): Unit = ()
    override def close(): Unit = {
      env.close()
    }

    override def combine(other: BlockChainStorage): Either[Throwable, Unit] = {
      ???
    }
  }

}
