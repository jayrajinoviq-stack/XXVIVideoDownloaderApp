package com.inoviq.videodownloader.videosaver.storysaver.utils

object FacebookMediaSniffer {

    /**
     * Injected JavaScript for m.facebook.com:
     * 1. IN-CARD INJECTION: Buttons are injected directly inside the video card, reel card, or story card.
     *    Because the button is an actual child element with position: absolute, it is physically part
     *    of the video/story element. When scrolling, the browser compositor moves the video and button
     *    together with zero lag, zero delay, and zero desync — exactly as if it was part of the video.
     * 2. EVERY VIDEO & STORY SUPPORTED:
     *    - Feed videos (normal posts, Watch, live streams)
     *    - Facebook Reels (vertical full-frame videos)
     *    - Story carousel cards (moves left/right with horizontal swipe)
     *    - Fullscreen story viewer (stories opened in viewer)
     *    - Feed photo posts
     * 3. INSTANT TOUCH RESPONSE: Captures touchend & click with stopImmediatePropagation so Facebook
     *    never navigates or triggers video fullscreen when tapping download.
     */
    fun getInjectionScript(): String {
        return """
        (function() {
            try {
                // 1. Remove any old overlay root if lingering
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
                            top: 10px !important;
                            right: 10px !important;
                            width: 40px !important;
                            height: 40px !important;
                            border-radius: 50% !important;
                            background: linear-gradient(135deg, #FA9500 0%, #FD7B01 100%) !important;
                            box-shadow: 0 4px 14px rgba(0, 0, 0, 0.55) !important;
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
                            transform: scale(0.82) !important;
                        }
                        .xxvi-btn-injected svg {
                            width: 19px !important;
                            height: 19px !important;
                            fill: #FFFFFF !important;
                            pointer-events: none !important;
                        }
                        .xxvi-btn-story {
                            width: 32px !important;
                            height: 32px !important;
                            top: 6px !important;
                            right: 6px !important;
                            border-width: 1.5px !important;
                        }
                        .xxvi-btn-story svg {
                            width: 15px !important;
                            height: 15px !important;
                        }
                        .xxvi-btn-photo {
                            width: 36px !important;
                            height: 36px !important;
                            top: 8px !important;
                            right: 8px !important;
                            border-width: 2px !important;
                        }
                        .xxvi-btn-photo svg {
                            width: 17px !important;
                            height: 17px !important;
                        }
                        .xxvi-btn-reel {
                            top: 68px !important;
                            right: 12px !important;
                        }
                    `;
                    (document.head || document.documentElement).appendChild(style);
                }

                var SVG_ICON = '<svg viewBox="0 0 27 27"><path d="M5.63 18.28c0.22 0 0.43 0.09 0.6 0.25 0.15 0.16 0.24 0.37 0.24 0.6v2.25c0 0.15 0.12 0.28 0.28 0.28h13.5c0.07 0 0.15-0.03 0.2-0.09 0.05-0.05 0.08-0.12 0.08-0.2v-2.25c0-0.22 0.09-0.43 0.25-0.6 0.16-0.15 0.37-0.24 0.6-0.24 0.22 0 0.43 0.09 0.6 0.25 0.15 0.16 0.24 0.37 0.24 0.6v2.25c0 0.52-0.2 1.02-0.58 1.39-0.37 0.37-0.87 0.57-1.39 0.57H6.75c-0.52 0-1.02-0.2-1.4-0.57C5 22.4 4.79 21.9 4.79 21.37v-2.25c0-0.22 0.09-0.43 0.25-0.6 0.16-0.15 0.37-0.24 0.6-0.24Z"/><path d="M12.08 4.22c-0.28 0-0.55 0.1-0.75 0.3-0.21 0.18-0.34 0.44-0.36 0.72-0.17 1.97-0.2 3.95-0.1 5.93l-0.83 0.05-1.67 0.12c-0.15 0.01-0.3 0.06-0.42 0.14-0.12 0.09-0.22 0.2-0.28 0.33-0.07 0.13-0.1 0.28-0.1 0.43 0.01 0.15 0.06 0.29 0.14 0.42 1.2 1.86 2.73 3.49 4.54 4.79l0.67 0.48c0.17 0.12 0.37 0.18 0.58 0.18 0.2 0 0.41-0.06 0.58-0.18l0.67-0.48c1.8-1.3 3.34-2.93 4.54-4.8 0.08-0.12 0.13-0.26 0.13-0.41 0.01-0.15-0.02-0.3-0.09-0.43-0.06-0.13-0.16-0.24-0.28-0.33-0.13-0.08-0.27-0.13-0.42-0.14l-1.67-0.12-0.84-0.05c0.11-1.98 0.08-3.96-0.09-5.93-0.02-0.28-0.15-0.54-0.36-0.73-0.2-0.19-0.47-0.3-0.75-0.3h-2.84Z"/></svg>';

                // Dynamically computes safe top offset below "Open app" button or header
                function getTopHeaderOffset(card) {
                    var openApp = document.querySelector('[aria-label*="Open app"], [aria-label*="Open App"], a[href*="open-app"], [data-sigil*="open_app"]');
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

                    var header = document.querySelector('header, [role="banner"], [aria-label*="Reels"]');
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

                // Strict check: Returns true if el or ANY parent/child has a download button
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

                function getExactMediaUrl(mediaEl) {
                    if (!mediaEl) return '';

                    // 1. Check data-store JSON (Facebook's direct MP4 URL)
                    var targets = [mediaEl, mediaEl.parentElement, (mediaEl.closest ? mediaEl.closest('[data-store]') : null), (mediaEl.querySelector ? mediaEl.querySelector('[data-store]') : null)];
                    for (var i = 0; i < targets.length; i++) {
                        var node = targets[i];
                        if (node && node.getAttribute) {
                            var ds = node.getAttribute('data-store');
                            if (ds) {
                                try {
                                    var data = JSON.parse(ds);
                                    var u = data.src || data.videoURL || data.sd_src || data.hd_src || data.playable_url || data.playable_url_quality_hd;
                                    if (u && u.startsWith('http')) return u;
                                } catch (e) {}
                            }
                            var dvu = node.getAttribute('data-video-url');
                            if (dvu && dvu.startsWith('http')) return dvu;
                        }
                    }

                    // 2. Video element src or source tag
                    var v = (mediaEl.tagName === 'VIDEO') ? mediaEl : (mediaEl.querySelector ? mediaEl.querySelector('video') : null);
                    if (v) {
                        if (v.currentSrc && v.currentSrc.startsWith('http')) return v.currentSrc;
                        if (v.src && v.src.startsWith('http')) return v.src;
                        var s = v.querySelector ? v.querySelector('source') : null;
                        if (s && s.src && s.src.startsWith('http')) return s.src;
                    }

                    // 3. Image element: pick highest resolution from srcset, or currentSrc/src
                    var img = (mediaEl.tagName === 'IMG') ? mediaEl : (mediaEl.querySelector ? mediaEl.querySelector('img') : null);
                    if (img) {
                        if (img.srcset) {
                            var parts = img.srcset.split(',');
                            var bestSrc = '';
                            var maxW = 0;
                            for (var k = 0; k < parts.length; k++) {
                                var part = parts[k].trim();
                                var matchW = part.match(/^(\S+)\s+(\d+)w/);
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
                            if (bestSrc) return bestSrc;
                        }
                        if (img.currentSrc && img.currentSrc.startsWith('http')) return img.currentSrc;
                        if (img.src && img.src.startsWith('http')) return img.src;
                        var ds = img.getAttribute('data-src') || img.getAttribute('data-original');
                        if (ds && ds.startsWith('http')) return ds;
                    }

                    // 4. Background-image CSS URL
                    var bg = '';
                    if (mediaEl.style && mediaEl.style.backgroundImage) {
                        bg = mediaEl.style.backgroundImage;
                    } else if (window.getComputedStyle) {
                        var comp = window.getComputedStyle(mediaEl);
                        if (comp && comp.backgroundImage) bg = comp.backgroundImage;
                    }
                    if (bg && bg !== 'none') {
                        var match = bg.match(/url\(['"]?(https?:\/\/[^'")]+)['"]?\)/i);
                        if (match && match[1]) return match[1];
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
                            thumb = mediaEl.currentSrc || mediaEl.src || '';
                        } else if (mediaEl.querySelector && mediaEl.querySelector('img')) {
                            var tImg = mediaEl.querySelector('img');
                            thumb = tImg.currentSrc || tImg.src || '';
                        } else if (type === 'video') {
                            var vEl = (mediaEl.tagName === 'VIDEO') ? mediaEl : (mediaEl.querySelector ? mediaEl.querySelector('video') : null);
                            if (vEl && vEl.poster) thumb = vEl.poster;
                        }
                        if (!thumb && exactUrl) thumb = exactUrl;
                        if (!exactUrl && thumb) exactUrl = thumb;

                        var title = 'facebook_' + (type === 'image' ? 'photo' : type) + '_' + Math.floor(Date.now() % 1000000);

                        if (window.XXVIBridge) {
                            window.XXVIBridge.onMediaClicked(exactUrl, type, title, thumb);
                        }
                    }

                    // Prevent click propagation to Facebook links and video player
                    btn.addEventListener('click', triggerDownload, true);
                    btn.addEventListener('touchend', triggerDownload, true);
                    btn.addEventListener('touchstart', function(e) { e.stopPropagation(); }, true);
                    btn.addEventListener('pointerdown', function(e) { e.stopPropagation(); }, true);

                    return btn;
                }

                // --- 1. ATTACH TO EVERY VIDEO & REEL (STRICT 1:1, SAFE BELOW OPEN APP) ---
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
                            // If button already exists, ensure it is safely positioned below Open app
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

                        var src = getExactMediaUrl(v);
                        if (src && src.startsWith('http') && window.XXVIBridge) {
                            window.XXVIBridge.onMediaDetected(src, 'video', 'facebook_video', v.poster || '');
                        }
                    });

                    // Check Facebook inline video containers that DO NOT have a <video> element yet
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

                        var src = getExactMediaUrl(vc);
                        if (src && src.startsWith('http') && window.XXVIBridge) {
                            window.XXVIBridge.onMediaDetected(src, 'video', 'facebook_video', '');
                        }
                    });
                }

                // --- 2. ATTACH TO EVERY STORY (EXACTLY ONE PER STORY CARD, SKIP CREATE STORY) ---
                function attachStoryButtons() {
                    var allDivs = document.querySelectorAll('div');
                    var carousel = null;
                    for (var i = 0; i < allDivs.length; i++) {
                        var d = allDivs[i];
                        if (d.scrollWidth > d.clientWidth && d.clientWidth > 220) {
                            var dr = d.getBoundingClientRect();
                            if (dr.top > 15 && dr.top < 380) {
                                carousel = d;
                                break;
                            }
                        }
                    }

                    if (carousel) {
                        var cards = carousel.querySelectorAll('div[role="button"], a, div');
                        var processedCards = [];

                        cards.forEach(function(c) {
                            var r = c.getBoundingClientRect();
                            if (r.width >= 55 && r.width <= 150 && r.height >= 95 && r.height <= 250) {
                                var isSub = processedCards.some(function(parent) { return parent.contains(c); });
                                if (!isSub) {
                                    processedCards.push(c);

                                    // SKIP "Create story" card
                                    var txt = (c.innerText || c.textContent || '').toLowerCase();
                                    var aria = (c.getAttribute('aria-label') || '').toLowerCase();
                                    if (txt.indexOf('create story') !== -1 ||
                                        txt.indexOf('add to story') !== -1 ||
                                        aria.indexOf('create story') !== -1 ||
                                        c.querySelector('[aria-label*="Create"], svg[aria-label*="Create"], .plus, [data-sigil*="create"]')) {
                                        c.setAttribute('data-xxvi-card-handled', 'true');
                                        return;
                                    }

                                    if (hasExistingDownloadButton(c)) return;

                                    var pos = window.getComputedStyle(c).position;
                                    if (pos === 'static') {
                                        c.style.position = 'relative';
                                    }
                                    var btn = createDownloadButton('story', c);
                                    c.appendChild(btn);
                                    c.setAttribute('data-xxvi-card-handled', 'true');

                                    var src = getExactMediaUrl(c);
                                    if (src && src.startsWith('http') && window.XXVIBridge) {
                                        window.XXVIBridge.onMediaDetected(src, 'story', 'facebook_story', '');
                                    }
                                }
                            }
                        });
                    }

                    var storyLinks = document.querySelectorAll('a[href*="/stories/"], a[href*="story.php"]');
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

                        var w = img.offsetWidth || img.clientWidth || (img.getBoundingClientRect ? img.getBoundingClientRect().width : 0) || img.naturalWidth || 0;
                        var h = img.offsetHeight || img.clientHeight || (img.getBoundingClientRect ? img.getBoundingClientRect().height : 0) || img.naturalHeight || 0;

                        // Ignore tiny icons or avatars
                        if (w > 0 && h > 0 && (w < 60 || h < 60)) return;

                        // If not yet fully loaded, attach listener once loaded
                        if (!img.complete || img.naturalWidth === 0) {
                            if (img.addEventListener) {
                                img.addEventListener('load', function() {
                                    attachPhotoButtons();
                                }, { once: true });
                            }
                            return;
                        }

                        if (img.naturalWidth < 60 || img.naturalHeight < 60) return;

                        var src = img.currentSrc || img.src || '';
                        if (!src || !src.startsWith('http')) {
                            var ds = img.getAttribute('data-src') || img.getAttribute('data-original');
                            if (ds && ds.startsWith('http')) src = ds;
                        }
                        if (!src || !src.startsWith('http')) return;

                        var sLower = src.toLowerCase();
                        // Ignore Facebook UI assets, emojis, sprites, blank pixels
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
                        if ((alt.indexOf('profile picture') !== -1 || alt.indexOf('avatar') !== -1) && (w < 130 || h < 130)) {
                            return;
                        }

                        // CRITICAL: Skip images inside story cards, reels, or video cards
                        if (img.closest) {
                            var parentMedia = img.closest('[data-xxvi-card-handled], [data-sigil*="inlineVideo"], [data-video-id], video, a[href*="/stories/"], a[href*="story.php"]');
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

                    // Check elements with background-image
                    var bgElements = document.querySelectorAll('[style*="background-image"], div[role="img"], i[role="img"]');
                    bgElements.forEach(function(bge) {
                        if (hasExistingDownloadButton(bge)) return;

                        var w = bge.offsetWidth || bge.clientWidth || 0;
                        var h = bge.offsetHeight || bge.clientHeight || 0;
                        if (w < 60 || h < 60) return;

                        var bgUrl = getExactMediaUrl(bge);
                        if (!bgUrl || !bgUrl.startsWith('http')) return;

                        var sLower = bgUrl.toLowerCase();
                        if (sLower.indexOf('rsrc.php') !== -1 ||
                            sLower.indexOf('emoji.php') !== -1 ||
                            sLower.indexOf('blank.gif') !== -1 ||
                            sLower.indexOf('/static_resources/') !== -1) {
                            return;
                        }

                        if (bge.closest && bge.closest('[data-xxvi-card-handled], [data-sigil*="inlineVideo"], [data-video-id], video, a[href*="/stories/"], a[href*="story.php"]')) return;

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

                // --- 4. CLEANUP PASS: PRUNE DUPLICATES & ENFORCE SAFE POSITIONS ---
                function cleanupDuplicateButtons() {
                    // 1. Remove duplicate sibling buttons in the same container
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

                    // 2. Remove nested duplicates (child button inside card that already has a button)
                    var allBtns = document.querySelectorAll('.xxvi-btn-injected');
                    allBtns.forEach(function(btn) {
                        var p = btn.parentElement;
                        while (p && p !== document.body && p !== document.documentElement) {
                            var ancestorBtn = p.querySelector(':scope > .xxvi-btn-injected');
                            if (ancestorBtn && ancestorBtn !== btn) {
                                if (btn.parentElement) {
                                    btn.parentElement.removeChild(btn);
                                }
                                break;
                            }
                            p = p.parentElement;
                        }
                    });

                    // 3. Keep video/reel buttons safely below Open app button or top header bar
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

                // Initial scan
                runScanner();

                // Listen for window scroll to immediately inject on new items in view
                if (!window.__XXVI_SCROLL_LISTENER__) {
                    window.__XXVI_SCROLL_LISTENER__ = true;
                    window.addEventListener('scroll', runScanner, { passive: true });
                }

                // Continuous observer for dynamic feed scrolling & SPA route changes
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
