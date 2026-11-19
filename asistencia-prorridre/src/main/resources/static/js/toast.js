// ============================================================
// PRORRIDRE — Toast (sistema de notificaciones)
// ============================================================
const Toast = (() => {
  let container;
  function _init() {
    if (!container) {
      container = document.createElement('div');
      container.className = 'toast-container';
      document.body.appendChild(container);
    }
  }
  function _show(msg, type, duracion = 3500) {
    _init();
    const t = document.createElement('div');
    t.className = `toast ${type}`;
    const icons = { success: '✓', error: '✕', warn: '⚠' };
    t.innerHTML = `<strong>${icons[type] || '•'}</strong> ${msg}`;
    container.appendChild(t);
    setTimeout(() => {
      t.style.opacity = '0';
      t.style.transition = 'opacity .3s';
      setTimeout(() => container.removeChild(t), 300);
    }, duracion);
  }
  return {
    ok:    (msg) => _show(msg, 'success'),
    error: (msg) => _show(msg, 'error', 5000),
    warn:  (msg) => _show(msg, 'warn')
  };
})();

