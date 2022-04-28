package verify

import io.iohk.atala.prism.api.node.NodeAuthApiImpl
import io.iohk.atala.prism.common.PrismSdkInternal
import io.iohk.atala.prism.credentials.json.JsonBasedCredential
import io.iohk.atala.prism.crypto.MerkleInclusionProof
import io.iohk.atala.prism.protos.GrpcOptions
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

val environment = "ppp.atalaprism.io"
val grpcOptions = GrpcOptions("https", environment, 50053)
val nodeAuthApi = NodeAuthApiImpl(grpcOptions)

@PrismSdkInternal
fun main(args: Array<String>) {
    val jsonFile = try { args[0] } catch (e: Exception) { throw Exception("expected filename of JSON-file containing the signed credential and the proof as first argument") }
    val json = Json.parseToJsonElement(File(jsonFile).readText()).jsonObject
    val credential = JsonBasedCredential.fromString(json["encodedSignedCredential"]?.jsonPrimitive?.content!!)
    val proof = MerkleInclusionProof.decode(json["proof"]?.jsonObject.toString())

    println("credential: $credential")
    println("Proof: $proof")
    println()

    val result = runBlocking {
            nodeAuthApi.verify(credential, proof)
    }
    println("verification result: $result")
    println()
}