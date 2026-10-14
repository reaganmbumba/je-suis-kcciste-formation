import UIKit
import WebKit
import Photos

/// JE SUIS KCCiste – FORMATION (iPhone)
/// Affiche l'application HTML (www/index.html, la même que l'APK) dans une WKWebView.
/// - localStorage conservé : progression, favoris et accès membre.
/// - Les liens externes (WhatsApp) s'ouvrent dans l'application correspondante.
/// - Le certificat s'enregistre dans Photos, se partage (WhatsApp, Facebook…) ou s'imprime / s'enregistre en PDF,
///   grâce aux mêmes fonctions que la version Android (window.KCCAndroid), reliées ici au code natif.
@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        window = UIWindow(frame: UIScreen.main.bounds)
        window?.rootViewController = WebViewController()
        window?.makeKeyAndVisible()
        return true
    }
}

final class WebViewController: UIViewController, WKScriptMessageHandler, WKNavigationDelegate, WKUIDelegate {
    private var web: WKWebView!
    private static let night = UIColor(red: 7 / 255, green: 26 / 255, blue: 61 / 255, alpha: 1)

    /// Mêmes fonctions que le pont Android, transmises au code natif.
    private static let bridgeJS = """
    window.KCCiOS = true;
    window.KCCAndroid = {
      printCertificate: function (title) { webkit.messageHandlers.kcc.postMessage({ a: "print", title: title }); },
      saveImage: function (b64, name, ok, err) { webkit.messageHandlers.kcc.postMessage({ a: "save", b64: b64, name: name, ok: ok, err: err }); },
      shareImage: function (b64, name, target, title, missing) { webkit.messageHandlers.kcc.postMessage({ a: "share", b64: b64, name: name, title: title }); }
    };
    """

    override var preferredStatusBarStyle: UIStatusBarStyle { .lightContent }

    override func loadView() {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()   // localStorage conservé entre les ouvertures
        config.userContentController.addUserScript(
            WKUserScript(source: Self.bridgeJS, injectionTime: .atDocumentStart, forMainFrameOnly: true))
        config.userContentController.add(self, name: "kcc")
        web = WKWebView(frame: .zero, configuration: config)
        web.navigationDelegate = self
        web.uiDelegate = self
        web.isOpaque = false
        web.backgroundColor = Self.night
        web.scrollView.backgroundColor = Self.night
        web.allowsBackForwardNavigationGestures = true   // balayage depuis le bord = écran précédent
        view = web
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        guard let index = Bundle.main.url(forResource: "index", withExtension: "html", subdirectory: "www") else { return }
        web.loadFileURL(index, allowingReadAccessTo: index.deletingLastPathComponent())
    }

    // MARK: liens externes (WhatsApp, sites web) : ouverts hors de l'application

    func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        guard let url = action.request.url else { return decisionHandler(.cancel) }
        if url.isFileURL || url.scheme == "about" { return decisionHandler(.allow) }
        UIApplication.shared.open(url)
        decisionHandler(.cancel)
    }

    /// Liens target="_blank"
    func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration,
                 for action: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        if let url = action.request.url { UIApplication.shared.open(url) }
        return nil
    }

    func webView(_ webView: WKWebView, runJavaScriptAlertPanelWithMessage message: String,
                 initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping () -> Void) {
        let a = UIAlertController(title: nil, message: message, preferredStyle: .alert)
        a.addAction(UIAlertAction(title: "OK", style: .default) { _ in completionHandler() })
        present(a, animated: true)
    }

    // MARK: pont avec index.html

    func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) {
        guard let m = message.body as? [String: Any], let action = m["a"] as? String else { return }
        switch action {
        case "print":
            printCertificate(title: m["title"] as? String ?? "Certificat")
        case "save":
            guard let image = Self.image(m["b64"]) else { return toast(m["err"]) }
            saveToPhotos(image, ok: m["ok"] as? String, err: m["err"] as? String)
        case "share":
            guard let image = Self.image(m["b64"]) else { return }
            share(image, name: m["name"] as? String ?? "Certificat-KCCiste.jpg")
        default:
            break
        }
    }

    private static func image(_ b64: Any?) -> UIImage? {
        guard let s = b64 as? String, let data = Data(base64Encoded: s, options: .ignoreUnknownCharacters) else { return nil }
        return UIImage(data: data)
    }

    private func toast(_ message: Any?) {
        guard let s = message as? String, !s.isEmpty,
              let json = try? JSONSerialization.data(withJSONObject: [s]),
              let arg = String(data: json, encoding: .utf8) else { return }
        web.evaluateJavaScript("typeof toast === 'function' && toast(\(arg)[0])")
    }

    private func saveToPhotos(_ image: UIImage, ok: String?, err: String?) {
        // Le message Android parle du dossier « Images › KCCiste » : sur iPhone, l'image va dans Photos.
        let okText = ok?.replacingOccurrences(of: #"\s*\([^)]*\)\s*$"#, with: "", options: .regularExpression)
        PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
            guard status == .authorized || status == .limited else {
                return DispatchQueue.main.async { self.toast(err) }
            }
            PHPhotoLibrary.shared().performChanges({
                PHAssetChangeRequest.creationRequestForAsset(from: image)
            }) { success, _ in
                DispatchQueue.main.async { self.toast(success ? okText : err) }
            }
        }
    }

    private func share(_ image: UIImage, name: String) {
        let file = FileManager.default.temporaryDirectory.appendingPathComponent(name)
        let item: Any = ((try? image.jpegData(compressionQuality: 0.92)?.write(to: file)) != nil) ? file : image
        let sheet = UIActivityViewController(activityItems: [item], applicationActivities: nil)
        sheet.popoverPresentationController?.sourceView = view   // iPad
        sheet.popoverPresentationController?.sourceRect = CGRect(x: view.bounds.midX, y: view.bounds.maxY - 80, width: 1, height: 1)
        present(sheet, animated: true)
    }

    /// Fenêtre d'impression d'iOS : imprimer, ou « Partager › Enregistrer dans Fichiers » pour obtenir le PDF.
    /// Le style @media print de index.html n'imprime que le certificat (#cert-print).
    private func printCertificate(title: String) {
        let info = UIPrintInfo(dictionary: nil)
        info.outputType = .general
        info.jobName = title
        info.orientation = .landscape
        let p = UIPrintInteractionController.shared
        p.printInfo = info
        p.printFormatter = web.viewPrintFormatter()
        p.present(animated: true)
    }
}
