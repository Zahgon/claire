# Claire — Java

A Java port of [`cloudflare/claire`](https://github.com/cloudflare/claire), the Google Chrome
extension that turns orange when the current page is on the
[Cloudflare](https://www.cloudflare.com) network and shows additional information about the
request when clicked.

> **Upstream status.** Claire is deprecated in favour of **Optics**, which shipped with
> Manifest V3 and Firefox support. This port mirrors the archived JavaScript source; it does not
> add features, and it does not track Optics.

## What this port is

The JavaScript original is a browser extension: its logic is inseparable from `chrome.*` and the
DOM at every call site. Java has no Chrome extension runtime, so the port keeps **all** of the
extension's behaviour and replaces only the ambient globals with interfaces the code is handed:

| JavaScript global | Java |
| --- | --- |
| `chrome.*` | `com.cloudflare.claire.browser.ChromeApi` |
| `document` | `com.cloudflare.claire.dom.Document` |
| `localStorage` | `com.cloudflare.claire.dom.LocalStorage` |
| `performance` | `com.cloudflare.claire.dom.Performance` |
| `console` | `com.cloudflare.claire.browser.Console` |
| `window.requests` | `com.cloudflare.claire.RequestRegistry` |

Everything else — header pre-processing, Railgun decoding, Ray ID and colo lookup, icon
selection, the trace URL, the options page, the popup and the content script — is a direct
translation. Supplying implementations of those six interfaces runs the extension's logic
unchanged.

## Layout

| Java | Ported from |
| --- | --- |
| `Request` | `source/request.js` |
| `Airports`, `Airport` | `source/airports.js` |
| `RailgunMetaData`, `RailgunFlag` | the Railgun decoder inside `source/request.js` |
| `Claire`, `RequestRegistry` | `source/claire.js` |
| `ContentScript` | `source/contentscript.js` |
| `Options` | `source/options.js` |
| `PageActionPopup` | `source/page-action-popup.js` |
| `js/JsValues`, `js/JsUrl` | JavaScript semantics with no Java equivalent |
| `src/main/resources/extension/` | `source/images`, `manifest.json`, the two HTML pages, `style.css` |

`js/JsValues` and `js/JsUrl` are the only additions. They exist because the source depends on
JavaScript coercion rules that Java does not share — truthiness, `parseInt` returning `NaN`,
`undefined` rendering as `"undefined"` in string concatenation, `split` keeping trailing empties,
and the WHATWG `URL` serialiser dropping default ports. Each rule is pinned by its own test
against values read off the Node runtime the original targets.

## Requirements

- JDK 21 or newer
- Maven 3.9 or newer

## Build and test

```bash
mvn verify
```

Coverage (JaCoCo, written to `target/site/jacoco/`):

```bash
mvn jacoco:prepare-agent test jacoco:report
```

## Dependencies

Two runtime dependencies, each replacing something the source got for free and neither adding a
capability of its own. Tests use **JUnit 5** only.

**Jackson Databind** reads `airports.json`. The original parses the airport table with
JavaScript's native object literal syntax; Java has no built-in JSON reader.

**ICU4J** canonicalises the trace link's host. The browser's URL parser applies UTS-46, and the
JDK ships only `java.net.IDN`, which implements the superseded IDNA2003 and folds the four UTS-46
deviation characters — resolving a host spelled with a sharp s to a *different* host than the
browser resolves it to. It is heavyweight (~14 MB) for a narrow purpose; see
`MIGRATION-REPORT.md` for the measurements behind the choice and what reverting it would cost.

## Known upstream behaviour preserved

These are defects in the original that the port reproduces rather than repairs, because fixing
them would be a behaviour change rather than a migration:

- A `CF-RAILGUN` header with fewer fields than expected yields a compression of `"NaN%"` and a
  time of `"undefinedsec"`.
- The airport table contains one record filed under an empty IATA code, so a `CF-RAY` value of
  `deadbeef-` resolves to Kvarkeno, Russia.
- Only the last of several response headers sharing a name survives pre-processing. The source
  flags this in a comment.
- No `claire-3-off-*-rg` artwork exists, so a Railgun response without a `Server: cloudflare`
  header requests an icon that is not shipped. `ExtensionAssetsTest` pins this rather than
  inventing the missing images.

## License

BSD-3-Clause, unchanged from the original. See [LICENSE.md](LICENSE.md).
