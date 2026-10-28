/**
 * Local Service Connect - Google Maps & Geolocation Integration
 * Supports Maps JS API, Places Autocomplete, Geocoding, Draggable Markers,
 * Service Coverage Radius Circles, and Browser Geolocation.
 */

const LSC_MAPS = (function () {
    let mapsConfig = null;
    let isApiLoaded = false;
    let loadPromise = null;

    /**
     * Fetch Google Maps configuration from backend and load the Maps JS API.
     */
    async function loadGoogleMaps() {
        if (loadPromise) return loadPromise;

        loadPromise = new Promise(async (resolve) => {
            try {
                const res = await fetch('/api/config/maps');
                if (!res.ok) {
                    resolve({ configured: false, error: 'Could not fetch maps configuration' });
                    return;
                }
                mapsConfig = await res.json();

                const localKey = localStorage.getItem('lsc_maps_key');
                if ((!mapsConfig.configured || !mapsConfig.apiKey) && localKey && localKey.trim()) {
                    mapsConfig.apiKey = localKey.trim();
                    mapsConfig.configured = true;
                    // Sync with backend asynchronously
                    fetch('/api/config/maps', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ apiKey: localKey.trim() })
                    }).catch(() => {});
                }

                if (!mapsConfig.configured || !mapsConfig.apiKey) {
                    console.info('Google Maps API key is not configured. Falling back to manual location input.');
                    resolve({ configured: false, config: mapsConfig });
                    return;
                }

                if (window.google && window.google.maps) {
                    isApiLoaded = true;
                    resolve({ configured: true, config: mapsConfig });
                    return;
                }

                // Prevent multiple script tags
                const existingScript = document.querySelector('script[src*="maps.googleapis.com/maps/api/js"]');
                if (existingScript) {
                    existingScript.addEventListener('load', () => {
                        isApiLoaded = true;
                        resolve({ configured: true, config: mapsConfig });
                    });
                    existingScript.addEventListener('error', (err) => {
                        resolve({ configured: false, error: 'Failed to load script', config: mapsConfig });
                    });
                    return;
                }

                // Inject Maps JS script tag
                const script = document.createElement('script');
                script.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(mapsConfig.apiKey)}&libraries=places`;
                script.async = true;
                script.defer = true;
                script.onload = () => {
                    isApiLoaded = true;
                    resolve({ configured: true, config: mapsConfig });
                };
                script.onerror = (err) => {
                    console.error('Failed to load Google Maps script:', err);
                    resolve({ configured: false, error: 'Failed to load script', config: mapsConfig });
                };
                document.head.appendChild(script);
            } catch (err) {
                console.error('Error initializing Google Maps:', err);
                resolve({ configured: false, error: err.message });
            }
        });

        return loadPromise;
    }

    /**
     * Handle Google Maps JavaScript API authentication failures gracefully.
     */
    window.gm_authFailure = function () {
        console.warn('Google Maps authentication failed (gm_authFailure). Please check your Google Cloud project billing and enabled APIs.');
        const mapEls = document.querySelectorAll('#modalInteractiveMap, #pickerMap, #bookingPickerMap, #providerMap');
        mapEls.forEach(el => {
            renderMapSetupCard(el, 'Google Maps authentication failed. Please verify that Billing is active on your Google Cloud project and that Maps JavaScript API, Places API, and Geocoding API are enabled.');
        });
    };

    function renderMapSetupCard(container, message) {
        if (!container) return;
        container.innerHTML = `
            <div class="p-3 text-center bg-light rounded-3 border h-100 d-flex flex-column justify-content-center align-items-center" style="min-height: 250px;">
                <div class="rounded-circle bg-warning-subtle text-warning p-2 d-flex align-items-center justify-content-center mb-2" style="width: 48px; height: 48px;">
                    <i class="bi bi-geo-alt-fill fs-4"></i>
                </div>
                <h6 class="fw-bold mb-1 text-dark">Google Maps Platform Setup Notice</h6>
                <p class="small text-muted mb-2 px-3" style="max-width: 440px;">
                    ${escapeHtml(message || 'To render live map tiles and places, enable Maps JavaScript API, Places API, and Geocoding API on Google Cloud with an active billing account.')}
                </p>
                <div class="small text-primary fw-semibold"><i class="bi bi-check-circle me-1"></i>You can continue booking by typing your exact address below.</div>
            </div>
        `;
    }

    /**
     * Reverse geocode latitude and longitude into locality, city, and formatted address.
     * Uses Google Maps Geocoding API exclusively without OpenStreetMap/Nominatim fallback.
     */
    async function reverseGeocode(lat, lng) {
        let area = '';
        let neighborhood = '';
        let city = '';
        let state = '';
        let postalCode = '';
        let country = '';
        let formattedAddress = '';

        // Check if Google Maps is loaded or can be loaded
        if (!isApiLoaded && !loadPromise) {
            try {
                await loadGoogleMaps();
            } catch (e) {
                // Non-blocking
            }
        }

        // Try Google Maps Geocoder if loaded
        if (window.google && window.google.maps && window.google.maps.Geocoder) {
            try {
                const geocoder = new google.maps.Geocoder();
                const res = await new Promise((resolve) => {
                    geocoder.geocode({ location: { lat, lng } }, (results, status) => {
                        if (status === 'OK' && results && results.length > 0) {
                            resolve(results);
                        } else {
                            resolve(null);
                        }
                    });
                });
                if (res && res[0]) {
                    formattedAddress = res[0].formatted_address || '';
                    for (const comp of (res[0].address_components || [])) {
                        const types = comp.types || [];
                        if (types.includes('sublocality_level_1') || types.includes('sublocality')) {
                            if (!area) area = comp.long_name;
                        } else if (types.includes('neighborhood')) {
                            if (!neighborhood) neighborhood = comp.long_name;
                        } else if (types.includes('locality')) {
                            if (!city) city = comp.long_name;
                        } else if (types.includes('administrative_area_level_2') && !city) {
                            city = comp.long_name;
                        } else if (types.includes('administrative_area_level_1')) {
                            if (!state) state = comp.long_name;
                        } else if (types.includes('postal_code')) {
                            if (!postalCode) postalCode = comp.long_name;
                        } else if (types.includes('country')) {
                            if (!country) country = comp.long_name;
                        }
                    }
                    if (!area && neighborhood) area = neighborhood;
                }
            } catch (e) {
                console.warn('Google reverse geocoding warning:', e);
            }
        }

        // Format human-readable location name
        let locationName = '';
        if (area && city) {
            const a = area.trim();
            const c = city.trim();
            if (a.toLowerCase() === c.toLowerCase() || a.toLowerCase().includes(c.toLowerCase())) {
                locationName = a;
            } else {
                locationName = `${a}, ${c}`;
            }
        } else if (area) {
            locationName = area.trim();
        } else if (city) {
            locationName = city.trim();
        } else if (formattedAddress) {
            const parts = formattedAddress.split(',').map(s => s.trim()).filter(Boolean);
            locationName = parts.slice(0, 2).join(', ') || formattedAddress;
        } else {
            locationName = `Pinned Location (${Number(lat).toFixed(4)}, ${Number(lng).toFixed(4)})`;
        }

        if (!formattedAddress) {
            formattedAddress = `Pinned Location (${Number(lat).toFixed(5)}, ${Number(lng).toFixed(5)})`;
        }

        return {
            lat,
            lng,
            area: area || '',
            city: city || '',
            state: state || '',
            postalCode: postalCode || '',
            country: country || 'India',
            formattedAddress,
            locationName
        };
    }

    /**
     * Get user's current GPS location via Browser Geolocation API.
     * Requests high accuracy GPS and handles permissions and timeouts gracefully.
     */
    function getCurrentLocation() {
        return new Promise((resolve, reject) => {
            if (!navigator.geolocation) {
                reject(new Error('Geolocation is not supported by your browser. Please search for your locality or enter your address manually.'));
                return;
            }
            navigator.geolocation.getCurrentPosition(
                async (pos) => {
                    const lat = pos.coords.latitude;
                    const lng = pos.coords.longitude;
                    const accuracy = pos.coords.accuracy || 0;
                    const isApproximate = accuracy > 200;
                    try {
                        const geo = await reverseGeocode(lat, lng);
                        resolve({ ...geo, accuracy, isApproximate });
                    } catch (e) {
                        resolve({
                            lat,
                            lng,
                            accuracy,
                            isApproximate,
                            area: '',
                            city: '',
                            state: '',
                            postalCode: '',
                            country: 'India',
                            formattedAddress: `GPS Location (${lat.toFixed(5)}, ${lng.toFixed(5)})`,
                            locationName: `GPS Location (${lat.toFixed(4)}, ${lng.toFixed(4)})`
                        });
                    }
                },
                (err) => {
                    let msg = 'Unable to retrieve location.';
                    if (err.code === 1) msg = 'Location access was denied. Please allow location permissions in your browser or search for your locality manually.';
                    else if (err.code === 2) msg = 'GPS location is currently unavailable. Please search for your area or enter address manually.';
                    else if (err.code === 3) msg = 'Location request timed out. Please enter your address manually or search for your locality.';
                    reject(new Error(msg));
                },
                { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 }
            );
        });
    }

    /**
     * Attach Google Places Autocomplete to an input element.
     * Supports element or element ID, and callback or options object.
     */
    function setupPlacesAutocomplete(inputElemOrId, onPlaceSelectedOrOptions) {
        const inputElem = typeof inputElemOrId === 'string' ? document.getElementById(inputElemOrId) : inputElemOrId;
        if (!inputElem) return null;
        if (!window.google || !window.google.maps || !window.google.maps.places) return null;

        const callback = typeof onPlaceSelectedOrOptions === 'function'
            ? onPlaceSelectedOrOptions
            : (onPlaceSelectedOrOptions && onPlaceSelectedOrOptions.onPlaceSelected);

        const autocomplete = new google.maps.places.Autocomplete(inputElem, {
            types: ['geocode', 'establishment'],
            componentRestrictions: { country: 'in' },
            fields: ['address_components', 'geometry', 'formatted_address', 'name']
        });

        autocomplete.addListener('place_changed', () => {
            const place = autocomplete.getPlace();
            if (!place.geometry || !place.geometry.location) return;

            const lat = place.geometry.location.lat();
            const lng = place.geometry.location.lng();
            const formattedAddress = place.formatted_address || place.name || '';
            let area = '';
            let neighborhood = '';
            let city = '';
            let state = '';
            let postalCode = '';
            let country = '';

            if (place.address_components) {
                for (const comp of place.address_components) {
                    const types = comp.types || [];
                    if (types.includes('sublocality') || types.includes('sublocality_level_1')) {
                        if (!area) area = comp.long_name;
                    } else if (types.includes('neighborhood')) {
                        if (!neighborhood) neighborhood = comp.long_name;
                    } else if (types.includes('locality')) {
                        if (!city) city = comp.long_name;
                    } else if (types.includes('administrative_area_level_2') && !city) {
                        city = comp.long_name;
                    } else if (types.includes('administrative_area_level_1')) {
                        if (!state) state = comp.long_name;
                    } else if (types.includes('postal_code')) {
                        if (!postalCode) postalCode = comp.long_name;
                    } else if (types.includes('country')) {
                        if (!country) country = comp.long_name;
                    }
                }
                if (!area && neighborhood) area = neighborhood;
            }

            if (callback) {
                callback({ lat, lng, formattedAddress, area, city, state, postalCode, country, place });
            }
        });

        return autocomplete;
    }

    /**
     * Create an interactive map with a draggable pin and optional coverage radius circle.
     * Prevents asynchronous race conditions using sequence tracking.
     */
    function createLocationPickerMap(containerId, options = {}) {
        const container = typeof containerId === 'string' ? document.getElementById(containerId) : containerId;
        if (!container) return null;

        if (!window.google || !window.google.maps) {
            renderMapSetupCard(container, 'Google Maps JavaScript API is not loaded yet. Enable Maps JavaScript API, Places API, and Geocoding API with active billing in Google Cloud.');
            return {
                map: null,
                marker: null,
                radiusCircle: null,
                setLocation: function () {},
                refresh: function () {},
                getMarkerPosition: function () {
                    return { lat: options.initialLat || 23.3441, lng: options.initialLng || 85.3096 };
                },
                setRadiusKm: function () {}
            };
        }

        const defaultLat = options.initialLat || (mapsConfig ? mapsConfig.defaultLat : 23.3441);
        const defaultLng = options.initialLng || (mapsConfig ? mapsConfig.defaultLng : 85.3096);
        const zoom = options.zoom || 15;

        const map = new google.maps.Map(container, {
            center: { lat: defaultLat, lng: defaultLng },
            zoom: zoom,
            mapTypeControl: false,
            streetViewControl: false,
            fullscreenControl: true,
            zoomControl: true
        });

        const marker = new google.maps.Marker({
            position: { lat: defaultLat, lng: defaultLng },
            map: map,
            draggable: options.draggable !== false,
            animation: google.maps.Animation.DROP,
            title: options.markerTitle || 'Selected Service Location'
        });

        let radiusCircle = null;
        if (options.showRadius && options.radiusKm) {
            radiusCircle = new google.maps.Circle({
                map: map,
                radius: options.radiusKm * 1000,
                fillColor: '#7c3aed',
                fillOpacity: 0.15,
                strokeColor: '#7c3aed',
                strokeOpacity: 0.6,
                strokeWeight: 2
            });
            radiusCircle.bindTo('center', marker, 'position');
        }

        let geocodeReqSequence = 0;

        async function handlePositionChange(newLat, newLng) {
            const currentReq = ++geocodeReqSequence;
            if (options.onLocationStart) {
                options.onLocationStart({ lat: newLat, lng: newLng });
            }

            try {
                const res = await reverseGeocode(newLat, newLng);
                // Stale response check: discard if newer drag/change occurred
                if (currentReq !== geocodeReqSequence) {
                    return;
                }
                if (options.onLocationChange) {
                    options.onLocationChange(res);
                }
            } catch (err) {
                if (currentReq !== geocodeReqSequence) return;
                if (options.onLocationChange) {
                    options.onLocationChange({
                        lat: newLat,
                        lng: newLng,
                        area: '',
                        city: '',
                        state: '',
                        postalCode: '',
                        country: 'India',
                        formattedAddress: `Pinned Location (${newLat.toFixed(5)}, ${newLng.toFixed(5)})`,
                        locationName: `Location (${newLat.toFixed(4)}, ${newLng.toFixed(4)})`
                    });
                }
            }
        }

        if (options.draggable !== false) {
            marker.addListener('dragstart', () => {
                if (options.onDragStart) options.onDragStart();
            });
            marker.addListener('dragend', () => {
                const pos = marker.getPosition();
                handlePositionChange(pos.lat(), pos.lng());
            });
        }

        map.addListener('click', (e) => {
            if (options.draggable !== false) {
                marker.setPosition(e.latLng);
                handlePositionChange(e.latLng.lat(), e.latLng.lng());
            }
        });

        return {
            map,
            marker,
            radiusCircle,
            setLocation: function (lat, lng, pan = true) {
                const pos = new google.maps.LatLng(lat, lng);
                marker.setPosition(pos);
                if (pan) map.panTo(pos);
            },
            refresh: function () {
                if (map) {
                    google.maps.event.trigger(map, 'resize');
                    const pos = marker.getPosition();
                    if (pos) map.setCenter(pos);
                }
            },
            getMarkerPosition: function () {
                const pos = marker ? marker.getPosition() : null;
                return pos ? { lat: pos.lat(), lng: pos.lng() } : { lat: defaultLat, lng: defaultLng };
            },
            setRadiusKm: function (radiusKm) {
                if (radiusCircle) {
                    radiusCircle.setRadius(radiusKm * 1000);
                } else if (options.showRadius) {
                    radiusCircle = new google.maps.Circle({
                        map: map,
                        radius: radiusKm * 1000,
                        fillColor: '#7c3aed',
                        fillOpacity: 0.15,
                        strokeColor: '#7c3aed',
                        strokeOpacity: 0.6,
                        strokeWeight: 2
                    });
                    radiusCircle.bindTo('center', marker, 'position');
                }
            }
        };
    }

    /**
     * Multi-marker map for Find Providers page: shows Customer pin + Provider pins with InfoWindows.
     */
    function createProvidersOverviewMap(containerId, options = {}) {
        const container = typeof containerId === 'string' ? document.getElementById(containerId) : containerId;
        if (!container || !window.google || !window.google.maps) return null;

        const defaultLat = options.centerLat || (mapsConfig ? mapsConfig.defaultLat : 23.3441);
        const defaultLng = options.centerLng || (mapsConfig ? mapsConfig.defaultLng : 85.3096);

        const map = new google.maps.Map(container, {
            center: { lat: defaultLat, lng: defaultLng },
            zoom: options.zoom || 12,
            mapTypeControl: false,
            streetViewControl: false
        });

        const bounds = new google.maps.LatLngBounds();
        let customerMarker = null;
        let providerMarkers = [];
        let infoWindow = new google.maps.InfoWindow();

        if (options.customerCoords && options.customerCoords.lat && options.customerCoords.lng) {
            const cPos = { lat: options.customerCoords.lat, lng: options.customerCoords.lng };
            customerMarker = new google.maps.Marker({
                position: cPos,
                map: map,
                title: 'Your Location',
                icon: {
                    path: google.maps.SymbolPath.CIRCLE,
                    scale: 9,
                    fillColor: '#7c3aed',
                    fillOpacity: 1,
                    strokeColor: '#ffffff',
                    strokeWeight: 3
                }
            });
            bounds.extend(cPos);
        }

        function setProviders(providers) {
            // Clear existing markers
            providerMarkers.forEach(m => m.setMap(null));
            providerMarkers = [];

            if (!providers || providers.length === 0) {
                if (customerMarker) map.panTo(customerMarker.getPosition());
                return;
            }

            const currentBounds = new google.maps.LatLngBounds();
            if (customerMarker) currentBounds.extend(customerMarker.getPosition());

            providers.forEach((p) => {
                if (!p.latitude || !p.longitude) return;
                const pos = { lat: p.latitude, lng: p.longitude };
                currentBounds.extend(pos);

                const marker = new google.maps.Marker({
                    position: pos,
                    map: map,
                    title: p.businessName,
                    icon: {
                        url: 'https://maps.google.com/mapfiles/ms/icons/purple-dot.png'
                    }
                });

                const contentString = `
                    <div style="max-width: 240px; padding: 4px;">
                        <h6 class="fw-bold mb-1" style="color: #0f172a;">${escapeHtml(p.businessName)}</h6>
                        <div class="small text-muted mb-1">${escapeHtml(p.formattedAddress || 'Local Area')}</div>
                        <div class="d-flex align-items-center gap-2 mb-2">
                            <span class="badge bg-warning text-dark"><i class="bi bi-star-fill"></i> ${p.rating ? Number(p.rating).toFixed(1) : 'New'}</span>
                            ${p.distanceKm != null ? `<span class="badge bg-light text-dark border"><i class="bi bi-geo-alt"></i> ${p.distanceKm} km away</span>` : ''}
                        </div>
                        <div class="d-flex gap-1">
                            <a href="/provider-profile.html?id=${p.id}" class="btn btn-sm btn-outline-secondary w-50" style="font-size: 11px;">View Profile</a>
                            <a href="/booking.html?providerId=${p.id}" class="btn btn-sm btn-primary w-50" style="font-size: 11px;">Book Now</a>
                        </div>
                    </div>
                `;

                marker.addListener('click', () => {
                    infoWindow.setContent(contentString);
                    infoWindow.open(map, marker);
                });

                providerMarkers.push(marker);
            });

            if (!currentBounds.isEmpty()) {
                map.fitBounds(currentBounds);
                const listener = google.maps.event.addListener(map, 'idle', () => {
                    if (map.getZoom() > 15) map.setZoom(15);
                    google.maps.event.removeListener(listener);
                });
            }
        }

        if (options.providers) {
            setProviders(options.providers);
        }

        return {
            map,
            setProviders,
            setCustomerLocation: function (lat, lng) {
                const pos = { lat, lng };
                if (customerMarker) {
                    customerMarker.setPosition(pos);
                } else {
                    customerMarker = new google.maps.Marker({
                        position: pos,
                        map: map,
                        title: 'Your Location',
                        icon: {
                            path: google.maps.SymbolPath.CIRCLE,
                            scale: 9,
                            fillColor: '#7c3aed',
                            fillOpacity: 1,
                            strokeColor: '#ffffff',
                            strokeWeight: 3
                        }
                    });
                }
                map.panTo(pos);
            }
        };
    }

    function escapeHtml(str) {
        if (!str) return '';
        return String(str).replace(/[&<>"']/g, function (m) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[m];
        });
    }

    return {
        loadGoogleMaps,
        loadGoogleMapsApi: loadGoogleMaps,
        getCurrentLocation,
        reverseGeocode,
        setupPlacesAutocomplete,
        attachPlacesAutocomplete: setupPlacesAutocomplete,
        createLocationPickerMap,
        createProvidersOverviewMap
    };
})();
