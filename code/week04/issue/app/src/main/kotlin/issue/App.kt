package issue

import io.iohk.atala.prism.api.CredentialClaim
import io.iohk.atala.prism.api.KeyGenerator
import io.iohk.atala.prism.api.models.AtalaOperationId
import io.iohk.atala.prism.api.models.AtalaOperationStatus
import io.iohk.atala.prism.api.node.NodeAuthApiImpl
import io.iohk.atala.prism.api.node.NodePayloadGenerator
import io.iohk.atala.prism.api.node.NodePublicApi
import io.iohk.atala.prism.common.PrismSdkInternal
import io.iohk.atala.prism.crypto.derivation.KeyDerivation
import io.iohk.atala.prism.identity.IssuingKeyUsage
import io.iohk.atala.prism.identity.MasterKeyUsage
import io.iohk.atala.prism.identity.PrismDid
import io.iohk.atala.prism.identity.RevocationKeyUsage
import io.iohk.atala.prism.protos.GetOperationInfoRequest
import io.iohk.atala.prism.protos.GrpcClient
import io.iohk.atala.prism.protos.GrpcOptions
import io.iohk.atala.prism.protos.NodeServiceCoroutine
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    val hashFile = try { args[1] } catch (e: Exception) { throw Exception("expected hash file path as second argument") }
    val issuerSeed = File(seedFile).readBytes()
    println("read issuer seed from file $seedFile")

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

    val names = arrayOf("Alice", "Bob", "Charlie")
    val claims = mutableListOf<CredentialClaim>()
    for (name in names) {
        val holderSeed = KeyDerivation.binarySeed(KeyDerivation.randomMnemonicCode(), "passphrase")
        val holderMasterKeyPair = KeyGenerator.deriveKeyFromFullPath(holderSeed, 0, MasterKeyUsage, 0)
        val holderUnpublishedDid = PrismDid.buildLongFormFromMasterPublicKey(holderMasterKeyPair.publicKey)

        val holderDidCanonical = holderUnpublishedDid.asCanonical().did
        val holderDidLongForm = holderUnpublishedDid.did

        println("$name canonical: $holderDidCanonical")
        println("$name long form: $holderDidLongForm")
        println()

        val credentialClaim = CredentialClaim(
                subjectDid = holderUnpublishedDid,
                content = JsonObject(mapOf(
                        Pair("name", JsonPrimitive(name)),
                        Pair("degree", JsonPrimitive("Atala Prism Pioneer")),
                        Pair("year", JsonPrimitive(2021)))))

        claims.add(credentialClaim)
    }

    val nodePayloadGenerator = NodePayloadGenerator(
            issuerUnpublishedDid,
            mapOf(PrismDid.DEFAULT_ISSUING_KEY_ID to issuerIssuingKeyPair.privateKey))

    val credentialsInfo = nodePayloadGenerator.issueCredentials(
            PrismDid.DEFAULT_ISSUING_KEY_ID,
            claims.toTypedArray())

    println("batchId: ${credentialsInfo.batchId.id}")
    for (info in credentialsInfo.credentialsAndProofs) {
        println(" - ${info.signedCredential.hash().hexValue}")
    }
    println()

    val issueCredentialsOperationId = runBlocking {
            nodeAuthApi.issueCredentials(
                credentialsInfo.payload,
                issuerUnpublishedDid.asCanonical(),
                PrismDid.DEFAULT_ISSUING_KEY_ID,
                credentialsInfo.merkleRoot)
        }

    println(
            """
            - Sent a request to issue credentials to PRISM Node.
            - The transaction can take up to 10 minutes to be confirmed by the Cardano network.
            - Operation identifier: ${issueCredentialsOperationId.hexValue()}
            """.trimIndent())
    println()
    waitUntilConfirmed(nodeAuthApi, issueCredentialsOperationId)

    val status = runBlocking { nodeAuthApi.getOperationStatus(issueCredentialsOperationId) }
    require(status == AtalaOperationStatus.CONFIRMED_AND_APPLIED) {
        "expected credentials to be issued"
    }

    println("credentials issued")
    println()

    val hash = credentialsInfo.operationHash.hexValue
    println("operation hash: $hash")
    File(hashFile).writeText(hash)
    println("wrote old hash to file $hashFile")
    println()
}
