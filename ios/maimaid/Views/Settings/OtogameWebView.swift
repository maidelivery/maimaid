import SwiftUI
import WebKit

struct OtogameWebView: UIViewRepresentable {
    let onAuthorizationHeader: @MainActor (String) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onAuthorizationHeader: onAuthorizationHeader)
    }

    func makeUIView(context: Context) -> WKWebView {
        let contentController = WKUserContentController()
        contentController.addUserScript(
            WKUserScript(
                source: Self.authorizationCaptureScript,
                injectionTime: .atDocumentStart,
                forMainFrameOnly: true,
                in: .page
            )
        )
        contentController.add(
            context.coordinator,
            contentWorld: .page,
            name: Self.messageHandlerName
        )

        let configuration = WKWebViewConfiguration()
        configuration.userContentController = contentController
        configuration.websiteDataStore = .default()

        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.allowsBackForwardNavigationGestures = true
        webView.scrollView.contentInsetAdjustmentBehavior = .automatic
        if let url = URL(string: "https://u.otogame.net/maimai/music") {
            webView.load(URLRequest(url: url))
        }
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {}

    static func dismantleUIView(_ webView: WKWebView, coordinator: Coordinator) {
        webView.stopLoading()
        webView.configuration.userContentController.removeScriptMessageHandler(
            forName: messageHandlerName,
            contentWorld: .page
        )
    }

    @MainActor
    final class Coordinator: NSObject, WKScriptMessageHandler {
        private let onAuthorizationHeader: @MainActor (String) -> Void

        init(onAuthorizationHeader: @escaping @MainActor (String) -> Void) {
            self.onAuthorizationHeader = onAuthorizationHeader
        }

        func userContentController(
            _ userContentController: WKUserContentController,
            didReceive message: WKScriptMessage
        ) {
            guard message.name == OtogameWebView.messageHandlerName,
                  let value = message.body as? String else {
                return
            }
            onAuthorizationHeader(value)
        }
    }

    private static let messageHandlerName = "otogameAuthorization"

    private static let authorizationCaptureScript = #"""
    (() => {
      const sendAuthorization = (value) => {
        if (typeof value !== 'string') return;
        const header = value.trim();
        if (/^Bearer\s+\S+$/i.test(header)) {
          window.webkit.messageHandlers.otogameAuthorization.postMessage(header);
        }
      };

      const inspectCredential = (key, value) => {
        if (typeof value !== 'string') return;
        const credential = value.trim();
        if (/^Bearer\s+\S+$/i.test(credential)) {
          sendAuthorization(credential);
          return;
        }
        if (typeof key === 'string'
            && key.toUpperCase() === 'TOKEN'
            && /^v2\.local\.[A-Za-z0-9._~+\/=\-]+$/i.test(credential)) {
          sendAuthorization(`Bearer ${credential}`);
        }
      };

      const inspectHeaders = (headers) => {
        if (!headers) return;
        try {
          new Headers(headers).forEach((value, name) => {
            if (name.toLowerCase() === 'authorization') inspectCredential(name, value);
          });
        } catch (_) {}
      };

      const originalFetch = window.fetch;
      if (originalFetch) {
        window.fetch = function(input, init) {
          try {
            inspectHeaders(init && init.headers);
            if (input instanceof Request) inspectHeaders(input.headers);
          } catch (_) {}
          return originalFetch.apply(this, arguments);
        };
      }

      const originalSetRequestHeader = XMLHttpRequest.prototype.setRequestHeader;
      XMLHttpRequest.prototype.setRequestHeader = function(name, value) {
        if (typeof name === 'string' && name.toLowerCase() === 'authorization') {
          inspectCredential(name, value);
        }
        return originalSetRequestHeader.apply(this, arguments);
      };

      const originalStorageSetItem = Storage.prototype.setItem;
      Storage.prototype.setItem = function(key, value) {
        const result = originalStorageSetItem.apply(this, arguments);
        inspectCredential(key, value);
        return result;
      };

      const inspectStorage = (storage) => {
        try {
          for (let index = 0; index < storage.length; index += 1) {
            const key = storage.key(index);
            inspectCredential(key, storage.getItem(key));
          }
        } catch (_) {}
      };

      inspectStorage(window.localStorage);
      inspectStorage(window.sessionStorage);
      window.addEventListener('storage', () => {
        inspectStorage(window.localStorage);
        inspectStorage(window.sessionStorage);
      });
    })();
    """#
}
