var API_BASE = 'https://local-service-connect-uc2u.onrender.com/api';

var Auth = (typeof window !== 'undefined' && window.Auth) ? window.Auth : {
  save(d) {
    if (d.token) localStorage.setItem('lsc_token', d.token);
    if (d.role) localStorage.setItem('lsc_role', d.role);
    if (d.name) localStorage.setItem('lsc_name', d.name);
    if (d.id) localStorage.setItem('lsc_id', d.id);
  },
  get() {
    return {
      token: localStorage.getItem('lsc_token'),
      role: localStorage.getItem('lsc_role'),
      name: localStorage.getItem('lsc_name'),
      id: localStorage.getItem('lsc_id')
    };
  },
  token() {
    return localStorage.getItem('lsc_token');
  },
  getToken() {
    return this.token();
  },
  isTokenExpired() {
    const t = this.token();
    if (!t) return true;
    try {
      const parts = t.split('.');
      if (parts.length !== 3) return false;
      const payload = JSON.parse(decodeURIComponent(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')).split('').map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)).join('')));
      if (payload.exp && Date.now() >= payload.exp * 1000) {
        return true;
      }
      return false;
    } catch {
      return false;
    }
  },
  user() {
    return this.get();
  },
  home(role) {
    if (role === 'ADMIN') return '/admin/dashboard.html';
    if (role === 'PROVIDER') return '/provider/home';
    if (role === 'CUSTOMER') return '/my-account.html';
    return '/';
  },
  logout() {
    localStorage.removeItem('lsc_token');
    localStorage.removeItem('lsc_role');
    localStorage.removeItem('lsc_name');
    localStorage.removeItem('lsc_id');
    location.href = '/login.html';
  },
  require(role) {
    const u = this.get();
    if (!u.token || this.isTokenExpired()) {
      if (u.token && this.isTokenExpired()) {
        localStorage.removeItem('lsc_token');
        localStorage.removeItem('lsc_role');
        localStorage.removeItem('lsc_name');
        localStorage.removeItem('lsc_id');
      }
      const redirect = encodeURIComponent(location.pathname + location.search);
      location.href = (role === 'ADMIN' ? '/admin/login.html' : '/login.html') + '?redirect=' + redirect + (u.token ? '&expired=1' : '');
      return null;
    }
    if (role && u.role !== role) {
      if (role === 'ADMIN') {
        const redirect = encodeURIComponent(location.pathname + location.search);
        location.href = '/admin/login.html?redirect=' + redirect + '&mismatch=1';
        return null;
      }
      location.href = this.home(u.role);
      return null;
    }
    return u;
  },
  requireAuth(allowedRoles = []) {
    const u = this.get();
    if (!u.token || this.isTokenExpired()) {
      if (u.token && this.isTokenExpired()) {
        localStorage.removeItem('lsc_token');
        localStorage.removeItem('lsc_role');
        localStorage.removeItem('lsc_name');
        localStorage.removeItem('lsc_id');
      }
      location.href = allowedRoles.includes('ADMIN') ? '/admin/login.html?expired=1' : '/login.html?expired=1';
      return null;
    }
    if (allowedRoles.length > 0 && !allowedRoles.includes(u.role)) {
      location.href = this.home(u.role);
      return null;
    }
    return u;
  },
  isProvider() {
    const u = this.get();
    return !!(u && u.token && u.role === 'PROVIDER' && !this.isTokenExpired());
  },
  isCustomer() {
    const u = this.get();
    return !!(u && u.token && u.role === 'CUSTOMER' && !this.isTokenExpired());
  },
  isAdmin() {
    const u = this.get();
    return !!(u && u.token && u.role === 'ADMIN' && !this.isTokenExpired());
  }
};

// Safe global getter for isProvider to prevent ReferenceError in any legacy scripts
if (typeof window !== 'undefined' && typeof window.isProvider === 'undefined') {
  try {
    Object.defineProperty(window, 'isProvider', {
      get() {
        return typeof Auth !== 'undefined' && typeof Auth.isProvider === 'function' ? Auth.isProvider() : false;
      },
      configurable: true
    });
  } catch {}
}

async function api(path, options = {}) {
  const url = path.startsWith('http') ? path : (API_BASE + (path.startsWith('/') ? path : '/' + path));
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {})
  };

  const token = Auth.token();
  if (token && !headers['Authorization']) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  if (options.body instanceof FormData) {
    delete headers['Content-Type'];
  }

  // Sensible default timeout (12s) to prevent hanging promises
  let timeoutId = null;
  let signal = options.signal;
  if (!signal && typeof AbortController !== 'undefined') {
    const controller = new AbortController();
    signal = controller.signal;
    const timeoutMs = options.timeout || 12000;
    timeoutId = setTimeout(() => {
      controller.abort();
    }, timeoutMs);
  }

  const config = {
    ...options,
    headers,
    signal
  };

  if (config.body && typeof config.body === 'object' && !(config.body instanceof FormData)) {
    config.body = JSON.stringify(config.body);
  }

  let res;
  try {
    res = await fetch(url, config);
  } catch (err) {
    if (err.name === 'AbortError' || (signal && signal.aborted)) {
      throw new Error('Request timed out. Please check your network connection.');
    }
    throw new Error(err.message && !err.message.includes('fetch') ? err.message : ('Unable to connect to server. Please ensure the backend is running at ' + API_BASE));
  } finally {
    if (timeoutId) clearTimeout(timeoutId);
  }

  let data = null;
  const contentType = res.headers.get('content-type');
  if (contentType && contentType.includes('application/json')) {
    data = await res.json().catch(() => null);
  } else {
    data = await res.text().catch(() => null);
  }

  if (!res.ok) {
    if (res.status === 401) {
      const msg = (data && typeof data === 'object' && data.message)
        ? data.message
        : 'Your session has expired. Please log in again.';
      throw new Error(msg);
    }
    let errorMsg = 'Request failed';
    if (data && typeof data === 'object') {
      if (Array.isArray(data.errors) && data.errors.length) {
        errorMsg = data.errors.map(e => e.defaultMessage || e.message || (typeof e === 'string' ? e : '')).filter(Boolean).join(', ');
      } else if (data.message) {
        errorMsg = data.message;
      } else if (data.error) {
        errorMsg = data.error;
      }
    } else if (typeof data === 'string' && data.length) {
      errorMsg = data;
    } else if (res.statusText) {
      errorMsg = res.statusText;
    }
    throw new Error(errorMsg);
  }

  return data;
}

function showError(el, message) {
  if (!el) return;
  el.textContent = message || 'Something went wrong. Please try again.';
  el.classList.remove('d-none');
}

function esc(s) {
  if (s === null || s === undefined) return '';
  return String(s).replace(/[&<>"']/g, c => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
  }[c]));
}

function stars(rating, count) {
  const r = Math.round(Number(rating || 0));
  let h = '<span class="text-warning">';
  for (let i = 1; i <= 5; i++) {
    h += i <= r ? '<i class="bi bi-star-fill"></i>' : '<i class="bi bi-star"></i>';
  }
  h += `</span> <span class="small text-muted ms-1">(${rating || 0.0}${count !== undefined ? ' &bull; ' + count + ' reviews' : ''})</span>`;
  return h;
}

function starText(rating) {
  const r = Math.round(Number(rating || 0));
  let h = '';
  for (let i = 1; i <= 5; i++) {
    h += i <= r ? '<i class="bi bi-star-fill"></i>' : '<i class="bi bi-star"></i>';
  }
  return h;
}

var STATUS = (typeof window !== 'undefined' && window.STATUS) ? window.STATUS : {
  PENDING: ['warning', 'Request Sent (Pending)'],
  ACCEPTED: ['primary', 'Accepted by Provider'],
  ON_THE_WAY: ['info', 'Provider On The Way'],
  SERVICE_STARTED: ['info', 'Service in Progress'],
  AWAITING_COMPLETION: ['warning', 'Awaiting Customer Confirmation'],
  COMPLETED: ['success', 'Completed'],
  REJECTED: ['danger', 'Declined by Provider'],
  CANCELLED: ['secondary', 'Cancelled']
};

function statusBadge(s) {
  const info = STATUS[s] || ['secondary', s];
  return `<span class="badge text-bg-${info[0]}">${esc(info[1])}</span>`;
}

function whenText(b) {
  if (b.bookingType === 'QUICK_SERVICE') {
    return 'Quick Service (within ~10 mins)';
  }
  if (b.bookingDate && b.bookingTime) {
    const timeStr = typeof b.bookingTime === 'string' ? b.bookingTime.slice(0, 5) : b.bookingTime;
    return `${b.bookingDate} at ${timeStr}`;
  }
  return 'Scheduled';
}

function formatTimeAgo(isoString) {
  if (!isoString) return '';
  const date = new Date(isoString);
  const now = new Date();
  const diffSec = Math.floor((now - date) / 1000);
  if (isNaN(diffSec) || diffSec < 0) return 'Just now';
  if (diffSec < 60) return 'Just now';
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) return `${diffMin}m ago`;
  const diffHr = Math.floor(diffMin / 60);
  if (diffHr < 24) return `${diffHr}h ago`;
  const diffDays = Math.floor(diffHr / 24);
  if (diffDays < 7) return `${diffDays}d ago`;
  return date.toLocaleDateString();
}

window.NotificationSystem = {
  _eventSource: null,
  _reconnectTimeout: null,
  _pollInterval: null,
  _notifications: [],
  _unreadCount: 0,
  _hasLoaded: false,

  init(user) {
    if (!user || !user.token) return;
    this.refresh();
    this.connectStream(user.token);
    this.startPollingFallback();
  },

  async refresh() {
    try {
      const listEl = document.getElementById('navNotifList');
      if (listEl && !this._hasLoaded) {
        listEl.innerHTML = `
          <div class="p-3 text-center text-muted">
            <span class="spinner-border spinner-border-sm text-primary me-2"></span>
            <span class="small">Loading notifications...</span>
          </div>
        `;
      }
      const data = await api('/notifications');
      this._notifications = Array.isArray(data) ? data : [];
      this._unreadCount = this._notifications.filter(n => !n.read).length;
      this._hasLoaded = true;
      this.updateUI();
    } catch (err) {
      const listEl = document.getElementById('navNotifList');
      if (listEl && !this._hasLoaded) {
        listEl.innerHTML = `
          <div class="p-3 text-center text-muted">
            <i class="bi bi-exclamation-circle text-warning fs-5 mb-1 d-block"></i>
            <div class="small fw-semibold text-dark">Could not load notifications</div>
            <button type="button" class="btn btn-link btn-sm p-0 small mt-1" onclick="NotificationSystem.refresh()">Retry</button>
          </div>
        `;
      }
    }
  },

  connectStream(token) {
    if (this._eventSource) {
      try { this._eventSource.close(); } catch {}
    }
    if (this._reconnectTimeout) {
      clearTimeout(this._reconnectTimeout);
      this._reconnectTimeout = null;
    }

    try {
      const url = '/api/notifications/stream?token=' + encodeURIComponent(token);
      const es = new EventSource(url);
      this._eventSource = es;

      es.addEventListener('init', (e) => {
        try {
          const d = JSON.parse(e.data);
          if (typeof d.unreadCount === 'number') {
            this._unreadCount = d.unreadCount;
            this.updateBadge();
          }
        } catch {}
      });

      es.addEventListener('pending_booking', (e) => {
        try {
          const pending = JSON.parse(e.data);
          window.dispatchEvent(new CustomEvent('lsc:pending-booking-notification', { detail: pending }));
        } catch {}
      });

      es.addEventListener('unread_count', (e) => {
        try {
          const d = JSON.parse(e.data);
          if (typeof d.unreadCount === 'number') {
            this._unreadCount = d.unreadCount;
            this.updateBadge();
          }
        } catch {}
      });

      es.addEventListener('new_booking', (e) => {
        try {
          const notif = JSON.parse(e.data);
          if (!this._notifications.some(n => n.id == notif.id)) {
            this._notifications.unshift(notif);
          }
          this._unreadCount++;
          this.updateUI();
          window.dispatchEvent(new CustomEvent('lsc:new-booking-notification', { detail: notif }));
        } catch {}
      });

      es.addEventListener('admin_notification', (e) => {
        try {
          const notif = JSON.parse(e.data);
          if (!this._notifications.some(n => n.id == notif.id)) {
            this._notifications.unshift(notif);
          }
          this._unreadCount++;
          this.updateUI();
          window.dispatchEvent(new CustomEvent('lsc:admin-notification', { detail: notif }));
        } catch {}
      });

      es.addEventListener('new_notification', (e) => {
        try {
          const notif = JSON.parse(e.data);
          if (!this._notifications.some(n => n.id == notif.id)) {
            this._notifications.unshift(notif);
          }
          this._unreadCount++;
          this.updateUI();
        } catch {}
      });

      es.addEventListener('booking_updated', (e) => {
        try {
          const eventData = JSON.parse(e.data);
          this._notifications.forEach(n => {
            if (n.bookingId == eventData.bookingId) {
              n.bookingStatus = eventData.status;
            }
          });
          this.updateUI();
          window.dispatchEvent(new CustomEvent('lsc:booking-updated', { detail: eventData }));
        } catch {}
      });

      es.onerror = () => {
        try { es.close(); } catch {}
        this._eventSource = null;
        this._reconnectTimeout = setTimeout(() => {
          const u = Auth.get();
          if (u && u.token && !Auth.isTokenExpired()) {
            this.connectStream(u.token);
          }
        }, 5000);
      };
    } catch (err) {
      console.warn('Notification stream init failed, using polling fallback:', err);
    }
  },

  startPollingFallback() {
    if (this._pollInterval) clearInterval(this._pollInterval);
    this._pollInterval = setInterval(async () => {
      const u = Auth.get();
      if (!u || !u.token || Auth.isTokenExpired()) {
        clearInterval(this._pollInterval);
        return;
      }
      try {
        const res = await api('/notifications/unread-count');
        if (res && typeof res.unreadCount === 'number') {
          if (res.unreadCount !== this._unreadCount) {
            this._unreadCount = res.unreadCount;
            this.updateBadge();
            this.refresh();
          }
        }
      } catch {}
    }, 25000);
  },

  updateBadge() {
    const badge = document.getElementById('navNotifBadge');
    const headerCount = document.getElementById('navNotifUnreadCount');
    const markReadBtn = document.getElementById('btnMarkAllNotifsRead');

    if (badge) {
      badge.textContent = this._unreadCount > 9 ? '9+' : this._unreadCount;
      badge.classList.toggle('d-none', this._unreadCount <= 0);
    }
    if (headerCount) {
      headerCount.textContent = `${this._unreadCount} new`;
      headerCount.classList.toggle('d-none', this._unreadCount <= 0);
    }
    if (markReadBtn) {
      markReadBtn.classList.toggle('d-none', this._unreadCount <= 0);
    }
  },

  updateUI() {
    this.updateBadge();
    const listEl = document.getElementById('navNotifList');
    if (!listEl) return;

    if (!this._notifications || this._notifications.length === 0) {
      listEl.innerHTML = `
        <div class="p-4 text-center text-muted">
          <div class="mb-2" style="font-size: 1.75rem; color: #a5b4fc;"><i class="bi bi-bell-slash"></i></div>
          <div class="fw-semibold text-dark small">No notifications yet</div>
          <p class="small text-muted mb-0" style="font-size: 0.75rem;">You are all caught up!</p>
        </div>
      `;
      return;
    }

    const u = Auth.get();
    const isProvider = u && u.role === 'PROVIDER';
    const isCustomer = u && u.role === 'CUSTOMER';
    const isAdmin = u && u.role === 'ADMIN';

    listEl.innerHTML = this._notifications.slice(0, 8).map(n => {
      const isUnread = !n.read;
      const isQuick = n.bookingType === 'QUICK_SERVICE' || (n.type && n.type.startsWith('QUICK_SERVICE'));
      let typeIcon = '<i class="bi bi-bell-fill text-primary me-1"></i>';
      if (isQuick) {
        typeIcon = '<i class="bi bi-lightning-charge-fill text-danger me-1"></i>';
      } else if (n.type === 'PROVIDER_VERIFICATION_SUBMITTED') {
        typeIcon = '<i class="bi bi-patch-check-fill text-warning me-1"></i>';
      } else if (n.type === 'NEW_PROVIDER_REGISTRATION') {
        typeIcon = '<i class="bi bi-person-plus-fill text-info me-1"></i>';
      } else if (n.type === 'CUSTOMER_COMPLAINT' || n.type === 'REVIEW_ALERT') {
        typeIcon = '<i class="bi bi-exclamation-triangle-fill text-danger me-1"></i>';
      } else if (n.type === 'BOOKING_CANCELLED') {
        typeIcon = '<i class="bi bi-x-circle-fill text-secondary me-1"></i>';
      } else if (n.type === 'NEW_BOOKING' || n.type === 'NEW_BOOKING_REQUEST') {
        typeIcon = '<i class="bi bi-calendar-plus-fill text-primary me-1"></i>';
      }
      
      const timeStr = n.createdAt ? formatTimeAgo(n.createdAt) : '';
      const statusBadge = n.bookingStatus ? `
        <span class="badge ${n.bookingStatus === 'PENDING' ? 'bg-warning-subtle text-warning-emphasis' : (n.bookingStatus === 'ACCEPTED' ? 'bg-success-subtle text-success' : (n.bookingStatus === 'CANCELLED' ? 'bg-secondary-subtle text-secondary' : 'bg-light text-muted'))} border rounded-pill px-1.5 py-0.5" style="font-size: 0.65rem;">
          ${esc(n.bookingStatus)}
        </span>
      ` : (n.priority && n.priority !== 'NORMAL' ? `
        <span class="badge ${n.priority === 'URGENT' ? 'bg-danger text-white' : 'bg-warning text-dark'} rounded-pill px-1.5 py-0.5" style="font-size: 0.65rem;">
          ${esc(n.priority)}
        </span>
      ` : '');

      return `
        <div class="notif-dropdown-item ${isUnread ? 'unread' : ''}" data-id="${n.id}" data-booking-id="${n.bookingId || ''}" style="cursor: pointer;">
          <div class="d-flex justify-content-between align-items-baseline mb-0.5">
            <span class="fw-bold small text-dark text-truncate me-2">${typeIcon}${esc(n.title || 'Notification')}</span>
            <span class="text-muted" style="font-size: 0.7rem; white-space: nowrap;">${esc(timeStr)}</span>
          </div>
          <p class="small text-muted mb-1 text-truncate" style="font-size: 0.78rem;">${esc(n.message || '')}</p>
          <div class="d-flex align-items-center justify-content-between" style="font-size: 0.7rem;">
            ${n.bookingId ? `<span class="badge bg-light text-dark border">#BK-${n.bookingId}</span>` : '<span></span>'}
            ${statusBadge}
          </div>
        </div>
      `;
    }).join('');

    // Wire click handlers on notification items
    listEl.querySelectorAll('.notif-dropdown-item').forEach(el => {
      el.onclick = async (e) => {
        e.preventDefault();
        const notifId = el.getAttribute('data-id');
        const bookingId = el.getAttribute('data-booking-id');

        if (notifId) {
          try {
            await api(`/notifications/${notifId}/read`, { method: 'PUT' });
            el.classList.remove('unread');
            const target = this._notifications.find(item => item.id == notifId);
            if (target) target.read = true;
            this._unreadCount = Math.max(0, this._unreadCount - 1);
            this.updateBadge();
          } catch {}
        }

        // Close dropdown
        const menu = document.getElementById('navNotifMenu');
        if (menu) menu.classList.remove('show');

        // Navigate / open details
        const notifItem = this._notifications.find(item => item.id == notifId);
        if (notifItem && notifItem.targetUrl) {
          window.location.href = notifItem.targetUrl;
          return;
        }

        if (isAdmin) {
          if (bookingId) {
            window.location.href = `/admin/bookings.html?bookingId=${bookingId}`;
          } else {
            window.location.href = '/admin/notifications.html';
          }
          return;
        }

        if (bookingId) {
          if (isProvider) {
            const oppCard = document.getElementById(`oppCard-${bookingId}`);
            if (oppCard) {
              oppCard.scrollIntoView({ behavior: 'smooth', block: 'center' });
              oppCard.style.outline = '3px solid #5b43f5';
              setTimeout(() => { oppCard.style.outline = ''; }, 3000);
              return;
            }
            window.location.href = `/provider-dashboard.html?tab=requests&bookingId=${bookingId}`;
          } else if (isCustomer) {
            window.location.href = `/booking-status.html?id=${bookingId}`;
          }
        }
      };
    });
  },

  async markAllRead() {
    try {
      await api('/notifications/read-all', { method: 'PUT' });
      this._notifications.forEach(n => { n.read = true; });
      this._unreadCount = 0;
      this.updateUI();
    } catch (err) {
      console.error('Failed to mark all notifications as read:', err);
    }
  }
};

function navbar() {
  const navEl = document.getElementById('nav');
  if (!navEl) return;
  const u = Auth.get();
  const isLoggedIn = !!(u && u.token);
  const isCustomer = isLoggedIn && u.role === 'CUSTOMER';
  const isProvider = isLoggedIn && u.role === 'PROVIDER';
  const isAdmin = isLoggedIn && u.role === 'ADMIN';

  // Extract user email safely from JWT or localStorage
  let userEmail = localStorage.getItem('lsc_email') || '';
  if (!userEmail && u.token) {
    try {
      const part = u.token.split('.')[1];
      if (part) {
        const decoded = JSON.parse(decodeURIComponent(atob(part.replace(/-/g, '+').replace(/_/g, '/')).split('').map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)).join('')));
        userEmail = decoded.sub || '';
      }
    } catch {
      userEmail = '';
    }
  }

  // Extract user avatar or fallback to initial
  let avatarPhoto = null;
  if (u && u.id) {
    avatarPhoto = localStorage.getItem('lsc_avatar_' + u.id) || localStorage.getItem('lsc_profile_photo');
  }
  const hasValidPhoto = avatarPhoto && !avatarPhoto.includes('<svg></svg>') && (avatarPhoto.startsWith('data:image') || avatarPhoto.startsWith('http'));
  const initial = (u.name ? u.name.trim().charAt(0) : (u.role ? u.role.charAt(0) : 'U')).toUpperCase();
  const avatarHtml = hasValidPhoto
    ? `<img src="${esc(avatarPhoto)}" class="user-avatar-img" alt="${esc(u.name || 'User')}">`
    : `<span>${esc(initial)}</span>`;

  // Cached availability status for provider controls
  const isOnlineCached = isProvider && u && u.id ? (localStorage.getItem('lsc_provider_avail_' + u.id) === 'true') : false;

  // Fetch real notifications from localStorage
  let notifs = [];
  if (u && u.id) {
    try {
      notifs = JSON.parse(localStorage.getItem('lsc_notifications_' + u.id) || '[]');
    } catch {
      notifs = [];
    }
  }
  const unreadCount = notifs.filter(n => n.unread).length;

  // Active navigation route detection
  const path = (location.pathname || '').toLowerCase();
  const hash = (location.hash || '').toLowerCase();
  const isHomeActive = path === '/' || path === '' || path.endsWith('/index.html') || path === '/index.html';
  const isServicesActive = path.endsWith('/services.html') || path.endsWith('services.html');
  const isProvidersActive = path.endsWith('/providers.html') || path.endsWith('providers.html') || path.endsWith('provider-profile.html');
  const isAdminActive = path.includes('/admin/');
  const isFindCustomersActive = isProvider && (hash === '#opportunities' || location.search.includes('tab=opportunities'));
  const isHowItWorksActive = isProvider && (hash === '#how-it-works');
  const isProviderHomeActive = isProvider && (
    path.endsWith('/provider/home') ||
    path.endsWith('/provider/home.html') ||
    path.endsWith('provider-home.html') ||
    path === '/provider/home'
  ) && !isFindCustomersActive && !isHowItWorksActive;
  const isDashboardActive = path.includes('provider-dashboard.html');
  const isMyServicesActive = path.includes('provider-dashboard.html') && location.search.includes('tab=services');
  const isProviderProfileActive = isProvider && (
    path.endsWith('/provider/profile') ||
    path.endsWith('/provider/profile.html') ||
    path.includes('/provider/profile')
  );

  // Right Side Controls
  let rightControlsHtml = '';
  if (isLoggedIn) {
    // 1. Notification Dropdown (Bell Icon)
    const notifFooterLink = isProvider
      ? '/provider-dashboard.html?tab=requests'
      : (isAdmin ? '/admin/bookings.html' : '/my-account.html?tab=bookings');
    const notifFooterText = isProvider
      ? 'View all in Booking Requests'
      : (isAdmin ? 'View all bookings' : 'View all in My Account');

    const notifDropdownHtml = `
      <div class="dropdown position-relative" id="navNotifContainer">
        <button class="navbar-icon-btn" id="navNotifBtn" type="button" aria-expanded="false" aria-haspopup="true" aria-label="Notifications" title="Notifications">
          <i class="bi bi-bell"></i>
          <span class="navbar-notif-badge d-none" id="navNotifBadge">0</span>
        </button>
        <div class="dropdown-menu navbar-dropdown-menu notif-dropdown-menu dropdown-menu-end shadow-lg" id="navNotifMenu" aria-labelledby="navNotifBtn">
          <div class="d-flex align-items-center justify-content-between px-3 py-2.5 border-bottom bg-light bg-opacity-50">
            <div class="d-flex align-items-center gap-2">
              <span class="fw-bold text-dark" style="font-size: 0.9rem;">Notifications</span>
              <span class="badge bg-danger rounded-pill d-none" style="font-size: 0.65rem;" id="navNotifUnreadCount">0 new</span>
            </div>
            <button type="button" class="btn btn-link btn-sm text-decoration-none p-0 fw-semibold text-primary d-none" style="font-size: 0.75rem;" id="btnMarkAllNotifsRead">Mark all read</button>
          </div>
          <div class="notif-dropdown-list" id="navNotifList">
            <div class="p-3 text-center text-muted">
              <span class="spinner-border spinner-border-sm text-primary me-2"></span>
              <span class="small">Loading notifications...</span>
            </div>
          </div>
          <div class="p-2 border-top text-center bg-light bg-opacity-25">
            <a href="${notifFooterLink}" class="small fw-semibold text-primary text-decoration-none d-inline-flex align-items-center gap-1" id="navNotifFooterLink">
              <span>${notifFooterText}</span>
              <i class="bi bi-arrow-right" style="font-size: 0.75rem;"></i>
            </a>
          </div>
        </div>
      </div>
    `;

    // 2. Profile Dropdown (Customer / Provider / Admin)
    let profileDropdownHtml = '';
    if (isCustomer) {
      profileDropdownHtml = `
        <div class="dropdown position-relative d-none d-lg-block" id="navUserContainer">
          <button class="navbar-user-btn" id="navUserDropdownBtn" type="button" aria-expanded="false" aria-haspopup="true" aria-label="Customer profile menu">
            <div class="user-avatar-circle">
              ${avatarHtml}
            </div>
            <div class="d-flex flex-column text-start">
              <span class="user-name-text">${esc(u.name || 'Customer')}</span>
              <span class="user-role-badge">CUSTOMER</span>
            </div>
            <i class="bi bi-chevron-down nav-dropdown-arrow"></i>
          </button>
          <div class="dropdown-menu navbar-dropdown-menu dropdown-menu-end shadow-lg" id="navUserDropdownMenu" aria-labelledby="navUserDropdownBtn">
            <div class="px-3 py-2.5 border-bottom bg-light bg-opacity-25 mb-1">
              <div class="d-flex align-items-center gap-2">
                <div class="user-avatar-circle" style="width: 36px; height: 36px; font-size: 0.9rem;">
                  ${avatarHtml}
                </div>
                <div style="min-width: 0;">
                  <div class="fw-bold text-dark text-truncate" style="font-size: 0.9rem;">${esc(u.name || 'Customer')}</div>
                  <div class="text-muted text-truncate" style="font-size: 0.75rem;">${esc(userEmail || '')}</div>
                </div>
              </div>
              <div class="mt-2">
                <span class="user-role-badge">CUSTOMER</span>
              </div>
            </div>
            
            <a class="dropdown-item" href="/my-account.html">
              <i class="bi bi-person-circle text-primary"></i>
              <span>My Account</span>
            </a>
            <a class="dropdown-item" href="/my-account.html?tab=bookings">
              <i class="bi bi-calendar2-check text-primary"></i>
              <span>My Bookings</span>
            </a>
            <a class="dropdown-item" href="/my-account.html?tab=addresses">
              <i class="bi bi-geo-alt text-primary"></i>
              <span>Saved Addresses</span>
            </a>
            <a class="dropdown-item" href="/my-account.html?tab=favorites">
              <i class="bi bi-heart text-danger"></i>
              <span>Favorite Providers</span>
            </a>
            <a class="dropdown-item" href="/my-account.html?tab=settings">
              <i class="bi bi-shield-lock text-secondary"></i>
              <span>Settings & Privacy</span>
            </a>
            
            <div class="dropdown-divider my-1"></div>
            
            <button type="button" class="dropdown-item text-danger" onclick="Auth.logout()">
              <i class="bi bi-box-arrow-right"></i>
              <span>Logout</span>
            </button>
          </div>
        </div>
      `;
    } else if (isProvider) {
      // Check cached verification and availability
      const cachedVerif = u && u.id ? localStorage.getItem('lsc_provider_verif_' + u.id) : null;
      const cachedAvail = u && u.id ? localStorage.getItem('lsc_provider_avail_' + u.id) : null;
      
      let verifBadgeHtml = '';
      if (cachedVerif === 'VERIFIED') {
        verifBadgeHtml = '<span class="badge bg-success-subtle text-success border border-success-subtle py-0.5 px-1.5" style="font-size: 0.62rem; font-weight: 700;" title="Identity & Background Verified Partner"><i class="bi bi-patch-check-fill me-0.5"></i>VERIFIED</span>';
      }

      let availBadgeHtml = '';
      if (cachedAvail === 'true') {
        availBadgeHtml = '<span class="badge bg-success text-white py-0.5 px-1.5" style="font-size: 0.62rem; font-weight: 600;"><span class="pulse-dot me-1"></span>Online</span>';
      } else if (cachedAvail === 'false') {
        availBadgeHtml = '<span class="badge bg-secondary-subtle text-secondary py-0.5 px-1.5" style="font-size: 0.62rem; font-weight: 600;">Offline</span>';
      }

      profileDropdownHtml = `
        <div class="dropdown position-relative d-none d-lg-block" id="navUserContainer">
          <button class="navbar-user-btn" id="navUserDropdownBtn" type="button" aria-expanded="false" aria-haspopup="true" aria-label="Provider profile menu">
            <div class="user-avatar-circle" style="background: linear-gradient(135deg, #10b981 0%, #059669 100%);">
              ${avatarHtml}
            </div>
            <div class="d-flex flex-column text-start">
              <span class="user-name-text">${esc(u.name || 'Provider')}</span>
              <span class="badge bg-success-subtle text-success border border-success-subtle py-0.5 px-1.5" style="font-size: 0.62rem; font-weight: 700; width: fit-content;">PROVIDER</span>
            </div>
            <i class="bi bi-chevron-down nav-dropdown-arrow"></i>
          </button>
          <div class="dropdown-menu navbar-dropdown-menu dropdown-menu-end shadow-lg" id="navUserDropdownMenu" aria-labelledby="navUserDropdownBtn" role="menu">
            <div class="px-3 py-2.5 border-bottom bg-light bg-opacity-25 mb-1">
              <div class="d-flex align-items-center gap-2 mb-1.5">
                <div class="user-avatar-circle" style="width: 38px; height: 38px; font-size: 0.95rem; background: linear-gradient(135deg, #10b981 0%, #059669 100%); flex-shrink: 0;">
                  ${avatarHtml}
                </div>
                <div style="min-width: 0;" class="flex-grow-1">
                  <div class="fw-bold text-dark text-truncate" style="font-size: 0.9rem;">${esc(u.name || 'Provider')}</div>
                  <div class="text-muted text-truncate" style="font-size: 0.75rem;">${esc(userEmail || '')}</div>
                </div>
              </div>
              <div class="d-flex align-items-center gap-1.5 flex-wrap">
                <span class="user-role-badge">PROVIDER</span>
                <span id="navProviderVerifBadge">${verifBadgeHtml}</span>
                <span id="navProviderAvailIndicator">${availBadgeHtml}</span>
              </div>
            </div>

            <a class="dropdown-item ${isProviderProfileActive ? 'active fw-bold' : ''}" href="/provider/profile" role="menuitem">
              <i class="bi bi-person-circle text-primary"></i>
              <span>My Profile</span>
            </a>
            <a class="dropdown-item" href="/provider-dashboard.html?tab=services" role="menuitem">
              <i class="bi bi-briefcase text-primary"></i>
              <span>My Business</span>
            </a>
            <a class="dropdown-item" href="/provider-dashboard.html?tab=history" role="menuitem">
              <i class="bi bi-clock-history text-success"></i>
              <span>Booking History</span>
            </a>
            <a class="dropdown-item" href="/provider-dashboard.html?tab=earnings" role="menuitem">
              <i class="bi bi-wallet2 text-info"></i>
              <span>Earnings & Payments</span>
            </a>
            <a class="dropdown-item" href="/provider/profile#cardSecurity" role="menuitem">
              <i class="bi bi-gear text-secondary"></i>
              <span>Settings</span>
            </a>

            <div class="dropdown-divider my-1"></div>

            <button type="button" class="dropdown-item text-danger d-flex align-items-center gap-2" onclick="Auth.logout()" role="menuitem">
              <i class="bi bi-box-arrow-right"></i>
              <span>Sign Out</span>
            </button>
          </div>
        </div>
      `;
    } else {
      // Admin
      profileDropdownHtml = `
        <div class="dropdown position-relative d-none d-lg-block" id="navUserContainer">
          <button class="navbar-user-btn" id="navUserDropdownBtn" type="button" aria-expanded="false" aria-haspopup="true" aria-label="Admin menu">
            <div class="user-avatar-circle" style="background: linear-gradient(135deg, #ef4444 0%, #b91c1c 100%);">
              ${avatarHtml}
            </div>
            <div class="d-flex flex-column text-start">
              <span class="user-name-text">${esc(u.name || 'Admin')}</span>
              <span class="badge bg-danger-subtle text-danger border border-danger-subtle py-0.5 px-1.5" style="font-size: 0.62rem; font-weight: 700; width: fit-content;">ADMIN</span>
            </div>
            <i class="bi bi-chevron-down nav-dropdown-arrow"></i>
          </button>
          <div class="dropdown-menu navbar-dropdown-menu dropdown-menu-end shadow-lg" id="navUserDropdownMenu" aria-labelledby="navUserDropdownBtn">
            <div class="px-3 py-2.5 border-bottom bg-light bg-opacity-25 mb-1">
              <div class="fw-bold text-dark text-truncate" style="font-size: 0.9rem;">${esc(u.name || 'Admin')}</div>
              <div class="text-muted text-truncate" style="font-size: 0.75rem;">${esc(userEmail || '')}</div>
              <span class="badge bg-danger-subtle text-danger border border-danger-subtle py-0.5 px-1.5 mt-1" style="font-size: 0.62rem;">ADMIN</span>
            </div>
            <a class="dropdown-item" href="/admin/dashboard.html">
              <i class="bi bi-shield-lock text-danger"></i>
              <span>Admin Dashboard</span>
            </a>
            <div class="dropdown-divider my-1"></div>
            <button type="button" class="dropdown-item text-danger" onclick="Auth.logout()">
              <i class="bi bi-box-arrow-right"></i>
              <span>Logout</span>
            </button>
          </div>
        </div>
      `;
    }

    // Mobile compact account avatar link
    const mobileAccountPill = `
      <a href="${isCustomer ? '/my-account.html' : Auth.home(u.role)}" class="user-avatar-circle d-lg-none text-decoration-none shadow-xs" style="width: 36px; height: 36px;" title="Account">
        ${avatarHtml}
      </a>
    `;

    const navAvailPillHtml = isProvider ? `
      <div class="nav-provider-avail-pill ${isOnlineCached ? 'online' : 'offline'} d-none d-md-inline-flex align-items-center" id="navProviderAvailControl" role="region" aria-label="Provider Quick Service Availability">
        <div class="nav-avail-status-group" id="navAvailStatusGroup">
          <span class="status-pulse-dot ${isOnlineCached ? 'online' : 'offline'}" id="navPulseDot" aria-hidden="true"></span>
          <span class="nav-avail-label ${isOnlineCached ? 'status-online' : 'status-offline'}" id="navAvailLabel">${isOnlineCached ? 'Online' : 'Offline'}</span>
        </div>
        <div class="form-check form-switch m-0 p-0 d-flex align-items-center position-relative">
          <input class="form-check-input nav-avail-switch" type="checkbox" role="switch" id="navAvailSwitch" ${isOnlineCached ? 'checked' : ''} aria-label="Toggle Quick Service Availability">
        </div>
        <span class="spinner-border spinner-border-sm text-primary ms-1 d-none" id="navAvailSpinner" style="width: 14px; height: 14px;" role="status" aria-label="Saving availability"></span>
      </div>
    ` : '';

    rightControlsHtml = `
      ${navAvailPillHtml}
      ${notifDropdownHtml}
      ${profileDropdownHtml}
      ${mobileAccountPill}
      <button class="mobile-nav-toggler d-lg-none" id="btnMobileNavToggle" type="button" aria-label="Open Navigation Menu">
        <i class="bi bi-list"></i>
      </button>
    `;
  } else {
    // Guest (Unauthenticated)
    rightControlsHtml = `
      <div class="d-none d-sm-flex align-items-center gap-2">
        <a href="/login.html" class="btn btn-outline-primary btn-sm">
          <i class="bi bi-box-arrow-in-right me-1"></i>Log in
        </a>
        <a href="/register.html" class="btn btn-primary btn-sm">
          <i class="bi bi-person-plus me-1"></i>Sign up
        </a>
      </div>
      <button class="mobile-nav-toggler d-lg-none" id="btnMobileNavToggle" type="button" aria-label="Open Navigation Menu">
        <i class="bi bi-list"></i>
      </button>
    `;
  }

  // Construct Full Navbar DOM
  navEl.innerHTML = `
    <nav class="navbar site-navbar sticky-top" id="mainSiteNav">
      <div class="lsc-container d-flex align-items-center justify-content-between">
        
        <!-- Left Section: Logo -->
        <div class="navbar-left">
          <a class="brand" href="${isProvider ? '/provider/home' : (isAdmin ? '/admin/dashboard.html' : '/')}" aria-label="Local Service Connect Homepage">
            <span class="brand-icon-box"><i class="bi bi-shield-fill-check"></i></span>
            <span class="brand-text">Local Service Connect</span>
          </a>
        </div>
        
        <!-- Center Section: Desktop Navigation Items -->
        <div class="navbar-center d-none d-lg-flex align-items-center gap-1">
          ${isProvider ? `
            <a class="nav-link ${isProviderHomeActive ? 'active' : ''}" href="/provider/home">
              <i class="bi bi-house-door-fill"></i>
              <span>Home</span>
            </a>
            <a class="nav-link ${isFindCustomersActive ? 'active' : ''}" href="/provider/home#opportunities">
              <i class="bi bi-search"></i>
              <span>Find Customers</span>
            </a>
            <a class="nav-link ${isMyServicesActive ? 'active' : ''}" href="/provider-dashboard.html?tab=services">
              <i class="bi bi-tools"></i>
              <span>My Services</span>
            </a>
            <a class="nav-link ${isHowItWorksActive ? 'active' : ''}" href="/provider/home#how-it-works">
              <i class="bi bi-info-circle"></i>
              <span>How It Works</span>
            </a>
          ` : isCustomer ? `
            <a class="nav-link ${isHomeActive ? 'active' : ''}" href="/">
              <i class="bi bi-house-door-fill text-primary"></i>
              <span>Home</span>
            </a>
            <a class="nav-link ${isServicesActive ? 'active' : ''}" href="/services.html">
              <i class="bi bi-grid-fill text-primary"></i>
              <span>Find Services</span>
            </a>
            <a class="nav-link" href="/my-account.html?tab=bookings">
              <i class="bi bi-calendar2-check text-primary"></i>
              <span>My Bookings</span>
            </a>
            <a class="nav-link" href="/my-account.html?tab=favorites">
              <i class="bi bi-heart text-danger"></i>
              <span>My Favorites</span>
            </a>
            <a class="nav-link" href="/my-account.html">
              <i class="bi bi-person-circle text-primary"></i>
              <span>My Account</span>
            </a>
          ` : isAdmin ? `
            <a class="nav-link ${isAdminActive ? 'active' : ''}" href="/admin/dashboard.html">
              <i class="bi bi-shield-lock-fill text-danger"></i>
              <span>Admin Console</span>
            </a>
          ` : `
            <a class="nav-link ${isHomeActive ? 'active' : ''}" href="/">
              <i class="bi bi-house-door-fill text-primary"></i>
              <span>Home</span>
            </a>
            <a class="nav-link ${isServicesActive ? 'active' : ''}" href="/services.html">
              <i class="bi bi-grid-fill text-primary"></i>
              <span>Find Services</span>
            </a>
            <a class="nav-link ${isProvidersActive ? 'active' : ''}" href="/providers.html">
              <i class="bi bi-people-fill text-primary"></i>
              <span>Find Providers</span>
            </a>
          `}
        </div>

        <!-- Right Section: Account, Dashboard & Notifications Controls -->
        <div class="navbar-right d-flex align-items-center gap-2 gap-sm-3">
          ${isProvider ? `
            <a class="btn btn-navbar-dashboard d-none d-sm-inline-flex align-items-center gap-1.5" href="/provider-dashboard.html" title="Open Operations Dashboard">
              <i class="bi bi-speedometer2"></i>
              <span>Dashboard</span>
            </a>
          ` : ''}
          ${rightControlsHtml}
        </div>

      </div>
    </nav>

    <!-- Accessible Mobile Navigation Drawer -->
    <div class="mobile-nav-backdrop" id="mobileNavBackdrop"></div>
    <div class="mobile-nav-drawer" id="mobileNavDrawer" role="dialog" aria-modal="true" aria-label="Mobile Navigation Menu">
      <div class="mobile-nav-drawer-header">
        <a class="brand" href="${isProvider ? '/provider/home' : (isAdmin ? '/admin/dashboard.html' : '/')}" aria-label="Local Service Connect Homepage">
          <span class="brand-icon-box"><i class="bi bi-shield-fill-check"></i></span>
          <span style="font-size: 1.1rem;">Local Service Connect</span>
        </a>
        <button type="button" class="btn-close" id="btnCloseMobileNav" aria-label="Close menu"></button>
      </div>
      <div class="mobile-nav-drawer-body">
        ${isLoggedIn ? `
          <div class="mobile-nav-profile-card">
            <div class="user-avatar-circle" style="width: 44px; height: 44px; font-size: 1.1rem;">
              ${avatarHtml}
            </div>
            <div style="min-width: 0;" class="flex-grow-1">
              <div class="fw-bold text-dark text-truncate" style="font-size: 0.95rem;">${esc(u.name || 'User')}</div>
              <div class="text-muted text-truncate" style="font-size: 0.75rem;">${esc(userEmail || '')}</div>
              <span class="user-role-badge mt-1">${esc(u.role)}</span>
            </div>
          </div>
        ` : ''}

        ${isProvider ? `
          <div class="mobile-nav-avail-card p-2.5 mb-3 rounded-3 border bg-light d-flex align-items-center justify-content-between" id="mobileNavAvailBox">
            <div class="d-flex align-items-center gap-2">
              <span class="status-pulse-dot ${isOnlineCached ? 'online' : 'offline'}" id="mobileNavAvailPulseDot" style="width: 10px; height: 10px; margin-right: 2px;"></span>
              <div>
                <div class="fw-bold small text-dark" id="mobileNavAvailLabel">Quick Service: ${isOnlineCached ? 'Online' : 'Offline'}</div>
                <div class="text-muted" id="mobileNavAvailHelp" style="font-size: 0.72rem;">${isOnlineCached ? 'Ready for emergency requests' : 'Offline - no urgent dispatches'}</div>
              </div>
            </div>
            <div class="form-check form-switch m-0 p-0 d-flex align-items-center">
              <input class="form-check-input" type="checkbox" role="switch" id="mobileNavAvailSwitch" ${isOnlineCached ? 'checked' : ''} style="width: 2.8rem; height: 1.55rem; cursor: pointer;" aria-label="Toggle Mobile Quick Service Availability">
            </div>
          </div>
        ` : ''}

        ${isProvider ? `
          <div>
            <div class="mobile-nav-section-title">Provider Navigation</div>
            <a href="/provider/home" class="mobile-nav-item ${isProviderHomeActive ? 'active' : ''}">
              <i class="bi bi-house-door-fill text-primary"></i>
              <span>Home</span>
            </a>
            <a href="/provider-dashboard.html" class="mobile-nav-item ${isDashboardActive ? 'active' : ''}">
              <i class="bi bi-speedometer2 text-success"></i>
              <span>Dashboard</span>
            </a>
            <a href="/provider/home#opportunities" class="mobile-nav-item ${isFindCustomersActive ? 'active' : ''}">
              <i class="bi bi-search text-primary"></i>
              <span>Find Customers</span>
            </a>
            <a href="/provider-dashboard.html?tab=services" class="mobile-nav-item ${isMyServicesActive ? 'active' : ''}">
              <i class="bi bi-tools text-primary"></i>
              <span>My Services</span>
            </a>
            <a href="/provider/home#how-it-works" class="mobile-nav-item ${isHowItWorksActive ? 'active' : ''}">
              <i class="bi bi-info-circle text-primary"></i>
              <span>How It Works</span>
            </a>
            <a href="/provider-dashboard.html?tab=requests" class="mobile-nav-item">
              <i class="bi bi-bell text-warning"></i>
              <span>Booking Requests</span>
            </a>
            <a href="/provider-dashboard.html?tab=active" class="mobile-nav-item">
              <i class="bi bi-lightning-charge text-primary"></i>
              <span>Active Jobs</span>
            </a>
            <div class="mobile-nav-section-title mt-3">Account & Management</div>
            <a href="/provider/profile" class="mobile-nav-item ${isProviderProfileActive ? 'active' : ''}">
              <i class="bi bi-person-circle text-primary"></i>
              <span>My Profile</span>
            </a>
            <a href="/provider-dashboard.html?tab=services" class="mobile-nav-item">
              <i class="bi bi-briefcase text-primary"></i>
              <span>My Business</span>
            </a>
            <a href="/provider-dashboard.html?tab=history" class="mobile-nav-item">
              <i class="bi bi-clock-history text-success"></i>
              <span>Booking History</span>
            </a>
            <a href="/provider-dashboard.html?tab=earnings" class="mobile-nav-item">
              <i class="bi bi-wallet2 text-info"></i>
              <span>Earnings & Payments</span>
            </a>
            <a href="/provider/profile#cardSecurity" class="mobile-nav-item">
              <i class="bi bi-gear text-secondary"></i>
              <span>Settings</span>
            </a>
            <div class="pt-2">
              <button type="button" class="btn btn-outline-danger btn-sm w-100 py-2 d-flex align-items-center justify-content-center gap-1.5" onclick="Auth.logout()">
                <i class="bi bi-box-arrow-right"></i>
                <span>Sign Out</span>
              </button>
            </div>
          </div>
        ` : isAdmin ? `
          <div>
            <div class="mobile-nav-section-title">Administration</div>
            <a href="/admin/dashboard.html" class="mobile-nav-item ${isAdminActive ? 'active' : ''}">
              <i class="bi bi-shield-lock-fill text-danger"></i>
              <span>Admin Dashboard</span>
            </a>
          </div>
        ` : `
          <div>
            <div class="mobile-nav-section-title">Explore</div>
            <a href="/services.html" class="mobile-nav-item ${isServicesActive ? 'active' : ''}">
              <i class="bi bi-grid-fill"></i>
              <span>Services</span>
            </a>
            <a href="/providers.html" class="mobile-nav-item ${isProvidersActive ? 'active' : ''}">
              <i class="bi bi-people-fill"></i>
              <span>Find Providers</span>
            </a>
          </div>
        `}

        ${isLoggedIn && isCustomer ? `
          <div>
            <div class="mobile-nav-section-title">Customer Navigation</div>
            <a href="/" class="mobile-nav-item ${isHomeActive ? 'active' : ''}">
              <i class="bi bi-house-door-fill text-primary"></i>
              <span>Home</span>
            </a>
            <a href="/services.html" class="mobile-nav-item ${isServicesActive ? 'active' : ''}">
              <i class="bi bi-grid-fill text-primary"></i>
              <span>Find Services</span>
            </a>
            <a href="/my-account.html?tab=bookings" class="mobile-nav-item">
              <i class="bi bi-calendar2-check text-primary"></i>
              <span>My Bookings</span>
            </a>
            <a href="/my-account.html?tab=favorites" class="mobile-nav-item">
              <i class="bi bi-heart text-danger"></i>
              <span>My Favorites</span>
            </a>
            <a href="/my-account.html" class="mobile-nav-item">
              <i class="bi bi-person-circle text-primary"></i>
              <span>My Account</span>
            </a>
            <div class="mobile-nav-section-title mt-3">Account & Settings</div>
            <a href="/my-account.html?tab=addresses" class="mobile-nav-item">
              <i class="bi bi-geo-alt"></i>
              <span>Saved Addresses</span>
            </a>
            <a href="/my-account.html?tab=notifications" class="mobile-nav-item">
              <i class="bi bi-bell"></i>
              <span>Notifications ${unreadCount > 0 ? `<span class="badge bg-danger rounded-pill ms-auto" style="font-size: 0.65rem;">${unreadCount}</span>` : ''}</span>
            </a>
            <a href="/my-account.html?tab=settings" class="mobile-nav-item">
              <i class="bi bi-shield-lock"></i>
              <span>Settings & Privacy</span>
            </a>
            <div class="pt-2">
              <button type="button" class="btn btn-outline-danger btn-sm w-100 py-2 d-flex align-items-center justify-content-center gap-1.5" onclick="Auth.logout()">
                <i class="bi bi-box-arrow-right"></i>
                <span>Sign Out</span>
              </button>
            </div>
          </div>
        ` : ''}


        ${!isLoggedIn ? `
          <div class="d-flex flex-column gap-2 pt-2">
            <a href="/login.html" class="btn btn-outline-primary w-100 py-2">
              <i class="bi bi-box-arrow-in-right me-1"></i> Log in
            </a>
            <a href="/register.html" class="btn btn-primary w-100 py-2">
              <i class="bi bi-person-plus me-1"></i> Sign up
            </a>
          </div>
        ` : ''}
      </div>

      ${isLoggedIn ? `
        <div class="mobile-nav-drawer-footer">
          <button type="button" class="btn btn-outline-danger w-100 py-2" onclick="Auth.logout()">
            <i class="bi bi-box-arrow-right me-2"></i> Logout
          </button>
        </div>
      ` : ''}
    </div>
  `;

  // Dynamic scroll shadow depth
  const navBarElement = document.getElementById('mainSiteNav');
  if (navBarElement) {
    const handleScroll = () => {
      const scrollPos = window.scrollY || (document.documentElement && document.documentElement.scrollTop) || 0;
      if (scrollPos > 15) {
        navBarElement.classList.add('scrolled');
      } else {
        navBarElement.classList.remove('scrolled');
      }
    };
    window.removeEventListener('scroll', handleScroll);
    window.addEventListener('scroll', handleScroll, { passive: true });
    handleScroll();
  }

  // Setup interactive listeners: dropdowns, mobile drawer, outside clicks, and Escape key
  setupNavbarInteractions(u);
}

/**
 * Setup interactive behaviors for navbar dropdowns and mobile drawer
 */
function setupNavbarInteractions(currentUser) {
  const u = currentUser || (typeof Auth !== 'undefined' && typeof Auth.get === 'function' ? Auth.get() : null);
  const isProvider = !!(u && u.token && u.role === 'PROVIDER');

  const notifBtn = document.getElementById('navNotifBtn');
  const notifMenu = document.getElementById('navNotifMenu');
  const userBtn = document.getElementById('navUserDropdownBtn');
  const userMenu = document.getElementById('navUserDropdownMenu');
  const mobileToggleBtn = document.getElementById('btnMobileNavToggle');
  const mobileDrawer = document.getElementById('mobileNavDrawer');
  const mobileBackdrop = document.getElementById('mobileNavBackdrop');
  const closeMobileBtn = document.getElementById('btnCloseMobileNav');

  function closeAllDropdowns() {
    if (notifMenu) {
      notifMenu.classList.remove('show');
      if (notifBtn) notifBtn.setAttribute('aria-expanded', 'false');
    }
    if (userMenu) {
      userMenu.classList.remove('show');
      if (userBtn) userBtn.setAttribute('aria-expanded', 'false');
    }
  }

  function closeMobileDrawer() {
    if (mobileDrawer) mobileDrawer.classList.remove('show');
    if (mobileBackdrop) mobileBackdrop.classList.remove('show');
    document.body.style.overflow = '';
  }

  function openMobileDrawer() {
    closeAllDropdowns();
    if (mobileDrawer) mobileDrawer.classList.add('show');
    if (mobileBackdrop) mobileBackdrop.classList.add('show');
    document.body.style.overflow = 'hidden';
  }

  if (notifBtn && notifMenu) {
    notifBtn.onclick = (e) => {
      e.stopPropagation();
      const isOpen = notifMenu.classList.contains('show');
      closeAllDropdowns();
      if (!isOpen) {
        notifMenu.classList.add('show');
        notifBtn.setAttribute('aria-expanded', 'true');
      }
    };
  }

  if (userBtn && userMenu) {
    userBtn.onclick = (e) => {
      e.stopPropagation();
      const isOpen = userMenu.classList.contains('show');
      closeAllDropdowns();
      if (!isOpen) {
        userMenu.classList.add('show');
        userBtn.setAttribute('aria-expanded', 'true');
      }
    };

    // Keyboard support for opening dropdown
    userBtn.addEventListener('keydown', (e) => {
      if (e.key === 'ArrowDown' || e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        userMenu.classList.add('show');
        userBtn.setAttribute('aria-expanded', 'true');
        const firstItem = userMenu.querySelector('.dropdown-item');
        if (firstItem) firstItem.focus();
      }
    });

    // Keyboard navigation within dropdown
    userMenu.addEventListener('keydown', (e) => {
      const items = Array.from(userMenu.querySelectorAll('.dropdown-item'));
      const activeIdx = items.indexOf(document.activeElement);

      if (e.key === 'ArrowDown') {
        e.preventDefault();
        const nextIdx = (activeIdx + 1) % items.length;
        items[nextIdx].focus();
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        const prevIdx = (activeIdx - 1 + items.length) % items.length;
        items[prevIdx].focus();
      } else if (e.key === 'Escape') {
        e.preventDefault();
        userMenu.classList.remove('show');
        userBtn.setAttribute('aria-expanded', 'false');
        userBtn.focus();
      } else if (e.key === 'Tab') {
        userMenu.classList.remove('show');
        userBtn.setAttribute('aria-expanded', 'false');
      }
    });

    // Close dropdown on click of any item
    userMenu.querySelectorAll('.dropdown-item').forEach(item => {
      item.addEventListener('click', () => {
        userMenu.classList.remove('show');
        userBtn.setAttribute('aria-expanded', 'false');
      });
    });
  }

  // Global sync function for all provider availability UI elements across the page
  window.syncAllProviderAvailabilityControls = function(isOnline, verificationStatus) {
    const user = Auth.get();
    let effectiveVerif = verificationStatus;
    if (!effectiveVerif && user && user.id) {
      effectiveVerif = localStorage.getItem('lsc_provider_verif_' + user.id);
    }
    if (effectiveVerif && user && user.id) {
      localStorage.setItem('lsc_provider_verif_' + user.id, effectiveVerif);
    }
    const isVerified = effectiveVerif ? effectiveVerif.toUpperCase() === 'VERIFIED' : true;

    // 1. Navbar elements
    const navControl = document.getElementById('navProviderAvailControl');
    const navSwitch = document.getElementById('navAvailSwitch');
    const navPulse = document.getElementById('navPulseDot');
    const navLabel = document.getElementById('navAvailLabel');

    if (navControl) {
      navControl.classList.toggle('online', !!isOnline);
      navControl.classList.toggle('offline', !isOnline);
    }
    if (navSwitch) {
      navSwitch.checked = !!isOnline;
      navSwitch.disabled = !isVerified;
      navSwitch.removeAttribute('title');
      navSwitch.setAttribute('aria-label', isVerified
        ? (isOnline ? 'Quick Service is Online. Toggle to switch Offline' : 'Quick Service is Offline. Toggle to switch Online')
        : 'Account pending admin verification. Online status disabled');
    }
    if (navPulse) {
      navPulse.className = isOnline ? 'status-pulse-dot online' : 'status-pulse-dot offline';
    }
    if (navLabel) {
      navLabel.textContent = isOnline ? 'Online' : 'Offline';
      navLabel.className = isOnline ? 'nav-avail-label status-online' : 'nav-avail-label status-offline';
    }

    // 2. Mobile drawer elements
    const mobileBox = document.getElementById('mobileNavAvailBox');
    const mobileSwitch = document.getElementById('mobileNavAvailSwitch');
    const mobPulse = document.getElementById('mobileNavAvailPulseDot');
    const mobLabel = document.getElementById('mobileNavAvailLabel');
    const mobHelp = document.getElementById('mobileNavAvailHelp');

    if (mobileBox) {
      mobileBox.classList.toggle('online', !!isOnline);
      mobileBox.classList.toggle('offline', !isOnline);
    }
    if (mobileSwitch) {
      mobileSwitch.checked = !!isOnline;
      mobileSwitch.disabled = !isVerified;
      mobileSwitch.removeAttribute('title');
      mobileSwitch.setAttribute('aria-label', isVerified
        ? (isOnline ? 'Quick Service is Online. Toggle to switch Offline' : 'Quick Service is Offline. Toggle to switch Online')
        : 'Account pending admin verification. Online status disabled');
    }
    if (mobPulse) mobPulse.className = isOnline ? 'status-pulse-dot online' : 'status-pulse-dot offline';
    if (mobLabel) mobLabel.textContent = isOnline ? 'Quick Service: Online' : 'Quick Service: Offline';
    if (mobHelp) {
      mobHelp.textContent = !isVerified
        ? 'Pending admin verification'
        : (isOnline ? 'Ready for emergency requests' : 'Offline - no urgent dispatches');
    }

    // 3. Hero card elements (Provider Homepage)
    const heroCard = document.getElementById('heroAvailabilityCard');
    const heroSwitch = document.getElementById('heroAvailabilitySwitch');
    const heroIconBubble = document.getElementById('heroAvailIconBubble');
    const heroBadge = document.getElementById('heroAvailBadge');
    const heroPulse = document.getElementById('heroAvailPulseDot');
    const heroStatusLabel = document.getElementById('heroAvailStatusLabel');
    const heroDesc = document.getElementById('heroAvailDesc');

    if (heroCard) {
      heroCard.classList.toggle('card-online', !!isOnline);
      heroCard.classList.toggle('card-offline', !isOnline);
    }
    if (heroSwitch) {
      heroSwitch.checked = !!isOnline;
      heroSwitch.disabled = !isVerified;
      heroSwitch.removeAttribute('title');
      heroSwitch.setAttribute('aria-label', isVerified
        ? (isOnline ? 'Quick Service is Online. Toggle to switch Offline' : 'Quick Service is Offline. Toggle to switch Online')
        : 'Account pending admin verification. Online status disabled');
    }
    if (heroIconBubble) {
      heroIconBubble.className = isOnline ? 'hero-avail-icon-bubble online' : 'hero-avail-icon-bubble offline';
    }
    if (heroBadge) {
      heroBadge.textContent = isOnline ? 'Online' : 'Offline';
      heroBadge.className = isOnline ? 'hero-avail-badge badge-online' : 'hero-avail-badge badge-offline';
    }
    if (heroPulse) {
      heroPulse.className = isOnline ? 'status-pulse-dot online' : 'status-pulse-dot offline';
    }
    if (heroStatusLabel) {
      heroStatusLabel.textContent = isOnline ? 'Online' : 'Offline';
      heroStatusLabel.className = isOnline ? 'hero-avail-status-label status-online' : 'hero-avail-status-label status-offline';
    }
    if (heroDesc) {
      heroDesc.textContent = 'Receive urgent service requests in your registered areas.';
    }

    // 4. Section 3 Lower summary card (Provider Homepage)
    const lowerPulse = document.getElementById('lowerSectionPulseDot');
    const lowerLabel = document.getElementById('lowerSectionStatusLabel');
    const lowerBadge = document.getElementById('lowerSectionStatusBadge');
    const lowerDesc = document.getElementById('lowerSectionStatusDesc');

    if (lowerPulse) lowerPulse.className = isOnline ? 'status-pulse-dot online' : 'status-pulse-dot offline';
    if (lowerLabel) lowerLabel.textContent = isOnline ? 'Status: Online' : 'Status: Offline';
    if (lowerBadge) {
      lowerBadge.className = isOnline ? 'badge bg-success text-white rounded-pill px-2.5 py-1' : 'badge bg-secondary-subtle text-secondary rounded-pill px-2.5 py-1';
      lowerBadge.textContent = isOnline ? 'Online' : 'Offline';
    }
    if (lowerDesc) {
      lowerDesc.textContent = isOnline
        ? 'Your account is online and eligible for urgent dispatches. Switch off anytime from the top of the page.'
        : 'Your account is offline and not receiving urgent requests. Toggle online at the top of the page.';
    }

    // 5. Performance Card (Card 4: Current Availability)
    const statAvailabilityText = document.getElementById('statAvailabilityText');
    const statAvailabilityMeta = document.getElementById('statAvailabilityMeta');
    if (statAvailabilityText) {
      statAvailabilityText.innerHTML = isOnline 
        ? '<span class="badge bg-success-subtle text-success border border-success-subtle py-1 px-2.5 rounded-pill"><i class="bi bi-broadcast me-1"></i>Online</span>' 
        : '<span class="badge bg-secondary-subtle text-secondary border border-secondary-subtle py-1 px-2.5 rounded-pill">Offline</span>';
    }
    if (statAvailabilityMeta) {
      statAvailabilityMeta.textContent = isOnline ? 'Receiving urgent local dispatches' : 'Not receiving urgent requests';
    }

    // 6. User Dropdown Menu Indicator
    const navDropdownAvailIndicator = document.getElementById('navProviderAvailIndicator');
    if (navDropdownAvailIndicator) {
      navDropdownAvailIndicator.innerHTML = isOnline
        ? '<span class="badge bg-success text-white py-0.5 px-1.5" style="font-size: 0.62rem; font-weight: 600;"><span class="status-pulse-dot online me-1" style="width: 7px; height: 7px;"></span>Online</span>'
        : '<span class="badge bg-secondary-subtle text-secondary py-0.5 px-1.5" style="font-size: 0.62rem; font-weight: 600;">Offline</span>';
    }
  };

  // Modern Floating Top-Right Toast Notification for Availability
  window.showAvailabilityToast = function(type, title, message) {
    let container = document.getElementById('availabilityToastContainer');
    if (!container) {
      container = document.createElement('div');
      container.id = 'availabilityToastContainer';
      container.className = 'availability-toast-container';
      container.setAttribute('aria-live', 'polite');
      container.setAttribute('aria-atomic', 'true');
      document.body.appendChild(container);
    }

    // Dismiss any existing toast to prevent clutter/duplicates
    container.innerHTML = '';

    const isOnline = type === 'online' || type === 'success';
    const isError = type === 'error' || type === 'danger' || type === 'warning';
    const toastClass = isOnline ? 'toast-online' : (isError ? 'toast-error' : 'toast-offline');
    const iconClass = isOnline ? 'online' : (isError ? 'error' : 'offline');
    const iconHtml = isOnline 
      ? '<i class="bi bi-check-lg"></i>' 
      : (isError ? '<i class="bi bi-exclamation-triangle-fill"></i>' : '<i class="bi bi-pause-fill"></i>');

    const defaultTitle = isOnline ? "You're Online!" : (isError ? 'Update Failed' : "You're Offline!");
    const defaultMsg = isOnline 
      ? 'You are now online and can receive Quick Service requests.' 
      : (isError ? 'Could not update availability. Please try again.' : "You won't receive new Quick Service requests.");

    const toastEl = document.createElement('div');
    toastEl.className = `availability-toast ${toastClass}`;
    toastEl.setAttribute('role', 'alert');
    toastEl.innerHTML = `
      <div class="availability-toast-body">
        <div class="toast-icon-circle ${iconClass}" aria-hidden="true">
          ${iconHtml}
        </div>
        <div class="toast-text-group">
          <div class="toast-heading"></div>
          <p class="toast-subtext"></p>
        </div>
        <button type="button" class="toast-dismiss-btn" aria-label="Close notification">
          <i class="bi bi-x-lg"></i>
        </button>
      </div>
    `;

    toastEl.querySelector('.toast-heading').textContent = title || defaultTitle;
    toastEl.querySelector('.toast-subtext').textContent = message || defaultMsg;

    let dismissTimer = null;
    const dismiss = () => {
      if (dismissTimer) {
        clearTimeout(dismissTimer);
        dismissTimer = null;
      }
      toastEl.classList.add('toast-hiding');
      setTimeout(() => {
        if (toastEl.parentNode) toastEl.remove();
      }, 280);
    };

    const closeBtn = toastEl.querySelector('.toast-dismiss-btn');
    if (closeBtn) {
      closeBtn.addEventListener('click', dismiss);
    }

    // Auto-dismiss after approximately 4 seconds
    dismissTimer = setTimeout(dismiss, 4000);

    container.appendChild(toastEl);
  };

  // Mutex lock to prevent duplicate concurrent network updates
  let _isUpdatingAvailability = false;

  // Centralized availability updater
  window.updateProviderAvailability = async function(nextState) {
    if (_isUpdatingAvailability) {
      return;
    }

    const user = Auth.get();
    if (!user || !user.token || Auth.isTokenExpired()) {
      window.dispatchEvent(new CustomEvent('lsc:provider-availability-sync', {
        detail: { available: false, error: new Error('Your session has expired. Redirecting to login...') }
      }));
      setTimeout(() => { Auth.logout(); }, 1200);
      throw new Error('Your session has expired. Please log in again.');
    }

    const cachedVerif = user.id ? localStorage.getItem('lsc_provider_verif_' + user.id) : null;
    if (cachedVerif && cachedVerif.toUpperCase() !== 'VERIFIED' && nextState) {
      const err = new Error('An admin must verify your account before you can go online');
      window.syncAllProviderAvailabilityControls(false, cachedVerif);
      window.dispatchEvent(new CustomEvent('lsc:provider-availability-sync', {
        detail: { available: false, error: err }
      }));
      throw err;
    }

    _isUpdatingAvailability = true;

    const navSwitch = document.getElementById('navAvailSwitch');
    const mobileSwitch = document.getElementById('mobileNavAvailSwitch');
    const heroSwitch = document.getElementById('heroAvailabilitySwitch');
    const navSpinner = document.getElementById('navAvailSpinner');
    const heroSpinner = document.getElementById('heroAvailSpinner');

    const switches = [navSwitch, mobileSwitch, heroSwitch].filter(Boolean);
    switches.forEach(s => { s.disabled = true; });
    if (navSpinner) navSpinner.classList.remove('d-none');
    if (heroSpinner) heroSpinner.classList.remove('d-none');

    try {
      const res = await api('/provider/availability', {
        method: 'PUT',
        body: { available: !!nextState }
      });
      const isOnline = !!res.availability;
      if (user && user.id) {
        localStorage.setItem('lsc_provider_avail_' + user.id, isOnline ? 'true' : 'false');
        if (res.verificationStatus) {
          localStorage.setItem('lsc_provider_verif_' + user.id, res.verificationStatus);
        }
      }
      window.syncAllProviderAvailabilityControls(isOnline, res.verificationStatus || cachedVerif);
      window.dispatchEvent(new CustomEvent('lsc:provider-availability-sync', {
        detail: { available: isOnline, res, error: null }
      }));
      return res;
    } catch (err) {
      const revertState = !nextState;
      window.syncAllProviderAvailabilityControls(revertState, cachedVerif);
      window.dispatchEvent(new CustomEvent('lsc:provider-availability-sync', {
        detail: { available: revertState, error: err }
      }));
      throw err;
    } finally {
      _isUpdatingAvailability = false;
      const finalVerif = user && user.id ? localStorage.getItem('lsc_provider_verif_' + user.id) : cachedVerif;
      const isVerified = finalVerif ? finalVerif.toUpperCase() === 'VERIFIED' : true;
      switches.forEach(s => { s.disabled = !isVerified; });
      if (navSpinner) navSpinner.classList.add('d-none');
      if (heroSpinner) heroSpinner.classList.add('d-none');
    }
  };

  // Real-time verification and availability status sync for provider
  if (isProvider && u && u.token && !window._lsc_provider_profile_sync) {
    window._lsc_provider_profile_sync = true;
    api('/provider/profile').then(p => {
      if (p) {
        localStorage.setItem('lsc_provider_verif_' + u.id, p.verificationStatus);
        localStorage.setItem('lsc_provider_avail_' + u.id, p.availability ? 'true' : 'false');
        const verifEl = document.getElementById('navProviderVerifBadge');
        if (verifEl) {
          verifEl.innerHTML = p.verificationStatus === 'VERIFIED'
            ? '<span class="badge bg-success-subtle text-success border border-success-subtle py-0.5 px-1.5" style="font-size: 0.62rem; font-weight: 700;" title="Identity & Background Verified Partner"><i class="bi bi-patch-check-fill me-0.5"></i>VERIFIED</span>'
            : '';
        }
        if (typeof window.syncAllProviderAvailabilityControls === 'function') {
          window.syncAllProviderAvailabilityControls(p.availability, p.verificationStatus);
        }
      }
    }).catch(() => {});
  }

  // Wire switch event listeners for provider
  if (isProvider) {
    const navSwitch = document.getElementById('navAvailSwitch');
    if (navSwitch) {
      navSwitch.onchange = function() {
        const targetState = navSwitch.checked;
        window.updateProviderAvailability(targetState).catch(() => {
          // Failure handling and error toast dispatched via lsc:provider-availability-sync
        });
      };
    }
    const mobileSwitch = document.getElementById('mobileNavAvailSwitch');
    if (mobileSwitch) {
      mobileSwitch.onchange = function() {
        const targetState = mobileSwitch.checked;
        window.updateProviderAvailability(targetState).catch(() => {
          // Failure handling and error toast dispatched via lsc:provider-availability-sync
        });
      };
    }

    // Synchronize initial UI state from cache
    if (u && u.id) {
      const cachedAvail = localStorage.getItem('lsc_provider_avail_' + u.id);
      const cachedVerif = localStorage.getItem('lsc_provider_verif_' + u.id);
      if (cachedAvail !== null && typeof window.syncAllProviderAvailabilityControls === 'function') {
        window.syncAllProviderAvailabilityControls(cachedAvail === 'true', cachedVerif);
      }
    }
  }

  if (mobileToggleBtn) {
    mobileToggleBtn.onclick = (e) => {
      e.stopPropagation();
      openMobileDrawer();
    };
  }

  if (closeMobileBtn) {
    closeMobileBtn.onclick = () => closeMobileDrawer();
  }

  if (mobileBackdrop) {
    mobileBackdrop.onclick = () => closeMobileDrawer();
  }

  // Close when clicking outside of dropdowns (attached once globally to prevent duplicates)
  if (!window._lsc_navbar_doc_listeners_attached) {
    window._lsc_navbar_doc_listeners_attached = true;
    document.addEventListener('click', (e) => {
      const curNotifMenu = document.getElementById('navNotifMenu');
      const curNotifBtn = document.getElementById('navNotifBtn');
      const curUserMenu = document.getElementById('navUserDropdownMenu');
      const curUserBtn = document.getElementById('navUserDropdownBtn');

      if (curNotifMenu && !curNotifMenu.contains(e.target) && (!curNotifBtn || !curNotifBtn.contains(e.target))) {
        curNotifMenu.classList.remove('show');
        if (curNotifBtn) curNotifBtn.setAttribute('aria-expanded', 'false');
      }
      if (curUserMenu && !curUserMenu.contains(e.target) && (!curUserBtn || !curUserBtn.contains(e.target))) {
        curUserMenu.classList.remove('show');
        if (curUserBtn) curUserBtn.setAttribute('aria-expanded', 'false');
      }
    });

    // Support keyboard Escape to dismiss dropdowns and drawer
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') {
        const curNotifMenu = document.getElementById('navNotifMenu');
        const curNotifBtn = document.getElementById('navNotifBtn');
        const curUserMenu = document.getElementById('navUserDropdownMenu');
        const curUserBtn = document.getElementById('navUserDropdownBtn');
        const curDrawer = document.getElementById('mobileNavDrawer');
        const curBackdrop = document.getElementById('mobileNavBackdrop');

        if (curNotifMenu) {
          curNotifMenu.classList.remove('show');
          if (curNotifBtn) curNotifBtn.setAttribute('aria-expanded', 'false');
        }
        if (curUserMenu) {
          curUserMenu.classList.remove('show');
          if (curUserBtn) curUserBtn.setAttribute('aria-expanded', 'false');
        }
        if (curDrawer) curDrawer.classList.remove('show');
        if (curBackdrop) curBackdrop.classList.remove('show');
        document.body.style.overflow = '';
      }
    });
  }

  // Handle Mark All Read in Notification Dropdown
  const markReadBtn = document.getElementById('btnMarkAllNotifsRead');
  if (markReadBtn) {
    markReadBtn.onclick = (e) => {
      e.preventDefault();
      e.stopPropagation();
      if (window.NotificationSystem) {
        window.NotificationSystem.markAllRead();
      }
    };
  }

  // Initialize Real-time Notification System for authenticated user
  if (u && u.token && window.NotificationSystem) {
    window.NotificationSystem.init(u);
  }
}

/**
 * Right-Side "My Account" Profile Drawer matching user screenshot
 */
function initAccountDrawer(u) {
  let backdrop = document.getElementById('accountDrawerBackdrop');
  let drawer = document.getElementById('accountDrawerOffcanvas');

  if (!drawer) {
    backdrop = document.createElement('div');
    backdrop.id = 'accountDrawerBackdrop';
    backdrop.className = 'account-drawer-backdrop';
    document.body.appendChild(backdrop);

    drawer = document.createElement('div');
    drawer.id = 'accountDrawerOffcanvas';
    drawer.className = 'account-drawer-offcanvas';
    document.body.appendChild(drawer);
  }

  const isLoggedIn = !!(u && u.token);
  const userName = u.name || 'Account';
  const initial = (u.name ? u.name.charAt(0) : 'U').toUpperCase();

  // Render Drawer Internal Content
  drawer.innerHTML = `
    <!-- Drawer Header -->
    <div class="account-drawer-header d-flex align-items-center justify-content-between">
      <div class="d-flex align-items-center gap-2">
        <button type="button" class="btn btn-sm btn-light rounded-circle p-1 px-2 border-0" id="btnCloseAccountDrawer" title="Back / Close">
          <i class="bi bi-arrow-left fs-5 text-dark"></i>
        </button>
        <h5 class="mb-0 fw-bold text-dark" id="accountDrawerTitle">My Account</h5>
      </div>
      <a href="/my-account.html" class="btn btn-outline-primary btn-sm rounded-pill py-0.5 px-2.5 fw-semibold" style="font-size:0.75rem;" title="Open Dedicated My Account Page">
        <i class="bi bi-box-arrow-up-right me-1"></i>Full Page
      </a>
    </div>

    <!-- Drawer Body -->
    <div class="account-drawer-body">
      <!-- Main Account View -->
      <div id="drawerMainView">
        ${isLoggedIn ? `
          <!-- Minimalist & Professional Profile Card -->
          <div class="account-profile-card mb-4" id="accountProfileCard">
            <!-- Header Row: Avatar with SVG Progress Ring, Name & Verification, Edit Button -->
            <div class="d-flex justify-content-between align-items-center mb-3">
              <div class="d-flex align-items-center gap-3">
                <div class="account-avatar-ring-wrapper">
                  <!-- SVG Circular Progress Ring (circumference: 175.93) -->
                  <svg class="avatar-progress-svg" width="64" height="64" viewBox="0 0 64 64">
                    <circle cx="32" cy="32" r="28" stroke="#e2e8f0" stroke-width="3" fill="none" />
                    <circle id="avatarRingProgress" cx="32" cy="32" r="28" stroke="#2563eb" stroke-width="3.5" stroke-linecap="round" fill="none" stroke-dasharray="175.93" stroke-dashoffset="43.98" transform="rotate(-90 32 32)" style="transition: stroke-dashoffset 0.6s cubic-bezier(0.4, 0, 0.2, 1), stroke 0.3s ease;" />
                  </svg>
                  <div class="account-profile-avatar-circle" id="drawerAvatarContainer">
                    <span id="drawerAvatarInitial">${esc(initial)}</span>
                    <img id="drawerAvatarImg" class="account-profile-avatar-img d-none" alt="Profile Photo">
                  </div>
                  <label for="avatarFileInput" class="avatar-camera-btn" title="Change Profile Photo">
                    <i class="bi bi-camera-fill"></i>
                  </label>
                  <input type="file" id="avatarFileInput" accept="image/*" class="d-none">
                </div>
                <div>
                  <h4 class="h5 fw-bold text-dark mb-0 text-capitalize" id="drawerDisplayName">${esc(userName)}</h4>
                  <div class="d-flex align-items-center gap-1.5 mt-1">
                    <span class="badge bg-success-subtle text-success py-0.5 px-2 rounded-pill fw-semibold" style="font-size: 0.68rem; letter-spacing: 0.1px;">
                      <i class="bi bi-patch-check-fill me-1"></i>Verified Resident
                    </span>
                    <span class="badge bg-primary-subtle text-primary border border-primary-subtle py-0.5 px-2 rounded-pill fw-bold" style="font-size: 0.68rem;" id="completionPercentText" title="Profile Strength">
                      75%
                    </span>
                  </div>
                </div>
              </div>
              <button type="button" class="account-edit-pill-btn" id="btnToggleProfileEdit" title="Edit Profile Details">
                <i class="bi bi-pencil" style="font-size: 0.7rem;"></i>
                <span>Edit</span>
              </button>
            </div>

            <!-- Clean Contact Info List (Phone & Email only, Doorstep removed) -->
            <div class="account-contact-clean my-2.5" id="drawerContactDetails">
              <!-- Phone -->
              <div class="account-clean-row">
                <div class="d-flex align-items-center gap-2 text-truncate">
                  <i class="bi bi-telephone text-muted" style="font-size: 0.82rem; width: 16px;"></i>
                  <span class="text-dark small fw-medium" id="drawerDisplayPhone">Loading phone...</span>
                </div>
                <span class="account-clean-tag text-success" id="badgePhoneVerified">
                  <i class="bi bi-check2"></i>Verified
                </span>
              </div>
              <!-- Email -->
              <div class="account-clean-row">
                <div class="d-flex align-items-center gap-2 text-truncate">
                  <i class="bi bi-envelope text-muted" style="font-size: 0.82rem; width: 16px;"></i>
                  <span class="text-dark small fw-medium text-truncate" style="max-width: 185px;" id="drawerDisplayEmail">Loading email...</span>
                </div>
                <span class="account-clean-tag text-success" id="badgeEmailVerified">
                  <i class="bi bi-check2"></i>Active
                </span>
              </div>
            </div>

            <!-- Edit Profile Form (Includes Full Name, Mobile OTP, Email, and Change Password) -->
            <form id="drawerInlineEditForm" class="d-none pt-2 border-top mt-2">
              <div class="d-flex justify-content-between align-items-center mb-2.5">
                <h6 class="small fw-bold text-dark mb-0"><i class="bi bi-person-lines-fill text-primary me-1"></i>Edit Profile Details</h6>
                <button type="button" class="btn-close small" id="btnCloseEditForm" style="font-size:0.7rem;" aria-label="Close"></button>
              </div>

              <!-- Photo change row -->
              <div class="p-2 bg-light rounded-3 mb-2.5 d-flex align-items-center justify-content-between border">
                <div class="d-flex align-items-center gap-2">
                  <i class="bi bi-image text-primary"></i>
                  <span class="small fw-semibold text-dark">Profile Photo</span>
                </div>
                <div class="d-flex gap-1">
                  <label for="avatarFileInput" class="btn btn-outline-primary btn-sm py-0 px-2" style="font-size:0.75rem; cursor:pointer;">
                    Upload
                  </label>
                  <button type="button" class="btn btn-light btn-sm py-0 px-2 border" id="btnRemovePhoto" style="font-size:0.75rem;" title="Reset to initials">
                    Reset
                  </button>
                </div>
              </div>

              <!-- Full Name -->
              <div class="mb-2.5">
                <label class="form-label small fw-semibold mb-1" style="font-size:0.75rem;">Full Name</label>
                <input id="editDrawerName" class="form-control form-control-sm" required value="${esc(userName)}" placeholder="e.g. Rahul Sharma">
              </div>

              <!-- Mobile Number with OTP Verification -->
              <div class="mb-2.5">
                <label class="form-label small fw-semibold mb-1 d-flex justify-content-between" style="font-size:0.75rem;">
                  <span>Mobile Number</span>
                  <span id="phoneVerifiedStatusBadge" class="text-success fw-bold" style="font-size:0.7rem;"><i class="bi bi-check-circle-fill"></i> Verified</span>
                </label>
                <div class="input-group input-group-sm">
                  <span class="input-group-text bg-light text-muted small">+91</span>
                  <input id="editDrawerPhone" class="form-control form-control-sm" pattern="[0-9]{10}" required placeholder="10-digit number">
                  <button type="button" class="btn btn-outline-primary btn-sm" id="btnTriggerPhoneOtp">
                    Verify OTP
                  </button>
                </div>

                <!-- OTP Verification Box (shown when changing phone) -->
                <div id="phoneOtpBox" class="mt-2 p-2 bg-light border rounded-3 d-none">
                  <div class="d-flex justify-content-between align-items-center mb-1">
                    <span class="small fw-semibold text-dark"><i class="bi bi-shield-lock-fill text-warning me-1"></i>Enter Verification OTP</span>
                    <span class="badge bg-primary text-white" id="demoOtpBadge" style="font-size:0.65rem;">Code: 4892</span>
                  </div>
                  <div class="input-group input-group-sm mb-1">
                    <input id="inputPhoneOtpCode" class="form-control form-control-sm text-center fw-bold" maxlength="4" placeholder="4-digit OTP">
                    <button type="button" class="btn btn-success btn-sm px-3" id="btnConfirmPhoneOtp">Verify</button>
                  </div>
                  <div class="small text-muted d-flex justify-content-between" style="font-size:0.7rem;">
                    <span>Code sent to entered mobile number</span>
                    <a href="javascript:void(0)" class="text-primary" id="btnResendOtp">Resend (30s)</a>
                  </div>
                  <div id="otpErrorMsg" class="alert alert-danger small py-1 px-2 mt-1 mb-0 d-none" style="font-size:0.72rem;"></div>
                </div>
              </div>

              <!-- Email Address -->
              <div class="mb-2.5">
                <label class="form-label small fw-semibold mb-1" style="font-size:0.75rem;">Email Address</label>
                <input id="editDrawerEmail" type="email" class="form-control form-control-sm" required placeholder="name@example.com">
              </div>

              <!-- Change Password Section inside Edit Profile -->
              <div class="p-2.5 bg-light rounded-3 border mb-2.5">
                <div class="d-flex justify-content-between align-items-center" style="cursor: pointer;" id="btnToggleEditPasswordSection">
                  <span class="small fw-bold text-dark d-flex align-items-center gap-1.5">
                    <i class="bi bi-key-fill text-warning"></i> Change Password
                  </span>
                  <span class="text-primary small fw-semibold" id="lblPwdSectionToggle">Optional <i class="bi bi-chevron-down ms-0.5" id="iconEditPwdChevron"></i></span>
                </div>
                <div id="editPasswordFields" class="d-none pt-2 mt-2 border-top">
                  <div class="mb-2">
                    <label class="form-label small fw-semibold mb-1" style="font-size:0.73rem;">Current Password</label>
                    <input id="editPwdCurrent" type="password" class="form-control form-control-sm" placeholder="Enter current password">
                  </div>
                  <div class="mb-2">
                    <label class="form-label small fw-semibold mb-1" style="font-size:0.73rem;">New Password</label>
                    <input id="editPwdNew" type="password" minlength="6" class="form-control form-control-sm" placeholder="Minimum 6 characters">
                  </div>
                  <div class="mb-1">
                    <label class="form-label small fw-semibold mb-1" style="font-size:0.73rem;">Confirm New Password</label>
                    <input id="editPwdConfirm" type="password" minlength="6" class="form-control form-control-sm" placeholder="Re-enter new password">
                  </div>
                </div>
              </div>

              <div id="editProfileAlert" class="alert small py-1 px-2 mb-2 d-none"></div>

              <div class="d-flex gap-2 justify-content-end pt-1">
                <button type="button" class="btn btn-sm btn-light" id="btnCancelProfileEdit">Cancel</button>
                <button type="submit" class="btn btn-sm btn-primary px-3" id="btnSaveProfileEdit">
                  <i class="bi bi-save me-1"></i> Save Changes
                </button>
              </div>
            </form>
          </div>

          <!-- 3 Quick Action Circles -->
          <div class="row text-center mb-4 g-2">
            <div class="col-4">
              <a href="javascript:void(0)" class="account-action-circle" id="btnDrawerOrders" title="My Bookings &amp; History">
                <i class="bi bi-calendar2-check-fill text-primary"></i>
              </a>
              <div class="account-action-label">My Bookings</div>
            </div>
            <div class="col-4">
              <a href="javascript:void(0)" class="account-action-circle" id="btnDrawerAddress" title="My Addresses">
                <i class="bi bi-geo-alt-fill text-danger"></i>
              </a>
              <div class="account-action-label">My Addresses</div>
            </div>
            <div class="col-4">
              <a href="javascript:void(0)" class="account-action-circle" id="btnDrawerReviews" title="My Reviews &amp; Ratings">
                <i class="bi bi-star-fill text-warning"></i>
              </a>
              <div class="account-action-label">My Reviews</div>
            </div>
          </div>

          <hr class="my-3 opacity-25">

          <!-- Menu List exactly as requested by user (7 items) -->
          <div class="account-menu-list">
            <!-- 1. My Bookings & History -->
            <a href="javascript:void(0)" class="account-menu-item" id="menuOrders">
              <div class="d-flex align-items-center gap-3">
                <span class="account-menu-icon text-primary"><i class="bi bi-calendar-check"></i></span>
                <span>My Bookings &amp; History</span>
              </div>
              <i class="bi bi-chevron-right account-menu-arrow"></i>
            </a>

            <!-- 2. My Addresses -->
            <a href="javascript:void(0)" class="account-menu-item" id="menuAddress">
              <div class="d-flex align-items-center gap-3">
                <span class="account-menu-icon text-danger"><i class="bi bi-geo-alt"></i></span>
                <span>My Addresses</span>
              </div>
              <i class="bi bi-chevron-right account-menu-arrow"></i>
            </a>

            <!-- 3. Find Verified Providers -->
            <a href="/providers.html" class="account-menu-item" id="menuProviders">
              <div class="d-flex align-items-center gap-3">
                <span class="account-menu-icon text-success"><i class="bi bi-people"></i></span>
                <span>Find Verified Providers</span>
              </div>
              <i class="bi bi-chevron-right account-menu-arrow"></i>
            </a>

            <!-- 4. Explore Service Categories -->
            <a href="/services.html" class="account-menu-item" id="menuCategories">
              <div class="d-flex align-items-center gap-3">
                <span class="account-menu-icon text-primary"><i class="bi bi-grid"></i></span>
                <span>Explore Service Categories</span>
              </div>
              <i class="bi bi-chevron-right account-menu-arrow"></i>
            </a>

            <!-- 5. My Reviews & Ratings -->
            <a href="javascript:void(0)" class="account-menu-item" id="menuReviews">
              <div class="d-flex align-items-center gap-3">
                <span class="account-menu-icon text-warning"><i class="bi bi-star"></i></span>
                <span>My Reviews &amp; Ratings</span>
              </div>
              <i class="bi bi-chevron-right account-menu-arrow"></i>
            </a>

            <!-- 6. Offers and Coupons -->
            <a href="javascript:void(0)" class="account-menu-item" id="menuOffers">
              <div class="d-flex align-items-center gap-3">
                <span class="account-menu-icon text-danger"><i class="bi bi-ticket-perforated"></i></span>
                <span>Offers and Coupons</span>
              </div>
              <div class="d-flex align-items-center gap-2">
                <span class="badge bg-danger-subtle text-danger border border-danger-subtle rounded-pill small" style="font-size:0.7rem;">4 Deals</span>
                <i class="bi bi-chevron-right account-menu-arrow"></i>
              </div>
            </a>

            <!-- 7. Help & Customer Support -->
            <a href="javascript:void(0)" class="account-menu-item" id="menuSupport">
              <div class="d-flex align-items-center gap-3">
                <span class="account-menu-icon text-info"><i class="bi bi-headset"></i></span>
                <span>Help &amp; Customer Support</span>
              </div>
              <i class="bi bi-chevron-right account-menu-arrow"></i>
            </a>

            <!-- 8. Log Out -->
            <a href="javascript:void(0)" onclick="Auth.logout()" class="account-menu-item text-danger mt-2 pt-3 border-top" id="menuLogout">
              <div class="d-flex align-items-center gap-3">
                <span class="account-menu-icon text-danger"><i class="bi bi-box-arrow-right"></i></span>
                <span class="fw-bold">Log Out</span>
              </div>
              <i class="bi bi-chevron-right account-menu-arrow text-danger"></i>
            </a>
          </div>
        ` : `
          <!-- Guest Account State -->
          <div class="text-center py-4 bg-light rounded-4 p-4 mb-4 border">
            <div class="user-avatar-circle mx-auto mb-3" style="width: 58px; height: 58px; font-size: 1.5rem;">
              <i class="bi bi-person"></i>
            </div>
            <h5 class="fw-bold text-dark">Welcome to Local Service Connect</h5>
            <p class="small text-muted mb-4">Log in or create an account to book verified electricians, plumbers, cleaners, track doorstep arrivals, and manage your service address.</p>
            <div class="d-grid gap-2">
              <a href="/login.html" class="btn btn-primary fw-semibold py-2">
                <i class="bi bi-box-arrow-in-right me-1"></i> Log in
              </a>
              <a href="/register.html" class="btn btn-outline-primary fw-semibold py-2">
                <i class="bi bi-person-plus me-1"></i> Quick Sign up
              </a>
            </div>
          </div>
          <div class="account-menu-list">
            <a href="/providers.html" class="account-menu-item">
              <div class="d-flex align-items-center gap-3">
                <span class="account-menu-icon text-success"><i class="bi bi-people"></i></span>
                <span>Find Verified Providers</span>
              </div>
              <i class="bi bi-chevron-right account-menu-arrow"></i>
            </a>
            <a href="/services.html" class="account-menu-item">
              <div class="d-flex align-items-center gap-3">
                <span class="account-menu-icon text-primary"><i class="bi bi-grid"></i></span>
                <span>Explore Service Categories</span>
              </div>
              <i class="bi bi-chevron-right account-menu-arrow"></i>
            </a>
            <a href="javascript:void(0)" class="account-menu-item" id="guestMenuOffers">
              <div class="d-flex align-items-center gap-3">
                <span class="account-menu-icon text-danger"><i class="bi bi-ticket-perforated"></i></span>
                <span>Offers and Coupons</span>
              </div>
              <div class="d-flex align-items-center gap-2">
                <span class="badge bg-danger-subtle text-danger border border-danger-subtle rounded-pill small" style="font-size:0.7rem;">4 Deals</span>
                <i class="bi bi-chevron-right account-menu-arrow"></i>
              </div>
            </a>
            <a href="javascript:void(0)" class="account-menu-item" id="guestMenuSupport">
              <div class="d-flex align-items-center gap-3">
                <span class="account-menu-icon text-info"><i class="bi bi-headset"></i></span>
                <span>Help &amp; Customer Support</span>
              </div>
              <i class="bi bi-chevron-right account-menu-arrow"></i>
            </a>
          </div>
        `}
      </div>

      <!-- Sub-View 1: My Bookings & History -->
      <div id="drawerOrdersView" class="d-none">
        <div class="d-flex justify-content-between align-items-center mb-3">
          <h6 class="fw-bold mb-0 text-dark">My Bookings &amp; History</h6>
          <span class="badge bg-primary-subtle text-primary border border-primary-subtle" id="drawerOrdersCountBadge">0 Bookings</span>
        </div>
        <div id="drawerOrdersList">
          <div class="text-center py-4 text-muted small">Loading bookings...</div>
        </div>
      </div>

      <!-- Sub-View 2: My Addresses -->
      <div id="drawerAddressView" class="d-none">
        <h6 class="fw-bold mb-3 text-dark">My Addresses</h6>
        <div class="card p-3 border-0 bg-light rounded-3 mb-3">
          <div class="d-flex align-items-start gap-2 mb-2">
            <i class="bi bi-geo-alt-fill text-danger fs-5"></i>
            <div>
              <div class="fw-bold text-dark" id="drawerAddrArea">Ranchi</div>
              <div class="small text-muted" id="drawerAddrFull">No address saved yet.</div>
            </div>
          </div>
        </div>

        <form id="drawerAddressForm">
          <div class="mb-3">
            <label class="form-label small fw-semibold">House / Flat / Door / Street Details</label>
            <div class="input-group">
              <input id="drawerInputAddress" class="form-control form-control-sm" placeholder="e.g. Flat 302, Green Valley Apartments" required>
              <button type="button" class="btn btn-outline-secondary btn-sm" id="btnDrawerDetectGps" title="Detect GPS">
                <i class="bi bi-crosshair" id="drawerGpsIcon"></i>
                <span class="spinner-border spinner-border-sm d-none" id="drawerGpsSpinner"></span>
              </button>
            </div>
          </div>
          <div class="row g-2 mb-3">
            <div class="col-6">
              <label class="form-label small fw-semibold">Area / Locality</label>
              <input id="drawerInputArea" class="form-control form-control-sm" placeholder="e.g. Lalpur">
            </div>
            <div class="col-6">
              <label class="form-label small fw-semibold">City</label>
              <input id="drawerInputCity" class="form-control form-control-sm" value="Ranchi">
            </div>
          </div>
          <button type="submit" class="btn btn-primary btn-sm w-100 py-2 fw-semibold" id="btnSaveAddress">
            <i class="bi bi-save me-1"></i> Save Address
          </button>
          <div id="drawerAddrMsg" class="alert small mt-2 d-none"></div>
        </form>
      </div>

      <!-- Sub-View 3: My Reviews & Ratings -->
      <div id="drawerReviewsView" class="d-none">
        <div class="d-flex justify-content-between align-items-center mb-3">
          <h6 class="fw-bold mb-0 text-dark">My Reviews &amp; Ratings</h6>
          <span class="badge bg-warning-subtle text-warning border border-warning-subtle" id="drawerReviewsCountBadge">0 Reviews</span>
        </div>
        <div id="drawerReviewsList">
          <div class="text-center py-4 text-muted small">Loading reviews...</div>
        </div>
      </div>

      <!-- Sub-View 4: Offers and Coupons -->
      <div id="drawerOffersView" class="d-none">
        <div class="d-flex justify-content-between align-items-center mb-3">
          <h6 class="fw-bold mb-0 text-dark">Offers and Coupons</h6>
          <span class="badge bg-danger-subtle text-danger border border-danger-subtle">4 Active Offers</span>
        </div>

        <div class="d-flex flex-column gap-3 mb-3" id="drawerOffersList">
          <!-- Offer 1 -->
          <div class="coupon-card">
            <div class="d-flex justify-content-between align-items-start mb-2">
              <div>
                <span class="coupon-badge text-primary bg-primary-subtle"><i class="bi bi-lightning-charge-fill me-1"></i>New Customer</span>
                <h6 class="fw-bold text-dark mt-2 mb-0">Flat ₹100 OFF First Service</h6>
              </div>
              <span class="badge bg-success text-white">SAVE ₹100</span>
            </div>
            <p class="small text-muted mb-2">Valid on Electrician, Plumber, Appliance &amp; Cleaning bookings above ₹399.</p>
            <div class="d-flex justify-content-between align-items-center pt-2 border-top">
              <span class="coupon-code-pill">WELCOME100</span>
              <button type="button" class="btn btn-outline-primary btn-sm py-1 px-3 fw-semibold btn-copy-coupon" data-code="WELCOME100">
                <i class="bi bi-clipboard me-1"></i> Copy Code
              </button>
            </div>
          </div>

          <!-- Offer 2 -->
          <div class="coupon-card">
            <div class="d-flex justify-content-between align-items-start mb-2">
              <div>
                <span class="coupon-badge text-success bg-success-subtle"><i class="bi bi-stars me-1"></i>Deep Cleaning</span>
                <h6 class="fw-bold text-dark mt-2 mb-0">20% OFF Home Cleaning</h6>
              </div>
              <span class="badge bg-success text-white">20% OFF</span>
            </div>
            <p class="small text-muted mb-2">Up to ₹250 discount on full home deep cleaning, kitchen &amp; bathroom sanitation.</p>
            <div class="d-flex justify-content-between align-items-center pt-2 border-top">
              <span class="coupon-code-pill">CLEANFEST</span>
              <button type="button" class="btn btn-outline-primary btn-sm py-1 px-3 fw-semibold btn-copy-coupon" data-code="CLEANFEST">
                <i class="bi bi-clipboard me-1"></i> Copy Code
              </button>
            </div>
          </div>

          <!-- Offer 3 -->
          <div class="coupon-card">
            <div class="d-flex justify-content-between align-items-start mb-2">
              <div>
                <span class="coupon-badge text-warning bg-warning-subtle"><i class="bi bi-snow me-1"></i>Cooling Specialists</span>
                <h6 class="fw-bold text-dark mt-2 mb-0">Flat ₹150 OFF AC Repair</h6>
              </div>
              <span class="badge bg-primary text-white">SAVE ₹150</span>
            </div>
            <p class="small text-muted mb-2">Valid on AC service, jet cleaning, gas recharge, and compressor diagnostics.</p>
            <div class="d-flex justify-content-between align-items-center pt-2 border-top">
              <span class="coupon-code-pill">COOLSUMMER</span>
              <button type="button" class="btn btn-outline-primary btn-sm py-1 px-3 fw-semibold btn-copy-coupon" data-code="COOLSUMMER">
                <i class="bi bi-clipboard me-1"></i> Copy Code
              </button>
            </div>
          </div>

          <!-- Offer 4 -->
          <div class="coupon-card">
            <div class="d-flex justify-content-between align-items-start mb-2">
              <div>
                <span class="coupon-badge text-info bg-info-subtle"><i class="bi bi-speedometer2 me-1"></i>Quick 10-Min Service</span>
                <h6 class="fw-bold text-dark mt-2 mb-0">Flat ₹50 OFF Instant Request</h6>
              </div>
              <span class="badge bg-success text-white">FLAT ₹50</span>
            </div>
            <p class="small text-muted mb-2">Valid on immediate emergency electrical &amp; plumbing quick visits.</p>
            <div class="d-flex justify-content-between align-items-center pt-2 border-top">
              <span class="coupon-code-pill">QUICK50</span>
              <button type="button" class="btn btn-outline-primary btn-sm py-1 px-3 fw-semibold btn-copy-coupon" data-code="QUICK50">
                <i class="bi bi-clipboard me-1"></i> Copy Code
              </button>
            </div>
          </div>
        </div>

        <div class="p-3 bg-light rounded-3 text-center border">
          <p class="small text-muted mb-2">Want to use a coupon code now?</p>
          <a href="/#servicesSection" class="btn btn-primary btn-sm w-100 fw-semibold" onclick="document.getElementById('accountDrawerBackdrop').click()">
            <i class="bi bi-bag-check me-1"></i> Book a Service &amp; Apply Coupon
          </a>
        </div>
      </div>

      <!-- Sub-View 5: Help & Customer Support -->
      <div id="drawerSupportView" class="d-none">
        <h6 class="fw-bold mb-3 text-dark">Help &amp; Customer Support</h6>
        <div class="list-group list-group-flush small mb-3">
          <div class="list-group-item d-flex align-items-center gap-3 px-0">
            <span class="p-2 rounded-circle bg-primary-subtle text-primary"><i class="bi bi-telephone-fill"></i></span>
            <div>
              <strong>Doorstep Helpline</strong>
              <div class="text-muted">1800-123-4567 (8 AM - 9 PM)</div>
            </div>
          </div>
          <div class="list-group-item d-flex align-items-center gap-3 px-0">
            <span class="p-2 rounded-circle bg-success-subtle text-success"><i class="bi bi-whatsapp"></i></span>
            <div>
              <strong>WhatsApp Live Help</strong>
              <div class="text-muted">+91 98765 43210</div>
            </div>
          </div>
          <div class="list-group-item d-flex align-items-center gap-3 px-0">
            <span class="p-2 rounded-circle bg-info-subtle text-info"><i class="bi bi-envelope-fill"></i></span>
            <div>
              <strong>Email Support</strong>
              <div class="text-muted">support@localserviceconnect.com</div>
            </div>
          </div>
        </div>
        <div class="card p-3 border-0 bg-light rounded-3">
          <h6 class="fw-bold mb-1 small text-dark"><i class="bi bi-shield-check text-success me-1"></i> 100% Service Guarantee</h6>
          <p class="small text-muted mb-0">Every provider on Local Service Connect is identity-verified. If you're not satisfied with the work, our support team coordinates a free revisit.</p>
        </div>
      </div>
    </div>
  `;

  // Drawer Toggle Handlers
  let currentSubView = null;

  function openDrawer() {
    drawer.classList.add('show');
    backdrop.classList.add('show');
    document.body.style.overflow = 'hidden';
    loadDrawerCustomerProfile();
  }

  function closeDrawer() {
    drawer.classList.remove('show');
    backdrop.classList.remove('show');
    document.body.style.overflow = '';
    showSubView(null);
  }

  function showSubView(viewId) {
    currentSubView = viewId;
    const views = ['drawerMainView', 'drawerOrdersView', 'drawerAddressView', 'drawerReviewsView', 'drawerOffersView', 'drawerSupportView'];
    views.forEach(v => {
      const el = document.getElementById(v);
      if (el) el.classList.toggle('d-none', viewId ? v !== viewId : v !== 'drawerMainView');
    });

    const titleEl = document.getElementById('accountDrawerTitle');
    if (!viewId || viewId === 'drawerMainView') {
      titleEl.textContent = 'My Account';
    } else if (viewId === 'drawerOrdersView') {
      titleEl.textContent = 'My Bookings & History';
      loadDrawerOrders();
    } else if (viewId === 'drawerAddressView') {
      titleEl.textContent = 'My Addresses';
    } else if (viewId === 'drawerReviewsView') {
      titleEl.textContent = 'My Reviews & Ratings';
      loadDrawerReviews();
    } else if (viewId === 'drawerOffersView') {
      titleEl.textContent = 'Offers and Coupons';
    } else if (viewId === 'drawerSupportView') {
      titleEl.textContent = 'Help & Customer Support';
    }
  }

  // Header back arrow
  const btnClose = document.getElementById('btnCloseAccountDrawer');
  if (btnClose) {
    btnClose.addEventListener('click', () => {
      if (currentSubView && currentSubView !== 'drawerMainView') {
        showSubView(null);
      } else {
        closeDrawer();
      }
    });
  }
  backdrop.addEventListener('click', closeDrawer);

  // Trigger buttons
  const btnOpen = document.getElementById('btnOpenAccountDrawer');
  if (btnOpen) btnOpen.addEventListener('click', openDrawer);
  const btnOpenGuest = document.getElementById('btnOpenAccountDrawerGuest');
  if (btnOpenGuest) btnOpenGuest.addEventListener('click', openDrawer);

  // Sub-view triggers
  const btnOrders = document.getElementById('btnDrawerOrders');
  if (btnOrders) btnOrders.addEventListener('click', () => showSubView('drawerOrdersView'));
  const menuOrders = document.getElementById('menuOrders');
  if (menuOrders) menuOrders.addEventListener('click', () => showSubView('drawerOrdersView'));

  const btnAddress = document.getElementById('btnDrawerAddress');
  if (btnAddress) btnAddress.addEventListener('click', () => showSubView('drawerAddressView'));
  const menuAddress = document.getElementById('menuAddress');
  if (menuAddress) menuAddress.addEventListener('click', () => showSubView('drawerAddressView'));

  const btnReviews = document.getElementById('btnDrawerReviews');
  if (btnReviews) btnReviews.addEventListener('click', () => showSubView('drawerReviewsView'));
  const menuReviews = document.getElementById('menuReviews');
  if (menuReviews) menuReviews.addEventListener('click', () => showSubView('drawerReviewsView'));

  const menuOffers = document.getElementById('menuOffers');
  if (menuOffers) menuOffers.addEventListener('click', () => showSubView('drawerOffersView'));
  const guestOffers = document.getElementById('guestMenuOffers');
  if (guestOffers) guestOffers.addEventListener('click', () => showSubView('drawerOffersView'));

  const menuSupport = document.getElementById('menuSupport');
  if (menuSupport) menuSupport.addEventListener('click', () => showSubView('drawerSupportView'));
  const guestSupport = document.getElementById('guestMenuSupport');
  if (guestSupport) guestSupport.addEventListener('click', () => showSubView('drawerSupportView'));

  // Coupon copy button handler
  drawer.addEventListener('click', (e) => {
    const btn = e.target.closest('.btn-copy-coupon');
    if (btn) {
      const code = btn.getAttribute('data-code');
      if (code && navigator.clipboard) {
        navigator.clipboard.writeText(code).then(() => {
          const original = btn.innerHTML;
          btn.innerHTML = '<i class="bi bi-check2 text-success me-1"></i> Copied!';
          btn.classList.remove('btn-outline-primary');
          btn.classList.add('btn-success', 'text-white');
          setTimeout(() => {
            btn.innerHTML = original;
            btn.classList.remove('btn-success', 'text-white');
            btn.classList.add('btn-outline-primary');
          }, 2000);
        }).catch(() => {
          prompt('Copy coupon code:', code);
        });
      }
    }
  });

  // State for mobile number OTP verification
  let activePhoneOtp = null;
  let isPhoneVerified = true;
  let originalPhone = '';
  let otpCountdownTimer = null;

  // Profile Edit Toggle Handlers
  const btnEdit = document.getElementById('btnToggleProfileEdit');
  const btnQuickEdit = document.getElementById('btnQuickEditTrigger');
  const editForm = document.getElementById('drawerInlineEditForm');
  const contactBox = document.getElementById('drawerContactDetails');
  const btnCancelEdit = document.getElementById('btnCancelProfileEdit');
  const btnCloseEdit = document.getElementById('btnCloseEditForm');

  function toggleProfileEdit(show) {
    if (!editForm || !contactBox) return;
    const isShowing = typeof show === 'boolean' ? show : editForm.classList.contains('d-none');
    editForm.classList.toggle('d-none', !isShowing);
    contactBox.classList.toggle('d-none', isShowing);
    const pwdForm = document.getElementById('drawerPasswordForm');
    if (pwdForm && isShowing) pwdForm.classList.add('d-none');
  }

  if (btnEdit) btnEdit.addEventListener('click', () => toggleProfileEdit());
  if (btnQuickEdit) btnQuickEdit.addEventListener('click', () => toggleProfileEdit(true));
  if (btnCancelEdit) btnCancelEdit.addEventListener('click', () => toggleProfileEdit(false));
  if (btnCloseEdit) btnCloseEdit.addEventListener('click', () => toggleProfileEdit(false));

  // Change Password Section toggle inside Edit Profile Form
  const btnToggleEditPwd = document.getElementById('btnToggleEditPasswordSection');
  const editPwdFields = document.getElementById('editPasswordFields');
  const iconEditPwdChevron = document.getElementById('iconEditPwdChevron');

  if (btnToggleEditPwd && editPwdFields) {
    btnToggleEditPwd.addEventListener('click', () => {
      const willShow = editPwdFields.classList.contains('d-none');
      editPwdFields.classList.toggle('d-none', !willShow);
      if (iconEditPwdChevron) {
        iconEditPwdChevron.className = willShow ? 'bi bi-chevron-up ms-0.5' : 'bi bi-chevron-down ms-0.5';
      }
    });
  }

  // Profile Photo Upload & Reset Handlers
  const avatarInput = document.getElementById('avatarFileInput');
  const btnRemovePhoto = document.getElementById('btnRemovePhoto');

  if (avatarInput) {
    avatarInput.addEventListener('change', async (e) => {
      const file = e.target.files && e.target.files[0];
      if (!file) return;

      const reader = new FileReader();
      reader.onload = async (evt) => {
        const dataUrl = evt.target.result;
        // Preview instantly
        applyAvatarImage(dataUrl);

        try {
          const updated = await api('/customer/profile', {
            method: 'PUT',
            body: { profileImage: dataUrl }
          });
          updateProfileCompletion(updated);
        } catch (err) {
          console.warn('Could not save avatar:', err);
        }
      };
      reader.readAsDataURL(file);
    });
  }

  if (btnRemovePhoto) {
    btnRemovePhoto.addEventListener('click', async () => {
      applyAvatarImage(null);
      try {
        const updated = await api('/customer/profile', {
          method: 'PUT',
          body: { profileImage: '' }
        });
        updateProfileCompletion(updated);
      } catch (err) {
        console.warn('Could not reset avatar:', err);
      }
    });
  }

  function applyAvatarImage(imgUrl) {
    const imgEl = document.getElementById('drawerAvatarImg');
    const initEl = document.getElementById('drawerAvatarInitial');
    if (imgUrl && imgUrl.trim()) {
      if (imgEl) {
        imgEl.src = imgUrl;
        imgEl.classList.remove('d-none');
      }
      if (initEl) initEl.classList.add('d-none');

      // Also update navbar user pill avatar
      const navAvatar = document.querySelector('.user-nav-pill .user-avatar-circle');
      if (navAvatar) {
        navAvatar.innerHTML = `<img src="${imgUrl}" style="width:100%;height:100%;border-radius:50%;object-fit:cover;">`;
      }
    } else {
      if (imgEl) imgEl.classList.add('d-none');
      if (initEl) initEl.classList.remove('d-none');
      const navAvatar = document.querySelector('.user-nav-pill .user-avatar-circle');
      if (navAvatar) {
        navAvatar.textContent = initEl ? initEl.textContent : 'U';
      }
    }
  }

  // Phone Input & OTP Verification Simulation
  const phoneInput = document.getElementById('editDrawerPhone');
  const btnTriggerOtp = document.getElementById('btnTriggerPhoneOtp');
  const phoneOtpBox = document.getElementById('phoneOtpBox');
  const demoOtpBadge = document.getElementById('demoOtpBadge');
  const inputPhoneOtpCode = document.getElementById('inputPhoneOtpCode');
  const btnConfirmPhoneOtp = document.getElementById('btnConfirmPhoneOtp');
  const btnResendOtp = document.getElementById('btnResendOtp');
  const otpErrorMsg = document.getElementById('otpErrorMsg');
  const phoneVerifiedBadge = document.getElementById('phoneVerifiedStatusBadge');

  if (phoneInput) {
    phoneInput.addEventListener('input', () => {
      const val = phoneInput.value.trim();
      if (val !== originalPhone) {
        isPhoneVerified = false;
        if (phoneVerifiedBadge) {
          phoneVerifiedBadge.className = 'text-warning fw-bold';
          phoneVerifiedBadge.innerHTML = '<i class="bi bi-exclamation-circle"></i> Verification Needed';
        }
      } else {
        isPhoneVerified = true;
        if (phoneVerifiedBadge) {
          phoneVerifiedBadge.className = 'text-success fw-bold';
          phoneVerifiedBadge.innerHTML = '<i class="bi bi-check-circle-fill"></i> Verified';
        }
      }
    });
  }

  function triggerPhoneOtp() {
    const val = phoneInput ? phoneInput.value.trim() : '';
    if (!val || val.length !== 10) {
      alert('Please enter a valid 10-digit mobile number.');
      return;
    }
    const code = String(Math.floor(1000 + Math.random() * 9000));
    activePhoneOtp = code;
    if (demoOtpBadge) demoOtpBadge.textContent = `Demo SMS OTP: ${code}`;
    if (phoneOtpBox) phoneOtpBox.classList.remove('d-none');
    if (otpErrorMsg) otpErrorMsg.classList.add('d-none');
    if (inputPhoneOtpCode) {
      inputPhoneOtpCode.value = '';
      inputPhoneOtpCode.focus();
    }

    // 30s countdown
    let secondsLeft = 30;
    if (otpCountdownTimer) clearInterval(otpCountdownTimer);
    if (btnResendOtp) {
      btnResendOtp.textContent = `Resend (${secondsLeft}s)`;
      btnResendOtp.style.pointerEvents = 'none';
      btnResendOtp.classList.add('text-muted');
      otpCountdownTimer = setInterval(() => {
        secondsLeft--;
        if (secondsLeft <= 0) {
          clearInterval(otpCountdownTimer);
          btnResendOtp.textContent = 'Resend Code';
          btnResendOtp.style.pointerEvents = 'auto';
          btnResendOtp.classList.remove('text-muted');
        } else {
          btnResendOtp.textContent = `Resend (${secondsLeft}s)`;
        }
      }, 1000);
    }
  }

  if (btnTriggerOtp) btnTriggerOtp.addEventListener('click', triggerPhoneOtp);
  if (btnResendOtp) btnResendOtp.addEventListener('click', triggerPhoneOtp);

  if (btnConfirmPhoneOtp) {
    btnConfirmPhoneOtp.addEventListener('click', () => {
      const entered = inputPhoneOtpCode ? inputPhoneOtpCode.value.trim() : '';
      if (entered === activePhoneOtp) {
        isPhoneVerified = true;
        originalPhone = phoneInput ? phoneInput.value.trim() : '';
        if (phoneOtpBox) phoneOtpBox.classList.add('d-none');
        if (phoneVerifiedBadge) {
          phoneVerifiedBadge.className = 'text-success fw-bold';
          phoneVerifiedBadge.innerHTML = '<i class="bi bi-check-circle-fill"></i> Verified';
        }
        const chipPhone = document.getElementById('badgePhoneVerified');
        if (chipPhone) {
          chipPhone.className = 'account-clean-tag text-success';
          chipPhone.innerHTML = '<i class="bi bi-check2"></i>Verified';
        }
      } else {
        if (otpErrorMsg) {
          otpErrorMsg.textContent = 'Invalid OTP. Please enter the demo code shown above.';
          otpErrorMsg.classList.remove('d-none');
        }
      }
    });
  }

  // Profile Edit Submit (Includes Name, Phone, Email and optional Password Change)
  if (editForm) {
    editForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const newName = document.getElementById('editDrawerName').value.trim();
      const newPhone = document.getElementById('editDrawerPhone').value.trim();
      const newEmail = document.getElementById('editDrawerEmail').value.trim();
      const editAlert = document.getElementById('editProfileAlert');

      if (!isPhoneVerified) {
        if (editAlert) {
          editAlert.className = 'alert alert-warning small py-1 px-2 mb-2';
          editAlert.textContent = 'Please verify your new mobile number with OTP first.';
          editAlert.classList.remove('d-none');
        }
        return;
      }

      // Check if password change was requested
      const pwdCurrent = document.getElementById('editPwdCurrent') ? document.getElementById('editPwdCurrent').value.trim() : '';
      const pwdNew = document.getElementById('editPwdNew') ? document.getElementById('editPwdNew').value.trim() : '';
      const pwdConfirm = document.getElementById('editPwdConfirm') ? document.getElementById('editPwdConfirm').value.trim() : '';

      if (pwdNew || pwdCurrent || pwdConfirm) {
        if (!pwdCurrent) {
          if (editAlert) {
            editAlert.className = 'alert alert-danger small py-1 px-2 mb-2';
            editAlert.textContent = 'Please enter your current password.';
            editAlert.classList.remove('d-none');
          }
          return;
        }
        if (pwdNew !== pwdConfirm) {
          if (editAlert) {
            editAlert.className = 'alert alert-danger small py-1 px-2 mb-2';
            editAlert.textContent = 'New passwords do not match.';
            editAlert.classList.remove('d-none');
          }
          return;
        }
        if (pwdNew.length < 6) {
          if (editAlert) {
            editAlert.className = 'alert alert-danger small py-1 px-2 mb-2';
            editAlert.textContent = 'New password must be at least 6 characters.';
            editAlert.classList.remove('d-none');
          }
          return;
        }

        try {
          await api('/auth/change-password', {
            method: 'POST',
            body: { currentPassword: pwdCurrent, newPassword: pwdNew }
          });
          document.getElementById('editPwdCurrent').value = '';
          document.getElementById('editPwdNew').value = '';
          document.getElementById('editPwdConfirm').value = '';
        } catch (err) {
          if (editAlert) {
            editAlert.className = 'alert alert-danger small py-1 px-2 mb-2';
            editAlert.textContent = 'Password error: ' + (err.message || 'Could not change password.');
            editAlert.classList.remove('d-none');
          }
          return;
        }
      }

      try {
        const updated = await api('/customer/profile', {
          method: 'PUT',
          body: { name: newName, phone: newPhone, email: newEmail }
        });
        localStorage.setItem('lsc_name', updated.name);
        document.getElementById('drawerDisplayName').textContent = updated.name;
        document.getElementById('drawerDisplayPhone').textContent = updated.phone || newPhone;
        document.getElementById('drawerDisplayEmail').textContent = updated.email || newEmail;
        originalPhone = updated.phone || newPhone;

        // Update top navbar name
        const navName = document.querySelector('.user-nav-pill span.small strong');
        if (navName) navName.textContent = updated.name;

        if (editAlert) {
          editAlert.className = 'alert alert-success small py-1 px-2 mb-2';
          editAlert.textContent = (pwdNew ? 'Profile details and password saved successfully!' : 'Profile details saved successfully!');
          editAlert.classList.remove('d-none');
        }

        updateProfileCompletion(updated);

        setTimeout(() => {
          if (editAlert) editAlert.classList.add('d-none');
          toggleProfileEdit(false);
        }, 1200);
      } catch (err) {
        if (editAlert) {
          editAlert.className = 'alert alert-danger small py-1 px-2 mb-2';
          editAlert.textContent = err.message || 'Could not update profile.';
          editAlert.classList.remove('d-none');
        }
      }
    });
  }

  // Profile Completion Calculation Function with Circular SVG Ring
  function updateProfileCompletion(prof) {
    if (!prof) return;
    let score = 0;

    // 4 key profile metrics: 25% each
    if (prof.name && prof.name.trim()) score += 25;
    if (prof.email && prof.email.trim()) score += 25;
    if (prof.phone && prof.phone.trim()) score += 25;
    if (prof.profileImage && prof.profileImage.trim()) score += 25;

    // Circular SVG Progress Ring (circumference: 175.93 for r=28)
    const C = 175.93;
    const offset = C - (C * score / 100);
    const ringEl = document.getElementById('avatarRingProgress');
    if (ringEl) {
      ringEl.style.strokeDashoffset = offset;
      if (score === 100) {
        ringEl.setAttribute('stroke', '#10b981'); // Emerald green when 100% complete
      } else {
        ringEl.setAttribute('stroke', '#2563eb'); // Royal blue
      }
    }

    const txtEl = document.getElementById('completionPercentText');
    if (txtEl) {
      txtEl.textContent = `${score}%`;
      if (score === 100) {
        txtEl.className = 'badge bg-success-subtle text-success border border-success-subtle py-0.5 px-2 rounded-pill fw-bold';
      } else {
        txtEl.className = 'badge bg-primary-subtle text-primary border border-primary-subtle py-0.5 px-2 rounded-pill fw-bold';
      }
    }

    // Update phone & email verification tags
    const chipPhone = document.getElementById('badgePhoneVerified');
    if (chipPhone) {
      if (prof.phone && prof.phone.trim()) {
        chipPhone.className = 'account-clean-tag text-success';
        chipPhone.innerHTML = '<i class="bi bi-check2"></i>Verified';
      } else {
        chipPhone.className = 'account-clean-tag text-warning';
        chipPhone.innerHTML = '<i class="bi bi-exclamation-circle"></i>Pending';
      }
    }

    const chipEmail = document.getElementById('badgeEmailVerified');
    if (chipEmail) {
      if (prof.email && prof.email.trim()) {
        chipEmail.className = 'account-clean-tag text-success';
        chipEmail.innerHTML = '<i class="bi bi-check2"></i>Active';
      } else {
        chipEmail.className = 'account-clean-tag text-warning';
        chipEmail.innerHTML = '<i class="bi bi-exclamation-circle"></i>Pending';
      }
    }
  }

  // Address Save Form inside Drawer
  const addrForm = document.getElementById('drawerAddressForm');
  if (addrForm) {
    addrForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const addrMsg = document.getElementById('drawerAddrMsg');
      const address = document.getElementById('drawerInputAddress').value.trim();
      const area = document.getElementById('drawerInputArea').value.trim();
      const city = document.getElementById('drawerInputCity').value.trim();
      try {
        const updated = await api('/customer/profile', {
          method: 'PUT',
          body: { address, area, city, formattedAddress: `${address}, ${area}, ${city}` }
        });
        addrMsg.className = 'alert alert-success small mt-2';
        addrMsg.textContent = 'Address saved successfully!';
        addrMsg.classList.remove('d-none');
        document.getElementById('drawerAddrArea').textContent = updated.area || area || 'Ranchi';
        document.getElementById('drawerAddrFull').textContent = updated.formattedAddress || `${address}, ${area}`;
        updateProfileCompletion(updated);
        setTimeout(() => addrMsg.classList.add('d-none'), 3000);
      } catch (err) {
        addrMsg.className = 'alert alert-danger small mt-2';
        addrMsg.textContent = err.message || 'Could not save address.';
        addrMsg.classList.remove('d-none');
      }
    });
  }

  // GPS Detect inside Address subview
  const btnDetectGps = document.getElementById('btnDrawerDetectGps');
  if (btnDetectGps) {
    btnDetectGps.addEventListener('click', () => {
      const icon = document.getElementById('drawerGpsIcon');
      const spinner = document.getElementById('drawerGpsSpinner');
      icon.classList.add('d-none'); spinner.classList.remove('d-none');

      if (!navigator.geolocation) {
        alert('Geolocation not supported');
        icon.classList.remove('d-none'); spinner.classList.add('d-none');
        return;
      }

      navigator.geolocation.getCurrentPosition(
        async (pos) => {
          icon.classList.remove('d-none'); spinner.classList.add('d-none');
          const lat = pos.coords.latitude;
          const lng = pos.coords.longitude;
          try {
            if (window.LSC_MAPS && typeof LSC_MAPS.reverseGeocode === 'function') {
              const geo = await LSC_MAPS.reverseGeocode(lat, lng);
              document.getElementById('drawerInputAddress').value = geo.formattedAddress || `GPS Location (${lat.toFixed(5)}, ${lng.toFixed(5)})`;
              document.getElementById('drawerInputArea').value = geo.area || geo.city || 'Ranchi';
            } else {
              document.getElementById('drawerInputAddress').value = `GPS Pinned (${lat.toFixed(5)}, ${lng.toFixed(5)})`;
            }
          } catch (e) {
            document.getElementById('drawerInputAddress').value = `GPS Pinned (${lat.toFixed(5)}, ${lng.toFixed(5)})`;
          }
        },
        (err) => {
          icon.classList.remove('d-none'); spinner.classList.add('d-none');
          alert('GPS detection failed: ' + err.message);
        },
        { timeout: 8000 }
      );
    });
  }

  // Async load profile contact and saved address
  async function loadDrawerCustomerProfile() {
    if (!isLoggedIn || u.role !== 'CUSTOMER') return;
    try {
      const prof = await api('/customer/profile');
      if (prof) {
        if (prof.name) {
          document.getElementById('drawerDisplayName').textContent = prof.name;
          const editName = document.getElementById('editDrawerName');
          if (editName) editName.value = prof.name;
        }
        if (prof.phone) {
          document.getElementById('drawerDisplayPhone').textContent = prof.phone;
          const editPhone = document.getElementById('editDrawerPhone');
          if (editPhone) editPhone.value = prof.phone;
          originalPhone = prof.phone;
          isPhoneVerified = true;
        }
        if (prof.email) {
          document.getElementById('drawerDisplayEmail').textContent = prof.email;
          const editEmail = document.getElementById('editDrawerEmail');
          if (editEmail) editEmail.value = prof.email;
        }
        if (prof.profileImage) {
          applyAvatarImage(prof.profileImage);
        }
        if (prof.address || prof.formattedAddress) {
          const areaEl = document.getElementById('drawerAddrArea');
          const fullEl = document.getElementById('drawerAddrFull');
          const inputAddr = document.getElementById('drawerInputAddress');
          const inputArea = document.getElementById('drawerInputArea');
          if (areaEl) areaEl.textContent = prof.area || 'Ranchi';
          if (fullEl) fullEl.textContent = prof.formattedAddress || prof.address;
          if (inputAddr) inputAddr.value = prof.address || prof.formattedAddress || '';
          if (inputArea) inputArea.value = prof.area || '';
        }
        // Calculate profile completion (Extra feature)
        updateProfileCompletion(prof);
      }
    } catch (e) {
      console.warn('Could not load drawer profile details:', e);
    }
  }

  // Async load customer bookings
  async function loadDrawerOrders() {
    if (!isLoggedIn) return;
    const listEl = document.getElementById('drawerOrdersList');
    const badgeEl = document.getElementById('drawerOrdersCountBadge');
    try {
      const bookings = await api('/bookings');
      if (!bookings || !bookings.length) {
        listEl.innerHTML = '<div class="text-center py-5 text-muted small bg-light rounded-3">No bookings placed yet.<div class="mt-2"><a href="/#servicesSection" class="btn btn-primary btn-sm" onclick="document.getElementById(\'accountDrawerBackdrop\').click()">Book a Service</a></div></div>';
        if (badgeEl) badgeEl.textContent = '0 Orders';
        return;
      }
      if (badgeEl) badgeEl.textContent = `${bookings.length} Order${bookings.length === 1 ? '' : 's'}`;
      listEl.innerHTML = bookings.map(b => `
        <div class="card p-3 border rounded-3 mb-2 shadow-xs">
          <div class="d-flex justify-content-between align-items-start mb-1">
            <h6 class="fw-bold mb-0 text-dark">${esc(b.serviceName || 'Service')}</h6>
            <span class="badge ${b.status === 'COMPLETED' ? 'bg-success' : (b.status === 'CANCELLED' ? 'bg-danger' : 'bg-primary')} small" style="font-size:0.7rem;">
              ${esc(b.status || 'BOOKED')}
            </span>
          </div>
          <div class="small text-muted mb-2">
            <i class="bi bi-person-fill text-secondary me-1"></i>${esc(b.providerBusinessName || b.businessName || 'Verified Specialist')}
          </div>
          <div class="d-flex justify-content-between align-items-center pt-2 border-top small">
            <span class="text-muted"><i class="bi bi-clock me-1"></i>${b.bookingDate || 'Quick Service'}</span>
            <a href="/booking-status.html?id=${b.id}" class="btn btn-outline-primary btn-sm py-0 px-2 fw-semibold" style="font-size:0.75rem;">
              Track Order <i class="bi bi-arrow-right"></i>
            </a>
          </div>
        </div>
      `).join('');
    } catch (err) {
      listEl.innerHTML = '<div class="text-muted small py-3 text-center">Could not load bookings list.</div>';
    }
  }

  // Async load customer reviews & unreviewed bookings
  async function loadDrawerReviews() {
    if (!isLoggedIn) return;
    const listEl = document.getElementById('drawerReviewsList');
    const badgeEl = document.getElementById('drawerReviewsCountBadge');
    try {
      let customerReviews = [];
      try {
        customerReviews = await api('/customer/reviews');
      } catch (e) {
        customerReviews = [];
      }

      let bookings = [];
      try {
        bookings = await api('/bookings');
      } catch (e) {
        bookings = [];
      }

      const completedUnreviewed = (bookings || []).filter(b => b.status === 'COMPLETED' && !b.reviewed);

      if ((!customerReviews || !customerReviews.length) && (!completedUnreviewed || !completedUnreviewed.length)) {
        listEl.innerHTML = `
          <div class="text-center py-5 text-muted small bg-light rounded-3">
            <i class="bi bi-star text-warning fs-2 d-block mb-2"></i>
            No reviews yet. When your service appointments are completed, you can rate and review your specialists here!
            <div class="mt-3">
              <a href="/providers.html" class="btn btn-outline-primary btn-sm">Find Providers</a>
            </div>
          </div>
        `;
        if (badgeEl) badgeEl.textContent = '0 Reviews';
        return;
      }

      let html = '';
      if (badgeEl) badgeEl.textContent = `${(customerReviews || []).length} Review${customerReviews.length === 1 ? '' : 's'}`;

      if (completedUnreviewed && completedUnreviewed.length > 0) {
        html += `
          <div class="alert alert-warning border-0 rounded-3 p-3 mb-3 small shadow-xs">
            <strong class="d-block mb-1 text-dark"><i class="bi bi-star-fill text-warning me-1"></i> Rate Completed Services</strong>
            <span class="text-muted">You have ${completedUnreviewed.length} completed booking(s) ready for your rating.</span>
          </div>
        `;
        completedUnreviewed.forEach(b => {
          html += `
            <div class="card p-3 border rounded-3 mb-3 shadow-xs bg-light">
              <div class="d-flex justify-content-between align-items-center mb-1">
                <strong class="text-dark small">${esc(b.serviceName || 'Service')}</strong>
                <span class="badge bg-success-subtle text-success small">Completed</span>
              </div>
              <div class="small text-muted mb-2">
                Specialist: <strong>${esc(b.providerBusinessName || b.businessName || 'Verified Specialist')}</strong>
              </div>
              <a href="/provider-profile.html?id=${b.providerId}#reviewsSection" class="btn btn-warning btn-sm fw-semibold w-100 py-1" style="font-size:0.8rem;">
                <i class="bi bi-star-fill me-1"></i> Rate This Specialist
              </a>
            </div>
          `;
        });
      }

      if (customerReviews && customerReviews.length > 0) {
        html += `<h6 class="small fw-bold text-muted text-uppercase mb-2">Your Submitted Ratings</h6>`;
        customerReviews.forEach(r => {
          const stars = '★'.repeat(r.rating || 5) + '☆'.repeat(Math.max(0, 5 - (r.rating || 5)));
          const dateStr = r.createdAt ? new Date(r.createdAt).toLocaleDateString() : '';
          html += `
            <div class="card p-3 border rounded-3 mb-2 shadow-xs">
              <div class="d-flex justify-content-between align-items-center mb-1">
                <strong class="text-dark small">${esc(r.providerBusinessName || 'Verified Specialist')}</strong>
                <span class="text-warning fw-bold small">${stars}</span>
              </div>
              <p class="small text-muted mb-1 fst-italic">"${esc(r.review || 'Great service experience!')}"</p>
              <div class="text-end text-muted small" style="font-size:0.75rem;">${dateStr}</div>
            </div>
          `;
        });
      }

      listEl.innerHTML = html;
    } catch (err) {
      listEl.innerHTML = '<div class="text-muted small py-3 text-center">Could not load reviews.</div>';
    }
  }
}

function adminNav(active = '') {
  const navEl = document.getElementById('nav');
  if (!navEl) return;
  const me = Auth.get();
  navEl.innerHTML = `
    <nav class="navbar navbar-expand-lg sticky-top mb-4 site-navbar shadow-sm" id="adminSiteNav">
      <div class="container-xl">
        <a class="brand h4 mb-0 me-4 text-decoration-none" href="/admin/dashboard.html">
          <span class="brand-icon-box" style="background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);">
            <i class="bi bi-shield-lock-fill"></i>
          </span>
          <span>Admin Console</span>
        </a>
        <button class="navbar-toggler border-0" type="button" data-bs-toggle="collapse" data-bs-target="#adminNavContent" aria-label="Toggle navigation">
          <span class="navbar-toggler-icon"></span>
        </button>
        <div class="collapse navbar-collapse" id="adminNavContent">
          <ul class="navbar-nav me-auto mb-2 mb-lg-0 gap-lg-1">
            <li class="nav-item">
              <a class="nav-link ${active === 'dashboard' ? 'active' : ''}" href="/admin/dashboard.html">
                <i class="bi bi-speedometer2 me-1"></i> Dashboard
              </a>
            </li>
            <li class="nav-item">
              <a class="nav-link ${active === 'services' ? 'active' : ''}" href="/admin/services.html">
                <i class="bi bi-grid me-1"></i> Services
              </a>
            </li>
            <li class="nav-item">
              <a class="nav-link ${active === 'providers' ? 'active' : ''}" href="/admin/providers.html">
                <i class="bi bi-tools me-1"></i> Providers
              </a>
            </li>
            <li class="nav-item">
              <a class="nav-link ${active === 'customers' ? 'active' : ''}" href="/admin/customers.html">
                <i class="bi bi-people me-1"></i> Customers
              </a>
            </li>
            <li class="nav-item">
              <a class="nav-link ${active === 'bookings' ? 'active' : ''}" href="/admin/bookings.html">
                <i class="bi bi-journal-text me-1"></i> Bookings
              </a>
            </li>
            <li class="nav-item">
              <a class="nav-link ${active === 'reviews' ? 'active' : ''}" href="/admin/reviews.html">
                <i class="bi bi-star-half me-1"></i> Reviews
              </a>
            </li>
            <li class="nav-item">
              <a class="nav-link ${active === 'activity-logs' ? 'active' : ''}" href="/admin/activity-logs.html">
                <i class="bi bi-clock-history me-1"></i> Activity Log
              </a>
            </li>
            <li class="nav-item dropdown d-none d-xl-block">
              <a class="nav-link dropdown-toggle text-muted" href="#" role="button" data-bs-toggle="dropdown">
                <i class="bi bi-globe me-1"></i> Public
              </a>
              <ul class="dropdown-menu dropdown-menu-end shadow-sm">
                <li><a class="dropdown-item" href="/services.html" target="_blank"><i class="bi bi-grid me-2"></i>Service Catalog</a></li>
                <li><a class="dropdown-item" href="/providers.html" target="_blank"><i class="bi bi-person-lines-fill me-2"></i>Find Providers</a></li>
              </ul>
            </li>
          </ul>
          <div class="d-flex align-items-center gap-2 mt-2 mt-lg-0">
            <!-- Admin Notification Bell -->
            <div class="dropdown me-1" id="adminNotifDropdownContainer">
              <button class="btn btn-light position-relative rounded-circle p-2 shadow-xs border" id="navNotifBtn" type="button" data-bs-toggle="dropdown" aria-expanded="false" title="Administrator Notifications">
                <i class="bi bi-bell-fill text-secondary fs-6"></i>
                <span class="position-absolute top-0 start-100 translate-middle badge rounded-pill bg-danger d-none shadow-sm" id="navNotifBadge" style="font-size: 0.65rem;">0</span>
              </button>
              <div class="dropdown-menu dropdown-menu-end shadow-lg border-0 rounded-4 p-0 mt-2" id="navNotifMenu" style="width: 360px; max-width: 90vw; z-index: 1060;">
                <div class="d-flex align-items-center justify-content-between p-3 border-bottom bg-light rounded-top-4">
                  <div class="d-flex align-items-center gap-2">
                    <span class="fw-bold text-dark fs-6"><i class="bi bi-bell-fill text-danger me-1"></i>Notifications</span>
                    <span class="badge bg-danger rounded-pill d-none" id="navNotifUnreadCount">0 new</span>
                  </div>
                  <button type="button" class="btn btn-link btn-sm text-decoration-none p-0 small fw-semibold text-primary d-none" id="btnMarkAllNotifsRead" onclick="NotificationSystem.markAllRead()">
                    Mark all read
                  </button>
                </div>
                <div class="notif-dropdown-list overflow-auto" id="navNotifList" style="max-height: 380px;">
                  <div class="p-3 text-center text-muted">
                    <span class="spinner-border spinner-border-sm text-primary me-2"></span>
                    <span class="small">Loading notifications...</span>
                  </div>
                </div>
                <div class="p-2.5 border-top bg-light rounded-bottom-4 text-center">
                  <a href="/admin/notifications.html" class="btn btn-outline-primary btn-sm rounded-pill w-100 fw-semibold">
                    <i class="bi bi-view-list me-1"></i>Open Notification Center
                  </a>
                </div>
              </div>
            </div>

            <div class="user-nav-pill">
              <span class="user-avatar-circle" style="background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);">A</span>
              <span class="d-none d-md-inline small text-muted"><strong>${esc(me.name || 'Admin')}</strong></span>
              <span class="badge bg-danger text-uppercase">ADMIN</span>
            </div>
            <button onclick="Auth.logout()" class="btn btn-outline-danger btn-sm">
              <i class="bi bi-box-arrow-right me-1"></i>Logout
            </button>
          </div>
        </div>
      </div>
    </nav>
  `;

  // Initialize real-time NotificationSystem for Administrator
  if (me && me.token && window.NotificationSystem) {
    window.NotificationSystem.init(me);
  }
}

/* ==========================================================================
   FAVORITES MANAGER (Synchronized State & Backend Authoritative)
   ========================================================================== */
const Favorites = {
  _cache: new Set(),
  _loaded: false,
  _loadingPromise: null,

  async loadCache(force = false) {
    if (!Auth.isCustomer()) {
      this._cache.clear();
      this._loaded = true;
      return this._cache;
    }
    if (this._loaded && !force) {
      return this._cache;
    }
    if (this._loadingPromise && !force) {
      return this._loadingPromise;
    }

    this._loadingPromise = (async () => {
      try {
        const res = await api('/favorites/ids');
        const ids = (res && Array.isArray(res.favoriteProviderIds)) ? res.favoriteProviderIds : [];
        this._cache = new Set(ids.map(Number));
        this._loaded = true;
      } catch (err) {
        console.warn('Could not load favorites cache:', err);
      } finally {
        this._loadingPromise = null;
      }
      return this._cache;
    })();

    return this._loadingPromise;
  },

  isFavorite(providerId) {
    return this._cache.has(Number(providerId));
  },

  async toggle(providerId, btnEl) {
    const pId = Number(providerId);
    if (!pId) return;

    if (!Auth.isCustomer()) {
      const redirect = encodeURIComponent(window.location.pathname + window.location.search);
      if (typeof showToast === 'function') {
        showToast('Please log in as a customer to bookmark favorite providers.', 'info', 'Authentication Required');
      }
      setTimeout(() => {
        window.location.href = '/login.html?redirect=' + redirect;
      }, 700);
      return;
    }

    if (btnEl && btnEl.dataset.loading === 'true') {
      return;
    }

    const wasFav = this.isFavorite(pId);
    const newFav = !wasFav;

    if (newFav) {
      this._cache.add(pId);
    } else {
      this._cache.delete(pId);
    }
    this.updateButtons(pId, newFav, true);

    if (btnEl) btnEl.dataset.loading = 'true';

    try {
      const res = await api(`/favorites/${pId}`, { method: 'POST' });
      const confirmedFav = !!(res && res.isFavorite);
      if (confirmedFav) {
        this._cache.add(pId);
      } else {
        this._cache.delete(pId);
      }
      this.updateButtons(pId, confirmedFav, false);

      const msg = confirmedFav ? 'Added to your favorites ❤️' : 'Removed from favorites';
      if (typeof showToast === 'function') {
        showToast(msg, 'success', 'Favorites Updated');
      }

      window.dispatchEvent(new CustomEvent('lsc:favorites-changed', {
        detail: { providerId: pId, isFavorite: confirmedFav }
      }));
    } catch (err) {
      if (wasFav) {
        this._cache.add(pId);
      } else {
        this._cache.delete(pId);
      }
      this.updateButtons(pId, wasFav, false);
      const errMsg = err.message || 'Failed to update favorite. Please try again.';
      if (typeof showToast === 'function') {
        showToast(errMsg, 'error', 'Favorites Error');
      } else {
        alert(errMsg);
      }
    } finally {
      if (btnEl) btnEl.dataset.loading = 'false';
    }
  },

  updateButtons(providerId, isFav, animate = false) {
    const pId = Number(providerId);
    const selector = `[data-fav-provider="${pId}"]`;
    document.querySelectorAll(selector).forEach(btn => {
      if (isFav) {
        btn.classList.add('active');
        btn.setAttribute('aria-label', 'Remove from favorites');
        btn.setAttribute('title', 'Remove from favorites');
        const icon = btn.querySelector('i');
        if (icon) {
          icon.className = 'bi bi-heart-fill text-danger';
        }
        const text = btn.querySelector('.fav-btn-text');
        if (text) text.textContent = 'Favorited';
      } else {
        btn.classList.remove('active');
        btn.setAttribute('aria-label', 'Add to favorites');
        btn.setAttribute('title', 'Add to favorites');
        const icon = btn.querySelector('i');
        if (icon) {
          icon.className = 'bi bi-heart';
        }
        const text = btn.querySelector('.fav-btn-text');
        if (text) text.textContent = 'Add to Favorites';
      }

      if (animate) {
        btn.classList.remove('anim-heart-pop');
        void btn.offsetWidth;
        btn.classList.add('anim-heart-pop');
      }
    });
  },

  syncAllButtons() {
    document.querySelectorAll('[data-fav-provider]').forEach(btn => {
      const pId = Number(btn.getAttribute('data-fav-provider'));
      const isFav = this.isFavorite(pId);
      this.updateButtons(pId, isFav, false);
    });
  }
};

window.addEventListener('lsc:favorites-changed', (e) => {
  if (e.detail && e.detail.providerId) {
    Favorites.updateButtons(e.detail.providerId, e.detail.isFavorite, false);
  }
});

/* ==========================================================================
   SHARE PROVIDER MANAGER (Canonical URLs, WhatsApp, Telegram, Email, Copy Link)
   ========================================================================== */
const ShareProvider = {
  getCanonicalUrl(providerId) {
    const origin = (typeof window !== 'undefined' && window.location && window.location.origin)
      ? window.location.origin
      : 'http://localhost:8080';
    return `${origin}/provider-profile.html?id=${encodeURIComponent(providerId)}`;
  },

  async openShareModal(provider) {
    if (!provider || !provider.id) return;

    const id = provider.id;
    const name = provider.businessName || provider.name || 'Verified Specialist';
    const location = provider.formattedAddress || (Array.isArray(provider.areas) && provider.areas[0] ? provider.areas[0].area : 'Ranchi');
    const url = this.getCanonicalUrl(id);
    const title = `${name} · Local Service Connect`;
    const message = `Check out ${name} on Local Service Connect for verified doorstep services in ${location}: ${url}`;

    let modalEl = document.getElementById('lscShareModal');
    if (!modalEl) {
      modalEl = document.createElement('div');
      modalEl.id = 'lscShareModal';
      modalEl.className = 'modal fade';
      modalEl.tabIndex = -1;
      modalEl.setAttribute('aria-hidden', 'true');
      modalEl.innerHTML = `
        <div class="modal-dialog modal-dialog-centered" style="max-width: 440px;">
          <div class="modal-content border-0 shadow-lg rounded-4">
            <div class="modal-header border-0 pb-0 pt-4 px-4">
              <div class="d-flex align-items-center gap-2">
                <div class="user-avatar-circle" style="width: 38px; height: 38px; background: #ede9fe; color: #7c3aed; font-size: 1.1rem;">
                  <i class="bi bi-share-fill"></i>
                </div>
                <div>
                  <h5 class="modal-title fw-bold text-dark mb-0" id="shareModalTitle">Share Specialist</h5>
                  <p class="small text-muted mb-0">Recommend to friends and neighbors</p>
                </div>
              </div>
              <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <div class="modal-body p-4">
              <div class="p-3 rounded-3 mb-3 border bg-light d-flex align-items-center gap-3">
                <div id="shareModalAvatar" class="provider-avatar" style="width: 42px; height: 42px; font-size: 1.2rem;">P</div>
                <div class="min-w-0 flex-grow-1">
                  <div class="d-flex align-items-center gap-1">
                    <span class="fw-bold text-dark text-truncate d-block" id="shareModalBizName">Provider Name</span>
                    <i class="bi bi-patch-check-fill text-success" title="Verified Provider"></i>
                  </div>
                  <span class="small text-muted text-truncate d-block" id="shareModalLocation">Ranchi</span>
                </div>
              </div>

              <div class="row g-2 mb-3">
                <div class="col-6">
                  <a href="#" target="_blank" rel="noopener noreferrer" id="shareBtnWhatsapp" class="share-channel-btn share-channel-whatsapp">
                    <div class="share-channel-icon"><i class="bi bi-whatsapp"></i></div>
                    <span>WhatsApp</span>
                  </a>
                </div>
                <div class="col-6">
                  <a href="#" target="_blank" rel="noopener noreferrer" id="shareBtnTelegram" class="share-channel-btn share-channel-telegram">
                    <div class="share-channel-icon"><i class="bi bi-telegram"></i></div>
                    <span>Telegram</span>
                  </a>
                </div>
                <div class="col-6">
                  <a href="#" id="shareBtnEmail" class="share-channel-btn share-channel-email">
                    <div class="share-channel-icon"><i class="bi bi-envelope-fill"></i></div>
                    <span>Email</span>
                  </a>
                </div>
                <div class="col-6" id="shareNativeCol">
                  <button type="button" id="shareBtnNative" class="share-channel-btn share-channel-native">
                    <div class="share-channel-icon"><i class="bi bi-share"></i></div>
                    <span>More Options</span>
                  </button>
                </div>
              </div>

              <label class="form-label small fw-bold text-dark mb-1">Permanent Profile Link</label>
              <div class="input-group input-group-sm">
                <input type="text" id="shareCopyInput" class="form-control bg-white font-monospace" style="font-size: 0.78rem;" readonly>
                <button type="button" class="btn btn-primary fw-semibold px-3" id="btnShareCopy">
                  <i class="bi bi-copy me-1" id="shareCopyIcon"></i><span id="shareCopyLabel">Copy</span>
                </button>
              </div>
              <div id="shareCopyFeedback" class="small text-success mt-1.5 fw-semibold d-none">
                <i class="bi bi-check-circle-fill me-1"></i>Provider link copied to clipboard!
              </div>
            </div>
          </div>
        </div>
      `;
      document.body.appendChild(modalEl);
    }

    document.getElementById('shareModalBizName').textContent = name;
    document.getElementById('shareModalLocation').textContent = location;
    const initial = (name ? name.charAt(0) : 'P').toUpperCase();
    const avatarEl = document.getElementById('shareModalAvatar');
    if (provider.profileImage) {
      avatarEl.innerHTML = `<img src="${esc(provider.profileImage)}" alt="${esc(name)}" style="width: 42px; height: 42px; object-fit: cover; border-radius: 10px;">`;
    } else {
      avatarEl.innerHTML = esc(initial);
    }

    const copyInput = document.getElementById('shareCopyInput');
    copyInput.value = url;

    const copyFeedback = document.getElementById('shareCopyFeedback');
    copyFeedback.classList.add('d-none');
    document.getElementById('shareCopyIcon').className = 'bi bi-copy me-1';
    document.getElementById('shareCopyLabel').textContent = 'Copy';

    const waUrl = `https://api.whatsapp.com/send?text=${encodeURIComponent(message)}`;
    document.getElementById('shareBtnWhatsapp').href = waUrl;

    const tgUrl = `https://t.me/share/url?url=${encodeURIComponent(url)}&text=${encodeURIComponent('Check out ' + name + ' on Local Service Connect')}`;
    document.getElementById('shareBtnTelegram').href = tgUrl;

    const emailSubject = encodeURIComponent(`Recommended Service Professional: ${name}`);
    const emailBody = encodeURIComponent(`Hi,\n\nI wanted to share this verified service professional with you on Local Service Connect:\n\n${name}\nLocation: ${location}\nProfile & Booking: ${url}\n\nHope this helps!`);
    document.getElementById('shareBtnEmail').href = `mailto:?subject=${emailSubject}&body=${emailBody}`;

    const nativeBtn = document.getElementById('shareBtnNative');
    if (navigator.share) {
      nativeBtn.onclick = async () => {
        try {
          await navigator.share({ title, text: `Check out ${name} on Local Service Connect:`, url });
        } catch (err) {
          if (err.name !== 'AbortError') {
            console.warn('Native share failed:', err);
          }
        }
      };
    } else {
      nativeBtn.onclick = () => {
        this.copyToClipboard(url);
      };
    }

    document.getElementById('btnShareCopy').onclick = () => {
      this.copyToClipboard(url);
    };

    if (typeof bootstrap !== 'undefined' && bootstrap.Modal) {
      const bsModal = bootstrap.Modal.getOrCreateInstance(modalEl);
      bsModal.show();
    } else {
      modalEl.classList.add('show', 'd-block');
    }
  },

  async copyToClipboard(text) {
    let success = false;
    if (navigator.clipboard && navigator.clipboard.writeText) {
      try {
        await navigator.clipboard.writeText(text);
        success = true;
      } catch (e) {
        success = false;
      }
    }
    if (!success) {
      try {
        const ta = document.createElement('textarea');
        ta.value = text;
        ta.style.position = 'fixed';
        ta.style.opacity = '0';
        document.body.appendChild(ta);
        ta.focus();
        ta.select();
        success = document.execCommand('copy');
        document.body.removeChild(ta);
      } catch (e) {
        success = false;
      }
    }

    const copyFeedback = document.getElementById('shareCopyFeedback');
    const copyIcon = document.getElementById('shareCopyIcon');
    const copyLabel = document.getElementById('shareCopyLabel');

    if (success) {
      if (copyFeedback) copyFeedback.classList.remove('d-none');
      if (copyIcon) copyIcon.className = 'bi bi-check2 me-1';
      if (copyLabel) copyLabel.textContent = 'Copied!';
      if (typeof showToast === 'function') {
        showToast('Provider profile link copied to clipboard!', 'success', 'Link Copied');
      }
      setTimeout(() => {
        if (copyIcon) copyIcon.className = 'bi bi-copy me-1';
        if (copyLabel) copyLabel.textContent = 'Copy';
      }, 3000);
    } else {
      if (typeof showToast === 'function') {
        showToast('Failed to copy link. Please manually copy the URL.', 'error', 'Copy Failed');
      }
    }
  }
};

