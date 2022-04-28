package credential

import io.iohk.atala.prism.api.CredentialClaim
import io.iohk.atala.prism.api.KeyGenerator
import io.iohk.atala.prism.api.node.NodePayloadGenerator
import io.iohk.atala.prism.crypto.derivation.KeyDerivation
import io.iohk.atala.prism.identity.IssuingKeyUsage
import io.iohk.atala.prism.identity.MasterKeyUsage
import io.iohk.atala.prism.identity.PrismDid
import io.iohk.atala.prism.identity.RevocationKeyUsage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

fun main() {
    val issuerSeed = KeyDerivation.binarySeed(KeyDerivation.randomMnemonicCode(), "passphrase")
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

    val holderSeed = KeyDerivation.binarySeed(KeyDerivation.randomMnemonicCode(), "passphrase")
    val holderMasterKeyPair = KeyGenerator.deriveKeyFromFullPath(holderSeed, 0, MasterKeyUsage, 0)
    val holderUnpublishedDid = PrismDid.buildLongFormFromMasterPublicKey(holderMasterKeyPair.publicKey)

    val holderDidCanonical = holderUnpublishedDid.asCanonical().did
    val holderDidLongForm = holderUnpublishedDid.did

    println("holder canonical: $holderDidCanonical")
    println("holder long form: $holderDidLongForm")
    println()

    val credentialClaim = CredentialClaim(
            subjectDid = holderUnpublishedDid,
            content = JsonObject(mapOf(
                    Pair("name", JsonPrimitive("Lars Brünjes")),
                    Pair("degree", JsonPrimitive("Doctor of Mathematics")),
                    Pair("year", JsonPrimitive(2001)))))

/*
    val credentialClaim = CredentialClaim(
            subjectDid = holderUnpublishedDid,
            content = JsonObject(mapOf(
                    Pair("name", JsonPrimitive("Lars Brünjes")),
                    Pair("role", JsonPrimitive("Director of Education")),
                    Pair("yearOfBirth", JsonPrimitive(1971)),
                    Pair("courses", JsonArray(listOf(
                            JsonObject(mapOf(
                                    Pair("year", JsonPrimitive(2017)),
                                    Pair("type", JsonPrimitive("Haskell")),
                                    Pair("location", JsonPrimitive("Athens")))),
                            JsonObject(mapOf(
                                    Pair("year", JsonPrimitive(2018)),
                                    Pair("type", JsonPrimitive("Haskell")),
                                    Pair("location", JsonPrimitive("Barbados")))),
                            JsonObject(mapOf(
                                    Pair("year", JsonPrimitive(2019)),
                                    Pair("type", JsonPrimitive("Haskell")),
                                    Pair("location", JsonPrimitive("Addis Ababa")))),
                            JsonObject(mapOf(
                                    Pair("year", JsonPrimitive(2019)),
                                    Pair("type", JsonPrimitive("Haskell")),
                                    Pair("location", JsonPrimitive("Addis Ababa")))),
                            JsonObject(mapOf(
                                    Pair("year", JsonPrimitive(2020)),
                                    Pair("type", JsonPrimitive("Haskell")),
                                    Pair("location", JsonPrimitive("virtual")))),
                            JsonObject(mapOf(
                                    Pair("year", JsonPrimitive(2021)),
                                    Pair("type", JsonPrimitive("Plutus")),
                                    Pair("location", JsonPrimitive("virtual")))),
                            JsonObject(mapOf(
                                    Pair("year", JsonPrimitive(2021)),
                                    Pair("type", JsonPrimitive("Prism")),
                                    Pair("location", JsonPrimitive("virtual"))))))))))
*/

    val issuerNodePayloadGenerator = NodePayloadGenerator(
            issuerUnpublishedDid,
            mapOf(PrismDid.DEFAULT_ISSUING_KEY_ID to issuerIssuingKeyPair.privateKey))

    val issueCredentialsInfo = issuerNodePayloadGenerator.issueCredentials(
            PrismDid.DEFAULT_ISSUING_KEY_ID,
            arrayOf(credentialClaim))

    val holderSignedCredential = issueCredentialsInfo.credentialsAndProofs.first().signedCredential
    println(holderSignedCredential)
    println()
    println(holderSignedCredential.content)
    println()
    println(holderSignedCredential.isValidSignature(issuerIssuingKeyPair.publicKey))
    println(holderSignedCredential.isValidSignature(issuerMasterKeyPair.publicKey))
}
