package com.cloudflare.claire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudflare.claire.browser.ConnectionInfo;
import com.cloudflare.claire.browser.Header;
import com.cloudflare.claire.browser.RequestDetails;
import com.cloudflare.claire.testing.TestEnvironment;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ties the paths {@link Request} computes to the image files the extension actually ships.
 *
 * <p>{@code getImagePath} builds a filename by string concatenation, so a missing asset is
 * invisible to every other test: the method happily returns a path to a file that is not there.
 * These cases resolve each computed path against the packaged resources instead.
 */
class ExtensionAssetsTest {

  private static final String ASSETS = "/extension/";

  /** Every feature combination {@code getImagePath} can produce, in its canonical order. */
  private static List<Request> everyFeatureCombination() {
    List<Request> requests = new ArrayList<>();
    for (boolean cloudflare : new boolean[] {false, true}) {
      for (boolean http2 : new boolean[] {false, true}) {
        for (boolean ipv6 : new boolean[] {false, true}) {
          for (boolean railgun : new boolean[] {false, true}) {
            List<Header> headers = new ArrayList<>();
            if (cloudflare) {
              headers.add(new Header("server", "cloudflare"));
            }
            if (railgun) {
              headers.add(new Header("cf-railgun", "normal 96 foobar 9001"));
            }
            Request request = new Request(
                TestEnvironment.create(),
                RequestDetails.withHeaders(headers).ip(ipv6 ? "2001:db8::1" : "1.2.3.4").build());
            if (http2) {
              request.setConnectionInfo(new ConnectionInfo("h2"));
            }
            requests.add(request);
          }
        }
      }
    }
    return requests;
  }

  private static boolean assetExists(String path) {
    return ExtensionAssetsTest.class.getResource(ASSETS + path) != null;
  }

  @Test
  @DisplayName("ships an icon for every state a Cloudflare response can be in")
  void shipsAnIconForEveryStateACloudflareResponseCanBeIn() {
    List<String> missing = new ArrayList<>();
    for (Request request : everyFeatureCombination()) {
      if (!request.servedByCloudFlare()) {
        continue;
      }
      String icon = request.getPageActionPath();
      if (!assetExists(icon + ".png")
          || !assetExists(icon + "@2x.png")
          || !assetExists(request.getPopupPath() + ".png")) {
        missing.add(icon);
      }
    }

    assertEquals(List.of(), missing);
  }

  @Test
  @DisplayName("ships an icon for every non-Railgun response from another origin")
  void shipsAnIconForEveryNonRailgunResponseFromAnotherOrigin() {
    List<String> missing = new ArrayList<>();
    for (Request request : everyFeatureCombination()) {
      if (request.servedByCloudFlare() || request.servedByRailgun()) {
        continue;
      }
      String icon = request.getPageActionPath();
      if (!assetExists(icon + ".png")
          || !assetExists(icon + "@2x.png")
          || !assetExists(request.getPopupPath() + ".png")) {
        missing.add(icon);
      }
    }

    assertEquals(List.of(), missing);
  }

  @Test
  @DisplayName("has no icon for a Railgun response that is not marked as Cloudflare")
  void hasNoIconForARailgunResponseThatIsNotMarkedAsCloudflare() {
    // Carried over from the source, which ships no `claire-3-off-*-rg` image either. The state
    // is reachable — a CF-RAILGUN header with no `Server: cloudflare` — and the extension then
    // asks Chrome for a file that does not exist. Recorded rather than fixed: inventing the
    // missing artwork would be a behaviour change, not a migration.
    List<String> unreachableAssets = new ArrayList<>();
    for (Request request : everyFeatureCombination()) {
      if (request.servedByCloudFlare() || !request.servedByRailgun()) {
        continue;
      }
      unreachableAssets.add(request.getPageActionPath());
    }

    assertEquals(
        List.of(
            "images/claire-3-off-rg",
            "images/claire-3-off-ipv6-rg",
            "images/claire-3-off-h2-rg",
            "images/claire-3-off-h2-ipv6-rg"),
        unreachableAssets);
    for (String icon : unreachableAssets) {
      assertTrue(!assetExists(icon + ".png"), icon + " unexpectedly has artwork");
    }
  }

  @Test
  @DisplayName("ships the manifest, pages and toolbar icons the extension declares")
  void shipsTheManifestPagesAndToolbarIconsTheExtensionDeclares() {
    assertNotNull(ExtensionAssetsTest.class.getResource(ASSETS + "manifest.json"));
    assertNotNull(ExtensionAssetsTest.class.getResource(ASSETS + "options.html"));
    assertNotNull(ExtensionAssetsTest.class.getResource(ASSETS + "page-action-popup.html"));
    assertNotNull(ExtensionAssetsTest.class.getResource(ASSETS + "style.css"));
    assertTrue(assetExists("images/orange-cloud-16.png"));
    assertTrue(assetExists("images/orange-cloud-48.png"));
    assertTrue(assetExists("images/orange-cloud-128.png"));
  }
}
