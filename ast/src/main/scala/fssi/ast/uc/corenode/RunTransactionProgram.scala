package fssi.ast
package uc

import bigknife.sop._
import bigknife.sop.implicits._
import fssi.types.biz.Contract.UserContract.Method
import fssi.types.biz.{Receipt, Transaction}
import fssi.types.biz.Transaction.{Deploy, Run, Transfer}
import fssi.types.implicits._

trait RunTransactionProgram[F[_]] extends CoreNodeProgram[F] with BaseProgram[F] {
  import model._

  def runTransaction(transaction: Transaction): SP[F, Receipt] = {
    for {
      verifyResult <- crypto.verifyTransactionSignature(transaction)
      _            <- requireM(verifyResult(), new RuntimeException("transaction signature tampered"))
      r <- transaction match {
        case transfer: Transfer => runTransferTransaction(transfer)
        case deploy: Deploy     => runDeployTransaction(deploy)
        case run: Run           => runRunTransaction(run)
      }
    } yield r
  }

  private def runTransferTransaction(transfer: Transfer): SP[F, Receipt] = {
    val message =
      s"payer ${transfer.payer.asBytesValue.bcBase58} transacts (${transfer.token}) to payee ${transfer.payee.asBytesValue.bcBase58}"
    for {
      _                <- log.info(message)
      duplicated       <- store.isTransactionDuplicated(transfer)
      _                <- requireM(!duplicated, new RuntimeException("transaction id duplicated"))
      snapshotOrFailed <- store.snapshotTransaction(transfer)
      receipt <- ifM(
        snapshotOrFailed.isRight.pureSP[F], {
          val info = s"$message success"
          for {
            _ <- log.info(info)
            log = Receipt.Log("INFO", info)
          } yield Receipt(transfer.id, success = true, Vector(log), 0)
        }
      )({
        val info = s"$message failed"
        for {
          _ <- log.error(info, Some(snapshotOrFailed.left.get))
          log = Receipt.Log("ERROR", info)
        } yield Receipt(transfer.id, success = false, Vector(log), 0)
      })
    } yield receipt
  }

  private def runDeployTransaction(deploy: Deploy): SP[F, Receipt] = {
    val owner = deploy.owner.asBytesValue.bcBase58
    val name  = deploy.contract.name.asBytesValue.bcBase58
    for {
      _          <- log.info(s"owner $owner starts deploying contract $name")
      duplicated <- store.isTransactionDuplicated(deploy)
      _          <- requireM(!duplicated, new RuntimeException("transaction id duplicated"))
      passed     <- store.canDeployNewTransaction(deploy)
      r <- ifM(
        passed.pureSP[F], {
          for {
            _ <- store.snapshotTransaction(deploy)
            _ <- log.info(s"owner $owner deployed contract $name success")
          } yield Receipt(deploy.id, success = true, Vector.empty, 0)
        }
      ) {
        for {
          _ <- log.error(
            s"owner $owner deployed contract $name failed, please check contract owner and version")
        } yield Receipt(deploy.id, success = false, Vector.empty, 0)
      }
    } yield r
  }

  private def runRunTransaction(run: Run): SP[F, Receipt] = {
    val caller          = run.caller.asBytesValue.bcBase58
    val owner           = run.owner.asBytesValue.bcBase58
    val contractName    = run.contractName.asBytesValue.bcBase58
    val contractVersion = run.contractVersion
    val info =
      s"caller $caller start running contract $contractName belonged to $owner at version $contractVersion"
    for {
      _ <- log.info(info)
      userContractCodeOpt <- store.loadContractCode(run.owner,
                                                    run.contractName,
                                                    run.contractVersion)
      _ <- requireM(
        userContractCodeOpt.nonEmpty,
        new RuntimeException(
          s"contract $contractName at version $contractVersion published by $owner not found")
      )
      kvStore    <- store.prepareKVStore(run.caller, run.contractName, run.contractVersion)
      tokenQuery <- store.prepareTokenQuery()
      context    <- store.createContextInstance(kvStore, tokenQuery, run.caller)
      result <- contract.invokeContract(context,
                                        userContractCodeOpt.get,
                                        Method(run.methodAlias, ""),
                                        run.contractParameter)
      r <- ifM(
        result.isRight.pureSP[F], {
          for {
            _ <- store.snapshotTransaction(run)
            msg = s"caller $caller run contract $contractName belonged to $owner at version $contractVersion success"
            _ <- log.info(msg)
            logs = Vector(Receipt.Log(label = "INFO", info), Receipt.Log("INFO", msg))
          } yield Receipt(run.id, success = true, logs, 0)
        }
      ) {
        val error =
          s"caller $caller run contract $contractName belonged to $owner at version $contractVersion failed: ${result.left.get.getMessage}"
        for {
          _ <- log.error(error)
          logs = Vector(Receipt.Log("INFO", info), Receipt.Log("ERROR", error))
        } yield Receipt(run.id, success = false, logs, 0)
      }
    } yield r
  }
}
