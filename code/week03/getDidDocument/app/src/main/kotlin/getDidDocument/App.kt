package getDidDocument

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
import java.io.File

val environment = "ppp.atalaprism.io"
val grpcOptions = GrpcOptions("https", environment, 50053)
val nodeAuthApi = NodeAuthApiImpl(grpcOptions)

@PrismSdkInternal
fun main(args: Array<String>) {
    if (args.size != 1) {
        throw Exception("expected exactly one command line argument, the DID")
    }

    val did = try { Did.fromString(args[0]) } catch (e: Exception) { throw Exception("illegal DID: ${args[0]}") }
    val prismDid = try { PrismDid.fromDid(did) } catch (e: Exception) { throw Exception("not a Prism DID: $did") }

    println("trying to retrieve document for $did")
    try {
        val model = runBlocking { nodeAuthApi.getDidDocument(prismDid) }
        println(model.didDataModel)
        for (info in model.publicKeys) {
            println()
            println(info)
        }
        println()
    } catch (e: Exception) {
        println("unknown prism DID")
    }
}
