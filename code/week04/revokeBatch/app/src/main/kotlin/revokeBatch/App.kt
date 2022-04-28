package revokeBatch

import io.iohk.atala.prism.api.KeyGenerator
import io.iohk.atala.prism.api.models.AtalaOperationId
import io.iohk.atala.prism.api.models.AtalaOperationStatus
import io.iohk.atala.prism.api.node.NodeAuthApiImpl
import io.iohk.atala.prism.api.node.NodePayloadGenerator
import io.iohk.atala.prism.api.node.NodePublicApi
import io.iohk.atala.prism.common.PrismSdkInternal
import io.iohk.atala.prism.credentials.CredentialBatchId
import io.iohk.atala.prism.crypto.Sha256Digest
import io.iohk.atala.prism.identity.IssuingKeyUsage
import io.iohk.atala.prism.identity.MasterKeyUsage
import io.iohk.atala.prism.identity.PrismDid
import io.iohk.atala.prism.identity.RevocationKeyUsage
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
    val batchId = try { CredentialBatchId.fromString(args[2])!! } catch (e: Exception) {throw Exception("expected batch id as fourth argument")}

    val issuerSeed = File(seedFile).readBytes()
    println("read issuer seed from file $seedFile")
    val oldHash = Sha256Digest.fromHex(File(oldHashFile).readText())
    println("read old hash from $oldHashFile: ${oldHash.hexValue}")

    val issuerMasterKeyPair = KeyGenerator.deriveKeyFromFullPath(issuerSeed, 0, MasterKeyUsage, 0)
    val issuerIssuingKeyPair = KeyGenerator.deriveKeyFromFullPath(issuerSeed, 0, IssuingKeyUsage, 0)
    val issuerRevocationKeyPair = KeyGenerator.deriveKeyFromFullPath(issuerSeed, 0, RevocationKeyUsage, 0)
    val issuerUnpublishedDid = PrismDid.buildExperimentalLongFormFromKeys(
            issuerMasterKeyPair.publicKey,
            issuerIssuingKeyPair.publicKey,
            issuerRevocationKeyPair.publicKey)

    val issuerDidCanonical = issuerUnpublishedDid.asCanonical().did
    val issuerDidLongForm = issuerUnpublishedDid.did

    println("issuer canonical: $issuerDidCanonical")
    println("issuer long form: $issuerDidLongForm")
    println()

    val nodePayloadGenerator = NodePayloadGenerator(
            issuerUnpublishedDid,
            mapOf(PrismDid.DEFAULT_REVOCATION_KEY_ID to issuerRevocationKeyPair.privateKey))

    val revokeInfo = nodePayloadGenerator.revokeCredentials(
            PrismDid.DEFAULT_REVOCATION_KEY_ID,
            oldHash,
            batchId.id,
            arrayOf())

    val revokeOperationId = runBlocking {
            nodeAuthApi.revokeCredentials(
                    revokeInfo.payload,
                    issuerUnpublishedDid.asCanonical(),
                    PrismDid.DEFAULT_REVOCATION_KEY_ID,
                    oldHash,
                    batchId.id,
                    arrayOf())
        }

    println(
            """
            - Sent a request to revoke batch to PRISM Node.
            - The transaction can take up to 10 minutes to be confirmed by the Cardano network.
            - Operation identifier: ${revokeOperationId.hexValue()}
            """.trimIndent())
    println()
    waitUntilConfirmed(nodeAuthApi, revokeOperationId)

    val status = runBlocking { nodeAuthApi.getOperationStatus(revokeOperationId) }
    require(status == AtalaOperationStatus.CONFIRMED_AND_APPLIED) {
        "expected batch to be revoked"
    }

    println("batch revoked")
    println()
}