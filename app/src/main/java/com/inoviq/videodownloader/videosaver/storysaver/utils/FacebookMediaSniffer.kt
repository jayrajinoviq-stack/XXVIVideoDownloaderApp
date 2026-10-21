package com.inoviq.videodownloader.videosaver.storysaver.utils

object FacebookMediaSniffer {

    /**
     * Injected JavaScript for Universal Web Media Detection & Download Button Injection:
     * 1. UNIVERSAL PLATFORM IDENTIFICATION:
     *    - Automatically detects platform: Instagram, Facebook, TikTok, Twitter/X, WhatsApp, YouTube, Pinterest, etc.
     *    - Names downloads accurately by source platform: instagram_video_..., facebook_video_..., tiktok_video_..., etc.
     * 2. VIDEO IDENTIFICATION & STREAM INTERCEPTION:
     *    - Injects download buttons on EVERY video, reel, post, and story across Instagram, Facebook, TikTok, Twitter, WhatsApp, etc.
     *    - Positions buttons neatly (top-right for full-screen reels/TikToks, bottom-right for feed posts).
     *    - Listens to video playback events (play, playing, loadeddata) to detect media instantly.
     *    - Monkey-patches XMLHttpRequest and fetch to intercept dynamic video streams (Instagram CDN, TikTok CDN, Twitter CDN, fbcdn).
     * 3. PHOTO IDENTIFICATION:
     *    - Injects download buttons on feed photos and carousels.
     *    - Accurately groups multi-photo posts while keeping single photos isolated.
     *    - Strictly ignores tiny avatars, emojis, and tracking pixels (< 110px).
     * 4. TOUCH & DOWNLOAD DISPATCH:
     *    - Triggers playback on tap if video is paused to initialize network streams.
     *    - Dispatches to window.XXVIBridge.onMediaClicked for immediate download start.
     */
    fun getInjectionScript(): String {
        return """
        (function() {
            try {
                // 1. Helper: Determine platform prefix
                // 1. Helper: Determine platform prefix
                function getPlatformPrefix() {
                    var h = (window.location.hostname || '').toLowerCase();
                    if (h.indexOf('instagram.com') !== -1) return 'instagram';
                    if (h.indexOf('facebook.com') !== -1 || h.indexOf('fb.watch') !== -1) return 'facebook';
                    if (h.indexOf('tiktok.com') !== -1) return 'tiktok';
                    if (h.indexOf('twitter.com') !== -1 || h.indexOf('x.com') !== -1) return 'twitter';
                    if (h.indexOf('whatsapp.com') !== -1) return 'whatsapp';
                    if (h.indexOf('snapchat.com') !== -1) return 'snapchat';
                    if (h.indexOf('pinterest.com') !== -1) return 'pinterest';
                    if (h.indexOf('youtube.com') !== -1 || h.indexOf('youtu.be') !== -1) return 'youtube';
                    if (h.indexOf('reddit.com') !== -1) return 'reddit';
                    var parts = h.replace('www.', '').replace('m.', '').split('.');
                    return (parts[0] && parts[0].length >= 3) ? parts[0] : 'video';
                }

                function cleanMediaUrl(u) {
                    if (!u) return '';
                    return u.replace(/\\\//g, '/')
                            .replace(/\\u0026/g, '&')
                            .replace(/&amp;/g, '&')
                            .trim();
                }

                // Strip byte range / DASH chunk parameters from URLs to force CDN to deliver complete full file
                function cleanVideoUrl(u) {
                    if (!u) return '';
                    var clean = cleanMediaUrl(u);
                    clean = clean.replace(/([?&])bytestart=\d+(&|$)/g, '$1')
                                 .replace(/([?&])byteend=\d+(&|$)/g, '$1');
                    clean = clean.replace(/\?&/g, '?').replace(/[?&]$/, '');
                    return clean;
                }

                // 2. Monkey-patch XHR and fetch to intercept dynamic video streams & parse progressive MP4s
                if (!window.__XXVI_NETWORK_HOOKED__) {
                    window.__XXVI_NETWORK_HOOKED__ = true;
                    window.__XXVI_RECENT_VIDEOS__ = [];
                    window.__XXVI_INSTA_CACHE__ = {};

                    function parseMediaFromApiText(txt) {
                        try {
                            if (!txt || typeof txt !== 'string') return;
                            if (!window.__XXVI_INSTA_CACHE__) window.__XXVI_INSTA_CACHE__ = {};

                            // Fast scan for video_versions blocks (Instagram, Threads, etc.)
                            var vidRegex = /["']video_versions["']\s*:\s*\[([^\]]+)\]/g;
                            var match;
                            while ((match = vidRegex.exec(txt)) !== null) {
                                var versionsBlock = match[1];
                                var urlRegex = /["']url["']\s*:\s*["'](https?:[^"']+)["']/g;
                                var uMatch;
                                var bestUrl = '';
                                while ((uMatch = urlRegex.exec(versionsBlock)) !== null) {
                                    var candidate = cleanVideoUrl(uMatch[1]);
                                    if (candidate && candidate.startsWith('http') && candidate.indexOf('.mp4') !== -1) {
                                        if (!bestUrl) bestUrl = candidate;
                                    }
                                }
                                if (bestUrl) {
                                    var startPos = Math.max(0, match.index - 800);
                                    var endPos = Math.min(txt.length, match.index + match[0].length + 800);
                                    var snippet = txt.substring(startPos, endPos);

                                    var codeMatch = snippet.match(/["'](?:code|shortcode)["']\s*:\s*["']([a-zA-Z0-9_-]{5,20})["']/i);
                                    var idMatch = snippet.match(/["'](?:id|pk)["']\s*:\s*["']?(\d{8,25})["']?/i);
                                    var thumbMatch = snippet.match(/["'](?:display_url|thumbnail_src|thumbnail_url)["']\s*:\s*["'](https?:[^"']+)["']/i);
                                    var thumbUrl = thumbMatch ? cleanMediaUrl(thumbMatch[1]) : '';

                                    var code = codeMatch ? codeMatch[1] : '';
                                    var id = idMatch ? idMatch[1] : '';

                                    if (code) {
                                        window.__XXVI_INSTA_CACHE__[code] = { url: bestUrl, thumb: thumbUrl, code: code };
                                    }
                                    if (id) {
                                        window.__XXVI_INSTA_CACHE__[id] = { url: bestUrl, thumb: thumbUrl, code: code };
                                    }

                                    if (window.__XXVI_RECENT_VIDEOS__.indexOf(bestUrl) === -1) {
                                        window.__XXVI_RECENT_VIDEOS__.unshift(bestUrl);
                                        if (window.__XXVI_RECENT_VIDEOS__.length > 50) {
                                            window.__XXVI_RECENT_VIDEOS__.pop();
                                        }
                                    }
                                    if (window.XXVIBridge) {
                                        var pref = getPlatformPrefix();
                                        var title = pref + '_video_' + (code || id || (Date.now() % 1000000));
                                        window.XXVIBridge.onMediaDetected(bestUrl, 'video', title, thumbUrl);
                                    }
                                }
                            }

                            // Also scan for single video_url, playable_url_quality_hd, or playable_url matches
                            var hdMatch = txt.match(/["'](?:playable_url_quality_hd|browser_native_hd_url)["']\s*:\s*["'](https?:[^"']+)["']/i);
                            var sdMatch = txt.match(/["'](?:playable_url|browser_native_sd_url)["']\s*:\s*["'](https?:[^"']+)["']/i);
                            var hdUrl = hdMatch ? cleanVideoUrl(hdMatch[1]) : '';
                            var sdUrl = sdMatch ? cleanVideoUrl(sdMatch[1]) : '';

                            var singleVidRegex = /["'](?:video_url|playable_url|playable_url_quality_hd|browser_native_hd_url)["']\s*:\s*["'](https?:[^"']+)["']/g;
                            var sMatch;
                            while ((sMatch = singleVidRegex.exec(txt)) !== null) {
                                var vUrl = cleanVideoUrl(sMatch[1]);
                                if (vUrl && vUrl.startsWith('http') && vUrl.indexOf('.mp4') !== -1) {
                                    var sStart = Math.max(0, sMatch.index - 600);
                                    var sSnippet = txt.substring(sStart, sMatch.index + 600);
                                    var sCode = sSnippet.match(/["'](?:code|shortcode)["']\s*:\s*["']([a-zA-Z0-9_-]{5,20})["']/i);
                                    var cVal = sCode ? sCode[1] : '';
                                    if (cVal) {
                                        window.__XXVI_INSTA_CACHE__[cVal] = { url: vUrl, thumb: '', code: cVal };
                                    }
                                    if (window.__XXVI_RECENT_VIDEOS__.indexOf(vUrl) === -1) {
                                        window.__XXVI_RECENT_VIDEOS__.unshift(vUrl);
                                    }
                                    if (window.XXVIBridge) {
                                        var pref2 = getPlatformPrefix();
                                        var title2 = pref2 + '_video_' + (cVal || (Date.now() % 1000000));
                                        if (window.XXVIBridge.onMediaDetectedWithQuality) {
                                            window.XXVIBridge.onMediaDetectedWithQuality(hdUrl || vUrl, 'video', title2, '', hdUrl || vUrl, sdUrl || vUrl);
                                        } else {
                                            window.XXVIBridge.onMediaDetected(vUrl, 'video', title2, '');
                                        }
                                    }
                                }
                            }
                        } catch(e) {}
                    }

                    function recordDetectedMedia(rawUrl) {
                        try {
                            if (!rawUrl || typeof rawUrl !== 'string') return;
                            var uLow = rawUrl.toLowerCase();
                            var isVid = uLow.indexOf('.mp4') !== -1 ||
                                        uLow.indexOf('.webm') !== -1 ||
                                        uLow.indexOf('.m3u8') !== -1 ||
                                        uLow.indexOf('.m4v') !== -1 ||
                                        (uLow.indexOf('video.xx.fbcdn.net') !== -1) ||
                                        (uLow.indexOf('video') !== -1 && uLow.indexOf('.fbcdn.net') !== -1) ||
                                        (uLow.indexOf('fbcdn.net') !== -1 && (uLow.indexOf('/v/t42.') !== -1 || uLow.indexOf('/v/t43.') !== -1)) ||
                                        (uLow.indexOf('cdninstagram.com') !== -1 && (uLow.indexOf('/v/t42.') !== -1 || uLow.indexOf('/v/t43.') !== -1 || uLow.indexOf('.mp4') !== -1)) ||
                                        uLow.indexOf('video.twimg.com') !== -1 ||
                                        uLow.indexOf('v.pinimg.com') !== -1 ||
                                        (uLow.indexOf('tiktokcdn.com') !== -1 && uLow.indexOf('video') !== -1) ||
                                        uLow.indexOf('sc-cdn.net') !== -1;

                            if (isVid) {
                                var clean = cleanVideoUrl(rawUrl);
                                if (clean && clean.startsWith('http')) {
                                    var isChunk = rawUrl.indexOf('bytestart=') !== -1;
                                    if (!isChunk) {
                                        if (window.__XXVI_RECENT_VIDEOS__.indexOf(clean) === -1) {
                                            window.__XXVI_RECENT_VIDEOS__.unshift(clean);
                                        }
                                    } else {
                                        if (window.__XXVI_RECENT_VIDEOS__.indexOf(clean) === -1) {
                                            window.__XXVI_RECENT_VIDEOS__.push(clean);
                                        }
                                    }
                                    if (window.__XXVI_RECENT_VIDEOS__.length > 50) {
                                        window.__XXVI_RECENT_VIDEOS__.pop();
                                    }
                                    if (window.XXVIBridge) {
                                        var pref = getPlatformPrefix();
                                        window.XXVIBridge.onMediaDetected(clean, 'video', pref + '_video', '');
                                    }
                                }
                            }
                        } catch(e) {}
                    }

                    var origOpen = XMLHttpRequest.prototype.open;
                    var origSend = XMLHttpRequest.prototype.send;
                    XMLHttpRequest.prototype.open = function(method, url) {
                        this.__xxvi_url = (typeof url === 'string') ? url : '';
                        recordDetectedMedia(this.__xxvi_url);
                        return origOpen.apply(this, arguments);
                    };
                    XMLHttpRequest.prototype.send = function() {
                        this.addEventListener('load', function() {
                            try {
                                var u = (this.__xxvi_url || '').toLowerCase();
                                if (u.indexOf('graphql') !== -1 ||
                                    u.indexOf('/api/v1/') !== -1 ||
                                    u.indexOf('clips') !== -1 ||
                                    u.indexOf('feed') !== -1 ||
                                    u.indexOf('media') !== -1 ||
                                    u.indexOf('reels') !== -1 ||
                                    u.indexOf('instagram.com') !== -1) {
                                    var txt = this.responseText;
                                    if (txt && (txt.indexOf('video_versions') !== -1 || txt.indexOf('video_url') !== -1 || txt.indexOf('playable_url') !== -1)) {
                                        parseMediaFromApiText(txt);
                                    }
                                }
                            } catch(e) {}
                        });
                        return origSend.apply(this, arguments);
                    };

                    if (window.fetch) {
                        var origFetch = window.fetch;
                        window.fetch = function(input, init) {
                            var urlStr = (typeof input === 'string') ? input : (input && input.url ? input.url : '');
                            recordDetectedMedia(urlStr);
                            return origFetch.apply(this, arguments).then(function(response) {
                                try {
                                    if (response && response.clone) {
                                        var rUrl = (response.url || urlStr || '').toLowerCase();
                                        if (rUrl.indexOf('graphql') !== -1 ||
                                            rUrl.indexOf('/api/v1/') !== -1 ||
                                            rUrl.indexOf('clips') !== -1 ||
                                            rUrl.indexOf('feed') !== -1 ||
                                            rUrl.indexOf('media') !== -1 ||
                                            rUrl.indexOf('reels') !== -1 ||
                                            rUrl.indexOf('instagram.com') !== -1) {
                                            var clone = response.clone();
                                            clone.text().then(function(txt) {
                                                if (txt && (txt.indexOf('video_versions') !== -1 || txt.indexOf('video_url') !== -1 || txt.indexOf('playable_url') !== -1)) {
                                                    parseMediaFromApiText(txt);
                                                }
                                            }).catch(function() {});
                                        }
                                    }
                                } catch(e) {}
                                return response;
                            });
                        };
                    }
                }

                // 3. Inject CSS for in-video, in-story, and in-photo buttons
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
                        .xxvi-btn-feed-video {
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

                function getTopHeaderOffset(card) {
                    var openApp = document.querySelector('[aria-label*="Open app" i], [aria-label*="Open App" i], a[href*="open-app"], [data-sigil*="open_app"]');
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
                                 window.location.href.indexOf('tiktok.com') !== -1 ||
                                 (cRect && cRect.top < 30 && cRect.height >= 380);

                    if (isReel) {
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
                        if (curr.hasAttribute && curr.hasAttribute('data-xxvi-card-handled')) return true;
                        if (curr.querySelector && curr.querySelector(':scope > .xxvi-btn-injected')) return true;
                        curr = curr.parentElement;
                    }
                    return false;
                }

                // Helper: Determine if an image or URL represents a user profile picture, avatar, or non-media icon
                function isProfileOrAvatar(img, url) {
                    if (!img && !url) return true;
                    var u = url || (img ? (img.currentSrc || img.src || '') : '');
                    var uLow = (u || '').toLowerCase();

                    // 1. Meta / Instagram CDN taxonomy & size tokens
                    // t51.2885-19 is Instagram's dedicated taxonomy for profile pictures (feed photos use 2885-15)
                    if (uLow.indexOf('2885-19') !== -1 ||
                        uLow.indexOf('/s150x150/') !== -1 ||
                        uLow.indexOf('/s320x320/') !== -1 ||
                        uLow.indexOf('/s50x50/') !== -1 ||
                        uLow.indexOf('/s64x64/') !== -1 ||
                        uLow.indexOf('/s100x100/') !== -1 ||
                        uLow.indexOf('profile_pic') !== -1 ||
                        uLow.indexOf('profilepic') !== -1 ||
                        uLow.indexOf('profile_photo') !== -1 ||
                        uLow.indexOf('avatar') !== -1) {
                        return true;
                    }

                    // 2. Facebook avatar patterns
                    if (uLow.indexOf('p50x50') !== -1 ||
                        uLow.indexOf('p100x100') !== -1 ||
                        uLow.indexOf('p160x160') !== -1 ||
                        uLow.indexOf('c0.0.50.50') !== -1) {
                        return true;
                    }

                    // 3. Generic web placeholders / trackers / emojis
                    if (uLow.indexOf('rsrc.php') !== -1 ||
                        uLow.indexOf('emoji.php') !== -1 ||
                        uLow.indexOf('blank.gif') !== -1 ||
                        uLow.indexOf('/static_resources/') !== -1 ||
                        uLow.indexOf('spacer.gif') !== -1 ||
                        uLow.indexOf('data:image/svg') !== -1) {
                        return true;
                    }

                    if (!img) return false;

                    // 4. Alt / Title / Aria-label text
                    var alt = (img.getAttribute('alt') || '').toLowerCase();
                    var aria = (img.getAttribute('aria-label') || '').toLowerCase();
                    var tit = (img.getAttribute('title') || '').toLowerCase();
                    if (alt.indexOf('profile picture') !== -1 ||
                        alt.indexOf('profile photo') !== -1 ||
                        alt.indexOf('profile') !== -1 ||
                        alt.indexOf('avatar') !== -1 ||
                        alt.indexOf('perfil') !== -1 ||
                        aria.indexOf('profile') !== -1 ||
                        aria.indexOf('avatar') !== -1 ||
                        tit.indexOf('profile') !== -1 ||
                        tit.indexOf('avatar') !== -1) {
                        return true;
                    }

                    // 5. Parent / Ancestor containers
                    if (img.closest) {
                        // Top post header, navbar, banner
                        var insideHeader = img.closest('header, nav, [role="banner"]');
                        if (insideHeader) return true;

                        // Avatar test-ids and classes
                        var insideAvatar = img.closest(
                            '[data-testid*="avatar" i], [data-testid*="User-Name" i], [data-testid*="Tweet-User-Avatar" i], ' +
                            '[class*="avatar" i], [class*="Avatar" i], [class*="profile-pic" i], [class*="ProfilePic" i], ' +
                            '[class*="_aa8j"], [class*="_ab63"], [class*="_aadp"]'
                        );
                        if (insideAvatar) return true;

                        // Comments, replies, form
                        var insideComment = img.closest('footer, form, [class*="comment" i], [class*="Comment" i], [aria-label*="comment" i]');
                        if (insideComment) return true;

                        // Story rings in header
                        var insideStoryLink = img.closest('a[href*="/stories/"], [aria-label*="stories" i]');
                        if (insideStoryLink) {
                            var rectA = insideStoryLink.getBoundingClientRect ? insideStoryLink.getBoundingClientRect() : null;
                            if (rectA && (rectA.width < 120 || rectA.height < 120)) return true;
                        }

                        // User profile link: <a href="/username/">
                        var userLink = img.closest('a[role="link"], a');
                        if (userLink && userLink.getAttribute) {
                            var href = userLink.getAttribute('href') || '';
                            if (href.startsWith('/') && !href.startsWith('/p/') && !href.startsWith('/reel/') && !href.startsWith('/tv/') && href.length > 2) {
                                var aRect = userLink.getBoundingClientRect ? userLink.getBoundingClientRect() : null;
                                if (aRect && (aRect.width < 150 || aRect.height < 150)) {
                                    return true;
                                }
                            }
                        }
                    }

                    // 6. Border-radius 50% (circular images are avatars or icons, never feed photos)
                    try {
                        var comp = window.getComputedStyle ? window.getComputedStyle(img) : null;
                        if (comp) {
                            var br = comp.borderRadius || '';
                            if (br === '50%' || br.indexOf('50%') !== -1 || (comp.clipPath && comp.clipPath.indexOf('circle') !== -1)) {
                                return true;
                            }
                        }
                    } catch(e) {}

                    // 7. On-screen displayed dimensions:
                    // Post photos in feeds on mobile are nearly full width (>= 200px).
                    // Avatars are small (24px to 64px, rarely over 120px).
                    var rect = img.getBoundingClientRect ? img.getBoundingClientRect() : null;
                    var dw = (rect && rect.width) ? rect.width : (img.clientWidth || img.offsetWidth || 0);
                    var dh = (rect && rect.height) ? rect.height : (img.clientHeight || img.offsetHeight || 0);
                    if (dw > 0 && dh > 0 && (dw < 180 || dh < 180)) {
                        return true;
                    }

                    // 8. Natural dimensions
                    var nw = img.naturalWidth || 0;
                    var nh = img.naturalHeight || 0;
                    if (nw > 0 && nh > 0 && (nw < 200 || nh < 200)) {
                        return true;
                    }

                    return false;
                }

                function isSameMedia(itemEl, itemUrl, targetEl, targetUrl) {
                    if (targetEl && itemEl) {
                        if (itemEl === targetEl) return true;
                        if (targetEl.contains && targetEl.contains(itemEl)) return true;
                        if (itemEl.contains && itemEl.contains(targetEl)) return true;
                        if (itemEl.parentElement && targetEl.parentElement && itemEl.parentElement === targetEl.parentElement) return true;
                    }
                    if (itemUrl && targetUrl) {
                        var cu = cleanMediaUrl(itemUrl).split('?')[0];
                        var ct = cleanMediaUrl(targetUrl).split('?')[0];
                        if (cu.length > 15 && ct.length > 15 && cu === ct) return true;
                        if (itemUrl === targetUrl) return true;
                    }
                    return false;
                }

                function getBestImageUrl(img) {
                    if (!img) return '';
                    var host = (window.location.hostname || '').toLowerCase();

                    // Pinterest: Convert thumbnail to maximum HD originals!
                    var pSrc = img.src || img.currentSrc || '';
                    if (host.indexOf('pinterest.com') !== -1 && pSrc.indexOf('pinimg.com') !== -1) {
                        var hdPin = pSrc.replace(/\/(236x|474x|564x|736x)\//, '/originals/');
                        if (hdPin && hdPin.startsWith('http')) return cleanMediaUrl(hdPin);
                    }

                    // Twitter / X: Convert image to original quality
                    if ((host.indexOf('twitter.com') !== -1 || host.indexOf('x.com') !== -1) && pSrc.indexOf('twimg.com') !== -1) {
                        var hdTw = pSrc.replace(/name=[a-z0-9_]+/i, 'name=orig');
                        if (hdTw.indexOf('name=orig') === -1) {
                            hdTw += (hdTw.indexOf('?') === -1 ? '?' : '&') + 'name=orig';
                        }
                        if (hdTw.startsWith('http')) return cleanMediaUrl(hdTw);
                    }

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

                    var ds = img.getAttribute('data-full-res-src') || img.getAttribute('data-src') || img.getAttribute('data-original');
                    if (ds && ds.startsWith('http')) return cleanMediaUrl(ds);

                    if (img.currentSrc && img.currentSrc.startsWith('http')) return cleanMediaUrl(img.currentSrc);
                    if (img.src && img.src.startsWith('http')) return cleanMediaUrl(img.src);

                    return '';
                }

                // Helper: Determine if an element (img, div, container) represents a video or is inside a video card
                function isVideoElementOrPoster(el) {
                    if (!el) return false;
                    if (el.tagName === 'VIDEO') return true;
                    if (el.querySelector && el.querySelector('video')) return true;

                    // Direct attributes on element
                    if (el.getAttribute) {
                        var directAria = (el.getAttribute('aria-label') || '').toLowerCase();
                        if (directAria.indexOf('play video') !== -1 || directAria.indexOf('watch video') !== -1 || directAria === 'video') return true;
                        if (el.hasAttribute('data-video-id') || el.hasAttribute('data-video-url')) return true;
                    }

                    // Check ancestors up to 6 levels (stop before feed or root)
                    var curr = el;
                    var depth = 0;
                    while (curr && curr !== document.body && curr !== document.documentElement && depth <= 6) {
                        if (curr.id === 'root' || curr.id === 'viewport') break;
                        var role = curr.getAttribute ? (curr.getAttribute('role') || '') : '';
                        if (role === 'feed' || role === 'main') break;

                        if (curr.getAttribute) {
                            if (curr.hasAttribute('data-video-id') || curr.hasAttribute('data-video-url')) return true;
                            var ds = (curr.getAttribute('data-sigil') || '').toLowerCase();
                            if (ds.indexOf('inlinevideo') !== -1 || ds.indexOf('video') !== -1 || ds.indexOf('play') !== -1) return true;
                            var dStore = (curr.getAttribute('data-store') || '').toLowerCase();
                            if (dStore.indexOf('video') !== -1 || dStore.indexOf('playable_url') !== -1 || dStore.indexOf('sd_src') !== -1 || dStore.indexOf('hd_src') !== -1) return true;
                            var aria = (curr.getAttribute('aria-label') || '').toLowerCase();
                            if (aria.indexOf('play video') !== -1 || aria.indexOf('watch video') !== -1 || aria.indexOf('reels') !== -1 || aria === 'video') return true;
                            if (role === 'progressbar') return true;
                        }

                        // Links: /watch/, /reel/, /videos/, fb.watch, video.php
                        if (curr.tagName === 'A' && curr.getAttribute) {
                            var href = (curr.getAttribute('href') || '').toLowerCase();
                            if (href.indexOf('/reel/') !== -1 || href.indexOf('/watch') !== -1 || href.indexOf('/videos/') !== -1 || href.indexOf('video.php') !== -1 || href.indexOf('fb.watch') !== -1) {
                                return true;
                            }
                        }

                        // Check children inside container: play button overlays, duration, view counts
                        if (curr.querySelector) {
                            if (curr.querySelector('video')) return true;
                            if (curr.querySelector('[role="button"][aria-label*="Play" i], [aria-label*="Play video" i], [aria-label*="Watch video" i], [data-sigil*="play"], [data-sigil*="video"], [data-sigil*="inlineVideo"]')) return true;
                            if (curr.querySelector('[data-video-id], [data-video-url]')) return true;
                            if (curr.querySelector('a[href*="/watch"], a[href*="/reel/"], a[href*="/videos/"], a[href*="fb.watch"]')) return true;

                            // Search for play icons (play button SVG or i)
                            var playSvgs = curr.querySelectorAll('svg, i, div[role="button"]');
                            for (var pi = 0; pi < playSvgs.length; pi++) {
                                var ps = playSvgs[pi];
                                var psAria = (ps.getAttribute('aria-label') || '').toLowerCase();
                                var psTitle = (ps.getAttribute('title') || '').toLowerCase();
                                if (psAria.indexOf('play') !== -1 || psTitle.indexOf('play') !== -1) return true;

                                var path = ps.querySelector ? ps.querySelector('path, polygon') : null;
                                if (path) {
                                    var dPath = path.getAttribute('d') || '';
                                    var pts = path.getAttribute('points') || '';
                                    if (pts || (dPath.indexOf('M') !== -1 && (dPath.indexOf('Z') !== -1 || dPath.indexOf('z') !== -1) && dPath.length < 160)) {
                                        var pr = ps.getBoundingClientRect ? ps.getBoundingClientRect() : null;
                                        if (pr && pr.width >= 20 && pr.width <= 140 && pr.height >= 20 && pr.height <= 140) {
                                            return true;
                                        }
                                    }
                                }
                            }
                        }

                        curr = curr.parentElement;
                        depth++;
                    }

                    return false;
                }

                // Scan document scripts and attributes for Facebook video stream URLs
                function extractFacebookVideoUrls(targetEl) {
                    var urls = [];
                    var curr = targetEl;
                    var depth = 0;
                    while (curr && curr !== document.body && depth < 6) {
                        if (curr.getAttribute) {
                            var ds = curr.getAttribute('data-store');
                            if (ds) {
                                try {
                                    var data = JSON.parse(ds);
                                    var u = data.src || data.videoURL || data.sd_src || data.hd_src || data.playable_url || data.playable_url_quality_hd;
                                    if (u && u.startsWith('http')) urls.push(cleanVideoUrl(u));
                                } catch(e) {}
                            }
                            var dvu = curr.getAttribute('data-video-url');
                            if (dvu && dvu.startsWith('http')) urls.push(cleanVideoUrl(dvu));
                        }
                        curr = curr.parentElement;
                        depth++;
                    }

                    var scripts = document.querySelectorAll('script');
                    for (var i = 0; i < scripts.length; i++) {
                        var txt = scripts[i].innerHTML || scripts[i].textContent || '';
                        if (txt.indexOf('playable_url') === -1 &&
                            txt.indexOf('browser_native') === -1 &&
                            txt.indexOf('sd_src') === -1 &&
                            txt.indexOf('hd_src') === -1 &&
                            txt.indexOf('fbcdn.net') === -1) {
                            continue;
                        }

                        // HD stream matches
                        var mHd = txt.match(/["'](?:playable_url_quality_hd|browser_native_hd_url|hd_src|hd_src_no_ratelimit)["']\s*:\s*["'](https?:[^"']+)["']/i);
                        if (mHd && mHd[1]) {
                            var cHd = cleanVideoUrl(mHd[1]);
                            if (cHd && urls.indexOf(cHd) === -1) urls.push(cHd);
                        }

                        // SD stream matches
                        var mSd = txt.match(/["'](?:playable_url|browser_native_sd_url|sd_src|sd_src_no_ratelimit|video_url)["']\s*:\s*["'](https?:[^"']+)["']/i);
                        if (mSd && mSd[1]) {
                            var cSd = cleanVideoUrl(mSd[1]);
                            if (cSd && urls.indexOf(cSd) === -1) urls.push(cSd);
                        }

                        // Any fbcdn video URL in script
                        var mFb = txt.match(/["'](https?:\\\/\\\/video[^"']+\.fbcdn\.net\\\/[^"']+)["']/i) ||
                                  txt.match(/["'](https?:\/\/video[^"']+\.fbcdn\.net\/[^"']+)["']/i);
                        if (mFb && mFb[1]) {
                            var cFb = cleanVideoUrl(mFb[1]);
                            if (cFb && urls.indexOf(cFb) === -1) urls.push(cFb);
                        }
                    }
                    return urls;
                }

                function getBestVideoUrl(videoEl, card) {
                    var host = (window.location.hostname || '').toLowerCase();

                    // 1. Direct <video> src or <source> src if not blob
                    if (videoEl) {
                        if (videoEl.currentSrc && videoEl.currentSrc.startsWith('http') && !videoEl.currentSrc.startsWith('blob:')) {
                            return cleanVideoUrl(videoEl.currentSrc);
                        }
                        if (videoEl.src && videoEl.src.startsWith('http') && !videoEl.src.startsWith('blob:')) {
                            return cleanVideoUrl(videoEl.src);
                        }
                        var s = videoEl.querySelector ? videoEl.querySelector('source') : null;
                        if (s && s.src && s.src.startsWith('http') && !s.src.startsWith('blob:')) {
                            return cleanVideoUrl(s.src);
                        }
                    }

                    // 2. Platform-Specific Extractors
                    // A. INSTAGRAM SPECIFIC
                    if (host.indexOf('instagram.com') !== -1) {
                        if (!window.__XXVI_INSTA_CACHE__) window.__XXVI_INSTA_CACHE__ = {};

                        // 1. Try finding shortcode from current URL, card, or video element
                        var shortcode = '';
                        var pageMatch = window.location.pathname.match(/\/(?:reel|reels|p)\/([a-zA-Z0-9_-]+)/i);
                        if (pageMatch && pageMatch[1]) {
                            shortcode = pageMatch[1];
                        }

                        if (!shortcode && card) {
                            var cardLink = card.querySelector('a[href*="/reel/"], a[href*="/p/"]');
                            if (cardLink) {
                                var mLink = (cardLink.getAttribute('href') || '').match(/\/(?:reel|reels|p)\/([a-zA-Z0-9_-]+)/i);
                                if (mLink && mLink[1]) shortcode = mLink[1];
                            }
                        }

                        if (!shortcode && videoEl && videoEl.closest) {
                            var art = videoEl.closest('article, [role="presentation"]');
                            if (art) {
                                var artLink = art.querySelector('a[href*="/reel/"], a[href*="/p/"]');
                                if (artLink) {
                                    var mArt = (artLink.getAttribute('href') || '').match(/\/(?:reel|reels|p)\/([a-zA-Z0-9_-]+)/i);
                                    if (mArt && mArt[1]) shortcode = mArt[1];
                                }
                            }
                        }

                        // 2. Check cache for this shortcode
                        if (shortcode && window.__XXVI_INSTA_CACHE__[shortcode]) {
                            var cached = window.__XXVI_INSTA_CACHE__[shortcode];
                            if (cached && cached.url) return cleanVideoUrl(cached.url);
                        }

                        // 3. Check <meta property="og:video"> and <meta property="og:video:secure_url">
                        var metaVid = document.querySelector('meta[property="og:video"], meta[property="og:video:secure_url"]');
                        if (metaVid) {
                            var mContent = metaVid.getAttribute('content');
                            if (mContent && mContent.startsWith('http') && mContent.indexOf('.mp4') !== -1) {
                                return cleanVideoUrl(mContent);
                            }
                        }

                        // 4. Check <script type="application/ld+json">
                        var ldScripts = document.querySelectorAll('script[type="application/ld+json"]');
                        for (var ldi = 0; ldi < ldScripts.length; ldi++) {
                            var ldTxt = ldScripts[ldi].innerHTML || ldScripts[ldi].textContent || '';
                            var mContentUrl = ldTxt.match(/["']contentUrl["']\s*:\s*["'](https?:[^"']+)["']/i);
                            if (mContentUrl && mContentUrl[1]) {
                                return cleanVideoUrl(mContentUrl[1]);
                            }
                        }

                        // 5. Scan document-level scripts for Instagram video URLs
                        var scripts = document.querySelectorAll('script[type="application/json"], script:not([src])');
                        for (var i = 0; i < scripts.length; i++) {
                            var txt = scripts[i].innerHTML || scripts[i].textContent || '';
                            if (txt.indexOf('video_versions') !== -1 || txt.indexOf('video_url') !== -1 || txt.indexOf('playable_url') !== -1) {
                                if (shortcode && txt.indexOf(shortcode) !== -1) {
                                    var sIdx = txt.indexOf(shortcode);
                                    var subSnippet = txt.substring(Math.max(0, sIdx - 400), Math.min(txt.length, sIdx + 1200));
                                    var mSub = subSnippet.match(/["']video_versions["']\s*:\s*\[\s*\{[^}]*?["']url["']\s*:\s*["'](https?:[^"']+)["']/i) ||
                                               subSnippet.match(/["']video_url["']\s*:\s*["'](https?:[^"']+)["']/i);
                                    if (mSub && mSub[1]) {
                                        var cSub = cleanVideoUrl(mSub[1]);
                                        if (cSub) return cSub;
                                    }
                                }
                                var m = txt.match(/["']video_versions["']\s*:\s*\[\s*\{[^}]*?["']url["']\s*:\s*["'](https?:[^"']+)["']/i) ||
                                        txt.match(/["']video_url["']\s*:\s*["'](https?:[^"']+)["']/i) ||
                                        txt.match(/["']playable_url["']\s*:\s*["'](https?:[^"']+)["']/i) ||
                                        txt.match(/["']playable_url_quality_hd["']\s*:\s*["'](https?:[^"']+)["']/i);
                                if (m && m[1]) {
                                    var cleaned = cleanVideoUrl(m[1]);
                                    if (cleaned) return cleaned;
                                }
                            }
                        }

                        // 6. Direct Instagram info API query using official Web App ID
                        if (shortcode) {
                            try {
                                var xhr = new XMLHttpRequest();
                                xhr.open('GET', '/p/' + shortcode + '/?__a=1&__d=dis', false);
                                xhr.setRequestHeader('X-IG-App-ID', '936619743392459');
                                xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
                                xhr.send();
                                if (xhr.status === 200 && xhr.responseText) {
                                    var respTxt = xhr.responseText;
                                    var mU = respTxt.match(/["']video_versions["']\s*:\s*\[\s*\{[^}]*?["']url["']\s*:\s*["'](https?:[^"']+)["']/i) ||
                                            respTxt.match(/["']video_url["']\s*:\s*["'](https?:[^"']+)["']/i);
                                    if (mU && mU[1]) {
                                        var directUrl = cleanVideoUrl(mU[1]);
                                        if (directUrl) {
                                            window.__XXVI_INSTA_CACHE__[shortcode] = { url: directUrl, thumb: '', code: shortcode };
                                            return directUrl;
                                        }
                                    }
                                }
                            } catch(e) {}
                        }

                        // 7. Check recent intercepted Instagram video URLs (filter for non-chunked mp4 first)
                        if (window.__XXVI_RECENT_VIDEOS__ && window.__XXVI_RECENT_VIDEOS__.length > 0) {
                            for (var rv = 0; rv < window.__XXVI_RECENT_VIDEOS__.length; rv++) {
                                var rUrl = window.__XXVI_RECENT_VIDEOS__[rv];
                                if ((rUrl.indexOf('cdninstagram.com') !== -1 || rUrl.indexOf('fbcdn.net') !== -1) &&
                                    rUrl.indexOf('.mp4') !== -1 &&
                                    rUrl.indexOf('bytestart=') === -1) {
                                    return rUrl;
                                }
                            }
                            for (var rv2 = 0; rv2 < window.__XXVI_RECENT_VIDEOS__.length; rv2++) {
                                var rUrl2 = window.__XXVI_RECENT_VIDEOS__[rv2];
                                if (rUrl2.indexOf('cdninstagram.com') !== -1 || rUrl2.indexOf('fbcdn.net') !== -1) {
                                    return cleanVideoUrl(rUrl2);
                                }
                            }
                        }
                    }

                    // B. PINTEREST SPECIFIC
                    if (host.indexOf('pinterest.com') !== -1) {
                        var pws = document.getElementById('__PWS_DATA__');
                        if (pws) {
                            var ptxt = pws.innerHTML || pws.textContent || '';
                            var pm = ptxt.match(/["']url["']\s*:\s*["'](https?:[^"']*?v\.pinimg\.com\/videos\/[^"']+\.mp4[^"']*)["']/i) ||
                                     ptxt.match(/["'](?:V_720P|V_EXP7|V_480P)["']\s*:\s*\{[^}]*?["']url["']\s*:\s*["'](https?:[^"']+)["']/i) ||
                                     ptxt.match(/(https?:\\\/\\\/v\.pinimg\.com\\\/videos\\\/[^"']+\.mp4)/i) ||
                                     ptxt.match(/(https?:\/\/v\.pinimg\.com\/videos\/[^"']+\.mp4)/i);
                            if (pm && pm[1]) {
                                return cleanVideoUrl(pm[1]);
                            }
                        }
                        var allScripts = document.querySelectorAll('script');
                        for (var si = 0; si < allScripts.length; si++) {
                            var stxt = allScripts[si].innerHTML || allScripts[si].textContent || '';
                            var sm = stxt.match(/["'](?:V_720P|V_EXP7|V_480P)["']\s*:\s*\{[^}]*?["']url["']\s*:\s*["'](https?:[^"']+)["']/i) ||
                                     stxt.match(/["']url["']\s*:\s*["'](https?:[^"']*?v\.pinimg\.com\/videos\/[^"']+\.mp4[^"']*)["']/i) ||
                                     stxt.match(/(https?:\/\/v\.pinimg\.com\/videos\/[^"']+\.mp4)/i) ||
                                     stxt.match(/(https?:\\\/\\\/v\.pinimg\.com\\\/videos\\\/[^"']+\.mp4)/i);
                            if (sm && sm[1]) {
                                return cleanVideoUrl(sm[1]);
                            }
                        }
                        if (window.__XXVI_RECENT_VIDEOS__) {
                            for (var pv = 0; pv < window.__XXVI_RECENT_VIDEOS__.length; pv++) {
                                if (window.__XXVI_RECENT_VIDEOS__[pv].indexOf('v.pinimg.com') !== -1) {
                                    return window.__XXVI_RECENT_VIDEOS__[pv];
                                }
                            }
                        }
                    }

                    // C. TWITTER / X SPECIFIC
                    if (host.indexOf('twitter.com') !== -1 || host.indexOf('x.com') !== -1) {
                        if (window.__XXVI_RECENT_VIDEOS__) {
                            for (var tv = 0; tv < window.__XXVI_RECENT_VIDEOS__.length; tv++) {
                                if (window.__XXVI_RECENT_VIDEOS__[tv].indexOf('video.twimg.com') !== -1) {
                                    return window.__XXVI_RECENT_VIDEOS__[tv];
                                }
                            }
                        }
                    }

                    // D. SNAPCHAT SPECIFIC
                    if (host.indexOf('snapchat.com') !== -1) {
                        if (window.__XXVI_RECENT_VIDEOS__) {
                            for (var sv = 0; sv < window.__XXVI_RECENT_VIDEOS__.length; sv++) {
                                if (window.__XXVI_RECENT_VIDEOS__[sv].indexOf('sc-cdn.net') !== -1) {
                                    return window.__XXVI_RECENT_VIDEOS__[sv];
                                }
                            }
                        }
                    }

                    // E. FACEBOOK SPECIFIC (scripts + data-store + data-video-url)
                    if (host.indexOf('facebook.com') !== -1 || host.indexOf('fb.watch') !== -1) {
                        var fbUrls = extractFacebookVideoUrls(videoEl || card);
                        if (fbUrls && fbUrls.length > 0) {
                            return fbUrls[0];
                        }
                    }

                    // F. Search inside card and parent container scripts
                    if (card && card.querySelectorAll) {
                        var cardScripts = card.querySelectorAll('script, template');
                        for (var sc = 0; sc < cardScripts.length; sc++) {
                            var txt2 = cardScripts[sc].innerHTML || cardScripts[sc].textContent || '';
                            var m2 = txt2.match(/["'](https?:\\\/\\\/[^"']+\.mp4[^"']*)["']/i) ||
                                     txt2.match(/["'](https?:\/\/[^"']+\.mp4[^"']*)["']/i) ||
                                     txt2.match(/["'](https?:\\\/\\\/[^"']+fbcdn\.net\\\/[^"']+)["']/i) ||
                                     txt2.match(/["'](https?:\/\/[^"']+fbcdn\.net\/[^"']+)["']/i);
                            if (m2 && m2[1]) {
                                return cleanVideoUrl(m2[1]);
                            }
                        }
                    }

                    // G. Global Recent Fallback
                    if (window.__XXVI_RECENT_VIDEOS__ && window.__XXVI_RECENT_VIDEOS__.length > 0) {
                        return window.__XXVI_RECENT_VIDEOS__[0];
                    }

                    return '';
                }

                function getExactMediaUrl(mediaEl) {
                    if (!mediaEl) return '';

                    if (mediaEl.tagName === 'VIDEO' || (mediaEl.querySelector && mediaEl.querySelector('video'))) {
                        var vUrl = getBestVideoUrl(mediaEl, mediaEl.parentElement);
                        if (vUrl) return vUrl;
                    }

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
                    if (!videoEl || !videoEl.parentElement) return null;
                    var p = videoEl.parentElement;
                    var host = (window.location.hostname || '').toLowerCase();

                    // Instagram: find article or reel container or direct parent
                    if (host.indexOf('instagram.com') !== -1) {
                        var art = videoEl.closest('article, [role="presentation"], div[role="dialog"], section');
                        if (art && art !== document.body && art !== document.documentElement) return art;
                        return p;
                    }

                    // Pinterest: find pin container
                    if (host.indexOf('pinterest.com') !== -1) {
                        var pinCard = videoEl.closest('[data-test-id="pin"], [data-test-id="pinWrapper"], [class*="Pin"], [data-test-id*="video"]');
                        if (pinCard && pinCard !== document.body && pinCard !== document.documentElement) return pinCard;
                        return p;
                    }

                    // TikTok: find video wrapper
                    if (host.indexOf('tiktok.com') !== -1) {
                        var ttCard = videoEl.closest('[data-e2e="feed-video"], [class*="DivVideoWrapper"], [class*="video-player"], div[class*="xgplayer"]');
                        if (ttCard && ttCard !== document.body && ttCard !== document.documentElement) return ttCard;
                        return p;
                    }

                    // Twitter / X:
                    if (host.indexOf('twitter.com') !== -1 || host.indexOf('x.com') !== -1) {
                        var twCard = videoEl.closest('[data-testid="videoComponent"], [data-testid="videoPlayer"], article[data-testid="tweet"]');
                        if (twCard && twCard !== document.body && twCard !== document.documentElement) return twCard;
                        return p;
                    }

                    // Snapchat:
                    if (host.indexOf('snapchat.com') !== -1) {
                        var scCard = videoEl.closest('[class*="Spotlight"], [class*="Snap"], [class*="Story"], div[data-testid="spotlight-player"]');
                        if (scCard && scCard !== document.body && scCard !== document.documentElement) return scCard;
                        return p;
                    }

                    // WhatsApp Web:
                    if (host.indexOf('whatsapp.com') !== -1) {
                        var waCard = videoEl.closest('[data-testid="status-v3"], [data-testid="status-viewer"], [data-testid="msg-container"], div[role="region"]');
                        if (waCard && waCard !== document.body && waCard !== document.documentElement) return waCard;
                        return p;
                    }

                    // Facebook & generic:
                    var curr = p;
                    var best = p;
                    var depth = 0;
                    while (curr && curr !== document.body && curr !== document.documentElement && depth <= 8) {
                        if (curr.id === 'root' || curr.id === 'viewport' || curr.id === 'm-story-view') break;
                        var r = curr.getBoundingClientRect();
                        if (r.height >= (window.innerHeight - 15) && r.width >= (window.innerWidth - 15) && curr !== p) {
                            break;
                        }
                        if (curr.querySelector && curr.querySelector('header, [role="banner"], [aria-label="Back"]')) {
                            break;
                        }
                        if (r.width >= 90 && r.height >= 80) {
                            best = curr;
                            var sigil = curr.getAttribute ? (curr.getAttribute('data-sigil') || '') : '';
                            var role = curr.getAttribute ? (curr.getAttribute('role') || '') : '';
                            if (sigil.indexOf('inlineVideo') !== -1 || sigil.indexOf('video') !== -1 || role === 'article') {
                                break;
                            }
                        }
                        curr = curr.parentElement;
                        depth++;
                    }
                    return best;
                }

                function findSinglePostContainer(el) {
                    if (!el) return null;
                    var host = (window.location.hostname || '').toLowerCase();

                    // 1. Instagram: closest article or presentation dialog (never beyond article!)
                    if (host.indexOf('instagram.com') !== -1) {
                        var art = el.closest('article');
                        if (art) return art;
                        var dlg = el.closest('[role="dialog"], [role="presentation"]');
                        if (dlg) return dlg;
                    }

                    // 2. Twitter / X: strictly closest tweet article
                    if (host.indexOf('twitter.com') !== -1 || host.indexOf('x.com') !== -1) {
                        var tw = el.closest('article[data-testid="tweet"], article');
                        if (tw) return tw;
                    }

                    // 3. TikTok: feed video container
                    if (host.indexOf('tiktok.com') !== -1) {
                        var tt = el.closest('[data-e2e="feed-video"], [data-e2e="recommend-list-item-container"], [class*="DivVideoWrapper"]');
                        if (tt) return tt;
                    }

                    // 4. Pinterest: pin wrapper
                    if (host.indexOf('pinterest.com') !== -1) {
                        var pin = el.closest('[data-test-id="pin"], [data-test-id="pinWrapper"], [class*="Pin"]');
                        if (pin) return pin;
                    }

                    // 5. Facebook:
                    if (host.indexOf('facebook.com') !== -1 || host.indexOf('fb.watch') !== -1) {
                        var story = el.closest('article, [role="article"], div[data-sigil*="story_div"], div[data-sigil*="feed_story"], div.story_body_container');
                        if (story) {
                            var nested = story.querySelectorAll('[role="article"], article, div[data-sigil*="story_div"]');
                            if (nested.length <= 1) return story;
                        }
                    }

                    // Strict Bounding: climb max 2 levels (only immediate photo grid/group)
                    // NEVER climb 4 or 5 levels which enters the entire feed!
                    var curr = el.parentElement;
                    var best = curr;
                    var depth = 0;

                    while (curr && curr !== document.body && curr !== document.documentElement && depth < 2) {
                        if (curr.id === 'root' || curr.id === 'viewport' || curr.id === 'm-story-view') break;
                        var role = curr.getAttribute ? (curr.getAttribute('role') || '') : '';
                        if (role === 'feed' || role === 'main') break;

                        if (curr.querySelectorAll) {
                            var appLinks = curr.querySelectorAll('[aria-label*="Open app" i], a[href*="open-app"]').length;
                            if (appLinks > 1) break;

                            var likeButtons = curr.querySelectorAll('[aria-label*="Like" i], [data-sigil*="like"]').length;
                            if (likeButtons > 1) break;

                            var commentSections = curr.querySelectorAll('[aria-label*="comment" i], a[href*="comments"]').length;
                            if (commentSections > 1) break;

                            var permalinks = curr.querySelectorAll('a[href*="/story.php"], a[href*="/permalink.php"], a[href*="/posts/"], a[href*="/p/"]').length;
                            if (permalinks > 1) break;

                            var articles = curr.querySelectorAll('article, [role="article"]').length;
                            if (articles > 1) break;
                        }

                        best = curr;
                        curr = curr.parentElement;
                        depth++;
                    }

                    return best || el.parentElement;
                }

                function getSiblingMediaInSamePost(targetMediaEl, type, exactUrl) {
                    if (type === 'story' || type === 'video') return [];
                    if (!targetMediaEl) return [];
                    var platform = getPlatformPrefix();
                    var el = targetMediaEl;
                    if (el.tagName !== 'IMG' && el.tagName !== 'VIDEO') {
                        var inner = el.querySelector ? el.querySelector('img, video') : null;
                        if (inner) el = inner;
                    }

                    var postContainer = findSinglePostContainer(el);
                    if (!postContainer) return [];

                    // Guard: verify postContainer does NOT encompass multiple posts
                    if (postContainer.querySelectorAll) {
                        var articles = postContainer.querySelectorAll('article, [role="article"]');
                        if (articles.length > 1) {
                            var closer = el.closest('article, [role="article"]');
                            if (closer) postContainer = closer;
                            else postContainer = el.parentElement ? (el.parentElement.parentElement || el.parentElement) : el;
                        }
                        var appLinks = postContainer.querySelectorAll('[aria-label*="Open app" i], a[href*="open-app"]').length;
                        if (appLinks > 1) {
                            postContainer = el.parentElement ? (el.parentElement.parentElement || el.parentElement) : el;
                        }
                    }

                    // For an image download click, only look for videos if type is explicitly video
                    // (prevent background video ads or other post videos from being grouped with an image)
                    var validVids = [];
                    if (type === 'video') {
                        var allVids = postContainer.querySelectorAll('video');
                        for (var vi = 0; vi < allVids.length; vi++) {
                            var vItem = allVids[vi];
                            if (vItem.closest && vItem.closest('header, footer, form, [class*="comment" i]')) continue;
                            var vRect = vItem.getBoundingClientRect();
                            if (vRect.width > 0 && vRect.height > 0 && (vRect.width < 120 || vRect.height < 120)) continue;
                            validVids.push(vItem);
                        }
                    }

                    // Collect valid post photos in THIS POST ONLY (strictly no avatars, no icons, no comments)
                    var allImgs = postContainer.querySelectorAll('img');
                    var validImgs = [];
                    for (var j = 0; j < allImgs.length; j++) {
                        var itemImg = allImgs[j];
                        if (isProfileOrAvatar(itemImg)) continue;
                        if (itemImg.closest && itemImg.closest('header, footer, form, [class*="comment" i], [class*="Comment" i], [aria-label*="comment" i]')) continue;

                        var u = getBestImageUrl(itemImg);
                        if (!u || !u.startsWith('http')) continue;
                        if (isProfileOrAvatar(itemImg, u)) continue;

                        var r = itemImg.getBoundingClientRect();
                        if (r.width > 0 && r.height > 0 && (r.width < 180 || r.height < 180)) continue;

                        validImgs.push({ el: itemImg, url: u });
                    }

                    // RULE: If this post has ONLY 1 media item, return [] so it downloads ONLY that 1 single item!
                    var totalCount = validVids.length + validImgs.length;
                    if (totalCount <= 1) {
                        return [];
                    }

                    var found = [];
                    var seen = {};

                    // Add videos from this post
                    for (var k = 0; k < validVids.length; k++) {
                        var videoItem = validVids[k];
                        var vUrl = getBestVideoUrl(videoItem, postContainer);
                        var vPoster = videoItem.poster || '';
                        if (!vUrl) {
                            vUrl = 'pending_video_' + (found.length + 1);
                        }
                        if (seen[vUrl]) continue;
                        seen[vUrl] = true;

                        var matchId = vUrl.match(/[_\/](\d{8,18})[_\.]/);
                        var vId = matchId ? matchId[1] : (Math.floor(Date.now() % 1000000) + '_' + (found.length + 1));

                        var isTarget = isSameMedia(videoItem, vUrl, targetMediaEl || el, exactUrl);

                        found.push({
                            url: vUrl,
                            thumb: vPoster || vUrl,
                            title: platform + '_video_' + vId,
                            isVideo: true,
                            isSelected: !!isTarget
                        });
                    }

                    // Add photos from this post
                    for (var m = 0; m < validImgs.length; m++) {
                        var pItem = validImgs[m];
                        var pImg = pItem.el;
                        var pUrl = pItem.url;
                        if (seen[pUrl]) continue;
                        seen[pUrl] = true;

                        var matchPhotoId = pUrl.match(/[_\/](\d{8,18})[_\.]/);
                        var photoId = matchPhotoId ? matchPhotoId[1] : (Math.floor(Date.now() % 1000000) + '_' + (found.length + 1));

                        var isTargetPhoto = isSameMedia(pImg, pUrl, targetMediaEl || el, exactUrl);

                        found.push({
                            url: pUrl,
                            thumb: pUrl,
                            title: platform + '_photo_' + photoId,
                            isVideo: false,
                            isSelected: !!isTargetPhoto
                        });
                    }

                    // If after filtering we have <= 1 item, return [] so it downloads as single item
                    if (found.length <= 1) {
                        return [];
                    }

                    // Ensure exactly one item has isSelected: true (the clicked one)
                    // The other items must be in the list with isSelected: false
                    var selectedCount = 0;
                    for (var sIdx = 0; sIdx < found.length; sIdx++) {
                        if (found[sIdx].isSelected) {
                            if (selectedCount > 0) {
                                found[sIdx].isSelected = false;
                            } else {
                                selectedCount++;
                            }
                        }
                    }
                    if (selectedCount === 0 && found.length > 0) {
                        found[0].isSelected = true;
                    }

                    return found;
                }

                function createDownloadButton(type, mediaEl) {
                    var btn = document.createElement('div');
                    var classSuffix = type === 'story' ? ' xxvi-btn-story' : (type === 'image' ? ' xxvi-btn-photo' : ' xxvi-btn-feed-video');
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

                        var platform = getPlatformPrefix();

                        // If video is paused, trigger play to initialize network stream
                        if (type === 'video') {
                            var v = (mediaEl.tagName === 'VIDEO') ? mediaEl : (mediaEl.querySelector ? mediaEl.querySelector('video') : null);
                            if (v && v.paused) {
                                try { v.play(); } catch (err) {}
                            }
                            var playBtn = mediaEl.querySelector ? mediaEl.querySelector('[role="button"][aria-label*="Play" i], [aria-label*="Play video" i], [aria-label*="Watch video" i], [data-sigil*="play"], [role="button"]') : null;
                            if (playBtn && !v) {
                                try { playBtn.click(); } catch (err) {}
                            }
                        }

                        var exactUrl = '';
                        var thumb = '';
                        if (type === 'image') {
                            var imgEl = (mediaEl.tagName === 'IMG') ? mediaEl : (mediaEl.querySelector ? mediaEl.querySelector('img') : null);
                            if (imgEl) {
                                exactUrl = getBestImageUrl(imgEl);
                                thumb = exactUrl;
                            } else {
                                exactUrl = getExactMediaUrl(mediaEl);
                                thumb = exactUrl;
                            }
                        } else if (type === 'video') {
                            var vEl = (mediaEl.tagName === 'VIDEO') ? mediaEl : (mediaEl.querySelector ? mediaEl.querySelector('video') : null);
                            exactUrl = getBestVideoUrl(vEl, mediaEl);
                            if (vEl && vEl.poster) thumb = cleanMediaUrl(vEl.poster);
                            if (!thumb) {
                                var posterEl = (mediaEl.tagName === 'IMG') ? mediaEl : (mediaEl.querySelector ? mediaEl.querySelector('img') : null);
                                if (posterEl && !isProfileOrAvatar(posterEl)) thumb = getBestImageUrl(posterEl);
                            }
                            if (!thumb) {
                                var vCard = findBestVideoCard(mediaEl) || mediaEl.parentElement;
                                if (vCard) {
                                    var cImgs = vCard.querySelectorAll('img');
                                    for (var cIdx = 0; cIdx < cImgs.length; cIdx++) {
                                        if (!isProfileOrAvatar(cImgs[cIdx])) {
                                            thumb = getBestImageUrl(cImgs[cIdx]);
                                            if (thumb) break;
                                        }
                                    }
                                }
                            }
                        } else {
                            exactUrl = getExactMediaUrl(mediaEl);
                            thumb = exactUrl;
                        }

                        // Fallback for Instagram video click if URL is not yet captured
                        if (type === 'video' && (!exactUrl || exactUrl.startsWith('blob:') || exactUrl.indexOf('http') === -1)) {
                            var hostName = (window.location.hostname || '').toLowerCase();
                            if (hostName.indexOf('instagram.com') !== -1) {
                                var sc = '';
                                var mPath = window.location.pathname.match(/\/(?:reel|reels|p)\/([a-zA-Z0-9_-]+)/i);
                                if (mPath && mPath[1]) sc = mPath[1];
                                if (!sc && mediaEl) {
                                    var cL = mediaEl.querySelector ? mediaEl.querySelector('a[href*="/reel/"], a[href*="/p/"]') : null;
                                    if (cL) {
                                        var mL = (cL.getAttribute('href') || '').match(/\/(?:reel|reels|p)\/([a-zA-Z0-9_-]+)/i);
                                        if (mL && mL[1]) sc = mL[1];
                                    }
                                }
                                if (sc && window.__XXVI_INSTA_CACHE__ && window.__XXVI_INSTA_CACHE__[sc]) {
                                    exactUrl = cleanVideoUrl(window.__XXVI_INSTA_CACHE__[sc].url);
                                }
                                if ((!exactUrl || exactUrl.startsWith('blob:')) && sc) {
                                    try {
                                        var xhr = new XMLHttpRequest();
                                        xhr.open('GET', '/p/' + sc + '/?__a=1&__d=dis', false);
                                        xhr.setRequestHeader('X-IG-App-ID', '936619743392459');
                                        xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
                                        xhr.send();
                                        if (xhr.status === 200 && xhr.responseText) {
                                            var mU = xhr.responseText.match(/["']video_versions["']\s*:\s*\[\s*\{[^}]*?["']url["']\s*:\s*["'](https?:[^"']+)["']/i) ||
                                                    xhr.responseText.match(/["']video_url["']\s*:\s*["'](https?:[^"']+)["']/i);
                                            if (mU && mU[1]) {
                                                exactUrl = cleanVideoUrl(mU[1]);
                                                window.__XXVI_INSTA_CACHE__[sc] = { url: exactUrl, thumb: '', code: sc };
                                            }
                                        }
                                    } catch(e) {}
                                }
                            }
                            if ((!exactUrl || exactUrl.startsWith('blob:')) && window.__XXVI_RECENT_VIDEOS__ && window.__XXVI_RECENT_VIDEOS__.length > 0) {
                                exactUrl = window.__XXVI_RECENT_VIDEOS__[0];
                            }
                        }

                        if (!thumb && exactUrl) thumb = exactUrl;

                        var matchId = exactUrl ? exactUrl.match(/[_\/](\d{8,18})[_\.]/) : null;
                        var photoId = matchId ? matchId[1] : Math.floor(Date.now() % 1000000);
                        var title = platform + '_' + (type === 'image' ? ('photo_' + photoId) : (type + '_' + photoId));

                        // If more than one media item in this post, show all items from this post with clicked item selected
                        // For video or story downloads, NEVER bundle with other media items!
                        var siblings = (type === 'story' || type === 'video') ? [] : getSiblingMediaInSamePost(mediaEl, type, exactUrl);
                        if (siblings.length > 1) {
                            if (window.XXVIBridge && window.XXVIBridge.onMultiMediaClicked) {
                                window.XXVIBridge.onMultiMediaClicked(JSON.stringify(siblings), type, title);
                                return;
                            }
                        }

                        // Exactly one media item: download single image or single video
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

                // --- 1. ATTACH TO EVERY VIDEO & REEL (INSTAGRAM, TIKTOK, TWITTER, FACEBOOK, ANY WEB) ---
                function attachVideoButtons() {
                    var videos = document.querySelectorAll('video');
                    var platform = getPlatformPrefix();

                    videos.forEach(function(v) {
                        var rect = v.getBoundingClientRect();
                        if (rect.width < 50 || rect.height < 50) return;

                        var card = findBestVideoCard(v);
                        if (!card) return;

                        // Add video playback listeners for immediate stream detection
                        if (!v.__xxvi_has_listener) {
                            v.__xxvi_has_listener = true;
                            var onPlayNotify = function() {
                                var src = getBestVideoUrl(v, card);
                                var poster = v.poster || (v.getAttribute && v.getAttribute('poster')) || '';
                                if (src && src.startsWith('http') && window.XXVIBridge) {
                                    window.XXVIBridge.onMediaDetected(src, 'video', platform + '_video', poster);
                                }
                            };
                            v.addEventListener('play', onPlayNotify, { passive: true });
                            v.addEventListener('playing', onPlayNotify, { passive: true });
                            v.addEventListener('loadeddata', onPlayNotify, { passive: true });
                        }

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
                        } else {
                            btn.classList.add('xxvi-btn-feed-video');
                            btn.style.setProperty('bottom', '12px', 'important');
                            btn.style.setProperty('right', '12px', 'important');
                        }

                        card.appendChild(btn);
                        card.setAttribute('data-xxvi-card-handled', 'true');
                        v.setAttribute('data-xxvi-card-handled', 'true');

                        var src = getBestVideoUrl(v, card);
                        if (src && src.startsWith('http') && window.XXVIBridge) {
                            window.XXVIBridge.onMediaDetected(src, 'video', platform + '_video', v.poster || '');
                        }
                    });

                    // Inline video containers without <video> yet
                    var inlineContainers = document.querySelectorAll('[data-sigil*="inlineVideo"], [data-video-id], [data-video-url], [data-e2e="feed-video"]');
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
                        if (rect.width < 90 || rect.height < 70) return;

                        var pos = window.getComputedStyle(vc).position;
                        if (pos === 'static') {
                            vc.style.position = 'relative';
                        }

                        var btn = createDownloadButton('video', vc);
                        if (topOff > 20) {
                            btn.classList.add('xxvi-btn-reel');
                            btn.style.setProperty('top', topOff + 'px', 'important');
                            btn.style.setProperty('right', '12px', 'important');
                        } else {
                            btn.classList.add('xxvi-btn-feed-video');
                            btn.style.setProperty('bottom', '12px', 'important');
                            btn.style.setProperty('right', '12px', 'important');
                        }

                        vc.appendChild(btn);
                        vc.setAttribute('data-xxvi-card-handled', 'true');

                        var src = getBestVideoUrl(null, vc);
                        var vcPoster = vc.querySelector('img') ? getBestImageUrl(vc.querySelector('img')) : '';
                        if (src && src.startsWith('http') && window.XXVIBridge) {
                            window.XXVIBridge.onMediaDetected(src, 'video', platform + '_video', vcPoster);
                        }
                    });

                    // Video preview posters and cards without <video> mounted yet (e.g. Facebook feed videos with play button overlay)
                    var allImgs = document.querySelectorAll('img');
                    allImgs.forEach(function(img) {
                        if (isProfileOrAvatar(img)) return;
                        if (!isVideoElementOrPoster(img)) return;

                        var card = findBestVideoCard(img) || img.parentElement;
                        if (!card) return;
                        if (hasExistingDownloadButton(card) || hasExistingDownloadButton(img)) return;

                        var r = card.getBoundingClientRect();
                        if (r.width < 100 || r.height < 80) return;

                        var pos = window.getComputedStyle(card).position;
                        if (pos === 'static') card.style.position = 'relative';

                        var btn = createDownloadButton('video', card);
                        var topOff = getTopHeaderOffset(card);
                        if (topOff > 20) {
                            btn.classList.add('xxvi-btn-reel');
                            btn.style.setProperty('top', topOff + 'px', 'important');
                            btn.style.setProperty('right', '12px', 'important');
                        } else {
                            btn.classList.add('xxvi-btn-feed-video');
                            btn.style.setProperty('bottom', '12px', 'important');
                            btn.style.setProperty('right', '12px', 'important');
                        }

                        card.appendChild(btn);
                        card.setAttribute('data-xxvi-card-handled', 'true');
                        img.setAttribute('data-xxvi-card-handled', 'true');

                        var vSrc = getBestVideoUrl(null, card);
                        var poster = getBestImageUrl(img) || img.currentSrc || img.src || '';
                        if (vSrc && vSrc.startsWith('http') && window.XXVIBridge) {
                            window.XXVIBridge.onMediaDetected(vSrc, 'video', platform + '_video', poster);
                        }
                    });
                }

                // --- 2. ATTACH TO STORIES (SKIP CREATE STORY) ---
                function attachStoryButtons() {
                    var platform = getPlatformPrefix();
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

                            // STRICTLY SKIP "Create story"
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
                                window.XXVIBridge.onMediaDetected(src, 'story', platform + '_' + author.replace(/[^a-zA-Z0-9]/g, '_'), src);
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
                            window.XXVIBridge.onMediaDetected(src, 'story', platform + '_story', '');
                        }
                    });
                }

                // --- 3. ATTACH TO EVERY PHOTO / IMAGE ---
                function attachPhotoButtons() {
                    var imgs = document.querySelectorAll('img');
                    var platform = getPlatformPrefix();

                    imgs.forEach(function(img) {
                        if (hasExistingDownloadButton(img)) return;

                        if (img.closest && img.closest('[data-xxvi-story-tray], [data-xxvi-card-handled], [aria-label*="stories" i], [aria-label*="story" i]')) {
                            return;
                        }

                        // STRICTLY REJECT AVATARS & PROFILE PICTURES & COMMENTS & HEADERS
                        if (isProfileOrAvatar(img)) return;

                        // STRICTLY REJECT VIDEOS & VIDEO POSTERS
                        if (isVideoElementOrPoster(img)) return;

                        if (img.closest) {
                            var parentMedia = img.closest('[data-sigil*="inlineVideo"], [data-video-id], video, a[href*="/stories/"], a[href*="story.php"], header, footer, form, [class*="comment" i]');
                            if (parentMedia) return;
                        }

                        var rect = img.getBoundingClientRect ? img.getBoundingClientRect() : null;
                        var w = (rect && rect.width) ? rect.width : (img.clientWidth || img.offsetWidth || 0);
                        var h = (rect && rect.height) ? rect.height : (img.clientHeight || img.offsetHeight || 0);

                        if (w > 0 && h > 0 && (w < 180 || h < 180)) return;
                        if (img.naturalWidth > 0 && img.naturalWidth < 180) return;
                        if (img.naturalHeight > 0 && img.naturalHeight < 180) return;

                        var src = getBestImageUrl(img);
                        if (!src || !src.startsWith('http')) return;
                        if (isProfileOrAvatar(img, src)) return;

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
                            window.XXVIBridge.onMediaDetected(src, 'image', platform + '_photo', src);
                        }
                    });

                    // Elements with CSS background-image
                    var bgElements = document.querySelectorAll('[style*="background-image"], div[role="img"], i[role="img"]');
                    bgElements.forEach(function(bge) {
                        if (hasExistingDownloadButton(bge)) return;

                        if (bge.closest && bge.closest('[data-xxvi-story-tray], [data-xxvi-card-handled], [data-sigil*="inlineVideo"], [data-video-id], video, a[href*="/stories/"], a[href*="story.php"], header, footer, form, [class*="comment" i]')) {
                            return;
                        }

                        if (isVideoElementOrPoster(bge)) return;

                        var w = bge.offsetWidth || bge.clientWidth || 0;
                        var h = bge.offsetHeight || bge.clientHeight || 0;
                        if (w < 180 || h < 180) return;

                        var bgUrl = getExactMediaUrl(bge);
                        if (!bgUrl || !bgUrl.startsWith('http')) return;
                        if (isProfileOrAvatar(bge, bgUrl)) return;

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
                            window.XXVIBridge.onMediaDetected(bgUrl, 'image', platform + '_photo', bgUrl);
                        }
                    });
                }

                // --- 4. CLEANUP PASS ---
                function cleanupDuplicateButtons() {
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

                    var createCards = document.querySelectorAll('[data-xxvi-story-tray] [aria-label*="Create" i], [data-xxvi-story-tray] a[href*="stories/create"]');
                    createCards.forEach(function(cc) {
                        var root = cc.closest('div') || cc;
                        var b = root.querySelector('.xxvi-btn-injected');
                        if (b && b.parentElement) b.parentElement.removeChild(b);
                    });

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
