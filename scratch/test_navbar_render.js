const fs = require('fs');

// Create minimal browser-like global environment
global.window = global;
global.window.addEventListener = function() {};
global.window.removeEventListener = function() {};
global.document = {
  getElementById: function(id) {
    if (id === 'nav') return mockNav;
    if (id === 'mainSiteNav') return mockMainSiteNav;
    return null;
  },
  querySelector: function() { return null; },
  querySelectorAll: function() { return []; },
  addEventListener: function() {},
  body: { classList: { add() {}, remove() {}, toggle() {}, contains() { return false; } } }
};

const mockNav = {
  id: 'nav',
  innerHTML: '',
  classList: { add() {}, remove() {}, toggle() {} }
};

const mockMainSiteNav = {
  id: 'mainSiteNav',
  classList: { add() {}, remove() {}, toggle() {} },
  style: {}
};

global.location = {
  pathname: '/',
  search: '',
  href: 'http://localhost:8080/'
};

global.localStorage = {
  getItem: () => null,
  setItem: () => {},
  removeItem: () => {}
};

global.sessionStorage = {
  getItem: () => null,
  setItem: () => {},
  removeItem: () => {}
};

// Load api.js
try {
  const apiCode = fs.readFileSync('frontend/js/api.js', 'utf8');
  eval(apiCode);
  console.log('api.js evaluated successfully');
  
  if (typeof navbar === 'function') {
    navbar();
    console.log('navbar() executed without throwing!');
    console.log('mockNav.innerHTML length:', mockNav.innerHTML.length);
    console.log('mockNav.innerHTML snippet:\n', mockNav.innerHTML.slice(0, 300));
  } else {
    console.error('navbar is not a function!');
  }
} catch (err) {
  console.error('Error executing navbar():', err);
}
