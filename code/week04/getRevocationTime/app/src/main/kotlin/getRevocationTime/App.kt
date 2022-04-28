package getRevocationTime

import io.iohk.atala.prism.api.node.NodeAuthApiImpl
import io.iohk.atala.prism.credentials.CredentialBatchId
import io.iohk.atala.prism.crypto.Sha256Digest
import io.iohk.atala.prism.protos.GrpcOptions
import kotlinx.coroutines.runBlocking

val environment = "ppp.atalaprism.io"
val grpcOptions = GrpcOptions("https", environment, 50053)
val nodeAuthApi = NodeAuthApiImpl(grpcOptions)

fun main(args: Array<String>) {
    val batchId = try { CredentialBatchId.fromString(args[0])!! } catch (e: Exception) {throw Exception("expected batch id as first argument")}
    val credentialHash = try { Sha256Digest.fromHex(args[1]) } catch (e: Exception) {throw Exception("expected credential hash as second argument")}

    val result = runBlocking {
            nodeAuthApi.getCredentialRevocationTime(
                    batchId.id,
                    credentialHash)
        }

    println(result)
}
