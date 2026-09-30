(() => {
  const REPO = "Mahmud0808/TeleDrive";
  const API = `https://api.github.com/repos/${REPO}`;
  const RELEASES_PAGE = `https://github.com/${REPO}/releases`;
  const CACHE_KEY = "teledrive:releases:v1";
  const CACHE_TTL = 10 * 60 * 1000;

  const root = document.documentElement;
  root.classList.add("js");

  const EASE_OUT = "cubic-bezier(0.16, 1, 0.3, 1)";
  const EASE_IN = "cubic-bezier(0.7, 0, 0.84, 0)";
  const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
  const duration = (ms) => (reducedMotion.matches ? Math.min(ms, 150) : ms);

  const PLATFORMS = {
    android: { label: "Android", pick: /-arm64-v8a-release\.apk$/i, family: /\.apk$/i },
    windows: { label: "Windows", pick: /\.msi$/i, family: /\.msi$/i },
    linux: { label: "Linux", pick: /_amd64\.deb$/i, family: /\.deb$/i },
    macos: { label: "macOS", pick: /\.dmg$/i, family: /\.dmg$/i },
  };

  const ANDROID_VARIANTS = [
    { abi: "armeabi-v7a", match: /-armeabi-v7a-release\.apk$/i, hint: "Older 32-bit devices" },
    { abi: "x86_64", match: /-x86_64-release\.apk$/i, hint: "64-bit emulators and x86 Chromebooks" },
    { abi: "x86", match: /-x86-release\.apk$/i, hint: "32-bit x86, rare outside older emulators" },
    { abi: "universal", match: /-universal-release\.apk$/i, hint: "Every architecture in one larger file" },
  ];

  const numberFormat = new Intl.NumberFormat();
  const dateFormat = new Intl.DateTimeFormat(undefined, { day: "numeric", month: "short", year: "numeric" });

  const formatSize = (bytes) => {
    const mb = bytes / 1048576;
    return mb >= 100 ? `${Math.round(mb)} MB` : `${mb.toFixed(1)} MB`;
  };

  const detectPlatform = () => {
    const ua = navigator.userAgent || "";
    const hint = (navigator.userAgentData && navigator.userAgentData.platform) || navigator.platform || "";
    if (/android/i.test(ua)) return "android";
    if (/iphone|ipad|ipod/i.test(ua) || (/mac/i.test(hint) && navigator.maxTouchPoints > 1)) return "ios";
    if (/win/i.test(hint) || /windows/i.test(ua)) return "windows";
    if (/mac/i.test(hint) || /mac os x/i.test(ua)) return "macos";
    if (/linux|x11|cros/i.test(hint) || /linux/i.test(ua)) return "linux";
    return null;
  };

  const fadeIn = (node) => {
    node.animate([{ opacity: 0 }, { opacity: 1 }], { duration: duration(320), easing: EASE_OUT });
  };

  const swapText = (node, value) => {
    if (node.textContent === value) return;
    node.textContent = value;
    fadeIn(node);
  };

  const setText = (selector, value, scope = document) => {
    scope.querySelectorAll(selector).forEach((node) => swapText(node, value));
  };

  const running = new WeakMap();

  const animateHeight = (node, from, to, opacityFrom, opacityTo, ms, easing) => {
    const previous = running.get(node);
    if (previous) previous.cancel();
    node.classList.add("is-animating");
    const animation = node.animate(
      [{ height: `${from}px`, opacity: opacityFrom }, { height: `${to}px`, opacity: opacityTo }],
      { duration: duration(ms), easing },
    );
    running.set(node, animation);
    const done = () => {
      if (running.get(node) === animation) running.delete(node);
      node.classList.remove("is-animating");
    };
    animation.addEventListener("cancel", done);
    return animation.finished.then(() => { done(); return true; }, () => false);
  };

  const expand = (node) => {
    if (!node.hidden && !running.has(node)) return;
    const from = node.hidden ? 0 : node.getBoundingClientRect().height;
    node.hidden = false;
    animateHeight(node, from, node.scrollHeight, from ? 1 : 0, 1, 360, EASE_OUT);
  };

  const collapse = (node) => {
    if (node.hidden) return;
    const from = node.getBoundingClientRect().height;
    animateHeight(node, from, 0, 1, 0, 260, EASE_IN).then((finished) => {
      if (finished) node.hidden = true;
    });
  };

  const wireDisclosure = (details) => {
    const summary = details.querySelector("summary");
    const body = summary && summary.nextElementSibling;
    if (!body) return;
    let closing = false;
    summary.addEventListener("click", (event) => {
      event.preventDefault();
      const from = details.open ? body.getBoundingClientRect().height : 0;
      if (!details.open || closing) {
        closing = false;
        details.open = true;
        animateHeight(body, from, body.scrollHeight, from ? 1 : 0, 1, 380, EASE_OUT);
      } else {
        closing = true;
        animateHeight(body, from, 0, 1, 0, 260, EASE_IN).then((finished) => {
          if (finished && closing) {
            details.open = false;
            closing = false;
          }
        });
      }
    });
  };

  const fromApi = (release) => ({
    tag: release.tag_name,
    name: release.name,
    published: release.published_at,
    prerelease: release.prerelease,
    draft: release.draft,
    url: release.html_url,
    assets: (release.assets || []).map((asset) => ({
      name: asset.name,
      size: asset.size,
      downloads: asset.download_count,
      url: asset.browser_download_url,
    })),
  });

  const readCache = () => {
    try {
      const raw = sessionStorage.getItem(CACHE_KEY);
      if (!raw) return null;
      const cached = JSON.parse(raw);
      return Date.now() - cached.savedAt < CACHE_TTL ? cached.data : null;
    } catch {
      return null;
    }
  };

  const writeCache = (data) => {
    try {
      sessionStorage.setItem(CACHE_KEY, JSON.stringify({ savedAt: Date.now(), data }));
    } catch {}
  };

  const fetchJson = async (url) => {
    const response = await fetch(url, { headers: { Accept: "application/vnd.github+json" } });
    if (!response.ok) throw new Error(`${response.status} ${url}`);
    return { body: await response.json(), link: response.headers.get("link") || "" };
  };

  const loadSnapshot = async () => {
    try {
      const response = await fetch("data/releases.json", { cache: "no-cache" });
      if (!response.ok) return null;
      return await response.json();
    } catch {
      return null;
    }
  };

  const loadLive = async (snapshot) => {
    const [releases, repo] = await Promise.allSettled([
      fetchJson(`${API}/releases?per_page=100`),
      fetchJson(API),
    ]);
    if (releases.status !== "fulfilled") return null;
    const live = releases.value.body.filter((r) => !r.draft).map(fromApi);
    const complete = !/rel="next"/.test(releases.value.link);
    let merged = live;
    if (!complete && snapshot) {
      const seen = new Set(live.map((r) => r.tag));
      merged = live.concat(snapshot.releases.filter((r) => !seen.has(r.tag)));
    }
    const stars = repo.status === "fulfilled"
      ? repo.value.body.stargazers_count
      : snapshot && snapshot.repo ? snapshot.repo.stars : null;
    return { releases: merged, repo: { stars } };
  };

  const stable = (data) => data.releases
    .filter((r) => !r.draft && !r.prerelease)
    .sort((a, b) => new Date(b.published) - new Date(a.published));

  const newestAsset = (releases, pattern) => {
    for (const release of releases) {
      const asset = release.assets.find((a) => pattern.test(a.name));
      if (asset) return { release, asset };
    }
    return null;
  };

  const sumDownloads = (releases, pattern) => releases.reduce(
    (total, release) => total + release.assets
      .filter((a) => !pattern || pattern.test(a.name))
      .reduce((n, a) => n + (a.downloads || 0), 0),
    0,
  );

  const MISSING_TEXT = "Arrives with the next release";

  const showMissing = (item, link) => {
    item.classList.add("is-missing");
    link.href = `${RELEASES_PAGE}/latest`;
    link.setAttribute("aria-disabled", "true");
    setText('[data-slot="file"]', MISSING_TEXT, item);
    setText('[data-slot="size"]', "", item);
    item.querySelectorAll('[data-needs-data]').forEach(collapse);
  };

  const clearMissing = (item, link) => {
    item.classList.remove("is-missing");
    link.removeAttribute("aria-disabled");
    item.querySelectorAll('[data-needs-data]').forEach(expand);
  };

  const assetLink = (label, asset, hint) => {
    const item = document.createElement("li");
    item.className = "variant";
    const anchor = document.createElement("a");
    anchor.href = asset.url;
    const name = document.createElement("span");
    name.className = "variant__abi";
    name.textContent = label;
    const size = document.createElement("span");
    size.className = "variant__size";
    size.textContent = formatSize(asset.size);
    anchor.append(name, size);
    if (hint) {
      const note = document.createElement("span");
      note.className = "variant__hint";
      note.textContent = hint;
      anchor.append(note);
    }
    item.append(anchor);
    return item;
  };

  const fillList = (list, entries, container) => {
    const signature = entries.map((entry) => entry.asset.url).join("|");
    if (list.dataset.signature !== signature) {
      list.dataset.signature = signature;
      list.replaceChildren(...entries.map((entry) => assetLink(entry.label, entry.asset, entry.hint)));
    }
    if (entries.length === 0) collapse(container);
    else expand(container);
  };

  const renderVariants = (list, release) => {
    const entries = ANDROID_VARIANTS
      .map((variant) => ({ label: variant.abi, hint: variant.hint, asset: release.assets.find((a) => variant.match.test(a.name)) }))
      .filter((entry) => entry.asset);
    fillList(list, entries, list.closest(".featured__side"));
  };

  const renderOthers = (latest) => {
    const container = document.querySelector("[data-others]");
    const list = container && container.querySelector('[data-slot="others"]');
    if (!list) return;
    const known = Object.values(PLATFORMS).map((spec) => spec.family);
    const entries = latest.assets
      .filter((asset) => !known.some((pattern) => pattern.test(asset.name)))
      .map((asset) => ({ label: asset.name, asset }));
    fillList(list, entries, container);
  };

  const renderPlatform = (item, key, releases, latest) => {
    const spec = PLATFORMS[key];
    const found = newestAsset(releases, spec.pick);
    const link = item.querySelector('[data-slot="link"]');

    if (!found) {
      showMissing(item, link);
      return null;
    }

    clearMissing(item, link);
    link.href = found.asset.url;

    const olderNote = latest && found.release.tag !== latest.tag ? ` · from ${found.release.tag}` : "";
    setText('[data-slot="file"]', found.asset.name, item);
    setText('[data-slot="size"]', `${formatSize(found.asset.size)}${olderNote}`, item);
    setText('[data-slot="count"]', numberFormat.format(sumDownloads(releases, spec.family)), item);

    const command = item.querySelector('[data-slot="command"]');
    if (command) swapText(command, `sudo apt install ./${found.asset.name}`);

    const list = item.querySelector('[data-slot="variants"]');
    if (list) renderVariants(list, found.release);
    return found;
  };

  const wireNotes = () => {
    document.querySelectorAll(".build__more[aria-controls]").forEach((button) => {
      const panel = document.getElementById(button.getAttribute("aria-controls"));
      if (!panel) return;
      button.addEventListener("click", () => {
        const opening = button.getAttribute("aria-expanded") !== "true";
        button.setAttribute("aria-expanded", String(opening));
        if (opening) expand(panel);
        else collapse(panel);
      });
    });
  };

  const platform = detectPlatform();

  const renderHero = (found) => {
    const cta = document.querySelector("[data-hero-cta]");
    const meta = document.querySelector("[data-hero-meta]");
    if (!cta || !found.android) return;
    cta.href = found.android.asset.url;
    if (meta) swapText(meta, `${found.android.release.tag} · ${formatSize(found.android.asset.size)} APK`);
  };

  const render = (data) => {
    const releases = stable(data);
    if (releases.length === 0) return false;
    const latest = releases[0];

    setText('[data-bind="version"]', latest.tag);
    setText('[data-bind="released"]', dateFormat.format(new Date(latest.published)));
    setText('[data-bind="total"]', numberFormat.format(sumDownloads(data.releases)));
    const stars = data.repo && typeof data.repo.stars === "number" ? numberFormat.format(data.repo.stars) : "";
    document.querySelectorAll('[data-bind="stars"]').forEach((node) => {
      const stat = node.closest(".stat");
      if (stars) {
        swapText(node, stars);
        if (stat) expand(stat);
      } else if (stat) {
        collapse(stat);
      }
    });
    document.querySelectorAll("[data-release-link]").forEach((node) => { node.href = latest.url; });

    const found = {};
    document.querySelectorAll("[data-platform]").forEach((item) => {
      const key = item.dataset.platform;
      if (PLATFORMS[key]) found[key] = renderPlatform(item, key, releases, latest);
    });
    renderHero(found);
    renderOthers(latest);

    root.classList.add("has-data");
    return true;
  };

  const markYours = () => {
    if (!platform || !PLATFORMS[platform]) return;
    const item = document.querySelector(`[data-platform="${platform}"]`);
    if (item) item.classList.add("is-yours");
  };

  const wireCopy = () => {
    document.querySelectorAll("[data-copy]").forEach((button) => {
      const label = button.querySelector("[data-copy-label]");
      const glyph = button.querySelector("use");
      let timer = 0;
      const settle = (text, icon, done) => {
        swapText(label, text);
        if (glyph && glyph.getAttribute("href") !== icon) {
          glyph.setAttribute("href", icon);
          fadeIn(glyph.parentElement);
        }
        button.classList.toggle("is-done", done);
      };
      button.addEventListener("click", async () => {
        const code = button.parentElement.querySelector("code");
        if (!code) return;
        try {
          await navigator.clipboard.writeText(code.textContent.trim());
          settle("Copied", "#i-check", true);
        } catch {
          const range = document.createRange();
          range.selectNodeContents(code);
          const selection = window.getSelection();
          selection.removeAllRanges();
          selection.addRange(range);
          settle("Press Ctrl+C", "#i-copy", false);
        }
        clearTimeout(timer);
        timer = setTimeout(() => settle("Copy", "#i-copy", false), 1800);
      });
    });
  };

  const wireBar = () => {
    const bar = document.querySelector("[data-bar]");
    const hero = document.querySelector(".hero__grid");
    const cta = bar && bar.querySelector(".bar__cta");
    if (!bar || !hero || !cta || !("IntersectionObserver" in window)) {
      if (bar) bar.classList.add("is-raised");
      return;
    }
    const movers = [...bar.querySelectorAll(".bar__end > :not(.bar__cta)")];
    const positions = () => movers.map((node) => node.getBoundingClientRect().left);
    const slide = (before) => {
      movers.forEach((node, index) => {
        const delta = before[index] - node.getBoundingClientRect().left;
        if (delta) {
          node.animate(
            [{ transform: `translateX(${delta}px)` }, { transform: "none" }],
            { duration: duration(360), easing: EASE_OUT },
          );
        }
      });
    };
    let raised = false;
    let exit = null;
    const setRaised = (next) => {
      if (next === raised) return;
      raised = next;
      if (exit) {
        exit.cancel();
        exit = null;
      }
      if (next) {
        const before = positions();
        bar.classList.add("is-raised");
        slide(before);
        cta.animate(
          [{ opacity: 0, transform: "translateY(-4px)" }, { opacity: 1, transform: "none" }],
          { duration: duration(320), easing: EASE_OUT },
        );
        return;
      }
      const animation = cta.animate(
        [{ opacity: 1 }, { opacity: 0, transform: "translateY(-4px)" }],
        { duration: duration(180), easing: EASE_IN, fill: "forwards" },
      );
      exit = animation;
      animation.finished.then(() => {
        if (exit !== animation) return;
        exit = null;
        const before = positions();
        bar.classList.remove("is-raised");
        animation.cancel();
        slide(before);
      }, () => {});
    };
    new IntersectionObserver(([entry]) => setRaised(!entry.isIntersecting), {
      rootMargin: "-64px 0px 0px 0px",
    }).observe(hero);
  };

  const wireImages = () => {
    document.querySelectorAll('img[loading="lazy"]').forEach((image) => {
      const show = () => image.classList.add("is-loaded");
      if (image.complete) show();
      else {
        image.addEventListener("load", show, { once: true });
        image.addEventListener("error", show, { once: true });
      }
    });
  };

  const giveUp = () => {
    document.querySelectorAll("[data-needs-data]").forEach(collapse);
  };

  const start = async () => {
    wireImages();
    markYours();
    wireCopy();
    wireBar();
    wireNotes();
    document.querySelectorAll("details").forEach(wireDisclosure);

    const cached = readCache();
    if (cached && render(cached)) return;

    const snapshot = await loadSnapshot();
    const hadSnapshot = snapshot ? render(snapshot) : false;

    try {
      const live = await loadLive(snapshot);
      if (live && render(live)) {
        writeCache(live);
        return;
      }
    } catch {}

    if (!hadSnapshot) giveUp();
  };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", start, { once: true });
  } else {
    start();
  }
})();
