package com.inoviq.videodownloader.videosaver.storysaver.utils

object FacebookMediaSniffer {

    /**
     * Injected JavaScript for web media detection and download button injection:
     * 1. ACCURATE STORY IDENTIFICATION:
     *    - Automatically detects story carousel/tray.
     *    - Strictly SKIPS "Create story" card (index 0 / contains 'Create story' / plus icon) so no button appears on it.
     *    - Attaches download button centered horizontally in lower half on real friend stories (Yasemin Aksoy, Joseph Lyons, etc.).
     * 2. ACCURATE PHOTO IDENTIFICATION:
     *    - Injects download buttons on feed photos, multi-photo grids, and photo posts (e.g. Cassandra Taylor's 4 photos).
     *    - Positions buttons neatly in bottom-right (bottom: 12px; right: 12px).
     *    - Ignores avatars, story tray items, UI icons, emojis, and tracking pixels (< 110px).
     *    - Extracts highest-resolution image URL from srcset, data-src, or currentSrc.
     * 3. ACCURATE VIDEO IDENTIFICATION:
     *    - Injects download button on feed videos, reels, and story videos.
     *    - Inspects <video>, data-store JSON, data-video-url, and inline script tags.
     *    - If video URL is pending or blob, triggers playback on tap to force network stream interception.
     * 4. TOUCH & DOWNLOAD DISPATCH:
     *    - Prevents Facebook navigation on tap.
     *    - Dispatches to window.XXVIBridge.onMediaClicked for immediate download start.
     */
    fun getInjectionScript(): String {
        return """
        (function() {
            try {
                // 1. Remove old overlay root if lingering
                var oldOverlay = document.getElementById('xxvi-overlay-root');
                if (oldOverlay && oldOverlay.parentElement) {
                    oldOverlay.parentElement.removeChild(oldOverlay);
                }

                // 2. Inject CSS for in-video, in-story, and in-photo buttons
                var style = document.getElementById('xxvi-dl-style');
                if (!style) {
                    style = document.createElement('style');
                    style.id = 'xxvi-dl-style';
                    style.innerHTML = `
                        .xxvi-btn-injected {
                            position: absolute !important;
                            width: 42px !important;
                            height: 42px !important;
                            border-radius: 50% !important;
                            background: linear-gradient(135deg, #FF9800 0%, #FF8A00 100%) !important;
                            box-shadow: 0 4px 14px rgba(0, 0, 0, 0.45) !important;
                            display: flex !important;
                            align-items: center !important;
                            justify-content: center !important;
                            pointer-events: auto !important;
                            cursor: pointer !important;
                            border: 2px solid #FFFFFF !important;
                            box-sizing: border-box !important;
                            outline: none !important;
                            user-select: none !important;
                            -webkit-tap-highlight-color: transparent !important;
                            transition: transform 0.12s cubic-bezier(0.4, 0, 0.2, 1) !important;
                            z-index: 999999 !important;
                        }
                        .xxvi-btn-injected:active {
                            transform: scale(0.85) !important;
                        }
                        .xxvi-btn-injected svg {
                            width: 20px !important;
                            height: 20px !important;
                            fill: #FFFFFF !important;
                            pointer-events: none !important;
                        }
                        .xxvi-btn-story {
                            width: 40px !important;
                            height: 40px !important;
                            top: auto !important;
                            bottom: 24px !important;
                            right: auto !important;
                            left: 50% !important;
                            transform: translateX(-50%) !important;
                            border-width: 2px !important;
                        }
                        .xxvi-btn-story:active {
                            transform: translateX(-50%) scale(0.85) !important;
                        }
                        .xxvi-btn-photo {
                            width: 40px !important;
                            height: 40px !important;
                            top: auto !important;
                            bottom: 12px !important;
                            right: 12px !important;
                            left: auto !important;
                            border-width: 2px !important;
                        }
                        .xxvi-btn-reel {
                            top: 68px !important;
                            right: 14px !important;
                        }
                    `;
                    (document.head || document.documentElement).appendChild(style);
                }

                var SVG_ICON = '<svg viewBox="0 0 27 27"><path d="M5.63 18.28c0.22 0 0.43 0.09 0.6 0.25 0.15 0.16 0.24 0.37 0.24 0.6v2.25c0 0.15 0.12 0.28 0.28 0.28h13.5c0.07 0 0.15-0.03 0.2-0.09 0.05-0.05 0.08-0.12 0.08-0.2v-2.25c0-0.22 0.09-0.43 0.25-0.6 0.16-0.15 0.37-0.24 0.6-0.24 0.22 0 0.43 0.09 0.6 0.25 0.15 0.16 0.24 0.37 0.24 0.6v2.25c0 0.52-0.2 1.02-0.58 1.39-0.37 0.37-0.87 0.57-1.39 0.57H6.75c-0.52 0-1.02-0.2-1.4-0.57C5 22.4 4.79 21.9 4.79 21.37v-2.25c0-0.22 0.09-0.43 0.25-0.6 0.16-0.15 0.37-0.24 0.6-0.24Z"/><path d="M12.08 4.22c-0.28 0-0.55 0.1-0.75 0.3-0.21 0.18-0.34 0.44-0.36 0.72-0.17 1.97-0.2 3.95-0.1 5.93l-0.83 0.05-1.67 0.12c-0.15 0.01-0.3 0.06-0.42 0.14-0.12 0.09-0.22 0.2-0.28 0.33-0.07 0.13-0.1 0.28-0.1 0.43 0.01 0.15 0.06 0.29 0.14 0.42 1.2 1.86 2.73 3.49 4.54 4.79l0.67 0.48c0.17 0.12 0.37 0.18 0.58 0.18 0.2 0 0.41-0.06 0.58-0.18l0.67-0.48c1.8-1.3 3.34-2.93 4.54-4.8 0.08-0.12 0.13-0.26 0.13-0.41 0.01-0.15-0.02-0.3-0.09-0.43-0.06-0.13-0.16-0.24-0.28-0.33-0.13-0.08-0.27-0.13-0.42-0.14l-1.67-0.12-0.84-0.05c0.11-1.98 0.08-3.96-0.09-5.93-0.02-0.28-0.15-0.54-0.36-0.73-0.2-0.19-0.47-0.3-0.75-0.3h-2.84Z"/></svg>';

                function cleanMediaUrl(u) {
                    if (!u) return '';
                    return u.replace(/\\\//g, '/').replace(/&amp;/g, '&').trim();
                }

                function getTopHeaderOffset(card) {
                    var openApp = document.querySelector('[aria-label*="Open app" i], [aria-label*="Open App" i], a[href*="open-app"], [data-sigil*="open_app"]');
                    if (!openApp) {
                        var allLinks = document.querySelectorAll('a, button, div[role="button"], span');
                        for (var i = 0; i < allLinks.length; i++) {
                            var txt = (allLinks[i].innerText || allLinks[i].textContent || '').trim();
                            if (txt === 'Open app' || txt === 'Open App' || txt.indexOf('Open app') !== -1) {
                                openApp = allLinks[i];
                                break;
                            }
                        }
                    }
                    if (openApp) {
                        var oRect = openApp.getBoundingClientRect();
                        if (oRect.bottom > 20 && oRect.bottom < 140) {
                            return Math.round(oRect.bottom + 14);
                        }
                    }

                    var header = document.querySelector('header, [role="banner"], [aria-label*="Reels" i]');
                    if (header) {
                        var hRect = header.getBoundingClientRect();
                        if (hRect.bottom > 25 && hRect.bottom < 140) {
                            return Math.round(hRect.bottom + 14);
                        }
                    }

                    var cRect = card ? card.getBoundingClientRect() : null;
                    var isReel = window.location.href.indexOf('/reel') !== -1 ||
                                 window.location.href.indexOf('/watch') !== -1 ||
                                 (document.body && document.body.innerText && document.body.innerText.indexOf('Reels') !== -1);

                    if (isReel || (cRect && cRect.top < 30 && cRect.height >= 380)) {
                        return 68;
                    }

                    return 0;
                }

                function hasExistingDownloadButton(el) {
                    if (!el) return false;
                    if (el.querySelector && el.querySelector('.xxvi-btn-injected')) return true;
                    var curr = el.parentElement;
                    while (curr && curr !== document.body && curr !== document.documentElement) {
                        if (curr.classList && curr.classList.contains('xxvi-btn-injected')) return true;
                        if (curr.hasAttribute('data-xxvi-card-handled')) return true;
                        if (curr.querySelector && curr.querySelector(':scope > .xxvi-btn-injected')) return true;
                        curr = curr.parentElement;
                    }
                    return false;
                }

                function getBestImageUrl(img) {
                    if (!img) return '';
                    // 1. Highest resolution from srcset
                    if (img.srcset) {
                        var parts = img.srcset.split(',');
                        var bestSrc = '';
                        var maxW = 0;
                        for (var k = 0; k < parts.length; k++) {
                            var part = parts[k].trim();
                            var matchW = part.match(/^(\S+)\s+(\d+)w/i);
                            if (matchW) {
                                var w = parseInt(matchW[2], 10);
                                if (w > maxW && matchW[1].startsWith('http')) {
                                    maxW = w;
                                    bestSrc = matchW[1];
                                }
                            } else {
                                var cand = part.split(' ')[0];
                                if (cand && cand.startsWith('http') && !bestSrc) bestSrc = cand;
                            }
                        }
                        if (bestSrc) return cleanMediaUrl(bestSrc);
                    }

                    // 2. data-full-res-src, data-src, data-original
                    var ds = img.getAttribute('data-full-res-src') || img.getAttribute('data-src') || img.getAttribute('data-original');
                    if (ds && ds.startsWith('http')) return cleanMediaUrl(ds);

                    // 3. currentSrc or src
                    if (img.currentSrc && img.currentSrc.startsWith('http')) return cleanMediaUrl(img.currentSrc);
                    if (img.src && img.src.startsWith('http')) return cleanMediaUrl(img.src);

                    return '';
                }

                function getBestVideoUrl(videoEl, card) {
                    // 1. Direct src on video element
                    if (videoEl) {
                        if (videoEl.currentSrc && videoEl.currentSrc.startsWith('http') && !videoEl.currentSrc.startsWith('blob:')) {
                            return cleanMediaUrl(videoEl.currentSrc);
                        }
                        if (videoEl.src && videoEl.src.startsWith('http') && !videoEl.src.startsWith('blob:')) {
                            return cleanMediaUrl(videoEl.src);
                        }
                        var s = videoEl.querySelector ? videoEl.querySelector('source') : null;
                        if (s && s.src && s.src.startsWith('http') && !s.src.startsWith('blob:')) {
                            return cleanMediaUrl(s.src);
                        }
                    }

                    // 2. data-store & data-video-url
                    var nodes = [videoEl, card];
                    if (videoEl && videoEl.parentElement) nodes.push(videoEl.parentElement);
                    for (var i = 0; i < nodes.length; i++) {
                        var n = nodes[i];
                        if (!n || !n.getAttribute) continue;
                        var ds = n.getAttribute('data-store');
                        if (ds) {
                            try {
                                var data = JSON.parse(ds);
                                var u = data.src || data.videoURL || data.sd_src || data.hd_src || data.playable_url || data.playable_url_quality_hd;
                                if (u && u.startsWith('http')) return cleanMediaUrl(u);
                            } catch(e) {}
                        }
                        var dvu = n.getAttribute('data-video-url');
                        if (dvu && dvu.startsWith('http')) return cleanMediaUrl(dvu);
                    }

                    // 3. Inner scripts
                    if (card && card.querySelectorAll) {
                        var scripts = card.querySelectorAll('script, template');
                        for (var s = 0; s < scripts.length; s++) {
                            var txt = scripts[s].innerHTML || scripts[s].textContent || '';
                            var m = txt.match(/["'](https?:\\\/\\\/[^"']+fbcdn\.net\\\/[^"']+)["']/i) ||
                                    txt.match(/["'](https?:\/\/[^"']+fbcdn\.net\/[^"']+)["']/i);
                            if (m && m[1]) {
                                var clean = cleanMediaUrl(m[1]);
                                if (clean.indexOf('.mp4') !== -1 || clean.indexOf('video') !== -1) return clean;
                            }
                        }
                    }

                    return '';
                }

                function getExactMediaUrl(mediaEl) {
                    if (!mediaEl) return '';

                    var vUrl = getBestVideoUrl(mediaEl, mediaEl.parentElement);
                    if (vUrl) return vUrl;

                    var img = (mediaEl.tagName === 'IMG') ? mediaEl : (mediaEl.querySelector ? mediaEl.querySelector('img') : null);
                    if (img) {
                        var iUrl = getBestImageUrl(img);
                        if (iUrl) return iUrl;
                    }

                    var bg = '';
                    if (mediaEl.style && mediaEl.style.backgroundImage) {
                        bg = mediaEl.style.backgroundImage;
                    } else if (window.getComputedStyle) {
                        var comp = window.getComputedStyle(mediaEl);
                        if (comp && comp.backgroundImage) bg = comp.backgroundImage;
                    }
                    if (bg && bg !== 'none') {
                        var match = bg.match(/url\(['"]?(https?:\/\/[^'")]+)['"]?\)/i);
                        if (match && match[1]) return cleanMediaUrl(match[1]);
                    }

                    return '';
                }

                function findBestVideoCard(videoEl) {
                    var curr = videoEl.parentElement;
                    var best = videoEl.parentElement;

                    while (curr && curr !== document.body && curr !== document.documentElement) {
                        if (curr.id === 'root' || curr.id === 'viewport' || curr.id === 'm-story-view') break;

                        var r = curr.getBoundingClientRect();
                        if (r.height >= (window.innerHeight - 15) && r.width >= (window.innerWidth - 15) && curr !== videoEl.parentElement) {
                            break;
                        }

                        if (curr.querySelector && curr.querySelector('header, [role="banner"], [aria-label="Back"]')) {
                            break;
                        }

                        if (r.width >= 100 && r.height >= 80) {
                            best = curr;
                            var sigil = curr.getAttribute('data-sigil') || '';
                            var role = curr.getAttribute('role') || '';
                            if (sigil.indexOf('inlineVideo') !== -1 || sigil.indexOf('video') !== -1 || role === 'article') {
                                break;
                            }
                        }
                        curr = curr.parentElement;
                    }
                    return best;
                }

                function createDownloadButton(type, mediaEl) {
                    var btn = document.createElement('div');
                    var classSuffix = type === 'story' ? ' xxvi-btn-story' : (type === 'image' ? ' xxvi-btn-photo' : '');
                    btn.className = 'xxvi-btn-injected' + classSuffix;
                    btn.innerHTML = SVG_ICON;

                    var clicked = false;
                    function triggerDownload(e) {
                        if (clicked) return;
                        clicked = true;
                        setTimeout(function() { clicked = false; }, 800);

                        e.stopPropagation();
                        e.preventDefault();
                        if (e.stopImmediatePropagation) e.stopImmediatePropagation();

                        // If video is paused, trigger play to initialize network stream
                        if (type === 'video') {
                            var v = (mediaEl.tagName === 'VIDEO') ? mediaEl : (mediaEl.querySelector ? mediaEl.querySelector('video') : null);
                            if (v && v.paused) {
                                try { v.play(); } catch (err) {}
                            }
                        }

                        var exactUrl = getExactMediaUrl(mediaEl);
                        var thumb = '';
                        if (mediaEl.tagName === 'IMG') {
                            thumb = getBestImageUrl(mediaEl);
                        } else if (mediaEl.querySelector && mediaEl.querySelector('img')) {
                            thumb = getBestImageUrl(mediaEl.querySelector('img'));
                        } else if (type === 'video') {
                            var vEl = (mediaEl.tagName === 'VIDEO') ? mediaEl : (mediaEl.querySelector ? mediaEl.querySelector('video') : null);
                            if (vEl && vEl.poster) thumb = cleanMediaUrl(vEl.poster);
                        }
                        if (!thumb && exactUrl && type !== 'video') thumb = exactUrl;

                        var title = 'facebook_' + (type === 'image' ? 'photo' : type) + '_' + Math.floor(Date.now() % 1000000);

                        if (window.XXVIBridge) {
                            window.XXVIBridge.onMediaClicked(exactUrl, type, title, thumb);
                        }
                    }

                    btn.addEventListener('click', triggerDownload, true);
                    btn.addEventListener('touchend', triggerDownload, true);
                    btn.addEventListener('touchstart', function(e) { e.stopPropagation(); }, true);
                    btn.addEventListener('pointerdown', function(e) { e.stopPropagation(); }, true);

                    return btn;
                }

                // --- 1. ATTACH TO EVERY VIDEO & REEL ---
                function attachVideoButtons() {
                    var videos = document.querySelectorAll('video');
                    videos.forEach(function(v) {
                        var rect = v.getBoundingClientRect();
                        if (rect.width < 60 || rect.height < 60) return;

                        var card = findBestVideoCard(v);
                        if (!card) return;

                        var existingBtn = card.querySelector(':scope > .xxvi-btn-injected') || v.querySelector(':scope > .xxvi-btn-injected');
                        var topOff = getTopHeaderOffset(card);

                        if (existingBtn) {
                            if (topOff > 20) {
                                existingBtn.classList.add('xxvi-btn-reel');
                                existingBtn.style.setProperty('top', topOff + 'px', 'important');
                                existingBtn.style.setProperty('right', '12px', 'important');
                            }
                            return;
                        }

                        if (hasExistingDownloadButton(card) || hasExistingDownloadButton(v)) return;

                        var pos = window.getComputedStyle(card).position;
                        if (pos === 'static') {
                            card.style.position = 'relative';
                        }

                        var btn = createDownloadButton('video', v);
                        if (topOff > 20) {
                            btn.classList.add('xxvi-btn-reel');
                            btn.style.setProperty('top', topOff + 'px', 'important');
                            btn.style.setProperty('right', '12px', 'important');
                        }

                        card.appendChild(btn);
                        card.setAttribute('data-xxvi-card-handled', 'true');
                        v.setAttribute('data-xxvi-card-handled', 'true');

                        var src = getBestVideoUrl(v, card);
                        if (src && src.startsWith('http') && window.XXVIBridge) {
                            window.XXVIBridge.onMediaDetected(src, 'video', 'facebook_video', v.poster || '');
                        }
                    });

                    // Inline video containers without <video> yet
                    var inlineContainers = document.querySelectorAll('[data-sigil*="inlineVideo"], [data-video-id]');
                    inlineContainers.forEach(function(vc) {
                        if (vc.querySelector('video')) return;

                        var existingBtn = vc.querySelector(':scope > .xxvi-btn-injected');
                        var topOff = getTopHeaderOffset(vc);
                        if (existingBtn) {
                            if (topOff > 20) {
                                existingBtn.classList.add('xxvi-btn-reel');
                                existingBtn.style.setProperty('top', topOff + 'px', 'important');
                                existingBtn.style.setProperty('right', '12px', 'important');
                            }
                            return;
                        }

                        if (hasExistingDownloadButton(vc)) return;

                        var rect = vc.getBoundingClientRect();
                        if (rect.width < 100 || rect.height < 80) return;

                        var pos = window.getComputedStyle(vc).position;
                        if (pos === 'static') {
                            vc.style.position = 'relative';
                        }

                        var btn = createDownloadButton('video', vc);
                        if (topOff > 20) {
                            btn.classList.add('xxvi-btn-reel');
                            btn.style.setProperty('top', topOff + 'px', 'important');
                            btn.style.setProperty('right', '12px', 'important');
                        }

                        vc.appendChild(btn);
                        vc.setAttribute('data-xxvi-card-handled', 'true');

                        var src = getBestVideoUrl(null, vc);
                        if (src && src.startsWith('http') && window.XXVIBridge) {
                            window.XXVIBridge.onMediaDetected(src, 'video', 'facebook_video', '');
                        }
                    });
                }

                // --- 2. ATTACH TO EVERY STORY (SKIP CREATE STORY COMPLETELY) ---
                function attachStoryButtons() {
                    var allDivs = document.querySelectorAll('div');
                    var carousel = null;
                    for (var i = 0; i < allDivs.length; i++) {
                        var d = allDivs[i];
                        if (d.scrollWidth > d.clientWidth && d.clientWidth > 200) {
                            var dr = d.getBoundingClientRect();
                            if (dr.top > 10 && dr.top < 380) {
                                carousel = d;
                                break;
                            }
                        }
                    }

                    if (carousel) {
                        carousel.setAttribute('data-xxvi-story-tray', 'true');
                        var items = carousel.querySelectorAll(':scope > div, :scope > div > div, div[role="button"], a[href*="/stories/"]');
                        var handledCards = [];

                        for (var idx = 0; idx < items.length; idx++) {
                            var card = items[idx];
                            var r = card.getBoundingClientRect();
                            if (r.width < 50 || r.width > 160 || r.height < 80 || r.height > 260) continue;

                            var isSub = handledCards.some(function(parent) { return parent.contains(card); });
                            if (isSub) continue;
                            handledCards.push(card);

                            var text = (card.innerText || card.textContent || '').toLowerCase();
                            var aria = (card.getAttribute('aria-label') || '').toLowerCase();

                            // STRICTLY SKIP "Create story" card (index 0 or has create text/icon)
                            if (handledCards.length === 1 && (text.indexOf('create') !== -1 || card.querySelector('[aria-label*="Create" i], a[href*="stories/create"], svg[aria-label*="Create" i], .plus, [data-sigil*="create" i]'))) {
                                card.setAttribute('data-xxvi-card-handled', 'true');
                                var subs = card.querySelectorAll('*');
                                for (var s = 0; s < subs.length; s++) {
                                    subs[s].setAttribute('data-xxvi-card-handled', 'true');
                                    var stray = subs[s].querySelector ? subs[s].querySelector('.xxvi-btn-injected') : null;
                                    if (stray && stray.parentElement) stray.parentElement.removeChild(stray);
                                }
                                continue;
                            }

                            if (text.indexOf('create story') !== -1 ||
                                text.indexOf('create a story') !== -1 ||
                                text.indexOf('add to story') !== -1 ||
                                aria.indexOf('create story') !== -1 ||
                                card.querySelector('[aria-label*="Create" i], a[href*="stories/create"], svg[aria-label*="Create" i], .plus, [data-sigil*="create" i]')) {
                                card.setAttribute('data-xxvi-card-handled', 'true');
                                var subs2 = card.querySelectorAll('*');
                                for (var s2 = 0; s2 < subs2.length; s2++) {
                                    subs2[s2].setAttribute('data-xxvi-card-handled', 'true');
                                    var stray2 = subs2[s2].querySelector ? subs2[s2].querySelector('.xxvi-btn-injected') : null;
                                    if (stray2 && stray2.parentElement) stray2.parentElement.removeChild(stray2);
                                }
                                continue;
                            }

                            if (hasExistingDownloadButton(card)) continue;

                            var pos = window.getComputedStyle(card).position;
                            if (pos === 'static') {
                                card.style.position = 'relative';
                            }
                            var btn = createDownloadButton('story', card);
                            card.appendChild(btn);
                            card.setAttribute('data-xxvi-card-handled', 'true');

                            var src = getExactMediaUrl(card);
                            if (src && src.startsWith('http') && window.XXVIBridge) {
                                var author = text.split('\n')[0] || 'story';
                                window.XXVIBridge.onMediaDetected(src, 'story', 'facebook_' + author.replace(/[^a-zA-Z0-9]/g, '_'), src);
                            }
                        }
                    }

                    // Direct story viewer links
                    var storyLinks = document.querySelectorAll('a[href*="/stories/"]:not([href*="stories/create"]), a[href*="story.php"]');
                    storyLinks.forEach(function(sl) {
                        if (hasExistingDownloadButton(sl)) return;
                        var r = sl.getBoundingClientRect();
                        if (r.width < 45 || r.height < 65) return;

                        var pos = window.getComputedStyle(sl).position;
                        if (pos === 'static') {
                            sl.style.position = 'relative';
                        }
                        var btn = createDownloadButton('story', sl);
                        sl.appendChild(btn);
                        sl.setAttribute('data-xxvi-card-handled', 'true');

                        var src = getExactMediaUrl(sl);
                        if (src && src.startsWith('http') && window.XXVIBridge) {
                            window.XXVIBridge.onMediaDetected(src, 'story', 'facebook_story', '');
                        }
                    });
                }

                // --- 3. ATTACH TO EVERY PHOTO / IMAGE (EXCLUDING STORIES & VIDEOS) ---
                function attachPhotoButtons() {
                    var imgs = document.querySelectorAll('img');
                    imgs.forEach(function(img) {
                        if (hasExistingDownloadButton(img)) return;

                        // CRITICAL: NEVER inject inside story tray, story cards, or Create story!
                        if (img.closest && img.closest('[data-xxvi-story-tray], [data-xxvi-card-handled], [aria-label*="stories" i], [aria-label*="story" i]')) {
                            return;
                        }

                        var w = img.offsetWidth || img.clientWidth || (img.getBoundingClientRect ? img.getBoundingClientRect().width : 0) || img.naturalWidth || 0;
                        var h = img.offsetHeight || img.clientHeight || (img.getBoundingClientRect ? img.getBoundingClientRect().height : 0) || img.naturalHeight || 0;

                        // Post photos are at least 110x110px. Ignore tiny icons, avatars, buttons
                        if (w > 0 && h > 0 && (w < 110 || h < 110)) return;

                        if (!img.complete || img.naturalWidth === 0) {
                            img.addEventListener('load', function() { attachPhotoButtons(); }, { once: true });
                            return;
                        }

                        if (img.naturalWidth < 110 || img.naturalHeight < 110) return;

                        var src = getBestImageUrl(img);
                        if (!src || !src.startsWith('http')) return;

                        var sLower = src.toLowerCase();
                        // Ignore UI assets, emojis, sprites, blank pixels
                        if (sLower.indexOf('rsrc.php') !== -1 ||
                            sLower.indexOf('emoji.php') !== -1 ||
                            sLower.indexOf('blank.gif') !== -1 ||
                            sLower.indexOf('/static_resources/') !== -1 ||
                            sLower.indexOf('/assets/') !== -1 ||
                            sLower.indexOf('spacer.gif') !== -1 ||
                            sLower.indexOf('data:image/svg') !== -1) {
                            return;
                        }

                        // Ignore profile avatars unless viewing full profile picture
                        var alt = (img.getAttribute('alt') || '').toLowerCase();
                        var aria = (img.getAttribute('aria-label') || '').toLowerCase();
                        if ((alt.indexOf('profile picture') !== -1 || alt.indexOf('avatar') !== -1 || aria.indexOf('profile') !== -1) && (w < 180 || h < 180)) {
                            return;
                        }

                        // Skip images inside reels or video cards
                        if (img.closest) {
                            var parentMedia = img.closest('[data-sigil*="inlineVideo"], [data-video-id], video, a[href*="/stories/"], a[href*="story.php"]');
                            if (parentMedia) return;
                        }

                        var container = img.parentElement;
                        if (!container) return;
                        if (hasExistingDownloadButton(container)) return;

                        var pos = window.getComputedStyle(container).position;
                        if (pos === 'static') {
                            container.style.position = 'relative';
                        }
                        var disp = window.getComputedStyle(container).display;
                        if (disp === 'inline') {
                            container.style.display = 'inline-block';
                        }

                        var btn = createDownloadButton('image', img);
                        container.appendChild(btn);
                        container.setAttribute('data-xxvi-card-handled', 'true');
                        img.setAttribute('data-xxvi-card-handled', 'true');

                        if (window.XXVIBridge) {
                            window.XXVIBridge.onMediaDetected(src, 'image', 'facebook_photo', src);
                        }
                    });

                    // Elements with CSS background-image
                    var bgElements = document.querySelectorAll('[style*="background-image"], div[role="img"], i[role="img"]');
                    bgElements.forEach(function(bge) {
                        if (hasExistingDownloadButton(bge)) return;

                        if (bge.closest && bge.closest('[data-xxvi-story-tray], [data-xxvi-card-handled], [data-sigil*="inlineVideo"], [data-video-id], video, a[href*="/stories/"], a[href*="story.php"]')) {
                            return;
                        }

                        var w = bge.offsetWidth || bge.clientWidth || 0;
                        var h = bge.offsetHeight || bge.clientHeight || 0;
                        if (w < 110 || h < 110) return;

                        var bgUrl = getExactMediaUrl(bge);
                        if (!bgUrl || !bgUrl.startsWith('http')) return;

                        var sLower = bgUrl.toLowerCase();
                        if (sLower.indexOf('rsrc.php') !== -1 ||
                            sLower.indexOf('emoji.php') !== -1 ||
                            sLower.indexOf('blank.gif') !== -1 ||
                            sLower.indexOf('/static_resources/') !== -1) {
                            return;
                        }

                        var pos = window.getComputedStyle(bge).position;
                        if (pos === 'static') {
                            bge.style.position = 'relative';
                        }

                        var btn = createDownloadButton('image', bge);
                        bge.appendChild(btn);
                        bge.setAttribute('data-xxvi-card-handled', 'true');

                        if (window.XXVIBridge) {
                            window.XXVIBridge.onMediaDetected(bgUrl, 'image', 'facebook_photo', bgUrl);
                        }
                    });
                }

                // --- 4. CLEANUP PASS ---
                function cleanupDuplicateButtons() {
                    // Remove duplicate sibling buttons
                    var handledContainers = document.querySelectorAll('[data-xxvi-card-handled]');
                    handledContainers.forEach(function(card) {
                        var btns = card.querySelectorAll(':scope > .xxvi-btn-injected');
                        if (btns.length > 1) {
                            for (var i = 1; i < btns.length; i++) {
                                if (btns[i] && btns[i].parentElement) {
                                    btns[i].parentElement.removeChild(btns[i]);
                                }
                            }
                        }
                    });

                    // Remove buttons incorrectly inside create story
                    var createCards = document.querySelectorAll('[data-xxvi-story-tray] [aria-label*="Create" i], [data-xxvi-story-tray] a[href*="stories/create"]');
                    createCards.forEach(function(cc) {
                        var root = cc.closest('div') || cc;
                        var b = root.querySelector('.xxvi-btn-injected');
                        if (b && b.parentElement) b.parentElement.removeChild(b);
                    });

                    // Safe top offset for reels/videos
                    var videoBtns = document.querySelectorAll('.xxvi-btn-injected:not(.xxvi-btn-story):not(.xxvi-btn-photo)');
                    videoBtns.forEach(function(b) {
                        var p = b.parentElement;
                        if (p) {
                            var neededTop = getTopHeaderOffset(p);
                            if (neededTop > 20) {
                                b.classList.add('xxvi-btn-reel');
                                b.style.setProperty('top', neededTop + 'px', 'important');
                                b.style.setProperty('right', '12px', 'important');
                            }
                        }
                    });
                }

                function runScanner() {
                    attachVideoButtons();
                    attachStoryButtons();
                    attachPhotoButtons();
                    cleanupDuplicateButtons();
                }

                runScanner();

                if (!window.__XXVI_SCROLL_LISTENER__) {
                    window.__XXVI_SCROLL_LISTENER__ = true;
                    window.addEventListener('scroll', runScanner, { passive: true });
                }

                if (!window.__XXVI_MUTATION_OBSERVER__) {
                    window.__XXVI_MUTATION_OBSERVER__ = true;
                    var observer = new MutationObserver(function() {
                        runScanner();
                    });
                    observer.observe(document.body || document.documentElement, { childList: true, subtree: true });
                }
            } catch (err) {
                console.error('XXVI Sniffer error: ' + err);
            }
        })();
        """.trimIndent()
    }
}
