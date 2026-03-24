import Foundation
import CryptoKit

struct AuthUtils {
    /// Generates a high-entropy cryptographically secure string for PKCE `code_verifier`.
    static func generateCodeVerifier() -> String {
        var buffer = [UInt8](repeating: 0, count: 64)
        _ = SecRandomCopyBytes(kSecRandomDefault, buffer.count, &buffer)
        
        let data = Data(buffer)
        return data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
            .trimmingCharacters(in: .whitespaces)
    }
    
    /// Generates the `code_challenge` for a given `code_verifier` using SHA-256.
    static func generateCodeChallenge(verifier: String) -> String {
        guard let data = verifier.data(using: .utf8) else { return "" }
        let hashed = SHA256.hash(data: data)
        let hashData = Data(hashed)
        
        return hashData.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
            .trimmingCharacters(in: .whitespaces)
    }
}
