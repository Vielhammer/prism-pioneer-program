package updateDid

import io.iohk.atala.prism.api.KeyGenerator
import io.iohk.atala.prism.api.models.AtalaOperationId
import io.iohk.atala.prism.api.models.AtalaOperationStatus
import io.iohk.atala.prism.api.node.NodeAuthApiImpl
import io.iohk.atala.prism.api.node.NodePayloadGenerator
import io.iohk.atala.prism.api.node.NodePublicApi
import io.iohk.atala.prism.common.PrismSdkInternal
import io.iohk.atala.prism.crypto.Sha256Digest
import io.iohk.atala.prism.identity.*
import io.iohk.atala.prism.protos.GetOperationInfoRequest
import io.iohk.atala.prism.protos.GrpcClient
import io.iohk.atala.prism.protos.GrpcOptions
import io.iohk.atala.prism.protos.NodeServiceCoroutine
import kotlinx.coroutines.runBlocking
import pbandk.ByteArr
import java.io.File

// Waits until an operation is confirmed by the Cardano network.
// NOTE: Confirmation doesn't necessarily mean that operation was applied.
// For example, it could be rejected because of an incorrect signature or other reasons.
@PrismSdkInternal
fun waitUntilConfirmed(nodePublicApi: NodePublicApi, operationId: AtalaOperationId) {
    var tid = ""
    var status = runBlocking {
            nodePublicApi.getOperationStatus(operationId)
    }
    while (status != AtalaOperationStatus.CONFIRMED_AND_APPLIED &&
        status != AtalaOperationStatus.CONFIRMED_AND_REJECTED
    ) {
        println("Current operation status: ${AtalaOperationStatus.asString(status)}")
        if (tid.isNullOrEmpty()) {
            tid = transactionId(operationId)
            if (!tid.isNullOrEmpty()) {
                println("Transaction id: $tid")
                println("Track the transaction in:\n- https://explorer.cardano-testnet.iohkdev.io/en/transaction?id=$tid")
            }
        }

        Thread.sleep(10000)
        status = runBlocking {
            nodePublicApi.getOperationStatus(operationId)
        }
    }
}

val environment = "ppp.atalaprism.io"
val grpcOptions = GrpcOptions("https", environment, 50053)
val nodeAuthApi = NodeAuthApiImpl(grpcOptions)

@PrismSdkInternal
fun transactionId(oid: AtalaOperationId): String {
    val node = NodeServiceCoroutine.Client(GrpcClient(grpcOptions))
    val response = runBlocking {
            node.GetOperationInfo(GetOperationInfoRequest(ByteArr(oid.value())))
        }
    return response.transactionId
}

@PrismSdkInternal
fun main(args: Array<String>) {
    val seedFile = try { args[0] } catch (e: Exception) { throw Exception("expected seed file path as first argument") }
    val oldHashFile = try { args[1] } catch (e: Exception) { throw Exception("expected old hash file path as second argument") }
    val newHashFile = try { args[2] } catch (e: Exception) { throw Exception("expected new hash file path as third argument") }

    val seed = File(seedFile).readBytes()
    println("read seed from file $seedFile")
    val oldHash = Sha256Digest.fromHex(File(oldHashFile).readText())
    println("read old hash from $oldHashFile: ${oldHash.hexValue}")

    val masterKeyPair = KeyGenerator.deriveKeyFromFullPath(seed, 0, MasterKeyUsage, 0)
    val issuingKeyPair = KeyGenerator.deriveKeyFromFullPath(seed, 0, IssuingKeyUsage, 0)
    val unpublishedDid = PrismDid.buildLongFormFromMasterPublicKey(masterKeyPair.publicKey)

    val didCanonical = unpublishedDid.asCanonical().did
    val didLongForm = unpublishedDid.did

    println("canonical: $didCanonical")
    println("long form: $didLongForm")
    println()

    println("updating DID...")
    var nodePayloadGenerator = NodePayloadGenerator(
            unpublishedDid,
            mapOf(PrismDid.DEFAULT_MASTER_KEY_ID to masterKeyPair.privateKey))
    val issuingKeyInfo = PrismKeyInformation(
            DidPublicKey( PrismDid.DEFAULT_ISSUING_KEY_ID,
            IssuingKeyUsage,
            issuingKeyPair.publicKey))
    val updateDidInfo = nodePayloadGenerator.updateDid(
            previousHash = oldHash,
            masterKeyId = PrismDid.DEFAULT_MASTER_KEY_ID,
            keysToAdd = arrayOf(issuingKeyInfo))
    val updateDidOperationId = runBlocking {
            nodeAuthApi.updateDid(
                payload = updateDidInfo.payload,
                did = unpublishedDid.asCanonical(),
                masterKeyId = PrismDid.DEFAULT_MASTER_KEY_ID,
                previousOperationHash = oldHash,
                keysToAdd = arrayOf(issuingKeyInfo),
                keysToRevoke = arrayOf())
        }

    println(
        """
        - Sent a request to update the DID to PRISM Node.
        - The transaction can take up to 10 minutes to be confirmed by the Cardano network.
        - Operation identifier: ${updateDidOperationId.hexValue()}
        """.trimIndent())
    println()
    waitUntilConfirmed(nodeAuthApi, updateDidOperationId)

    val status = runBlocking { nodeAuthApi.getOperationStatus(updateDidOperationId) }
    require(status == AtalaOperationStatus.CONFIRMED_AND_APPLIED) {
        "expected updating to be applied"
    }

    println("DID updated")
    val newHash = updateDidInfo.operationHash.hexValue
    File(newHashFile).writeText(newHash)
    println("wrote new hash $newHash to file $newHashFile")
    println()
}
