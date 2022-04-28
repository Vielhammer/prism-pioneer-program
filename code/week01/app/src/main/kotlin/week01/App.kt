package week01

import io.iohk.atala.prism.api.*
import io.iohk.atala.prism.api.models.AtalaOperationId
import io.iohk.atala.prism.api.models.AtalaOperationStatus
import io.iohk.atala.prism.api.node.*
import io.iohk.atala.prism.common.PrismSdkInternal
import io.iohk.atala.prism.crypto.Sha256Digest
import io.iohk.atala.prism.crypto.derivation.KeyDerivation
import io.iohk.atala.prism.crypto.derivation.MnemonicCode
import io.iohk.atala.prism.crypto.keys.ECKeyPair
import io.iohk.atala.prism.identity.*
import io.iohk.atala.prism.protos.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pbandk.ByteArr

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

        Thread.sleep(30000)
        status = runBlocking {
            nodePublicApi.getOperationStatus(operationId)
        }
    }
}

// Creates a list of potentially useful keys out of a mnemonic code
fun prepareKeysFromMnemonic(mnemonic: MnemonicCode, pass: String): Map<String, ECKeyPair> {
    val seed = KeyDerivation.binarySeed(mnemonic, pass)
    val issuerMasterKeyPair = KeyGenerator.deriveKeyFromFullPath(seed, 0, MasterKeyUsage, 0)
    val issuerIssuingKeyPair = KeyGenerator.deriveKeyFromFullPath(seed, 0, IssuingKeyUsage, 0)
    val issuerRevocationKeyPair = KeyGenerator.deriveKeyFromFullPath(seed, 0, RevocationKeyUsage, 0)
    return mapOf(
        Pair(PrismDid.DEFAULT_MASTER_KEY_ID, issuerMasterKeyPair),
        Pair(PrismDid.DEFAULT_ISSUING_KEY_ID, issuerIssuingKeyPair),
        Pair(PrismDid.DEFAULT_REVOCATION_KEY_ID, issuerRevocationKeyPair))
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
fun publishDid(unpublishedDid: LongFormPrismDid, keys: Map<String, ECKeyPair>): Pair<NodePayloadGenerator, Sha256Digest> {
    val did = unpublishedDid.asCanonical()

    var nodePayloadGenerator = NodePayloadGenerator(
            unpublishedDid,
            mapOf( PrismDid.DEFAULT_MASTER_KEY_ID to keys[PrismDid.DEFAULT_MASTER_KEY_ID]?.privateKey!!))

    // creation of CreateDID operation
    val createDidInfo = nodePayloadGenerator.createDid()

    // sending CreateDID operation to the ledger
    val createDidOperationId = runBlocking {
            nodeAuthApi.createDid(
                createDidInfo.payload,
                unpublishedDid,
                PrismDid.DEFAULT_MASTER_KEY_ID)
        }

    println(
        """
        - Sent a request to create a new DID to PRISM Node.
        - The transaction can take up to 10 minutes to be confirmed by the Cardano network.
        - Operation identifier: ${createDidOperationId.hexValue()}
        """.trimIndent())
    println()

    // Wait until Cardano network confirms the DID creation
    waitUntilConfirmed(nodeAuthApi, createDidOperationId)

    println(
        """
        - DID with id $did is created
        """.trimIndent())
    println()

    return Pair(nodePayloadGenerator, createDidInfo.operationHash)
}

@PrismSdkInternal
fun main() {
    println("Prism Tutorial")
    println()

    // Issuer claims an identity
    println("Issuer: Generates and registers a DID")
    val issuerKeys = prepareKeysFromMnemonic(KeyDerivation.randomMnemonicCode(), "passphrase")
    val issuerUnpublishedDid = PrismDid.buildLongFormFromMasterPublicKey(issuerKeys[PrismDid.DEFAULT_MASTER_KEY_ID]?.publicKey!!)
    var (issuerNodePayloadGenerator, issuingOperationHash) = publishDid(issuerUnpublishedDid, issuerKeys)
    // Holder generates its identity
    val holderKeys = prepareKeysFromMnemonic(KeyDerivation.randomMnemonicCode(), "secret")
    val holderUnpublishedDid = PrismDid.buildLongFormFromMasterPublicKey(holderKeys[PrismDid.DEFAULT_MASTER_KEY_ID]?.publicKey!!)
    println("Holder: DID generated: $holderUnpublishedDid")
    println()
    // Generator should contain the issuing key so let's create a new instance of it with this key inside
    issuerNodePayloadGenerator = NodePayloadGenerator(
        issuerNodePayloadGenerator.did,
        issuerNodePayloadGenerator.keys + (PrismDid.DEFAULT_ISSUING_KEY_ID to issuerKeys[PrismDid.DEFAULT_ISSUING_KEY_ID]?.privateKey!!))
    val issuingKeyInfo =
        PrismKeyInformation(
            DidPublicKey(PrismDid.DEFAULT_ISSUING_KEY_ID,
            IssuingKeyUsage,
            issuerKeys[PrismDid.DEFAULT_ISSUING_KEY_ID]?.publicKey!!))
    // creation of UpdateDid operation
    val addIssuingKeyDidInfo = issuerNodePayloadGenerator.updateDid(
        issuingOperationHash,
        PrismDid.DEFAULT_MASTER_KEY_ID,
        keysToAdd = arrayOf(issuingKeyInfo))
    // sending the operation to the ledger
    val addIssuingKeyOperationId = runBlocking {
            nodeAuthApi.updateDid(
                addIssuingKeyDidInfo.payload,
                issuerUnpublishedDid.asCanonical(),
                PrismDid.DEFAULT_MASTER_KEY_ID,
                issuingOperationHash,
                keysToAdd = arrayOf(issuingKeyInfo),
                keysToRevoke = arrayOf())
        }
    println(
        """
        Issuer: Add issuing key, the transaction can take up to 10 minutes to be confirmed by the Cardano network
        - IssuerDID = ${issuerUnpublishedDid.asCanonical()}
        - Add issuing key to DID operation identifier = ${addIssuingKeyOperationId.hexValue()}
        """.trimIndent())
    // Wait until Cardano network confirms the DID creation
    waitUntilConfirmed(nodeAuthApi, addIssuingKeyOperationId)
    println(
        """
        - DID with id ${issuerUnpublishedDid.asCanonical()} updated
        """.trimIndent())
    println()

    // Issuer generates a credential to Holder identified by its unpublished DID
    val credentialClaim = CredentialClaim(
            subjectDid = holderUnpublishedDid,
            content = JsonObject(mapOf(
                Pair("name", JsonPrimitive("Lars Brünjes")),
                Pair("certificate", JsonPrimitive("Certificate of PRISM SDK tutorial completion")))))
    val issueCredentialsInfo = issuerNodePayloadGenerator.issueCredentials(
            PrismDid.DEFAULT_ISSUING_KEY_ID,
            arrayOf(credentialClaim))
    val issueCredentialBatchOperationId = runBlocking {
            nodeAuthApi.issueCredentials(
                issueCredentialsInfo.payload,
                issuerUnpublishedDid.asCanonical(),
                PrismDid.DEFAULT_ISSUING_KEY_ID,
                issueCredentialsInfo.merkleRoot)
        }
    waitUntilConfirmed(nodeAuthApi, issueCredentialBatchOperationId)
    val holderSignedCredential = issueCredentialsInfo.credentialsAndProofs.first().signedCredential
    val holderCredentialMerkleProof = issueCredentialsInfo.credentialsAndProofs.first().inclusionProof
    println(
        """
        Issuer [${issuerUnpublishedDid.asCanonical()}] issued new credentials for the holder [$holderUnpublishedDid].
        - issueCredentialBatch operation identifier: ${issueCredentialBatchOperationId.hexValue()}
        - Credential content: ${holderSignedCredential.content}
        - Signed credential: ${holderSignedCredential.canonicalForm}
        - Inclusion proof (encoded): ${holderCredentialMerkleProof.encode()}
        - Batch id: ${issueCredentialsInfo.batchId}
        """.trimIndent())

    // Verifier, who owns credentialClam, can easily verify the validity of the credentials.
    println("Verifier: Verifying received credential using single convenience method")
    val credentialVerificationServiceResult = runBlocking {
            nodeAuthApi.verify(
                signedCredential = holderSignedCredential,
                merkleInclusionProof = holderCredentialMerkleProof)
        }
    require(credentialVerificationServiceResult.verificationErrors.isEmpty()) {
        "VerificationErrors should be empty"
    }

    // Generator should contain the revocation key so let's create a new instance of it with this key inside
    issuerNodePayloadGenerator = NodePayloadGenerator(
        issuerNodePayloadGenerator.did,
        issuerNodePayloadGenerator.keys + (PrismDid.DEFAULT_REVOCATION_KEY_ID to issuerKeys[PrismDid.DEFAULT_REVOCATION_KEY_ID]?.privateKey!!))
    // Issuer revokes the credential
    val revocationKeyInfo = PrismKeyInformation(
            DidPublicKey(PrismDid.DEFAULT_REVOCATION_KEY_ID,
            RevocationKeyUsage,
            issuerKeys[PrismDid.DEFAULT_REVOCATION_KEY_ID]?.publicKey!!))
    // creation of UpdateDID operation
    val addRevocationKeyDidInfo = issuerNodePayloadGenerator.updateDid(
            addIssuingKeyDidInfo.operationHash,
            PrismDid.DEFAULT_MASTER_KEY_ID,
            keysToAdd = arrayOf(revocationKeyInfo))
    // sending the operation to the ledger
    val addRevocationKeyOperationId = runBlocking {
            nodeAuthApi.updateDid(
                addRevocationKeyDidInfo.payload,
                issuerUnpublishedDid.asCanonical(),
                PrismDid.DEFAULT_MASTER_KEY_ID,
                addIssuingKeyDidInfo.operationHash,
                keysToAdd = arrayOf(revocationKeyInfo),
                arrayOf())
        }
    val revokeCredentialsInfo = issuerNodePayloadGenerator.revokeCredentials(
        PrismDid.DEFAULT_REVOCATION_KEY_ID,
        issueCredentialsInfo.operationHash,
        issueCredentialsInfo.batchId.id,
        arrayOf(holderSignedCredential.hash()))
    val revokeCredentialsOperationId = runBlocking {
            nodeAuthApi.revokeCredentials(
                revokeCredentialsInfo.payload,
                issuerUnpublishedDid.asCanonical(),
                PrismDid.DEFAULT_REVOCATION_KEY_ID,
                issueCredentialsInfo.operationHash,
                issueCredentialsInfo.batchId.id,
                arrayOf(holderSignedCredential.hash()))
        }
    println(
        """
        Issuer: Asked PRISM Node to revoke credentials. The transaction can take up to 10 minutes to be confirmed by the Cardano network
        - addRevocationKey operation identifier: ${addRevocationKeyOperationId.hexValue()}
        - revokeCredentials operation identifier: ${revokeCredentialsOperationId.hexValue()}
        """.trimIndent())
    println()
    waitUntilConfirmed(nodeAuthApi, revokeCredentialsOperationId)
    println("Credentials revoked")
    println()

    println("Verifier: Checking the credential validity again, expect an error explaining that the credential is revoked")
    val verifierReceivedCredentialRevocationTime2 = runBlocking {
            nodeAuthApi.getCredentialRevocationTime(
                batchId = issueCredentialsInfo.batchId.id,
                credentialHash = holderSignedCredential.hash())
        }
    // Verifier checks the credential validity (which fails)
    val credentialVerificationServiceResult2 = runBlocking {
            nodeAuthApi.verify(
                signedCredential = holderSignedCredential,
                merkleInclusionProof = holderCredentialMerkleProof)
        }
    require(
        credentialVerificationServiceResult2.verificationErrors.contains(
            VerificationError.CredentialWasRevokedOn(
                verifierReceivedCredentialRevocationTime2.ledgerData!!.timestampInfo)))
        { "CredentialWasRevokedOn error is expected" }
}
