
navbar();
const $ = id => document.getElementById(id);
$('yr').textContent = new Date().getFullYear();

// Global State
let allServices = [];
let lastProviders = [];
let selectedProvider = null;
let currentCoords = null;
let currentFormattedAddress = null;

// Default dates
const today = new Date().toISOString().split('T')[0];
$('modalDate').value = today;
$('modalDate').min = today;
const nowTime = new Date(Date.now() + 45 * 60000);
$('modalTime').value = nowTime.toTimeString().slice(0, 5);

const currentType = () => document.querySelector('input[name=bookingType]:checked').value;
const isQuick = () => currentType() === 'QUICK_SERVICE';

function updateBookingTypeUI() {
  const quick = isQuick();
  $('quickServiceHint').classList.toggle('d-none', !quick);
  if ($('normalModeDesc')) {
    $('normalModeDesc').classList.toggle('d-none', quick);
  }
  const lblN = $('lblNormal');
  const lblQ = $('lblQuick');
  if (lblN && lblQ) {
    if (quick) {
      lblN.classList.remove('active-normal');
      lblQ.classList.add('active-quick');
    } else {
      lblQ.classList.remove('active-quick');
      lblN.classList.add('active-normal');
    }
  }
}

document.querySelectorAll('input[name=bookingType]').forEach(r => {
  r.addEventListener('change', () => {
    updateBookingTypeUI();
    if ($('svcSelect').value) {
      findProviders();
    }
  });
});

$('btnQuickShortcut').addEventListener('click', () => {
  $('typeQuick').checked = true;
  updateBookingTypeUI();
  $('svcSelect').focus();
  $('frontBookingForm').scrollIntoView({ behavior: 'smooth' });
});

const promoBtn = $('btnPromoQuickAction');
if (promoBtn) {
  promoBtn.addEventListener('click', () => {
    $('typeQuick').checked = true;
    updateBookingTypeUI();
    $('svcSelect').focus();
    $('frontBookingForm').scrollIntoView({ behavior: 'smooth' });
  });
}

$('btnBrowseAllServices').addEventListener('click', () => {
  $('servicesSection').scrollIntoView({ behavior: 'smooth' });
});

// Quick Area Chips with Active Selection State & Keyboard Support
document.querySelectorAll('.area-chip').forEach(chip => {
  const selectArea = () => {
    document.querySelectorAll('.area-chip').forEach(c => c.classList.remove('active'));
    chip.classList.add('active');
    $('areaInput').value = chip.dataset.area;
    $('areaInput').title = chip.dataset.area;
    currentCoords = null;
    currentFormattedAddress = chip.dataset.area;
    if ($('svcSelect').value) {
      findProviders();
    } else {
      $('svcSelect').focus();
    }
  };
  chip.addEventListener('click', selectArea);
  chip.addEventListener('keydown', e => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      selectArea();
    }
  });
});

// Focus location input when clicking inside the hero location box container
const heroLocBox = $('heroLocationBox');
if (heroLocBox) {
  heroLocBox.addEventListener('click', e => {
    if (e.target !== $('btnGps') && !$('btnGps').contains(e.target)) {
      $('areaInput').focus();
    }
  });
}

// Editable location input - clear coordinates if user manually enters a different area
$('areaInput').addEventListener('input', () => {
  if (currentFormattedAddress && $('areaInput').value.trim() !== currentFormattedAddress) {
    currentCoords = null;
  }
  const val = $('areaInput').value.trim().toLowerCase();
  document.querySelectorAll('.area-chip').forEach(c => {
    c.classList.toggle('active', c.dataset.area && c.dataset.area.toLowerCase() === val);
  });
  $('areaInput').title = $('areaInput').value.trim();
});

// Frontpage GPS Auto-detect button with real location detection
$('btnGps').addEventListener('click', async () => {
  const icon = $('gpsIcon'), spinner = $('gpsSpinner'), btn = $('btnGps');
  btn.disabled = true;
  icon.classList.add('d-none');
  spinner.classList.remove('d-none');
  try {
    const loc = await LSC_MAPS.getCurrentLocation();
    if (!loc || loc.lat == null || loc.lng == null) {
      throw new Error('Unable to retrieve GPS coordinates. Please enter your location manually.');
    }

    currentCoords = { lat: loc.lat, lng: loc.lng };
    currentFormattedAddress = loc.formattedAddress || loc.locationName || '';

    // Prefer resolved locality and city (e.g. "Lalpur, Ranchi")
    const resolvedName = loc.locationName || (loc.area && loc.city ? `${loc.area}, ${loc.city}` : (loc.area || loc.city || loc.formattedAddress));

    if (!resolvedName) {
      throw new Error('GPS coordinates detected, but address could not be resolved. Please enter your locality manually.');
    }

    $('areaInput').value = resolvedName;
    $('areaInput').title = loc.formattedAddress || resolvedName;

    // Synchronize popular locality chips
    document.querySelectorAll('.area-chip').forEach(c => {
      c.classList.toggle('active', c.dataset.area && c.dataset.area.toLowerCase().includes(resolvedName.toLowerCase()));
    });

    if ($('svcSelect').value) {
      findProviders();
    } else {
      $('svcSelect').focus();
    }
  } catch (err) {
    showLocationToast(err.message || 'Unable to detect location. Please enter your area manually.', 'warning');
    $('areaInput').focus();
  } finally {
    btn.disabled = false;
    icon.classList.remove('d-none');
    spinner.classList.add('d-none');
  }
});

// Form submit: find providers directly
$('frontBookingForm').addEventListener('submit', e => {
  e.preventDefault();
  findProviders();
});

// Floating toast alert for instant feedback near the hero card
function showLocationToast(msg, type = 'warning') {
  showHubAlert(msg, type);

  let toastContainer = $('globalToastContainer');
  if (!toastContainer) {
    toastContainer = document.createElement('div');
    toastContainer.id = 'globalToastContainer';
    toastContainer.className = 'toast-container position-fixed top-0 end-0 p-3';
    toastContainer.style.zIndex = '9999';
    document.body.appendChild(toastContainer);
  }

  const bgClass = type === 'warning' ? 'text-bg-warning' : (type === 'danger' ? 'text-bg-danger' : 'text-bg-dark');
  const iconClass = type === 'warning' ? 'bi-exclamation-triangle-fill' : (type === 'danger' ? 'bi-exclamation-octagon-fill' : 'bi-info-circle-fill');

  const toastEl = document.createElement('div');
  toastEl.className = `toast align-items-center ${bgClass} border-0 shadow-lg`;
  toastEl.setAttribute('role', 'alert');
  toastEl.setAttribute('aria-live', 'assertive');
  toastEl.setAttribute('aria-atomic', 'true');
  toastEl.innerHTML = `
    <div class="d-flex">
      <div class="toast-body d-flex align-items-center gap-2 small fw-medium">
        <i class="bi ${iconClass} fs-6 flex-shrink-0"></i>
        <div>${esc(msg)}</div>
      </div>
      <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Close"></button>
    </div>
  `;
  toastContainer.appendChild(toastEl);

  const bsToast = new bootstrap.Toast(toastEl, { delay: 4500 });
  bsToast.show();
  toastEl.addEventListener('hidden.bs.toast', () => toastEl.remove());
}

// Initialize Google Maps configuration & Autocomplete on homepage
LSC_MAPS.loadGoogleMaps().then(res => {
  if (res && res.configured && window.google && window.google.maps) {
    LSC_MAPS.setupPlacesAutocomplete($('areaInput'), (placeData) => {
      currentCoords = { lat: placeData.lat, lng: placeData.lng };
      currentFormattedAddress = placeData.formattedAddress;
      const placeName = placeData.area && placeData.city
        ? `${placeData.area}, ${placeData.city}`
        : (placeData.area || placeData.city || placeData.formattedAddress);
      $('areaInput').value = placeName;
      $('areaInput').title = placeData.formattedAddress || placeName;
      if ($('svcSelect').value) {
        findProviders();
      }
    });
  }
}).catch(() => {});

function showHubAlert(msg, type = 'danger') {
  const a = $('hubAlert');
  a.className = `alert alert-${type} mb-3 shadow-sm border-0`;
  a.innerHTML = msg;
  a.classList.remove('d-none');
}

function hideHubAlert() {
  $('hubAlert').classList.add('d-none');
}

// Core Matching function: stays right on this page!
async function findProviders() {
  const serviceId = $('svcSelect').value;
  const area = $('areaInput').value.trim();
  const quick = isQuick();

  if (!serviceId) {
    showHubAlert('<i class="bi bi-info-circle-fill me-1"></i> Please select a service category first.', 'warning');
    $('svcSelect').focus();
    return;
  }

  hideHubAlert();
  $('hubLoading').classList.remove('d-none');

  // Render modern skeleton shimmer cards during fetch
  $('hubProvidersList').innerHTML = [1, 2, 3].map(() => `
    <div class="col-md-6 col-lg-4">
      <div class="card h-100 provider-card p-4">
        <div class="d-flex gap-3 mb-3">
          <div class="skeleton-shimmer" style="width: 52px; height: 52px; border-radius: 14px;"></div>
          <div class="flex-grow-1">
            <div class="skeleton-shimmer mb-2" style="height: 18px; width: 70%;"></div>
            <div class="skeleton-shimmer" style="height: 14px; width: 45%;"></div>
          </div>
        </div>
        <div class="skeleton-shimmer mb-3" style="height: 38px; width: 100%;"></div>
        <div class="skeleton-shimmer mt-auto" style="height: 40px; width: 100%; border-radius: 10px;"></div>
      </div>
    </div>
  `).join('');

  const svcObj = allServices.find(s => String(s.id) === String(serviceId));
  const svcName = svcObj ? svcObj.name : 'Service';

  $('hubSubtitle').innerHTML = `Showing verified providers for <strong class="text-primary">${esc(svcName)}</strong> in <strong>${esc(area || 'your area')}</strong> (${quick ? '<span class="badge bg-danger-subtle text-danger border border-danger-subtle"><i class="bi bi-lightning-fill"></i> Quick Service (~10 min Target)</span>' : '<span class="badge bg-primary-subtle text-primary border border-primary-subtle">Scheduled</span>'})`;

  try {
    const params = new URLSearchParams();
    params.set('serviceId', serviceId);
    if (area) params.set('area', area);
    params.set('type', currentType());
    if (currentCoords) {
      params.set('latitude', currentCoords.lat);
      params.set('longitude', currentCoords.lng);
    }

    const list = await api('/providers/matching?' + params);
    lastProviders = list;
    await Favorites.loadCache();
    $('hubCountBadge').textContent = `${list.length} Verified Provider${list.length === 1 ? '' : 's'}`;

    if (!list.length) {
      $('hubProvidersList').innerHTML = `
        <div class="col-12 text-center py-5 bg-white rounded-4 border shadow-sm">
          <div class="mb-3">
            <span class="d-inline-flex align-items-center justify-content-center bg-light text-muted rounded-circle" style="width: 64px; height: 64px; font-size: 2rem;">
              <i class="bi bi-geo-alt"></i>
            </span>
          </div>
          <h5 class="fw-bold text-dark">No active ${quick ? 'Quick Service' : ''} providers found in this area right now</h5>
          <p class="text-muted small mb-3 max-w-md mx-auto">Try scheduling with Normal Booking ahead of time, or select another nearby area like Lalpur or Morabadi.</p>
          <button type="button" class="btn btn-outline-primary btn-sm" onclick="$('typeNormal').checked=true; updateBookingTypeUI(); findProviders();">
            <i class="bi bi-calendar-event me-1"></i> Switch to Normal Booking
          </button>
        </div>
      `;
    } else {
      $('hubProvidersList').innerHTML = list.map(p => {
        const initial = (p.businessName ? p.businessName.charAt(0) : 'P').toUpperCase();
        return `
        <div class="col-md-6 col-lg-4">
          <div class="card h-100 provider-card">
            <div class="card-body p-4 d-flex flex-column">
              <div class="d-flex align-items-start gap-3 mb-3 position-relative">
                <a href="/provider-profile.html?id=${p.id}" class="text-decoration-none flex-shrink-0" title="View ${esc(p.businessName)}'s Profile">
                  ${p.profileImage ? `
                    <img src="${esc(p.profileImage)}" alt="${esc(p.businessName)}" class="provider-avatar" style="width: 48px; height: 48px; object-fit: cover; border-radius: 14px;">
                  ` : `
                    <div class="provider-avatar">
                      ${esc(initial)}
                    </div>
                  `}
                </a>
                <div class="flex-grow-1 min-w-0" style="padding-right: 76px;">
                  <div class="d-flex align-items-center gap-1 mb-1 flex-wrap">
                    <a href="/provider-profile.html?id=${p.id}" class="hover-primary-link text-decoration-none" title="View ${esc(p.businessName)}'s Profile">
                      <h3 class="h6 mb-0 fw-bold text-dark text-truncate">${esc(p.businessName)}</h3>
                    </a>
                    <span class="verified-badge" title="Verified Specialist">
                      <i class="bi bi-patch-check-fill text-success"></i> Verified
                    </span>
                  </div>
                  <div>
                    ${p.availability 
                      ? '<span class="badge bg-success-subtle text-success border border-success-subtle fw-semibold" style="font-size:0.75rem;"><span class="pulse-dot me-1"></span>Online &amp; Ready</span>' 
                      : '<span class="badge bg-secondary-subtle text-secondary border border-secondary-subtle fw-normal" style="font-size:0.75rem;">Currently Busy</span>'}
                  </div>
                </div>
                <!-- Action Controls: Share and Favorite -->
                <div class="position-absolute top-0 end-0 d-flex align-items-center gap-1">
                  <button type="button" class="btn-share-action" style="width:34px;height:34px;font-size:0.9rem;" onclick='ShareProvider.openShareModal(${JSON.stringify({id: p.id, businessName: p.businessName, formattedAddress: p.formattedAddress, profileImage: p.profileImage})})' title="Share Provider" aria-label="Share provider">
                    <i class="bi bi-share-fill"></i>
                  </button>
                  <button type="button" class="btn-fav-action ${Favorites.isFavorite(p.id) ? 'active' : ''}" style="width:34px;height:34px;font-size:0.95rem;" data-fav-provider="${p.id}" onclick="Favorites.toggle(${p.id}, this)" title="${Favorites.isFavorite(p.id) ? 'Remove from favorites' : 'Add to favorites'}" aria-label="${Favorites.isFavorite(p.id) ? 'Remove from favorites' : 'Add to favorites'}">
                    <i class="bi ${Favorites.isFavorite(p.id) ? 'bi-heart-fill text-danger' : 'bi-heart'}"></i>
                  </button>
                </div>
              </div>

              <div class="mb-3 d-flex align-items-center justify-content-between flex-wrap gap-2">
                <div>${stars(p.rating, p.reviewCount)}</div>
                ${p.distanceKm != null ? `<span class="badge bg-primary-subtle text-primary border border-primary-subtle fw-semibold" style="font-size:0.75rem;"><i class="bi bi-geo-alt-fill me-1"></i>${p.distanceKm} km away</span>` : ''}
              </div>

              <p class="text-muted small mb-3 flex-grow-1" style="display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;">
                ${esc(p.description || 'Certified and background-checked service professional.')}
              </p>

              <div class="mb-3 d-flex flex-wrap gap-1">
                ${p.services.map(s => `<span class="badge bg-light text-dark border fw-normal" style="font-size:0.72rem;">${esc(s.name)}</span>`).join('')}
              </div>

              <div class="pt-3 border-top small text-muted d-flex justify-content-between align-items-center mb-3">
                <span class="text-truncate" style="max-width: 60%;" title="${esc(p.formattedAddress || 'Ranchi')}">
                  <i class="bi bi-geo-alt text-danger me-1"></i>${esc(p.formattedAddress || p.areas.map(a => a.area).join(', ') || 'Ranchi')}
                </span>
                ${p.approxPrice != null ? `<span class="fw-bold text-dark">₹${esc(p.approxPrice)} <span class="fw-normal text-muted" style="font-size:0.75rem;">est.</span></span>` : ''}
              </div>

              <div class="d-flex gap-2 mt-auto">
                <a href="/provider-profile.html?id=${p.id}" class="btn btn-outline-primary btn-sm flex-fill py-2 fw-semibold d-inline-flex align-items-center justify-content-center">
                  <i class="bi bi-person-badge me-1"></i> View Profile
                </a>
                <button class="btn ${quick ? 'btn-danger' : 'btn-primary'} btn-sm flex-fill py-2 fw-semibold btn-book-now" data-id="${p.id}">
                  ${quick ? '<i class="bi bi-lightning-fill me-1"></i> Quick Book' : '<i class="bi bi-calendar2-check-fill me-1"></i> Book Now'}
                </button>
              </div>
            </div>
          </div>
        </div>
      `;
      }).join('');
    }

    // Smooth scroll to providers
    $('bookingHub').scrollIntoView({ behavior: 'smooth', block: 'start' });

  } catch (err) {
    showHubAlert(err.message || 'Error fetching providers. Please try again.');
  } finally {
    $('hubLoading').classList.add('d-none');
  }
}

// Click on Book Now: open modal right on this page!
document.addEventListener('click', e => {
  const btn = e.target.closest('.btn-book-now');
  if (!btn) return;
  const pId = btn.dataset.id;
  selectedProvider = lastProviders.find(p => String(p.id) === String(pId));
  if (!selectedProvider) return;

  openBookingModal(selectedProvider);
});

// =========================================================================
// INTERACTIVE GOOGLE MAPS LOCATION PICKER & ACCURATE SYNCHRONIZATION
// =========================================================================
let bookingMapPicker = null;
let placesAutocompleteObj = null;
let modalGeocodeSeq = 0;

// Single consistent selected-location state
let modalSelectedLocation = {
  latitude: null,
  longitude: null,
  formattedAddress: '',
  locality: '',
  city: '',
  state: '',
  postalCode: '',
  country: 'India'
};

// Synchronize state and UI
function syncBookingLocation(lat, lng, addressData = {}, panMap = false) {
  if (lat == null || lng == null) return;
  const numLat = parseFloat(lat);
  const numLng = parseFloat(lng);

  modalSelectedLocation.latitude = numLat;
  modalSelectedLocation.longitude = numLng;
  if (addressData.formattedAddress) modalSelectedLocation.formattedAddress = addressData.formattedAddress;
  if (addressData.area || addressData.locality) modalSelectedLocation.locality = addressData.area || addressData.locality;
  if (addressData.city) modalSelectedLocation.city = addressData.city;
  if (addressData.state) modalSelectedLocation.state = addressData.state;
  if (addressData.postalCode) modalSelectedLocation.postalCode = addressData.postalCode;
  if (addressData.country) modalSelectedLocation.country = addressData.country;

  // Update hidden booking form fields
  $('modalLat').value = numLat;
  $('modalLng').value = numLng;
  const dispLoc = modalSelectedLocation.locality || modalSelectedLocation.city || 'Selected Location';
  $('modalArea').value = dispLoc;
  $('modalFormattedAddress').value = modalSelectedLocation.formattedAddress || `${dispLoc} (${numLat.toFixed(5)}, ${numLng.toFixed(5)})`;

  // Update Deliver-To Card
  $('displayLocalityName').textContent = dispLoc;
  $('displayFullAddress').textContent = modalSelectedLocation.formattedAddress || `Latitude: ${numLat.toFixed(5)}, Longitude: ${numLng.toFixed(5)}`;
  if ($('pinLocalityBadge')) $('pinLocalityBadge').textContent = dispLoc;

  // Move marker & map if requested
  if (panMap && bookingMapPicker && typeof bookingMapPicker.setLocation === 'function') {
    bookingMapPicker.setLocation(numLat, numLng, true);
  }
}

// Reverse geocode with sequence tracking to prevent async race conditions
async function reverseGeocodeModalLocation(lat, lng) {
  const currentReq = ++modalGeocodeSeq;
  $('displayLocalityName').textContent = 'Detecting exact address...';
  if ($('pinLocalityBadge')) $('pinLocalityBadge').textContent = 'Locating...';

  try {
    const res = await LSC_MAPS.reverseGeocode(lat, lng);
    if (currentReq !== modalGeocodeSeq) {
      // Discard stale geocode response
      return;
    }
    syncBookingLocation(lat, lng, res, false);
  } catch (err) {
    if (currentReq !== modalGeocodeSeq) return;
    console.warn('Geocoding notice:', err);
    syncBookingLocation(lat, lng, {
      formattedAddress: `Pinned coordinates (${lat.toFixed(5)}, ${lng.toFixed(5)})`,
      locality: 'Selected Location'
    }, false);
  }
}

// Initialize or update the interactive Google Map inside the booking modal
function initOrUpdateModalMap(initialLat, initialLng, initialLocality) {
  const lat = initialLat || (currentCoords ? currentCoords.lat : 23.3441);
  const lng = initialLng || (currentCoords ? currentCoords.lng : 85.3096);

  if (!bookingMapPicker) {
    bookingMapPicker = LSC_MAPS.createLocationPickerMap('modalInteractiveMap', {
      initialLat: lat,
      initialLng: lng,
      draggable: true,
      onLocationStart: () => {
        $('displayLocalityName').textContent = 'Locating position...';
        if ($('pinLocalityBadge')) $('pinLocalityBadge').textContent = 'Locating...';
      },
      onLocationChange: (loc) => {
        syncBookingLocation(loc.lat, loc.lng, loc, false);
      }
    });

    // Initialize Google Places Autocomplete on modal search field
    if (!placesAutocompleteObj) {
      placesAutocompleteObj = LSC_MAPS.setupPlacesAutocomplete('mapSearchInput', (placeData) => {
        if (!placeData || placeData.lat == null) return;
        $('btnClearMapSearch').classList.remove('d-none');
        syncBookingLocation(placeData.lat, placeData.lng, placeData, true);
      });
    }
  } else {
    bookingMapPicker.setLocation(lat, lng, true);
    bookingMapPicker.refresh();
  }

  // Set initial coordinates & run reverse geocoding
  syncBookingLocation(lat, lng, { locality: initialLocality }, false);
  reverseGeocodeModalLocation(lat, lng);
}

// "Use my current location" inside map
$('btnModalUseGps').addEventListener('click', async () => {
  const icon = $('modalGpsIcon'), spinner = $('modalGpsSpinner');
  icon.classList.add('d-none'); spinner.classList.remove('d-none');

  try {
    const loc = await LSC_MAPS.getCurrentLocation();
    syncBookingLocation(loc.lat, loc.lng, loc, true);
    if (loc.isApproximate) {
      const msg = `Approximate location detected (~${Math.round(loc.accuracy || 0)}m). Drag marker to exact building.`;
      $('displayFullAddress').textContent = `${loc.formattedAddress} (${msg})`;
    }
  } catch (err) {
    alert(err.message || 'Could not detect your current location. Please search or enter address manually.');
  } finally {
    icon.classList.remove('d-none'); spinner.classList.add('d-none');
  }
});

// Clear Search Input Button
$('btnClearMapSearch').addEventListener('click', () => {
  $('mapSearchInput').value = '';
  $('btnClearMapSearch').classList.add('d-none');
});

$('btnChangeSearch').addEventListener('click', () => {
  $('mapSearchInput').focus();
  $('mapSearchInput').select();
});

// Open Booking Modal Function
function openBookingModal(provider) {
  const quick = isQuick();
  const svcId = $('svcSelect').value;
  const svcObj = allServices.find(s => String(s.id) === String(svcId));
  const svcName = svcObj ? svcObj.name : 'Service';

  $('modalProviderName').textContent = provider.businessName;
  $('modalServiceBadge').textContent = svcName;
  $('modalTypeBadge').textContent = quick ? '⚡ Quick Service (~10 min)' : '📅 Scheduled Booking';
  $('modalTypeBadge').className = quick ? 'badge bg-danger' : 'badge bg-primary';

  // Populate sub-services packages
  const subData = typeof getSubServicesData === 'function' ? getSubServicesData(svcName) : null;
  if (subData && subData.subServices && subData.subServices.length) {
    $('modalSubSelect').innerHTML = subData.subServices.map(sub => 
      `<option value="${sub.name}" data-price="${sub.price}">
        ${esc(sub.name)} (Starts at ₹${sub.price} • ${esc(sub.duration)})
      </option>`
    ).join('');
    $('modalSubSelectRow').classList.remove('d-none');
    $('modalPriceText').textContent = `₹${subData.subServices[0].price} approx.`;
    $('modalSubSelect').onchange = () => {
      const opt = $('modalSubSelect').selectedOptions[0];
      if (opt && opt.dataset.price) {
        $('modalPriceText').textContent = `₹${opt.dataset.price} approx.`;
      }
    };
  } else {
    $('modalSubSelectRow').classList.add('d-none');
    $('modalPriceText').textContent = provider.approxPrice ? `₹${provider.approxPrice} approx.` : 'Standard Rates';
  }

  $('modalScheduleRow').classList.toggle('d-none', quick);
  $('modalDate').required = !quick;
  $('modalTime').required = !quick;
  $('modalQuickNotice').classList.toggle('d-none', !quick);

  // Determine starting coordinates
  let startLat = 23.3441;
  let startLng = 85.3096;
  let startLocality = $('areaInput').value.trim() || 'Ranchi';

  if (currentCoords) {
    startLat = currentCoords.lat;
    startLng = currentCoords.lng;
  } else if (startLocality.toLowerCase().includes('morabadi')) {
    startLat = 23.3908; startLng = 85.3278;
  } else if (startLocality.toLowerCase().includes('kokar')) {
    startLat = 23.3712; startLng = 85.3512;
  } else if (startLocality.toLowerCase().includes('kanke')) {
    startLat = 23.4150; startLng = 85.3220;
  } else if (startLocality.toLowerCase().includes('lalpur')) {
    startLat = 23.3685; startLng = 85.3340;
  }

  // Pre-fill door details if customer has saved address
  const u = Auth.get();
  if (u && u.token && u.role === 'CUSTOMER') {
    $('modalLoggedInUser').classList.remove('d-none');
    $('modalGuestAuth').classList.add('d-none');
    $('modalUserName').textContent = u.name || 'Customer';
    $('modalUserEmail').textContent = u.name ? '' : 'Account active';
  } else {
    $('modalLoggedInUser').classList.add('d-none');
    $('modalGuestAuth').classList.remove('d-none');
  }

  $('modalMsg').className = 'alert d-none';
  $('btnModalConfirm').disabled = false;
  $('modalSpinner').classList.add('d-none');
  $('modalConfirmIcon').classList.remove('d-none');

  const modalEl = $('instantBookingModal');
  const modal = new bootstrap.Modal(modalEl);
  modal.show();

  // Resize and center Google Map after modal animation finishes
  const onModalShown = () => {
    initOrUpdateModalMap(startLat, startLng, startLocality);
    setTimeout(() => {
      if (bookingMapPicker && typeof bookingMapPicker.refresh === 'function') {
        bookingMapPicker.refresh();
      }
    }, 150);
    modalEl.removeEventListener('shown.bs.modal', onModalShown);
  };
  modalEl.addEventListener('shown.bs.modal', onModalShown);
}

// Modal submission
$('instantBookingForm').addEventListener('submit', async e => {
  e.preventDefault();
  const quick = isQuick();
  const svcId = $('svcSelect').value;
  let user = Auth.get();

  $('modalMsg').className = 'alert d-none';
  $('btnModalConfirm').disabled = true;
  $('modalSpinner').classList.remove('d-none');
  $('modalConfirmIcon').classList.add('d-none');

  try {
    // If not logged in as Customer, authenticate first right inside the modal
    if (!user || !user.token || user.role !== 'CUSTOMER') {
      const activeTab = document.querySelector('#authTabs .nav-link.active').id;
      if (activeTab === 'tab-login') {
        const email = $('authLoginEmail').value.trim();
        const password = $('authLoginPassword').value;
        if (!email || !password) throw new Error('Please enter your email and password to book.');
        const authRes = await api('/auth/login', { method: 'POST', body: { email, password } });
        Auth.save(authRes);
        user = Auth.get();
        if (user.role !== 'CUSTOMER') {
          throw new Error('This account is registered as ' + user.role + '. Only customers can book services.');
        }
      } else {
        const name = $('authRegName').value.trim();
        const phone = $('authRegPhone').value.trim();
        const email = $('authRegEmail').value.trim();
        const password = $('authRegPassword').value;
        if (!name || !email || !password) throw new Error('Please fill in your name, email, and password.');
        const authRes = await api('/auth/register', {
          method: 'POST',
          body: { name, phone: phone || '9876543210', email, password, role: 'CUSTOMER' }
        });
        Auth.save(authRes);
        user = Auth.get();
      }
      navbar(); // update top navbar
    }

    const latVal = $('modalLat').value ? parseFloat($('modalLat').value) : null;
    const lngVal = $('modalLng').value ? parseFloat($('modalLng').value) : null;
    const formattedAddr = $('modalFormattedAddress').value || $('modalArea').value || 'Ranchi';
    const specificDoorDetails = $('modalAddress').value.trim();
    const finalAddress = specificDoorDetails ? `${specificDoorDetails}, ${formattedAddr}` : formattedAddr;

    const chosenSub = $('modalSubSelect') && $('modalSubSelect').value ? $('modalSubSelect').value : '';
    const rawNotes = $('modalNotes').value.trim();
    const combinedNotes = chosenSub ? (rawNotes ? `[Sub-service: ${chosenSub}] ${rawNotes}` : `[Sub-service: ${chosenSub}]`) : (rawNotes || null);

    // Submit booking with exact pinned latitude, longitude, and formatted address
    const booking = await api('/bookings', {
      method: 'POST',
      body: {
        providerId: selectedProvider.id,
        serviceId: Number(svcId),
        bookingType: currentType(),
        bookingDate: quick ? null : $('modalDate').value || null,
        bookingTime: quick ? null : ($('modalTime').value ? $('modalTime').value + ':00' : null),
        address: finalAddress,
        area: $('modalArea').value.trim() || 'Ranchi',
        notes: combinedNotes,
        formattedAddress: formattedAddr,
        latitude: latVal,
        longitude: lngVal
      }
    });

    // Close modal and redirect directly to booking status
    bootstrap.Modal.getInstance($('instantBookingModal')).hide();
    location.href = '/booking-status.html?id=' + booking.id;

  } catch (err) {
    $('modalMsg').className = 'alert alert-danger';
    $('modalMsg').textContent = err.message || 'Booking failed. Please try again.';
    $('modalMsg').classList.remove('d-none');
    $('btnModalConfirm').disabled = false;
    $('modalSpinner').classList.add('d-none');
    $('modalConfirmIcon').classList.remove('d-none');
  }
});

// Load services, reviews, and featured providers
(async () => {
  try {
    const [svcs, provs, revs] = await Promise.all([
      api('/services').catch(() => []),
      api('/providers').catch(() => []),
      api('/reviews/featured').catch(() => [])
    ]);

    // Safeguard: Ensure unique services by normalized name
    const seenNames = new Set();
    const uniqueSvcs = [];
    for (const s of (svcs || [])) {
      const norm = (s.name || '').trim().toLowerCase();
      if (norm && !seenNames.has(norm)) {
        seenNames.add(norm);
        uniqueSvcs.push(s);
      }
    }

    allServices = uniqueSvcs;

    // Populate service dropdown
    $('svcSelect').innerHTML = '<option value="">Choose a service category...</option>' +
      uniqueSvcs.map(s => `<option value="${s.id}">${esc(s.name)}</option>`).join('');

    // Popular services cards: accessible click & keyboard interaction selects category
    $('pop').innerHTML = uniqueSvcs.map(s => `
      <div class="col-6 col-md-4 col-lg-3 col-xl-2">
        <div class="service-tile-card shadow-xs" data-service-id="${s.id}" role="button" tabindex="0" aria-label="Book ${esc(s.name)} service">
          <div class="icon-container">
            <i class="bi ${esc(s.icon || 'bi-tools')}"></i>
          </div>
          <div class="small fw-bold text-dark mb-1 text-truncate">${esc(s.name)}</div>
          <div class="text-muted small flex-grow-1 mb-2" style="font-size: 0.76rem; display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden; line-height: 1.4;">
            ${esc(s.description || 'Verified local service specialists.')}
          </div>
          <div class="pt-2 border-top d-flex justify-content-between align-items-center mt-auto">
            <span class="badge bg-primary-subtle text-primary border border-primary-subtle" style="font-size: 0.68rem; font-weight: 700;">Select &amp; Book</span>
            <i class="bi bi-arrow-right text-primary" style="font-size: 0.75rem;"></i>
          </div>
        </div>
      </div>
    `).join('') || '<div class="text-muted small">No services available.</div>';

    // Click and keyboard handlers on popular service cards
    document.querySelectorAll('.service-tile-card').forEach(card => {
      const selectCard = () => {
        const sid = card.dataset.serviceId;
        $('svcSelect').value = sid;
        document.querySelectorAll('.service-tile-card').forEach(c => c.classList.remove('selected-svc'));
        card.classList.add('selected-svc');
        if (!$('areaInput').value.trim()) {
          $('areaInput').value = 'Morabadi, Ranchi';
          document.querySelectorAll('.area-chip').forEach(c => {
            c.classList.toggle('active', c.dataset.area && c.dataset.area.includes('Morabadi'));
          });
        }
        findProviders();
      };
      card.addEventListener('click', selectCard);
      card.addEventListener('keydown', e => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          selectCard();
        }
      });
    });

    // Check URL parameters (e.g. ?serviceId=...&area=...&type=...)
    const q = new URLSearchParams(location.search);
    if (q.get('serviceId')) {
      $('svcSelect').value = q.get('serviceId');
    }
    if (q.get('area')) {
      $('areaInput').value = q.get('area');
      document.querySelectorAll('.area-chip').forEach(c => {
        c.classList.toggle('active', c.dataset.area && c.dataset.area.includes(q.get('area')));
      });
    }
    if (q.get('type') === 'QUICK_SERVICE') {
      $('typeQuick').checked = true;
      updateBookingTypeUI();
    }
    if (q.get('serviceId') || q.get('area')) {
      findProviders();
    }

    // Featured providers
    if (provs && provs.length) {
      $('featured').innerHTML = provs.slice(0, 3).map(p => `
        <div class="col-md-4">
          <div class="card h-100 border-0 shadow-sm provider-card">
            <div class="card-body p-4 d-flex flex-column">
              <div class="d-flex align-items-start gap-3 mb-2 position-relative">
                ${p.profileImage ? `
                  <img src="${esc(p.profileImage)}" alt="${esc(p.businessName)}" class="provider-avatar rounded-3" style="width: 48px; height: 48px; object-fit: cover;">
                ` : ''}
                <div class="flex-grow-1 min-w-0" style="padding-right: 76px;">
                  <h3 class="h5 mb-1 fw-bold text-truncate">${esc(p.businessName)} <i class="bi bi-patch-check-fill text-primary" title="Verified"></i></h3>
                  <div>${stars(p.rating, p.reviewCount)}</div>
                </div>
                <div class="position-absolute top-0 end-0 d-flex align-items-center gap-1">
                  <button type="button" class="btn-share-action" style="width:34px;height:34px;font-size:0.9rem;" onclick='ShareProvider.openShareModal(${JSON.stringify({id: p.id, businessName: p.businessName, formattedAddress: p.formattedAddress, profileImage: p.profileImage})})' title="Share Provider" aria-label="Share provider">
                    <i class="bi bi-share-fill"></i>
                  </button>
                  <button type="button" class="btn-fav-action ${Favorites.isFavorite(p.id) ? 'active' : ''}" style="width:34px;height:34px;font-size:0.95rem;" data-fav-provider="${p.id}" onclick="Favorites.toggle(${p.id}, this)" title="${Favorites.isFavorite(p.id) ? 'Remove from favorites' : 'Add to favorites'}" aria-label="${Favorites.isFavorite(p.id) ? 'Remove from favorites' : 'Add to favorites'}">
                    <i class="bi ${Favorites.isFavorite(p.id) ? 'bi-heart-fill text-danger' : 'bi-heart'}"></i>
                  </button>
                </div>
              </div>
              <div class="mb-2 d-flex flex-wrap gap-1">${p.services.map(s => `<span class="badge text-bg-light border">${esc(s.name)}</span>`).join(' ')}</div>
              <p class="small text-muted flex-grow-1">${esc(p.description || 'Verified local service specialist.')}</p>
              <div class="d-flex gap-2 mt-auto">
                <a href="/provider-profile.html?id=${p.id}" class="btn btn-outline-secondary btn-sm flex-fill fw-semibold py-2">
                  <i class="bi bi-person-badge me-1"></i> Profile
                </a>
                <button class="btn btn-primary btn-sm flex-fill fw-semibold py-2 btn-book-now" data-id="${p.id}">
                  <i class="bi bi-calendar-check me-1"></i> Book
                </button>
              </div>
            </div>
          </div>
        </div>
      `).join('');
      $('featuredSec').classList.remove('d-none');
    }

    // Customer reviews (Display real reviews from backend)
    if (revs && revs.length) {
      $('reviews').innerHTML = revs.slice(0, 4).map(r => {
        const ratingVal = r.rating || 5;
        const initial = (r.customerName ? r.customerName.charAt(0) : 'C').toUpperCase();
        return `
        <div class="col-md-6 col-lg-3">
          <div class="review-card">
            <!-- Card Header: Rating on left, Verified badge on right -->
            <div class="review-card-header">
              <div class="review-card-rating">
                ${stars(ratingVal)}
                <span class="rating-num">${Number(ratingVal).toFixed(1)}</span>
              </div>
              <span class="review-card-badge">
                <i class="bi bi-patch-check-fill"></i>
                <span>Verified Booking</span>
              </span>
            </div>

            <!-- Card Body: Comfortable spacing, readable line height -->
            <div class="review-card-body">
              <p class="review-card-text">
                "${esc(r.review)}"
              </p>
            </div>

            <!-- Card Footer: Subtle top border, avatar, meta info -->
            <div class="review-card-footer">
              <div class="review-avatar-box">
                ${esc(initial)}
              </div>
              <div class="review-meta-info">
                <div class="review-author-name">${esc(r.customerName || 'Verified Resident')}</div>
                <div class="review-provider-sub">booked ${esc(r.providerBusinessName || r.businessName || 'Specialist')}</div>
              </div>
            </div>
          </div>
        </div>
      `;
      }).join('');
      $('reviewsSec').classList.remove('d-none');
    } else {
      $('reviews').innerHTML = `
        <div class="col-12 text-center py-4 text-muted bg-white border rounded-4">
          <i class="bi bi-chat-heart text-secondary fs-2 mb-2 d-block"></i>
          <p class="small mb-0">Verified customer feedback and service reviews will appear here as bookings complete.</p>
        </div>
      `;
      $('reviewsSec').classList.remove('d-none');
    }

  } catch (err) {
    console.error('Initialization error:', err);
  }
})();
