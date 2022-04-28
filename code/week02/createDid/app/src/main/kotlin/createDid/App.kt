package createDid

import io.iohk.atala.prism.api.KeyGenerator
import io.iohk.atala.prism.common.PrismSdkInternal
import io.iohk.atala.prism.crypto.derivation.KeyDerivation
import io.iohk.atala.prism.identity.MasterKeyUsage
import io.iohk.atala.prism.identity.PrismDid
import java.io.File

@PrismSdkInternal
fun main(args: Array<String>) {
    val seedFile = try { args[0] } catch (e: Exception) {throw Exception("expected seed file path as argument")}
    val seed = KeyDerivation.binarySeed(KeyDerivation.randomMnemonicCode(), "passphrase")
    File(seedFile).writeBytes(seed)
    println("wrote seed to file $seedFile")
    println()

    val masterKeyPair = KeyGenerator.deriveKeyFromFullPath(seed, 0, MasterKeyUsage, 0)
    val unpublishedDid = PrismDid.buildLongFormFromMasterPublicKey(masterKeyPair.publicKey)

    val didCanonical = unpublishedDid.asCanonical().did
    val didLongForm = unpublishedDid.did

    println("canonical: $didCanonical")
    println("long form: $didLongForm")
    println()
}
