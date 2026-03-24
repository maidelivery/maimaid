import Foundation

enum BackendAuthenticationMode {
    case required
    case optional
    case none
}

struct BackendAPIError: LocalizedError {
    let statusCode: Int?
    let code: String?
    let message: String

    var errorDescription: String? { message }

    static let unconfigured = BackendAPIError(
        statusCode: nil,
        code: nil,
        message: String(localized: "settings.cloud.config.error.unconfigured")
    )
    static let unauthorized = BackendAPIError(
        statusCode: 401,
        code: "unauthorized",
        message: String(localized: "community.alias.submit.loginRequired")
    )
    static let badResponse = BackendAPIError(statusCode: nil, code: nil, message: "Invalid server response.")
}

private struct BackendErrorPayload: Decodable {
    let code: String?
    let message: String?
}

enum BackendAPIClient {
    static let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        return encoder
    }()

    static let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }()

    static func request<Response: Decodable>(
        path: String,
        method: String = "GET",
        authentication: BackendAuthenticationMode = .required
    ) async throws -> Response {
        try await request(path: path, method: method, body: Optional<String>.none, authentication: authentication)
    }

    static func request<Request: Encodable, Response: Decodable>(
        path: String,
        method: String = "POST",
        body: Request?,
        authentication: BackendAuthenticationMode = .required
    ) async throws -> Response {
        guard let url = BackendConfig.endpoint(path) else {
            throw BackendAPIError.unconfigured
        }

        let initialToken = await resolveToken(for: authentication)
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token = initialToken {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        } else if authentication == .required {
            throw BackendAPIError.unauthorized
        }
        if let body {
            request.httpBody = try encoder.encode(body)
        }

        let firstTry = try await execute(request)
        if shouldRetry(firstTry.response, authMode: authentication, hadToken: initialToken != nil) {
            let refreshed = await BackendSessionManager.shared.refreshSessionSilently()
            if refreshed {
                let retryToken = await MainActor.run { BackendSessionManager.shared.accessTokenForRequest() }
                var retryRequest = request
                if let retryToken {
                    retryRequest.setValue("Bearer \(retryToken)", forHTTPHeaderField: "Authorization")
                }
                let retryTry = try await execute(retryRequest)
                return try decodeOrThrow(data: retryTry.data, response: retryTry.response)
            }
        }

        return try decodeOrThrow(data: firstTry.data, response: firstTry.response)
    }

    static func requestData<Request: Encodable>(
        path: String,
        method: String = "GET",
        body: Request?,
        authentication: BackendAuthenticationMode = .required
    ) async throws -> Data {
        guard let url = BackendConfig.endpoint(path) else {
            throw BackendAPIError.unconfigured
        }

        let token = await resolveToken(for: authentication)
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        } else if authentication == .required {
            throw BackendAPIError.unauthorized
        }

        if let body {
            request.httpBody = try encoder.encode(body)
        }

        let result = try await execute(request)
        guard (200...299).contains(result.response.statusCode) else {
            throw errorFrom(statusCode: result.response.statusCode, data: result.data)
        }
        return result.data
    }

    private static func resolveToken(for mode: BackendAuthenticationMode) async -> String? {
        switch mode {
        case .none:
            return nil
        case .required, .optional:
            return await MainActor.run { BackendSessionManager.shared.accessTokenForRequest() }
        }
    }

    private static func execute(_ request: URLRequest) async throws -> (data: Data, response: HTTPURLResponse) {
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw BackendAPIError.badResponse
        }
        return (data, http)
    }

    private static func shouldRetry(
        _ response: HTTPURLResponse,
        authMode: BackendAuthenticationMode,
        hadToken: Bool
    ) -> Bool {
        guard response.statusCode == 401 else {
            return false
        }
        switch authMode {
        case .none:
            return false
        case .optional, .required:
            return hadToken
        }
    }

    private static func decodeOrThrow<Response: Decodable>(data: Data, response: HTTPURLResponse) throws -> Response {
        guard (200...299).contains(response.statusCode) else {
            throw errorFrom(statusCode: response.statusCode, data: data)
        }
        return try decoder.decode(Response.self, from: data)
    }

    private static func errorFrom(statusCode: Int, data: Data) -> BackendAPIError {
        let payload = try? decoder.decode(BackendErrorPayload.self, from: data)
        let fallbackMessage = payload?.message ?? "HTTP \(statusCode)"
        let localizedMessage = localizedAuthMessage(code: payload?.code) ?? fallbackMessage
        return BackendAPIError(statusCode: statusCode, code: payload?.code, message: localizedMessage)
    }

    private static func localizedAuthMessage(code: String?) -> String? {
        switch code {
        case "email_rate_limited":
            return String(localized: "settings.cloud.message.emailRateLimited")
        case "email_exists":
            return String(localized: "settings.cloud.message.emailAlreadyRegistered")
        case "email_not_registered":
            return String(localized: "settings.cloud.message.emailNotRegistered")
        case "email_not_verified":
            return String(localized: "settings.cloud.message.signupVerificationSent")
        case "invalid_email":
            return String(localized: "settings.cloud.message.emailInvalidFormat")
        case "invalid_password":
            return String(localized: "settings.cloud.message.passwordRequirementNotMet")
        case "invalid_reset_token":
            return String(localized: "settings.cloud.message.recoveryLinkInvalid")
        default:
            return nil
        }
    }
}
